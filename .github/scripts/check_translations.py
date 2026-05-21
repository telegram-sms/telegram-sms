#!/usr/bin/env python3
"""
Translation Completeness Checker
Compares language pack string resource files with the reference files
to find missing translations.
"""

import os
import sys
from lxml import etree
from pathlib import Path
from typing import Dict, Set, List, Tuple

# Define the string resource files to check
STRING_RESOURCE_FILES = [
    'strings.xml',
    'strings_battery.xml',
    'strings_telegram.xml',
    'strings_sms.xml',
    'strings_call.xml',
    'strings_ussd.xml',
    'strings_network.xml',
    'strings_cc.xml',
    'strings_notification.xml',
    'strings_scanner.xml',
    'strings_privacy_about.xml',
    'strings_common.xml'
]

# Files that should be merged into other files for checking
# Format: source_file -> target_file
FILE_MERGE_MAP = {
    'strings_chat.xml': 'strings_telegram.xml',
    'strings_sms_manage.xml': 'strings_sms.xml'
}


def parse_strings_xml(file_path: str) -> Dict[str, str]:
    """Parse strings.xml file and return a dictionary of string names and values."""
    try:
        tree = etree.parse(file_path)
        root = tree.getroot()

        strings = {}
        for string_elem in root.findall('string'):
            name = string_elem.get('name')
            if name:
                # Get text content, handling None case
                text = string_elem.text or ''
                strings[name] = text

        return strings
    except Exception as e:
        print(f"Error parsing {file_path}: {e}")
        return {}


def get_language_packs(base_path: str) -> List[Tuple[str, Path]]:
    """Get all language pack directories."""
    language_packs = []
    language_pack_dir = Path(base_path) / 'app' / 'language_pack'

    if not language_pack_dir.exists():
        print(f"Warning: Language pack directory not found: {language_pack_dir}")
        return language_packs

    for item in language_pack_dir.iterdir():
        if item.is_dir() and item.name.startswith('values-'):
            language_packs.append((item.name, item))

    return language_packs


def get_reference_strings(base_path: str) -> Dict[str, Dict[str, str]]:
    """Get all reference strings from the values directory, organized by file."""
    reference_dir = Path(base_path) / 'app' / 'src' / 'main' / 'res' / 'values'
    all_strings = {}

    # Load main files
    for resource_file in STRING_RESOURCE_FILES:
        file_path = reference_dir / resource_file
        if file_path.exists():
            strings = parse_strings_xml(str(file_path))
            if strings:
                all_strings[resource_file] = strings
        else:
            print(f"Warning: Reference file not found: {file_path}")

    # Merge additional files into their target files
    for source_file, target_file in FILE_MERGE_MAP.items():
        source_path = reference_dir / source_file
        if source_path.exists():
            source_strings = parse_strings_xml(str(source_path))
            if source_strings and target_file in all_strings:
                # Merge source strings into target
                all_strings[target_file].update(source_strings)
                print(f"Merged {source_file} into {target_file} ({len(source_strings)} strings)")

    return all_strings


def get_language_strings(language_dir: Path) -> Dict[str, Dict[str, str]]:
    """Get all strings from a language pack directory, organized by file."""
    all_strings = {}

    for resource_file in STRING_RESOURCE_FILES:
        file_path = language_dir / resource_file
        if file_path.exists():
            strings = parse_strings_xml(str(file_path))
            if strings:
                all_strings[resource_file] = strings

    return all_strings


def check_translation_completeness(
    reference_strings: Dict[str, Dict[str, str]],
    language_strings: Dict[str, Dict[str, str]],
    language_name: str
) -> Dict[str, Tuple[Set[str], Set[str], Set[str]]]:
    """
    Compare reference strings with language strings for each file.
    Returns: Dict[filename, (missing_keys, extra_keys, missing_files)]
    """
    results = {}
    missing_files = set()

    for filename, ref_strings in reference_strings.items():
        if filename not in language_strings:
            missing_files.add(filename)
            results[filename] = (set(ref_strings.keys()), set(), missing_files)
        else:
            reference_keys = set(ref_strings.keys())
            language_keys = set(language_strings[filename].keys())

            missing_keys = reference_keys - language_keys
            extra_keys = language_keys - reference_keys

            results[filename] = (missing_keys, extra_keys, set())

    return results


