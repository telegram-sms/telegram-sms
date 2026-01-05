#!/usr/bin/env python3
"""
Script to update template.xml files in all language packs
to ensure they include all required template strings.
"""

import os
from lxml import etree
from pathlib import Path
from typing import Dict, List

# Required template strings that must exist in all language packs
REQUIRED_TEMPLATES = [
    'TPL_received_sms',
    'TPL_received_mms',
    'TPL_send_sms',
    'TPL_missed_call',
    'TPL_notification',
    'TPL_send_USSD',
    'TPL_system_message',
    'TPL_battery',
    'TPL_receiving_call',
    'TPL_send_sms_chat',
    'TPL_send_USSD_chat'
]


def parse_template_xml(file_path: str) -> Dict[str, etree.Element]:
    """Parse template.xml and return dictionary of template elements."""
    try:
        tree = etree.parse(file_path)
        root = tree.getroot()

        templates = {}
        for elem in root.findall('string'):
            name = elem.get('name')
            if name:
                templates[name] = elem

        return templates
    except Exception as e:
        print(f"Error parsing {file_path}: {e}")
        return {}


def get_reference_templates(base_path: str) -> Dict[str, str]:
    """Get reference templates from English values directory."""
    reference_file = Path(base_path) / 'app' / 'src' / 'main' / 'res' / 'values' / 'template.xml'

    if not reference_file.exists():
        print(f"Error: Reference template file not found: {reference_file}")
        return {}

    templates = {}
    tree = etree.parse(str(reference_file))
    root = tree.getroot()

    for elem in root.findall('string'):
        name = elem.get('name')
        if name:
            # Get text with preserved formatting
            text = etree.tostring(elem, encoding='unicode', method='text')
            templates[name] = text.strip()

    return templates


def update_template_file(language_dir: Path, reference_templates: Dict[str, str]) -> bool:
    """Update template.xml in a language pack to include all required templates."""
    template_file = language_dir / 'template.xml'

    if not template_file.exists():
        print(f"  Creating new template.xml")
        # Create new file with all reference templates (needs translation)
        root = etree.Element('resources')

        for name in REQUIRED_TEMPLATES:
            if name in reference_templates:
                elem = etree.SubElement(root, 'string')
                elem.set('name', name)
                elem.text = reference_templates[name]

        tree = etree.ElementTree(root)
        tree.write(
            str(template_file),
            encoding='utf-8',
            xml_declaration=True,
            pretty_print=True
        )
        return True

    # Parse existing file
    existing_templates = parse_template_xml(str(template_file))
    missing_templates = []

    for template_name in REQUIRED_TEMPLATES:
        if template_name not in existing_templates:
            missing_templates.append(template_name)

    if not missing_templates:
        return False  # No updates needed

    # Add missing templates
    tree = etree.parse(str(template_file))
    root = tree.getroot()

    for template_name in missing_templates:
        if template_name in reference_templates:
            # Add comment before new template
            comment = etree.Comment(f' TODO: Translate {template_name} ')
            root.append(comment)

            # Add the template with English text (needs translation)
            elem = etree.SubElement(root, 'string')
            elem.set('name', template_name)
            elem.text = reference_templates[template_name]

    # Write back to file
    tree.write(
        str(template_file),
        encoding='utf-8',
        xml_declaration=True,
        pretty_print=True
    )

    return True


def check_and_update_language_packs(base_path: str):
    """Check and update all language pack template files."""
    language_pack_dir = Path(base_path) / 'app' / 'language_pack'

    if not language_pack_dir.exists():
        print(f"Error: Language pack directory not found: {language_pack_dir}")
        return

    print("=" * 80)
    print("Template File Update Tool")
    print("=" * 80)
    print()

    # Get reference templates
    print("Loading reference templates from English values directory...")
    reference_templates = get_reference_templates(base_path)

    if not reference_templates:
        print("Error: Could not load reference templates")
        return

    print(f"Found {len(reference_templates)} reference templates")
    print(f"Required templates: {', '.join(REQUIRED_TEMPLATES)}")
    print()

    # Find all language pack directories
    language_packs = []
    for item in language_pack_dir.iterdir():
        if item.is_dir() and item.name.startswith('values-'):
            language_packs.append(item)

    if not language_packs:
        print("No language packs found.")
        return

    print(f"Found {len(language_packs)} language pack(s)\n")

    # Process each language pack
    updated_count = 0
    for lang_dir in sorted(language_packs):
        print(f"Checking {lang_dir.name}...")

        template_file = lang_dir / 'template.xml'

        if not template_file.exists():
            print(f"  ⚠️  No template.xml found, creating...")
            if update_template_file(lang_dir, reference_templates):
                print(f"  ✅ Created template.xml with {len(REQUIRED_TEMPLATES)} templates")
                updated_count += 1
        else:
            # Check existing file
            existing = parse_template_xml(str(template_file))
            missing = [t for t in REQUIRED_TEMPLATES if t not in existing]

            if missing:
                print(f"  ⚠️  Missing {len(missing)} template(s): {', '.join(missing)}")
                if update_template_file(lang_dir, reference_templates):
                    print(f"  ✅ Added missing templates (marked for translation)")
                    updated_count += 1
            else:
                print(f"  ✅ All templates present ({len(existing)} templates)")

    print()
    print("=" * 80)
    print(f"Update complete! Updated {updated_count} language pack(s)")
    print("=" * 80)
    print()

    if updated_count > 0:
        print("⚠️  Note: Newly added templates are in English and need translation.")
        print("    Look for comments like '<!-- TODO: Translate ... -->' in the files.")


def main():
    """Main function."""
    workspace_path = os.getcwd()
    check_and_update_language_packs(workspace_path)


if __name__ == "__main__":
    main()

