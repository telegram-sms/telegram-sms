#!/usr/bin/env python3
"""
Demonstration: Paper to MMKV Breaking Change Detection
Shows how the changelog generator detects library migration as a breaking change.
"""

import re

def detect_breaking_changes(commits: str) -> tuple[bool, list]:
    """
    Detect if there are breaking changes in commit messages
    Returns: (has_breaking_changes, list_of_matched_patterns)
    """
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
    detected_patterns = []

    for line in commit_lines:
        if not line.strip():
            continue

        line_lower = line.lower()

        # Check for explicit breaking change indicators
        for indicator in breaking_indicators:
            if indicator in line_lower:
                detected_patterns.append({
                    'type': 'explicit',
                    'indicator': indicator,
                    'commit': line.strip()
                })
                return True, detected_patterns

        # Check for conventional commit with !
        if " | " in line:
            parts = line.split(" | ")
            if len(parts) >= 4:
                message = parts[3]
                if message.strip().startswith(('feat!', 'fix!', 'refactor!', 'perf!')):
                    detected_patterns.append({
                        'type': 'conventional',
                        'marker': '!',
                        'commit': line.strip()
                    })
                    return True, detected_patterns

                # Check for potential breaking patterns
                message_lower = message.lower()
                for pattern in potential_breaking_patterns:
                    if re.search(pattern, message_lower):
                        has_potential_breaking = True
                        detected_patterns.append({
                            'type': 'pattern',
                            'pattern': pattern,
                            'commit': line.strip()
                        })
                        break

    return has_potential_breaking, detected_patterns


def main():
    # Test case 1: Paper to MMKV migration
    print("=" * 80)
    print("TEST CASE 1: Paper to MMKV Migration (Implicit Breaking Change)")
    print("=" * 80)

    test_commits_1 = """
d419095 | John Doe | 2024-10-30 | refactor(storage): replace Paper library with MMKV for resend list management
abc1234 | Jane Smith | 2024-10-29 | feat: add new notification feature
"""

    has_breaking, patterns = detect_breaking_changes(test_commits_1)
    print(f"\nCommits:")
    print(test_commits_1)
    print(f"\n{'✓' if has_breaking else '✗'} Breaking Changes Detected: {has_breaking}")
    print(f"\nDetected Patterns:")
    for p in patterns:
        print(f"  - Type: {p['type']}")
        print(f"    Pattern: {p.get('pattern', p.get('indicator', p.get('marker')))}")
        print(f"    Commit: {p['commit']}")

    # Test case 2: SharedPreferences to MMKV migration
    print("\n" + "=" * 80)
    print("TEST CASE 2: SharedPreferences to MMKV Migration (Implicit Breaking Change)")
    print("=" * 80)

    test_commits_2 = """
4a80671 | Developer | 2024-10-30 | refactor(storage): migrate SharedPreferences to MMKV for better performance
def5678 | Developer | 2024-10-29 | fix: fix bug in notification service
"""

    has_breaking, patterns = detect_breaking_changes(test_commits_2)
    print(f"\nCommits:")
    print(test_commits_2)
    print(f"\n{'✓' if has_breaking else '✗'} Breaking Changes Detected: {has_breaking}")
    print(f"\nDetected Patterns:")
    for p in patterns:
        print(f"  - Type: {p['type']}")
        print(f"    Pattern: {p.get('pattern', p.get('indicator', p.get('marker')))}")
        print(f"    Commit: {p['commit']}")

    # Test case 3: Explicit breaking change marker
    print("\n" + "=" * 80)
    print("TEST CASE 3: Explicit Breaking Change Marker")
    print("=" * 80)

    test_commits_3 = """
abc1234 | Developer | 2024-10-30 | refactor!: restructure Carbon Copy API
def5678 | Developer | 2024-10-29 | feat: add new feature
"""

    has_breaking, patterns = detect_breaking_changes(test_commits_3)
    print(f"\nCommits:")
    print(test_commits_3)
    print(f"\n{'✓' if has_breaking else '✗'} Breaking Changes Detected: {has_breaking}")
    print(f"\nDetected Patterns:")
    for p in patterns:
        print(f"  - Type: {p['type']}")
        print(f"    Pattern: {p.get('pattern', p.get('indicator', p.get('marker')))}")
        print(f"    Commit: {p['commit']}")

    # Test case 4: No breaking changes
    print("\n" + "=" * 80)
    print("TEST CASE 4: No Breaking Changes")
    print("=" * 80)

    test_commits_4 = """
abc1234 | Developer | 2024-10-30 | feat: add new optional feature
def5678 | Developer | 2024-10-29 | fix: fix bug in service
ghi9012 | Developer | 2024-10-28 | docs: update documentation
"""

    has_breaking, patterns = detect_breaking_changes(test_commits_4)
    print(f"\nCommits:")
    print(test_commits_4)
    print(f"\n{'✓' if has_breaking else '✗'} Breaking Changes Detected: {has_breaking}")
    if patterns:
        print(f"\nDetected Patterns:")
        for p in patterns:
            print(f"  - Type: {p['type']}")
            print(f"    Pattern: {p.get('pattern', p.get('indicator', p.get('marker')))}")
            print(f"    Commit: {p['commit']}")
    else:
        print("\nNo patterns detected - this is a safe release.")

    # Summary
    print("\n" + "=" * 80)
    print("SUMMARY")
    print("=" * 80)
    print("""
The improved breaking change detection can now identify:

1. ✓ Explicit markers (BREAKING CHANGE:, feat!, refactor!, etc.)
2. ✓ Storage library migrations (Paper → MMKV)
3. ✓ SharedPreferences → MMKV migrations
4. ✓ General migration patterns (migrate, replace X with Y)
5. ✓ API changes (change api, incompatible, deprecate)

This ensures that even when developers forget to add explicit breaking change
markers, the system can still detect potentially breaking changes based on
commit message content and prompt the AI to verify and document them properly.
""")


if __name__ == "__main__":
    main()

