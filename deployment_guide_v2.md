# DevOps Edition v2: Deploying the Modified Telegram-SMS App with Corrected GitHub Actions

**Documented at: 4:00 PM UTC, October 26, 2023**

This guide focuses on deploying the modified Telegram-SMS application, primarily using GitHub Releases and GitHub Actions for automation. It incorporates corrections to the GitHub Actions workflow for more robust NDK installation and SDK tool path handling.

## Introduction

The purpose of this guide is to provide instructions for:
1.  Manually creating a GitHub Release and attaching the application's APK.
2.  Automating the build, sign, and release process using the corrected GitHub Actions workflow.
3.  Understanding how to publish the APK to GitHub Packages (with updated guidance).

This guide assumes you have already gone through the process of modifying the Telegram-SMS app, including setting up Device Administration, secret key protection, and potentially hiding the app icon.

## Prerequisites

Before you begin, ensure you have the following:
*   **GitHub Repository:** Your modified Telegram-SMS app code hosted on GitHub.
*   **GitHub Personal Access Token (PAT):** If you intend to use command-line tools to interact with the GitHub API (not strictly required for the GitHub Actions web UI setup of secrets). Ensure it has `repo` scope for creating releases and `write:packages` for publishing packages.
*   **Signed APK (`app-release.apk`):** For manual release, you'll need a signed APK. The automated workflow will generate this.
*   **Java Development Kit (JDK):** Required for signing APKs and running Gradle commands locally (ensure version matches project, e.g., JDK 17).
*   **Android SDK Tools:** Required for building the app locally. Android Studio provides these.
*   **`keytool`:** A command-line tool (part of JDK) for managing keystores.
*   **`base64`:** A command-line tool for encoding the keystore.
*   **Your Android Keystore File (e.g., `test-telegram-sms.jks`):** The JKS file used to sign your application.
*   **Keystore Passwords and Alias:** Store password, key alias, and key password for your keystore.

## Part 1: Manual GitHub Release

This method is suitable for one-off releases or if you prefer a manual process.

1.  **Navigate to Your Repository on GitHub.**
2.  **Go to "Releases":** On the right sidebar (or under the "Code" tab), click on "Releases."
3.  **Create a New Release:** Click on "Create a new release" or "Draft a new release."
4.  **Tag Version:**
    *   Click "Choose a tag."
    *   Type a new version tag that matches your release (e.g., `v1.0.2`). Use semantic versioning.
    *   Click "+ Create new tag: vX.X.X on publish."
5.  **Release Title:** Enter a title for your release (e.g., "Release v1.0.2").
6.  **Description:**
    *   Provide a summary of changes. Example:
        ```markdown
        ### Release v1.0.2 - Corrected CI/CD Workflow

        This release includes:
        - Updates to the GitHub Actions workflow for more reliable NDK installation.
        - All previous features (Device Admin, Secret Key, Icon Hiding) remain.

        **Note:** This build is signed with a test key.
        ```
7.  **Attach APK:** Drag and drop your signed `app-release.apk` into the designated area.
8.  **Pre-release (Optional):** Check if it's a pre-release.
9.  **Publish Release:** Click "Publish release."

## Part 2: Automated Deployment with GitHub Actions (Corrected)

This automates the build, sign, and release process using a corrected GitHub Actions workflow.

### A. GitHub Secrets Setup

Store your sensitive signing information as encrypted secrets in your GitHub repository.
1.  **Navigate to your GitHub repository.**
2.  Go to **Settings > Secrets and variables > Actions**.
3.  Click **New repository secret** for each:
    *   **`SIGNING_KEYSTORE_BASE64`**: Base64 encoded content of your `.jks` file (e.g., `base64 -w 0 your_keystore.jks`).
    *   **`SIGNING_KEYSTORE_PASSWORD`**: Your keystore password.
    *   **`SIGNING_KEY_ALIAS`**: The alias for your key.
    *   **`SIGNING_KEY_PASSWORD`**: The password for your key alias.

### B. Configure `build.gradle` for CI Signing (Verify)

