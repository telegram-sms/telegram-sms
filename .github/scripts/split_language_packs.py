#!/usr/bin/env python3
"""
Script to split monolithic strings.xml files into categorized files
for all language packs based on the new structure.
"""

import os
import sys
from lxml import etree
from pathlib import Path
from typing import Dict, Set

# Define the categorization mapping
STRING_CATEGORIES = {
    'strings.xml': {'Lang', 'time_format'},

    'strings_battery.xml': {
        'battery_low', 'charger_connect', 'charger_disconnect',
        'current_battery_level', 'low_battery_status_end',
        'battery_monitoring', 'battery_monitoring_notify',
        'charger_status', 'charging', 'not_charging', 'battery_title'
    },

    'strings_telegram.xml': {
        'success_connect', 'connect_wait_title', 'connect_wait_message',
        'select_chat', 'bot_token', 'chat_id', 'message_thread_id',
        'get_recent_chat_id', 'test_and_save', 'token_not_configure',
        'chat_id_or_token_not_config', 'unable_get_recent',
        'chat_command', 'unknown_command', 'chat_command_service_name',
        'get_recent_chat_title', 'get_recent_chat_message',
        'using_privacy_mode', 'set_api_title',
        # Chat command strings
        'available_command', 'sendsms_dual', 'sendsms',
        'send_ussd_command', 'send_ussd_dual_command',
        'get_spam_sms', 'spam_count_title', 'no_spam_history'
    },

    'strings_sms.xml': {
        'trusted_phone_number', 'network_error_falls_back_to_sms',
        'trusted_phone_number_empty', 'display_sim_card_alias_in_dual_card_mode',
        'using_verification_code_identification', 'enter_reply_number',
        'enter_reply_content', 'unable_get_phone_number', 'failed_resend',
        'keywords', 'spam_sms_keyword_title', 'spam_keyword_edit_title',
        'spam_keyword_add_title', 'unable_to_obtain_information',
        'template_title', 'send_sms_title', 'receive_sms_title',
        'receive_mms_title', 'verification_code', 'this_is_a_test_message',
        'please_reply_to_continue',
        # Also accept old naming
        'enter_number', 'enter_content',
        # SMS management strings
        'listsms_command', 'listsms_inbox_command', 'sms_list_header',
        'sms_list_empty', 'sms_detail_header', 'sms_from', 'sms_to',
        'sms_date', 'sms_content', 'sms_type_inbox', 'sms_type_sent',
        'sms_type_all', 'sms_not_found', 'sms_deleted', 'sms_delete_failed',
        'sms_delete_confirm', 'not_default_sms_app', 'prev_page', 'next_page'
    },

    'strings_call.xml': {
        'receive_call_title', 'missed_call_title', 'call_notify',
        'receiving_call_title', 'Incoming_number', 'hide_phone_number'
    },

    'strings_ussd.xml': {
        'ussd_code_running', 'enter_ussd_code', 'invalid_ussd_code',
        'send_ussd_title'
    },

    'strings_network.xml': {
        'current_network_connection_status', 'airplane_mode', 'no_network',
        'using_doh', 'proxy_enable', 'proxy_host', 'proxy_port',
        'proxy_username', 'proxy_password', 'proxy_title',
        'proxy_dialog_title', 'doh_over_socks5'
    },

    'strings_cc.xml': {
        'edit_cc_service', 'add_cc_service', 'copy_notification_menu',
        'cc_service_enabled', 'cc_service_disabled', 'cc_service_config_title'
    },

    'strings_notification.xml': {
        'Notification_Listener_title', 'set_notification_listener',
        'receive_notification_title', 'app_name_title', 'title'
    },

    'strings_scanner.xml': {
        'scan_title', 'no_camera_permission', 'transfer_configuration',
        'qrcode_notice', 'an_error_occurred_while_decrypting_the_configuration',
        'an_error_occurred_while_getting_the_configuration',
        'error_id_cannot_be_empty', 'error_password_cannot_be_empty',
        'error_id_must_be_9_characters', 'configuration_sent_successfully',
        'please_enter_your_info', 'getting_configuration',
        'sending_configuration', 'please_enter_your_password',
        'error_password_must_be_6_characters', 'invalid_json_structure',
        'no_entries_available'
    },

    'strings_privacy_about.xml': {
        'user_manual', 'privacy_policy', 'donate',
        'privacy_reminder_information', 'privacy_reminder_title',
        'agree', 'decline', 'visit_page', 'about_title', 'about_content',
        'check_update', 'update_dialog_title', 'update_dialog_body',
        'update_dialog_ok', 'update_dialog_no', 'browser_not_found'
    },

    'strings_common.xml': {
        'logcat', 'service_is_running', 'success', 'restart_service',
        'status', 'send_failed', 'failed_to_get_information',
        'sending', 'no_logs', 'app_list', 'request', 'time',
        'ok_button', 'cancel_button', 'delete_button', 'send_button',
        'reset_button', 'error_title', 'no_service_available',
        'system_message_head'
    }
}

