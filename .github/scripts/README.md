# Language Pack Management Tools

This directory contains tools for managing and validating language pack translations.

## Available Tools

### 1. Translation Completeness Checker

**File**: `check_translations.py`

**Purpose**: Validates that all language packs have complete translations across all categorized string resource files.

**Usage**:
```bash
# From project root
python .github/scripts/check_translations.py
```

**What it does**:
- Checks all 12 categorized string resource files
- Compares language packs against English reference
- Reports missing strings and files
- Generates `translation_report.txt`
- Exits with error code if translations incomplete (for CI)

**Output**:
- Console: Detailed per-language, per-file report
- File: `translation_report.txt` with full report

### 2. Language Pack Splitter

**File**: `split_language_packs.py`

**Purpose**: Splits monolithic `strings.xml` files into 12 categorized files.

**Usage**:
```bash
# From project root
python .github/scripts/split_language_packs.py
```

**What it does**:
- Reads existing `strings.xml` in each language pack
- Categorizes strings into appropriate files
- Creates 12 separate XML files
- Reports uncategorized strings
- Preserves all translations

**When to use**:
- Migrating from old single-file structure
- Re-organizing existing language packs
- After major string restructuring

### 3. Template File Updater

**File**: `update_templates.py` / `format_templates.py`

**Purpose**: Ensures all language packs have complete and properly formatted template.xml files.

**Usage**:
```bash
# From project root
python .github/scripts/format_templates.py
```

**What it does**:
- Checks template.xml in each language pack
- Adds missing template strings
- Formats files properly
- Adds translations where available
- Marks untranslated templates for review

**When to use**:
- After adding new templates to English reference
- To fix formatting issues in template files
- To ensure all language packs have complete templates

## String Resource Categories

The tools work with these 12 categorized files:

| File | Purpose | ~Strings |
|------|---------|----------|
| `strings.xml` | Base configuration | 2 |
| `strings_battery.xml` | Battery monitoring | 11 |
| `strings_telegram.xml` | Telegram API + commands | 26 |
| `strings_sms.xml` | SMS management | 42 |
| `strings_call.xml` | Call notifications | 6 |
| `strings_ussd.xml` | USSD codes | 4 |
| `strings_network.xml` | Network settings | 12 |
| `strings_cc.xml` | Carbon Copy | 6 |
| `strings_notification.xml` | Notification listener | 5 |
| `strings_scanner.xml` | QR scanner | 16 |
| `strings_privacy_about.xml` | Privacy & About | 16 |
| `strings_common.xml` | Common UI | 20 |

**Total**: ~170 strings

## Workflow for Translators

### Step 1: Check Current Status
```bash
python .github/scripts/check_translations.py
```

This shows which strings are missing in your language.

### Step 2: Add Missing Translations

Find the appropriate file and add the missing strings:
```bash
# Example: Add missing battery strings to Simplified Chinese
nano app/language_pack/values-zh-rCN/strings_battery.xml
```

### Step 3: Verify Completeness
```bash
python .github/scripts/check_translations.py
```

Repeat until you see:
```
✅ All translations are complete!
```

## Workflow for Developers

### Adding New Strings

1. **Choose the appropriate category file** in `app/src/main/res/values/`
2. **Add the string** with a descriptive ID
3. **Run the checker** to see which language packs need updates
4. **Create an issue** or PR for translators

Example:
```xml
<!-- app/src/main/res/values/strings_battery.xml -->
<string name="battery_optimization_warning">Battery optimization may affect service reliability.</string>
```

### Before Committing

Always run the checker:
```bash
python .github/scripts/check_translations.py
```

If translations are incomplete, either:
- Add translations yourself (if you know the language)
- Create an issue for translators
- Mark the PR as "needs translation"

## CI Integration

The translation checker runs automatically on:
- Pull requests affecting `app/language_pack/**`
- Pull requests affecting `app/src/main/res/values/strings*.xml`
- Pushes to master branch

PRs with incomplete translations will **fail** the check, but can be merged with maintainer approval if:
- New feature requires new strings
- Translation issue is tracked
- English strings are complete

## Requirements

```bash
pip install lxml
```

Or using the project's requirements (if it exists):
```bash
pip install -r requirements.txt
```

## Troubleshooting

### "No language packs found"
- Ensure you're running from project root
- Check that `app/language_pack/` exists
- Verify language pack directories start with `values-`

### "Reference file not found"
- Ensure `app/src/main/res/values/strings*.xml` files exist
- Check you haven't renamed or moved reference files

### "Error parsing XML"
- Check XML file syntax
- Ensure proper UTF-8 encoding
- Validate XML structure (must have `<resources>` root)

### Script shows no output
- Check if lxml is installed: `pip list | grep lxml`
- Try running with explicit python: `python3 .github/scripts/check_translations.py`
- Check for Python errors: `python .github/scripts/check_translations.py 2>&1`

## File Structure Example

After running the splitter, each language pack should look like:

```
values-zh-rCN/
├── strings.xml                 ✓ 2 strings (Lang, time_format)
├── strings_battery.xml         ✓ 11 strings
├── strings_call.xml           ✓ 6 strings
├── strings_cc.xml             ✓ 6 strings
├── strings_common.xml          ✓ 20 strings
├── strings_network.xml         ✓ 12 strings
├── strings_notification.xml    ✓ 5 strings
├── strings_privacy_about.xml   ✓ 16 strings
├── strings_scanner.xml         ✓ 16 strings
├── strings_sms.xml            ✓ 42 strings
├── strings_telegram.xml        ✓ 26 strings
├── strings_ussd.xml           ✓ 4 strings
└── template.xml               (message templates)
```

## Additional Resources

- **String Resources Documentation**: `docs/docs/STRING_RESOURCES.md`
- **Translation Checker Documentation**: `docs/docs/TRANSLATION_CHECKER.md`
- **Project Instructions**: `docs/docs/instructions/project.instructions.md`

## Support

For questions or issues:
- GitHub Issues: https://github.com/telegram-sms/telegram-sms/issues
- Telegram Channel: https://t.me/tg_sms_changelog_eng

