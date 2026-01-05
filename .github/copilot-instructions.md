# GitHub Copilot Instructions for Telegram SMS

## Project Context

This is the **Telegram SMS** Android application project. Please refer to the comprehensive project documentation at `docs/docs/instructions/project.instructions.md` for detailed information about:

- Project architecture and structure
- Technology stack and dependencies
- Key components and services
- Data storage with MMKV
- Security features
- Build configuration and CI/CD
- Development guidelines

## Key Guidelines

### 1. Language and Code Style
- **Primary commit language**: English
- **Kotlin coding conventions**: Follow standard Kotlin style guide
- **Documentation language**: English for developer docs (`docs/`)

### 2. String Resources
- String resources are organized into category-based XML files
- See `docs/docs/STRING_RESOURCES.md` for detailed guidelines
- Always consider internationalization (i18n) when adding UI strings
- Use appropriate category files:
  - `strings_battery.xml` - Battery monitoring
  - `strings_telegram.xml` - Telegram API
  - `strings_sms.xml` - SMS forwarding
  - `strings_call.xml` - Phone calls
  - `strings_cc.xml` - Carbon Copy services
  - etc.

### 3. Data Storage
- Use **MMKV** for configuration persistence (not SharedPreferences)
- Update `DataMigrationManager` when modifying data storage
- See `MMKV/DataMigrationManager.kt` for migration examples

### 4. Architecture Considerations
- **No native code**: Project has no C/C++ despite NDK filters
- **Background services**: Handle Android 8+ background execution limits
- **Dual SIM support**: Code must account for multiple SIM slots
- **Permission handling**: Update AndroidManifest.xml and runtime permissions
- **Telegram API**: All bot interactions via `TelegramApi.kt` wrapper

### 5. Dependencies
- **Networking**: OkHttp 5.x with DNS-over-HTTPS
- **Security**: Conscrypt, Lazysodium (libsodium)
- **Storage**: MMKV 2.x (Tencent)
- **JSON**: Gson 2.x
- **QR Code**: code-scanner, AwesomeQRCode (custom)

### 6. Build and Versioning
- **compileSdk**: 36, **minSdk**: 23, **targetSdk**: 36
- **JDK**: 21
- **Kotlin**: 2.2.21
- **Version naming**: Ubuntu-style `YY.MM` (e.g., `26.01`)
- **Build variants**: debug, release, nightly

### 7. Testing and Quality
- Manual testing required for SMS/Call functionality
- QR code configuration for easy setup
- Log viewing within app for debugging
- Always validate changes with `get_errors` after editing

### 8. Documentation
When making significant changes, update documentation in `docs/docs/`:
- New features → Create feature documentation
- API changes → Update `DATA_STRUCTURE_VERSION.md`
- New integrations → Update `CarbonCopyProvider.md`
- Security changes → Update `CRYPTO_DOC.md`

**Note**: Focus on developer docs in `docs/`. User documentation in `document/` requires multi-language support.

### 9. Git Submodules
The project uses git submodules:
- `language_pack/` - Translations (9 languages)
- `AwesomeQrRenderer/` - QR code rendering
- `CodeauxLibPortable/` - Portable library

Handle submodules carefully when making changes.

### 10. Important Notes
- **Multi-language support**: 9 languages via language_pack system
- **Carbon Copy system**: Extensible notification forwarding
- **Remote commands**: Users control device via Telegram
- **Security**: End-to-end encryption with libsodium
- **CI/CD**: GitLab CI and GitHub Actions configured

## Quick Reference

### File Locations
- Main code: `app/src/main/java/com/qwe7002/telegram_sms/`
- Resources: `app/src/main/res/`
- Translations: `app/language_pack/`
- Developer docs: `docs/docs/`
- User docs: `document/docs/`

### Key Classes
- `MainActivity.kt` - Main UI entry
- `ChatService.kt` - Telegram polling
- `TelegramApi.kt` - API wrapper
- `DataMigrationManager.kt` - MMKV migrations
- `ChatCommand.kt` - Command parser

### Build Commands
```bash
# Copy language pack
./gradlew copy_language_pack

# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# Clean build
./gradlew clean
```

## Additional Resources

- **Project overview**: `docs/docs/instructions/project.instructions.md`
- **String resources**: `docs/docs/STRING_RESOURCES.md`
- **Data structures**: `docs/docs/DATA_STRUCTURE_VERSION.md`
- **Carbon Copy**: `docs/docs/CarbonCopyProvider.md`
- **Crypto**: `docs/docs/CRYPTO_DOC.md`

For complete project details, always refer to the comprehensive project instructions.