# Additional strings from strings_sms_manage.xml and strings_chat.xml
ADDITIONAL_STRINGS = {
    'strings_sms.xml': {
        'listsms_command', 'listsms_inbox_command', 'sms_list_header',
        'sms_list_empty', 'sms_detail_header', 'sms_from', 'sms_to',
        'sms_date', 'sms_content', 'sms_type_inbox', 'sms_type_sent',
        'sms_type_all', 'sms_not_found', 'sms_deleted', 'sms_delete_failed',
        'sms_delete_confirm', 'not_default_sms_app', 'prev_page', 'next_page'
    },
    'strings_telegram.xml': {
        'available_command', 'sendsms_dual', 'sendsms',
        'send_ussd_command', 'send_ussd_dual_command',
        'get_spam_sms', 'spam_count_title', 'no_spam_history'
    },
    'strings_network.xml': {
        'no_service_available'
    }
}

# Merge additional strings
for category, strings in ADDITIONAL_STRINGS.items():
    if category in STRING_CATEGORIES:
        STRING_CATEGORIES[category].update(strings)


def parse_strings_xml(file_path: str) -> Dict[str, etree.Element]:
    """Parse strings.xml and return dictionary of string elements."""
    try:
        tree = etree.parse(file_path)
        root = tree.getroot()

        strings = {}
        for elem in root.findall('string'):
            name = elem.get('name')
            if name:
                strings[name] = elem

        return strings
    except Exception as e:
        print(f"Error parsing {file_path}: {e}")
        return {}


def create_xml_file(file_path: str, string_elements: list):
    """Create a new XML file with the given string elements."""
    root = etree.Element('resources')

    for elem in string_elements:
        root.append(elem)

    tree = etree.ElementTree(root)

    # Create directory if it doesn't exist
    os.makedirs(os.path.dirname(file_path), exist_ok=True)

    # Write with proper formatting
    tree.write(
        file_path,
        encoding='utf-8',
        xml_declaration=True,
        pretty_print=True
    )

    print(f"Created: {file_path}")


def split_language_pack(language_pack_dir: Path):
    """Split strings.xml in a language pack into categorized files."""
    strings_file = language_pack_dir / 'strings.xml'

    if not strings_file.exists():
        print(f"Skipping {language_pack_dir.name}: strings.xml not found")
        return

    print(f"\nProcessing {language_pack_dir.name}...")

    # Parse existing strings.xml
    all_strings = parse_strings_xml(str(strings_file))

    if not all_strings:
        print(f"  No strings found in {strings_file}")
        return

    print(f"  Found {len(all_strings)} strings")

    # Track which strings have been categorized
    categorized = set()
    uncategorized = set()

    # Split into category files
    for category_file, string_names in STRING_CATEGORIES.items():
        category_strings = []

        for name in sorted(string_names):
            if name in all_strings:
                category_strings.append(all_strings[name])
                categorized.add(name)

        if category_strings:
            output_file = language_pack_dir / category_file
            create_xml_file(str(output_file), category_strings)

    # Check for uncategorized strings
    for name in all_strings:
        if name not in categorized:
            uncategorized.add(name)

    if uncategorized:
        print(f"  ⚠️  Warning: {len(uncategorized)} uncategorized string(s):")
        for name in sorted(uncategorized):
            print(f"    - {name}")

    print(f"  ✅ Categorized {len(categorized)} strings into {len(STRING_CATEGORIES)} files")


def main():
    """Main function to split all language packs."""
    workspace_path = Path(os.getcwd())
    language_pack_base = workspace_path / 'app' / 'language_pack'

    if not language_pack_base.exists():
        print(f"Error: Language pack directory not found: {language_pack_base}")
        sys.exit(1)

    print("=" * 80)
    print("Language Pack String File Splitter")
    print("=" * 80)
    print(f"\nBase directory: {language_pack_base}\n")

    # Find all language pack directories
    language_packs = []
    for item in language_pack_base.iterdir():
        if item.is_dir() and item.name.startswith('values-'):
            language_packs.append(item)

    if not language_packs:
        print("No language packs found.")
        sys.exit(0)

    print(f"Found {len(language_packs)} language pack(s)\n")

    # Process each language pack
    for lang_dir in sorted(language_packs):
        split_language_pack(lang_dir)

    print("\n" + "=" * 80)
    print("✅ Language pack splitting complete!")
    print("=" * 80)


if __name__ == "__main__":
    main()

