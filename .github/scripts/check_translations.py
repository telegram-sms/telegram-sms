#!/usr/bin/env python3
"""
Translation Completeness Checker
Compares language pack strings.xml files with the reference strings.xml
to find missing translations.
"""

import os
import sys
from lxml import etree
from pathlib import Path
from typing import Dict, Set, List, Tuple


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


def get_language_packs(base_path: str) -> List[Tuple[str, str]]:
    """Get all language pack directories and their strings.xml files."""
    language_packs = []
    language_pack_dir = Path(base_path) / 'app' / 'language_pack'

    if not language_pack_dir.exists():
        print(f"Warning: Language pack directory not found: {language_pack_dir}")
        return language_packs

    for item in language_pack_dir.iterdir():
        if item.is_dir() and item.name.startswith('values-'):
            strings_file = item / 'strings.xml'
            if strings_file.exists():
                language_packs.append((item.name, str(strings_file)))

    return language_packs


def check_translation_completeness(
    reference_strings: Dict[str, str],
    language_strings: Dict[str, str],
    language_name: str
) -> Tuple[Set[str], Set[str]]:
    """
    Compare reference strings with language strings.
    Returns: (missing_keys, extra_keys)
    """
    reference_keys = set(reference_strings.keys())
    language_keys = set(language_strings.keys())

    missing_keys = reference_keys - language_keys
    extra_keys = language_keys - reference_keys

    return missing_keys, extra_keys


def generate_report(
    reference_strings: Dict[str, str],
    language_packs: List[Tuple[str, str]]
) -> str:
    """Generate a detailed report of translation completeness."""
    report_lines = []
    report_lines.append("=" * 80)
    report_lines.append("Translation Completeness Report")
    report_lines.append("=" * 80)
    report_lines.append(f"\nReference strings.xml contains {len(reference_strings)} strings.\n")

    all_complete = True

    for language_name, language_file in sorted(language_packs):
        language_strings = parse_strings_xml(language_file)
        missing_keys, extra_keys = check_translation_completeness(
            reference_strings, language_strings, language_name
        )

        completeness = (
            (len(language_strings) - len(extra_keys)) / len(reference_strings) * 100
            if len(reference_strings) > 0 else 0
        )

        report_lines.append("-" * 80)
        report_lines.append(f"Language: {language_name}")
        report_lines.append(f"File: {language_file}")
        report_lines.append(f"Total strings: {len(language_strings)}")
        report_lines.append(f"Completeness: {completeness:.1f}%")

        if missing_keys:
            all_complete = False
            report_lines.append(f"\n⚠️  Missing {len(missing_keys)} string(s):")
            for key in sorted(missing_keys):
                report_lines.append(f"  - {key}")
        else:
            report_lines.append("\n✅ All strings present!")

        if extra_keys:
            report_lines.append(f"\n⚠️  Extra {len(extra_keys)} string(s) not in reference:")
            for key in sorted(extra_keys):
                report_lines.append(f"  - {key}")

        report_lines.append("")

    report_lines.append("=" * 80)
    if all_complete:
        report_lines.append("✅ All language packs are complete!")
    else:
        report_lines.append("⚠️  Some language packs have missing strings.")
    report_lines.append("=" * 80)

    return "\n".join(report_lines)


def main():
    """Main function to check translation completeness."""
    # Get workspace path
    workspace_path = os.getcwd()

    # Parse reference strings.xml from the repository
    reference_file = os.path.join(workspace_path, 'app', 'src', 'main', 'res', 'values', 'strings.xml')

    if not os.path.exists(reference_file):
        print(f"Error: Reference file not found: {reference_file}")
        sys.exit(1)

    print(f"Loading reference strings from {reference_file}...")
    reference_strings = parse_strings_xml(reference_file)

    if not reference_strings:
        print("Error: No strings found in reference file.")
        sys.exit(1)

    print(f"Found {len(reference_strings)} strings in reference file.")

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
    for language_name, language_file in language_packs:
        language_strings = parse_strings_xml(language_file)
        missing_keys, _ = check_translation_completeness(
            reference_strings, language_strings, language_name
        )
        if missing_keys:
            has_missing = True
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