Ensure your `app/build.gradle` file is configured to use environment variables for signing, as detailed in previous tutorials. The key part is the `signingConfigs.release` block:

```gradle
// In app/build.gradle
android {
    // ... other configurations ...

    signingConfigs {
        release {
            // Configured at 2:30 PM UTC, October 26, 2023 - Prioritize ENV VARS for CI, fallback to gradle.properties
            try {
                storeFile file(System.getenv('SIGNING_KEYSTORE_FILE_PATH') ?: project.properties['TELEGRAM_SMS_KEYSTORE_FILE'] ?: 'test-telegram-sms.jks')
                storePassword System.getenv('SIGNING_KEYSTORE_PASSWORD') ?: project.properties['TELEGRAM_SMS_KEYSTORE_PASSWORD']
                keyAlias System.getenv('SIGNING_KEY_ALIAS') ?: project.properties['TELEGRAM_SMS_KEY_ALIAS']
                keyPassword System.getenv('SIGNING_KEY_PASSWORD') ?: project.properties['TELEGRAM_SMS_KEY_PASSWORD']
                v1SigningEnabled true
                v2SigningEnabled true
            } catch (all) {
                println "Signing config not found or incomplete. Release build might fail."
            }
        }
    }
    // ...
}
```
This setup allows the GitHub Actions workflow to provide the signing details securely.

### C. Create GitHub Actions Workflow (`release.yml`) - Corrected Version

Create or update your workflow file at `.github/workflows/release.yml` with the following corrected content:

```yaml
# .github/workflows/release.yml
# Configured at 2:00 PM UTC, October 26, 2023 - Workflow for building and releasing Android APK
# Corrected at 3:30 PM UTC, October 26, 2023 - Fixed sdkmanager path and NDK installation

name: Build and Release APK

on:
  push:
    tags:
      - 'v*.*.*' # Trigger on version tags like v1.0.0

jobs:
  build-and-release:
    name: Build, Sign, and Release
    runs-on: ubuntu-latest

    permissions:
      contents: write # Required to create releases and upload release assets
      packages: write # Required to publish to GitHub Packages (or upload artifacts)

    steps:
      - name: Checkout code
        uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: '17'

      - name: Set up Android SDK and Command Line Tools
        uses: android-actions/setup-android@v3

      - name: Accept Android SDK licenses and Install NDK 21.0.6113669
        run: |
          echo "Accepting Android SDK licenses..."
          # Defaulting to direct call assuming PATH is set up by android-actions/setup-android.
          # Fallback to full path is included in the NDK install if direct call fails.
          yes | sdkmanager --licenses || echo "Failed to accept licenses with direct sdkmanager, or no new licenses. Continuing..."
          
          echo "Installing NDK version 21.0.6113669..."
          sdkmanager "ndk;21.0.6113669" || {
            echo "Failed to install NDK 21.0.6113669 using default sdkmanager path."
            echo "Attempting with full path: $ANDROID_SDK_ROOT/cmdline-tools/latest/bin/sdkmanager"
            # Re-accept licenses just in case context changed for the fallback sdkmanager call
            yes | $ANDROID_SDK_ROOT/cmdline-tools/latest/bin/sdkmanager --licenses || echo "Failed to accept licenses (fallback attempt). Continuing..."
            $ANDROID_SDK_ROOT/cmdline-tools/latest/bin/sdkmanager "ndk;21.0.6113669"
          }
          echo "NDK installation step completed."
        env:
          PATH: ${{ env.PATH }}:${{ env.ANDROID_SDK_ROOT }}/cmdline-tools/latest/bin:${{ env.ANDROID_SDK_ROOT }}/platform-tools

      - name: Decode Keystore
        env:
          SIGNING_KEYSTORE_BASE64: ${{ secrets.SIGNING_KEYSTORE_BASE64 }}
        run: |
          echo "Decoding keystore..."
          echo $SIGNING_KEYSTORE_BASE64 | base64 --decode > ${{ github.workspace }}/app/test-telegram-sms.jks
          echo "Keystore decoded successfully at ${{ github.workspace }}/app/test-telegram-sms.jks"

      - name: Grant execute permission for gradlew
        run: chmod +x ./gradlew

      - name: Build and Sign Release APK
        env:
          SIGNING_KEYSTORE_PASSWORD: ${{ secrets.SIGNING_KEYSTORE_PASSWORD }}
          SIGNING_KEY_ALIAS: ${{ secrets.SIGNING_KEY_ALIAS }}
          SIGNING_KEY_PASSWORD: ${{ secrets.SIGNING_KEY_PASSWORD }}
          SIGNING_KEYSTORE_FILE_PATH: ${{ github.workspace }}/app/test-telegram-sms.jks
        run: |
          echo "Starting Gradle build..."
          ./gradlew :app:assembleRelease

      - name: Get Tag Name
        id: get_tag
        run: echo "tag_name=${GITHUB_REF#refs/tags/}" >> $GITHUB_OUTPUT

      - name: Create GitHub Release
        id: create_release
        uses: actions/create-release@v1
        env:
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
        with:
          tag_name: ${{ steps.get_tag.outputs.tag_name }}
          release_name: Release ${{ steps.get_tag.outputs.tag_name }}
          body: |
            Release of version ${{ steps.get_tag.outputs.tag_name }}
            Signed app-release.apk attached.
            This APK was built using the configured signing key.
            NDK version used: 21.0.6113669.
          draft: false
          prerelease: false

      - name: Upload Release APK to GitHub Release
        uses: actions/upload-release-asset@v1
        env:
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
        with:
          upload_url: ${{ steps.create_release.outputs.upload_url }}
          asset_path: ./app/build/outputs/apk/release/app-release.apk
          asset_name: app-release-${{ steps.get_tag.outputs.tag_name }}.apk
          asset_content_type: application/vnd.android.package-archive

      - name: Publish APK as Action Artifact
        uses: actions/upload-artifact@v4
        with:
          name: app-release-artifact-${{ steps.get_tag.outputs.tag_name }}
          path: ./app/build/outputs/apk/release/app-release.apk
```

