# DevOps Edition: Deploying the Modified Telegram-SMS App

**Documented at: 3:00 PM UTC, October 26, 2023**

This guide focuses on deploying the modified Telegram-SMS application, primarily using GitHub Releases and GitHub Actions for automation. It also provides guidance on publishing to GitHub Packages.

## Introduction

The purpose of this guide is to provide instructions for:
1.  Manually creating a GitHub Release and attaching the application's APK.
2.  Automating the build, sign, and release process using GitHub Actions.
3.  Understanding how to (conceptually) publish the APK to GitHub Packages, though GitHub Releases is generally preferred for distributing APKs.

This guide assumes you have already gone through the process of modifying the Telegram-SMS app, including setting up Device Administration, secret key protection, and potentially hiding the app icon.

## Prerequisites

Before you begin, ensure you have the following:
*   **GitHub Repository:** Your modified Telegram-SMS app code hosted on GitHub.
*   **GitHub Personal Access Token (PAT):** If you intend to use command-line tools to interact with the GitHub API or for certain manual package publishing steps (not strictly required for the GitHub Actions web UI setup of secrets). Ensure it has `repo` scope for creating releases and `write:packages` for publishing packages.
*   **Signed APK (`app-release.apk`):** For manual release, you'll need a signed APK. The automated workflow will generate this.
*   **Java Development Kit (JDK):** Required for signing APKs and running Gradle commands locally (ensure version matches project, e.g., JDK 17).
*   **Android SDK Tools:** Required for building the app locally. Android Studio provides these.
*   **`keytool`:** A command-line tool (part of JDK) for managing keystores.
*   **`base64`:** A command-line tool for encoding the keystore (available on Linux, macOS, and Windows via WSL or Git Bash).
*   **Your Android Keystore File (e.g., `test-telegram-sms.jks`):** The JKS file used to sign your application.
*   **Keystore Passwords and Alias:** Store password, key alias, and key password for your keystore.

## Part 1: Manual GitHub Release

This method is suitable for one-off releases or if you prefer a manual process.

1.  **Navigate to Your Repository on GitHub.**
2.  **Go to "Releases":** On the right sidebar (or under the "Code" tab), click on "Releases."
3.  **Create a New Release:** Click on "Create a new release" or "Draft a new release."
4.  **Tag Version:**
    *   Click "Choose a tag."
    *   Type a new version tag that matches your release (e.g., `v1.0.1`). It's good practice to use semantic versioning (e.g., `vMAJOR.MINOR.PATCH`).
    *   Click "+ Create new tag: vX.X.X on publish."
5.  **Release Title:** Enter a title for your release (e.g., "Release v1.0.1").
6.  **Description:**
    *   Provide a summary of changes, new features, and bug fixes in this version.
    *   You can use Markdown for formatting.
    *   Example:
        ```markdown
        ### Release v1.0.1 - Enhanced Security Features

        This release introduces:
        - Device Administration for uninstall protection.
        - Secret key mechanism to authorize Device Admin deactivation.
        - Option to hide the app icon from the launcher after setup.

        **Note:** This build is signed with a test key for demonstration purposes.
        ```
7.  **Attach APK:**
    *   Drag and drop your signed `app-release.apk` file into the "Attach binaries by dropping them here or selecting them" area.
    *   Alternatively, click to select the file from your computer.
8.  **Pre-release (Optional):** If this is not a stable release ready for everyone, check the "This is a pre-release" box.
9.  **Publish Release:**
    *   Once you're satisfied, click "Publish release" (or "Save draft" if you want to publish later).

Users can now download the `app-release.apk` directly from your GitHub Releases page.

## Part 2: Automated Deployment with GitHub Actions

This automates the process of building, signing, and creating a GitHub Release when you push a new version tag.

### A. GitHub Secrets Setup

Store your sensitive signing information as encrypted secrets in your GitHub repository.
1.  **Navigate to your GitHub repository.**
2.  Go to **Settings > Secrets and variables > Actions**.
3.  Click **New repository secret** for each of the following:
    *   **`SIGNING_KEYSTORE_BASE64`**:
        *   **Value:** The base64 encoded content of your `.jks` keystore file.
        *   **Command to generate (run in your terminal):**
            ```bash
            base64 -w 0 your_keystore_filename.jks
            ```
            (Replace `your_keystore_filename.jks` with your actual keystore file name. On some systems, you might not need `-w 0` or might use `base64 -i your_keystore_filename.jks -o - | tr -d '\n'` for macOS). Copy the entire output.
    *   **`SIGNING_KEYSTORE_PASSWORD`**:
        *   **Value:** Your keystore password.
    *   **`SIGNING_KEY_ALIAS`**:
        *   **Value:** The alias for your key within the keystore.
    *   **`SIGNING_KEY_PASSWORD`**:
        *   **Value:** The password for your key alias.

### B. Configure `build.gradle` for CI Signing

Ensure your `app/build.gradle` file is configured to use these environment variables for signing. The following snippet shows the relevant `signingConfigs.release` block (original timestamp included for context):

