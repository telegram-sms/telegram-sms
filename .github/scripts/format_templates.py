#!/usr/bin/env python3
"""
Script to properly format and update template.xml files in all language packs.
Ensures all required templates are present with proper formatting.
"""

import os
from pathlib import Path
from typing import Dict

# Template translations for each language
TEMPLATE_TRANSLATIONS = {
    'zh-rCN': {  # Simplified Chinese
        'TPL_system_message': '[系统信息]\n{{Message}}',
        'TPL_battery': '[电池监控]\n电池电量: {{BatteryLevel}}%\n{{Message}}',
        'TPL_send_USSD_chat': '[发送 USSD]\n{{Content}}'
    },
    'zh-rTW': {  # Traditional Chinese
        'TPL_system_message': '[系統資訊]\n{{Message}}',
        'TPL_battery': '[電池監控]\n電池電量: {{BatteryLevel}}%\n{{Message}}',
        'TPL_send_USSD_chat': '[傳送 USSD]\n{{Content}}'
    },
    'zh-rHK': {  # Hong Kong Chinese
        'TPL_system_message': '[系統資訊]\n{{Message}}',
        'TPL_battery': '[電池監控]\n電池電量: {{BatteryLevel}}%\n{{Message}}',
        'TPL_send_USSD_chat': '[傳送 USSD]\n{{Content}}',
        'TPL_receiving_call': '[{{SIM}}接聽來電]\n來自: {{From}}'
    },
    'yue-rCN': {  # Cantonese (China)
        'TPL_system_message': '[系统信息]\n{{Message}}',
        'TPL_battery': '[电池监控]\n电池电量: {{BatteryLevel}}%\n{{Message}}',
        'TPL_send_USSD_chat': '[发送 USSD]\n{{Content}}'
    },
    'yue-rHK': {  # Cantonese (Hong Kong)
        'TPL_system_message': '[系統資訊]\n{{Message}}',
        'TPL_battery': '[電池監控]\n電池電量: {{BatteryLevel}}%\n{{Message}}',
        'TPL_send_USSD_chat': '[傳送 USSD]\n{{Content}}',
        'TPL_receiving_call': '[{{SIM}}接聽嚟電]\n來自: {{From}}'
    },
    'ja-rJP': {  # Japanese
        'TPL_system_message': '[システム情報]\n{{Message}}',
        'TPL_battery': '[バッテリー監視]\nバッテリーレベル: {{BatteryLevel}}%\n{{Message}}',
        'TPL_send_USSD_chat': '[USSD送信]\n{{Content}}'
    },
    'es-rES': {  # Spanish
        'TPL_system_message': '[Información del Sistema]\n{{Message}}',
        'TPL_battery': '[Monitoreo de Batería]\nNivel de batería: {{BatteryLevel}}%\n{{Message}}',
        'TPL_send_USSD_chat': '[Enviar USSD]\n{{Content}}'
    },
    'ru': {  # Russian
        'TPL_system_message': '[Системная информация]\n{{Message}}',
        'TPL_battery': '[Мониторинг батареи]\nУровень батареи: {{BatteryLevel}}%\n{{Message}}',
        'TPL_send_USSD_chat': '[Отправить USSD]\n{{Content}}'
    },
    'vi': {  # Vietnamese
        'TPL_system_message': '[Thông tin hệ thống]\n{{Message}}',
        'TPL_battery': '[Giám sát pin]\nMức pin: {{BatteryLevel}}%\n{{Message}}',
        'TPL_send_USSD_chat': '[Gửi USSD]\n{{Content}}'
    }
}


def create_template_xml(lang_code: str, existing_templates: Dict[str, str]) -> str:
    """Create properly formatted template.xml content."""
    lines = ['<?xml version="1.0" encoding="utf-8"?>', '<resources>']

    # Get translations for this language
    translations = TEMPLATE_TRANSLATIONS.get(lang_code, {})

    # Add existing templates first
    for name, value in existing_templates.items():
        if not name.startswith('TPL_'):
            continue
        lines.append(f'    <string name="{name}">{value}</string>')

    # Add missing templates with translations
    required = [
        'TPL_system_message',
        'TPL_battery',
        'TPL_send_USSD_chat',
        'TPL_receiving_call'
    ]

    for template_name in required:
        if template_name not in existing_templates:
            # Use translated version if available
            if template_name in translations:
                value = translations[template_name]
                lines.append(f'    <string name="{template_name}">{value}</string>')

    lines.append('</resources>')
    lines.append('')  # Empty line at end

    return '\n'.join(lines)


def parse_template_file(file_path: Path) -> Dict[str, str]:
    """Parse template.xml and extract template strings."""
    templates = {}

    if not file_path.exists():
        return templates

    try:
        content = file_path.read_text(encoding='utf-8')

        # Simple parsing to extract templates
        import re
        pattern = r'<string name="(TPL_[^"]+)">([^<]+)</string>'
        matches = re.findall(pattern, content)

        for name, value in matches:
            templates[name] = value

    except Exception as e:
        print(f"  Error parsing {file_path}: {e}")

    return templates


def update_language_pack(lang_dir: Path, lang_code: str):
    """Update template.xml for a language pack."""
    template_file = lang_dir / 'template.xml'

    print(f"Processing {lang_code}...")

    # Parse existing templates
    existing = parse_template_file(template_file)

    if not existing:
        print(f"  ⚠️  No templates found in {template_file}")
        return False

    print(f"  Found {len(existing)} existing templates")

    # Create new formatted content
    new_content = create_template_xml(lang_code, existing)

    # Write back
    template_file.write_text(new_content, encoding='utf-8')
    print(f"  ✅ Updated and formatted template.xml")

    return True


def main():
    """Main function."""
    workspace_path = Path(os.getcwd())
    language_pack_dir = workspace_path / 'app' / 'language_pack'

    if not language_pack_dir.exists():
        print(f"Error: Language pack directory not found: {language_pack_dir}")
        return

    print("=" * 80)
    print("Template File Formatter and Updater")
    print("=" * 80)
    print()

    # Process each language pack
    language_packs = [
        ('values-zh-rCN', 'zh-rCN'),
        ('values-zh-rTW', 'zh-rTW'),
        ('values-zh-rHK', 'zh-rHK'),
        ('values-yue-rCN', 'yue-rCN'),
        ('values-yue-rHK', 'yue-rHK'),
        ('values-ja-rJP', 'ja-rJP'),
        ('values-es-rES', 'es-rES'),
        ('values-ru', 'ru'),
        ('values-vi', 'vi')
    ]

    updated_count = 0

    for dir_name, lang_code in language_packs:
        lang_dir = language_pack_dir / dir_name

        if not lang_dir.exists():
            print(f"⚠️  Directory not found: {dir_name}")
            continue

        if update_language_pack(lang_dir, lang_code):
            updated_count += 1

        print()

    print("=" * 80)
    print(f"✅ Updated {updated_count} language pack(s)")
    print("=" * 80)


if __name__ == "__main__":
    main()