### D. Understanding Key Workflow Steps (Highlighting the Fix)

*   **`Set up Android SDK and Command Line Tools`**: The `android-actions/setup-android@v3` action is crucial. It installs the Android SDK, command-line tools, and sets necessary environment variables like `ANDROID_SDK_ROOT` and `ANDROID_HOME`. It should also add the correct `sdkmanager` to the `PATH`.
*   **`Accept Android SDK licenses and Install NDK 21.0.6113669` (Corrected Step)**:
    *   **License Acceptance**: `yes | sdkmanager --licenses || ...` attempts to automatically accept SDK licenses. The `|| echo "..."` part ensures the workflow continues even if there are no new licenses to accept or if the command has a non-zero exit code for other reasons (common in some environments).
    *   **NDK Installation**:
        *   `sdkmanager "ndk;21.0.6113669" || { ... }`: This attempts to install the specified NDK version using `sdkmanager` (expected to be in `PATH`).
        *   **Fallback Mechanism**: If the direct `sdkmanager` call fails (the `||` part), the script block inside `{...}` is executed. This block:
            1.  Prints a message indicating the failure.
            2.  Attempts the NDK installation again, but this time using the full path: `$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/sdkmanager`. This is a robust way to ensure the correct `sdkmanager` is called, even if `PATH` issues occur.
            3.  It also re-attempts license acceptance with the full path before the fallback NDK installation, just in case.
    *   **`env: PATH: ...`**: This line within the NDK installation step explicitly adds the common locations for `sdkmanager` and `platform-tools` to the `PATH` for *that specific step*. This further increases the chance of `sdkmanager` being found correctly by the shell.
*   **Other Steps**: Decoding keystore, building, creating release, and uploading assets remain conceptually the same but benefit from a correctly configured environment.

### E. Triggering the Workflow