```gradle
// In app/build.gradle
android {
    // ... other configurations ...

    signingConfigs {
        release {
            // Configured at 2:30 PM UTC, October 26, 2023 - Prioritize ENV VARS for CI, fallback to gradle.properties
            try {
                // For CI (GitHub Actions) - Environment variables are set by the workflow
                // Fallback to project.properties for local builds, then to a default name 'test-telegram-sms.jks'
                storeFile file(System.getenv('SIGNING_KEYSTORE_FILE_PATH') ?: project.properties['TELEGRAM_SMS_KEYSTORE_FILE'] ?: 'test-telegram-sms.jks')
                storePassword System.getenv('SIGNING_KEYSTORE_PASSWORD') ?: project.properties['TELEGRAM_SMS_KEYSTORE_PASSWORD']
                keyAlias System.getenv('SIGNING_KEY_ALIAS') ?: project.properties['TELEGRAM_SMS_KEY_ALIAS']
                keyPassword System.getenv('SIGNING_KEY_PASSWORD') ?: project.properties['TELEGRAM_SMS_KEY_PASSWORD']
                v1SigningEnabled true
                v2SigningEnabled true
            } catch (all) {
                println "Signing config not found or incomplete in environment variables or gradle.properties. Release build might fail if signing is required."
            }
        }
    }

    buildTypes {
        release {
            // ... other release build type settings ...
            signingConfig signingConfigs.release // Ensure this line is present
        }
    }
}
```
**Note:** The `SIGNING_KEYSTORE_FILE_PATH` environment variable will be set by the GitHub Actions workflow to point to the decoded keystore file.

### C. Create GitHub Actions Workflow (`release.yml`)

Create a workflow file in your repository at `.github/workflows/release.yml`. This workflow will build, sign, and release your APK.

```yaml
# .github/workflows/release.yml
# Configured at 2:00 PM UTC, October 26, 2023 - Workflow for building and releasing Android APK

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
      packages: write # Required for future GitHub Packages integration if fully implemented

    steps:
      - name: Checkout code
        uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: '17' # Ensure this matches project requirements

      - name: Set up Android SDK
        uses: android-actions/setup-android@v3

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
          SIGNING_KEYSTORE_FILE_PATH: ${{ github.workspace }}/app/test-telegram-sms.jks # Path to decoded keystore
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
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }} # Provided by Actions
        with:
          tag_name: ${{ steps.get_tag.outputs.tag_name }}
          release_name: Release ${{ steps.get_tag.outputs.tag_name }}
          body: |
            Release of version ${{ steps.get_tag.outputs.tag_name }}
            Signed app-release.apk attached.
            This APK was built using the configured signing key.
          draft: false
          prerelease: false

      - name: Upload Release APK to GitHub Release
        uses: actions/upload-release-asset@v1
        env:
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
        with:
          upload_url: ${{ steps.create_release.outputs.upload_url }}
          asset_path: ./app/build/outputs/apk/release/app-release.apk
          asset_name: app-release-${{ steps.get_tag.outputs.tag_name }}.apk # Example: app-release-v1.0.0.apk
          asset_content_type: application/vnd.android.package-archive

      - name: "Conceptual: Publish APK to GitHub Packages (as Artifact)"
        # This step uses upload-artifact for demonstration.
        # True generic package publishing to GitHub Packages requires more specific API interaction.
        uses: actions/upload-artifact@v4
        with:
          name: app-release-artifact-${{ steps.get_tag.outputs.tag_name }}
          path: ./app/build/outputs/apk/release/app-release.apk
```

### D. Triggering the Workflow

1.  **Commit and Push Changes:** Make sure your `app/build.gradle` and `.github/workflows/release.yml` files are committed to your repository.
2.  **Create and Push a Tag:**
    To trigger the workflow, create a new tag locally and push it to GitHub.
    ```bash
    git tag v1.0.0 # Or your desired version
    git push origin v1.0.0
    ```
    (Replace `v1.0.0` with your actual tag.)
3.  **Monitor Workflow:** Go to the "Actions" tab in your GitHub repository. You should see the workflow running. It will build the APK, sign it using the secrets, create a new GitHub Release, and upload the signed APK as an asset to that release.

## Part 3: Publishing to GitHub Packages (Guidance)

While GitHub Releases is the primary and recommended way to distribute APKs to end-users, GitHub Packages can be used to store various types of packages, including generic files like APKs.

**Current Workflow's Approach (Artifact):**
The provided `release.yml` workflow includes a step:
```yaml
      - name: "Conceptual: Publish APK to GitHub Packages (as Artifact)"
        uses: actions/upload-artifact@v4
        with:
          name: app-release-artifact-${{ steps.get_tag.outputs.tag_name }}
          path: ./app/build/outputs/apk/release/app-release.apk
```
This step uploads the APK as a build **artifact** associated with the workflow run. Users can download it from the "Actions" tab summary page for that specific run. This fulfills the "publishing" aspect by making the APK available, but it's not a true "GitHub Package" in the sense of being listed under your repository's "Packages" section with versioning.