def generate_report(
    reference_strings: Dict[str, Dict[str, str]],
    language_packs: List[Tuple[str, Path]]
) -> str:
    """Generate a detailed report of translation completeness."""
    report_lines = []
    report_lines.append("=" * 80)
    report_lines.append("Translation Completeness Report")
    report_lines.append("=" * 80)

    # Calculate total reference strings
    total_ref_strings = sum(len(strings) for strings in reference_strings.values())
    report_lines.append(f"\nReference files contain {total_ref_strings} strings across {len(reference_strings)} files.\n")

    all_complete = True

    for language_name, language_dir in sorted(language_packs):
        language_strings = get_language_strings(language_dir)
        results = check_translation_completeness(
            reference_strings, language_strings, language_name
        )

        # Calculate overall statistics
        total_lang_strings = sum(len(strings) for strings in language_strings.values())
        total_missing = sum(len(missing) for missing, _, _ in results.values())
        total_extra = sum(len(extra) for _, extra, _ in results.values())

        completeness = (
            ((total_lang_strings - total_extra) / total_ref_strings * 100)
            if total_ref_strings > 0 else 0
        )

        report_lines.append("-" * 80)
        report_lines.append(f"Language: {language_name}")
        report_lines.append(f"Directory: {language_dir}")
        report_lines.append(f"Total strings: {total_lang_strings}")
        report_lines.append(f"Completeness: {completeness:.1f}%")

        # Check for missing files
        missing_files = [f for f, (_, _, mf) in results.items() if mf]
        if missing_files:
            all_complete = False
            report_lines.append(f"\n⚠️  Missing {len(missing_files)} file(s):")
            for filename in sorted(missing_files):
                report_lines.append(f"  - {filename}")

        # Report per-file issues
        has_issues = False
        for filename in sorted(results.keys()):
            missing_keys, extra_keys, _ = results[filename]

            if missing_keys or extra_keys:
                if not has_issues:
                    report_lines.append(f"\nFile-specific issues:")
                    has_issues = True

                report_lines.append(f"\n  [{filename}]")

                if missing_keys:
                    all_complete = False
                    report_lines.append(f"    ⚠️  Missing {len(missing_keys)} string(s):")
                    for key in sorted(missing_keys):
                        report_lines.append(f"      - {key}")

                if extra_keys:
                    report_lines.append(f"    ⚠️  Extra {len(extra_keys)} string(s):")
                    for key in sorted(extra_keys):
                        report_lines.append(f"      - {key}")

        if not has_issues and not missing_files:
            report_lines.append("\n✅ All files and strings present!")

        report_lines.append("")

    report_lines.append("=" * 80)
    if all_complete:
        report_lines.append("✅ All language packs are complete!")
    else:
        report_lines.append("⚠️  Some language packs have missing strings or files.")
    report_lines.append("=" * 80)

    return "\n".join(report_lines)


def main():
    """Main function to check translation completeness."""
    # Get workspace path
    workspace_path = os.getcwd()

    print(f"Loading reference strings from app/src/main/res/values/...")
    reference_strings = get_reference_strings(workspace_path)

    if not reference_strings:
        print("Error: No reference strings found.")
        sys.exit(1)

    total_ref_strings = sum(len(strings) for strings in reference_strings.values())
    print(f"Found {total_ref_strings} strings across {len(reference_strings)} files.")

    # Get all language packs
    print(f"\nScanning language packs in {workspace_path}...")
    language_packs = get_language_packs(workspace_path)

    if not language_packs:
        print("Warning: No language packs found.")
        sys.exit(0)

    print(f"Found {len(language_packs)} language pack(s).\n")

    # Generate report
    report = generate_report(reference_strings, language_packs)

    # Print to console
    print(report)

    # Save to file
    report_file = "translation_report.txt"
    with open(report_file, 'w', encoding='utf-8') as f:
        f.write(report)

    print(f"\nReport saved to {report_file}")

    # Check if any language pack has missing strings
    has_missing = False
    for language_name, language_dir in language_packs:
        language_strings = get_language_strings(language_dir)
        results = check_translation_completeness(
            reference_strings, language_strings, language_name
        )

        for filename, (missing_keys, _, missing_files) in results.items():
            if missing_keys or missing_files:
                has_missing = True
                break

        if has_missing:
            break

    # Exit with error code if there are missing strings
    if has_missing:
        print("\n⚠️  Some translations are incomplete. Please review the report above.")
        sys.exit(1)
    else:
        print("\n✅ All translations are complete!")
        sys.exit(0)


if __name__ == "__main__":
    main()