1.  **Commit and Push Changes:** Ensure your updated `.github/workflows/release.yml` is committed.
2.  **Create and Push a Tag:**
    ```bash
    git tag v1.0.2 # Or your desired version, matching the one in manual release if you did that
    git push origin v1.0.2
    ```
3.  **Monitor Workflow:** Check the "Actions" tab in your GitHub repository.

## Part 3: Publishing to GitHub Packages (Updated Guidance)

Distributing APKs primarily via **GitHub Releases** is user-friendly. GitHub Packages can store APKs, but the process is less direct for generic files.

*   **Current Workflow (Artifact Upload):** The `Upload APK as Action Artifact` step in `release.yml` uses `actions/upload-artifact@v4`. This makes the APK downloadable from the "Actions" run summary page. It's a good way to store build outputs but isn't a true "GitHub Package" that appears in your repository's "Packages" section.
*   **True Generic Package Publishing (Advanced):**
    *   This requires using the GitHub API, typically with `curl`, to upload the APK.
    *   It needs a Personal Access Token (PAT) with `write:packages` scope.
    *   The API endpoint and request structure must be precise. For example:
        ```bash
        # Conceptual:
        # curl -L -X PUT \
        # -H "Authorization: token ${{ secrets.YOUR_PAT_FOR_PACKAGES }}" \
        # -H "Accept: application/vnd.github.v3+json" \
        # "https://api.github.com/repos/YOUR_OWNER/YOUR_REPO/contents/packages/com.yourcompany.yourapp/v1.0.2/app.apk" \
        # -d '{"message":"Upload APK v1.0.2","content":"'$(base64 -w 0 ./app/build/outputs/apk/release/app-release.apk)'"}'
        ```
        (This is a simplified concept; actual generic package upload might use different endpoints or methods, e.g., targeting `https://maven.pkg.github.com/...` if structuring as a Maven-like generic package.)
    *   **Recommendation:** For most APK distribution, **GitHub Releases is preferred.** If you need versioned package hosting in GitHub Packages for specific integration reasons, explore dedicated GitHub Actions from the marketplace or custom scripting with careful API use.

## Part 4: Risk Assessment for CI/CD and Deployment (Updated)

Automating deployments introduces specific risks:

1.  **Secret Exposure:**
    *   **Risk:** Compromise of `SIGNING_KEYSTORE_BASE64` or other signing secrets.
    *   **Mitigation:** Use GitHub's encrypted secrets; limit repository access; never log secrets; ensure workflow scripts don't inadvertently expose them.
2.  **Build Environment Integrity:**
    *   **Risk:** Malicious code injection via compromised GitHub Actions runners or third-party actions.
    *   **Mitigation:** Use official/trusted actions; pin action versions (e.g., `@v4` is good, specific commit SHAs are even stricter); regularly review workflow dependencies.
3.  **Incorrect NDK/SDK Configuration:**
    *   **Risk:** Builds failing or using unexpected tool versions due to `sdkmanager` path issues or NDK installation failures.
    *   **Mitigation:** The corrected workflow attempts to mitigate this by:
        *   Using `android-actions/setup-android@v3` which is generally reliable.
        *   Providing fallback paths for `sdkmanager`.
        *   Explicitly setting `PATH` in the NDK installation step.
        *   Clearly specifying the NDK version.
4.  **Tagging and Versioning Errors:**
    *   **Risk:** Accidental deployment of non-production code.
    *   **Mitigation:** Branch protection rules; clear tagging strategy (Semantic Versioning); consider manual approval steps for critical releases.
5.  **Gradle Configuration Errors:**
    *   **Risk:** Unsigned or incorrectly signed APKs.
    *   **Mitigation:** Test signing locally; the `try-catch` in `build.gradle` helps identify missing local configs; ensure workflow passes correct environment variables.

## Conclusion

This guide provides instructions for deploying the modified Telegram-SMS app using both manual GitHub Releases and an improved, automated GitHub Actions workflow. The corrected workflow enhances the reliability of the NDK and SDK tool setup. Always prioritize security and thorough testing in your CI/CD pipeline.