**True Generic Package Publishing to GitHub Packages (Advanced):**
To publish an APK as a versioned generic package directly visible under your repository's "Packages" section:
1.  **Manual Upload via UI (Limited):** GitHub's UI for package publishing is primarily designed for common package types (npm, Maven, Docker, etc.). Uploading generic files like APKs this way is not straightforward.
2.  **Using the GitHub API:** You would typically use `curl` or a script to upload the APK to the GitHub Packages API for generic packages. This requires:
    *   A Personal Access Token (PAT) with `write:packages` scope.
    *   Constructing the correct API endpoint URL:
        `https://maven.pkg.github.com/OWNER/REPO/com/yourcompany/yourappname/${TAG_VERSION}/app-release-${TAG_VERSION}.apk`
        (The URL structure might vary; consult GitHub documentation for generic package uploads.)
    *   Using `curl` with `PUT` or `POST` requests. Example conceptual snippet:
        ```bash
        # Conceptual - requires correct URL structure and package naming for generic packages
        # curl -L -X PUT \
        # -u "${{ github.actor }}:${{ secrets.GITHUB_TOKEN }}" \ # Or your PAT
        # -H "Accept: application/vnd.github+json" \
        # "https://maven.pkg.github.com/YOUR_OWNER/YOUR_REPO/com.qwe7002.telegram_sms/app-release/${TAG_NAME}/app-release-${TAG_NAME}.apk" \
        # --upload-file ./app/build/outputs/apk/release/app-release.apk
        ```
3.  **Dedicated GitHub Actions:** There might be third-party GitHub Actions on the marketplace designed for publishing generic files to GitHub Packages.

**Recommendation for APKs:**
*   **Primary Distribution:** Use **GitHub Releases**. It's user-friendly for downloading executables.
*   **Artifact Storage:** The `upload-artifact` step in the workflow provides good storage tied to the build run, useful for testing or internal distribution.
*   **GitHub Packages (Generic):** Consider this for specific versioning or programmatic access needs if Releases/Artifacts don't suffice. It's more complex for APKs.

## Part 4: Risk Assessment for CI/CD and Deployment

Automating your build and release process introduces new considerations:

1.  **Secret Exposure:**
    *   **Risk:** Keystore passwords, alias passwords, and the keystore itself (even if base64 encoded) stored as GitHub Secrets are critical. If a workflow is compromised or secrets are inadvertently logged, signing keys could be exposed.
    *   **Mitigation:**
        *   Limit access to repository settings and secrets.
        *   Use GitHub's built-in secret redaction (though it's not foolproof).
        *   Never print secrets to logs.
        *   Ensure the `SIGNING_KEYSTORE_BASE64` secret is not easily decodable from logs or workflow scripts if they were ever exposed (the base64 string itself is not the raw key but an encoded version of the JKS file).
        *   Consider using more advanced secret management like HashiCorp Vault if your organization requires it, though GitHub Secrets are generally secure for most use cases when managed properly.
2.  **Build Environment Integrity:**
    *   **Risk:** The GitHub Actions runner environment or third-party actions could be compromised, potentially leading to malicious code injection into your APK during the build.
    *   **Mitigation:**
        *   Use official and trusted GitHub Actions (e.g., `actions/checkout`, `actions/setup-java`, `android-actions/setup-android`).
        *   Pin actions to specific commit SHAs or versions for stability and to avoid silent updates that might introduce vulnerabilities (`uses: actions/checkout@v4` is good, `uses: actions/checkout@main` is riskier).
        *   Regularly review workflow dependencies.
3.  **Tagging and Versioning Errors:**
    *   **Risk:** Incorrectly tagging a release (e.g., pushing a non-production ready commit with a release tag) could lead to unintended deployments.
    *   **Mitigation:**
        *   Implement branch protection rules (e.g., require pull requests, status checks before merging to main/release branches).
        *   Use a clear tagging strategy (e.g., Semantic Versioning).
        *   Consider adding manual approval steps in the workflow for critical releases if needed (GitHub Actions supports environments and approvals).
4.  **Gradle Configuration Errors:**
    *   **Risk:** Incorrect `build.gradle` configurations (especially around signing) could lead to unsigned or incorrectly signed APKs being released.
    *   **Mitigation:**
        *   Thoroughly test the signing configuration locally before relying on it in CI.
        *   The `try-catch` block in the provided `build.gradle` helps identify missing configurations.
        *   Ensure the workflow explicitly passes the correct environment variables that `build.gradle` expects.
5.  **Denial of Service (DoS) on GitHub Actions:**
    *   **Risk:** If workflows are triggered excessively (e.g., by mistake or malicious activity if your repo is public and open to external PR triggers without proper checks), it could consume your GitHub Actions minutes.
    *   **Mitigation:**
        *   For public repositories, be cautious with workflow triggers from forks.
        *   Monitor Actions usage.
        *   The current workflow triggers only on tags, which is a controlled event.

## Conclusion

This guide has provided steps for manually releasing your Telegram-SMS APK and for automating this process using GitHub Actions. The automated workflow enhances consistency and efficiency in deployment. Always prioritize security, especially when handling signing keys and configuring automated systems. Choose the deployment and publishing strategy (GitHub Releases, Artifacts, or Packages) that best fits your project's needs.
