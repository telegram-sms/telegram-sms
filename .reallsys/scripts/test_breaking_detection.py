#!/usr/bin/env python3
"""Test script for breaking change detection"""

import re


def detect_breaking_changes(commits: str) -> bool:
    """Test the breaking change detection logic"""
    breaking_indicators = [
        "BREAKING CHANGE:",
        "breaking change:",
        "BREAKING:",
        "breaking:",
        "!:",
    ]

    potential_breaking_patterns = [
        "migrate",
        "migration",
        "replace.*with",
        "removed.*feature",
        "deprecate",
        "rename.*package",
        "change.*api",
        "incompatible",
        "paper.*mmkv",
        "sharedpreferences.*mmkv",
    ]

    commit_lines = commits.split('\n')
    has_potential_breaking = False
    matched_patterns = []

    for line in commit_lines:
        line_lower = line.lower()

        # Check for explicit breaking change indicators
        for indicator in breaking_indicators:
            if indicator in line_lower:
                print(f"✓ Explicit breaking change found: {indicator}")
                return True

        # Check for conventional commit with !
        if " | " in line:
            parts = line.split(" | ")
            if len(parts) >= 4:
                message = parts[3]
                if message.strip().startswith(('feat!', 'fix!', 'refactor!', 'perf!')):
                    print(f"✓ Conventional commit with ! found: {message}")
                    return True

                # Check for potential breaking patterns
                message_lower = message.lower()
                for pattern in potential_breaking_patterns:
                    if re.search(pattern, message_lower):
                        has_potential_breaking = True
                        matched_patterns.append(f"  - Pattern '{pattern}' in: {message}")
                        break

    if matched_patterns:
        print("✓ Potential breaking changes detected:")
        for match in matched_patterns:
            print(match)

    return has_potential_breaking


# Test cases
test_commits = """
4a80671 | John Doe | 2024-10-30 | refactor(storage): migrate SharedPreferences to MMKV for better performance
d419095 | Jane Smith | 2024-10-30 | refactor(storage): replace Paper library with MMKV for resend list management
abc1234 | Developer | 2024-10-29 | feat: add new feature
def5678 | Developer | 2024-10-28 | fix: fix bug in service
"""

print("Testing Paper to MMKV migration detection:")
print("=" * 60)
result = detect_breaking_changes(test_commits)
print("\n" + "=" * 60)
print(f"Result: {'BREAKING CHANGES DETECTED' if result else 'No breaking changes'}")

