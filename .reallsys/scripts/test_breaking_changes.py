#!/usr/bin/env python3
"""
Test script for Breaking Changes detection
Tests the detect_breaking_changes() method with various commit message patterns
"""

import sys
import os

# Add parent directory to path to import changelogGenerate
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from changelogGenerate import ChangelogGenerator


def test_breaking_changes_detection():
    """Test breaking changes detection with various commit patterns"""

    generator = ChangelogGenerator()

    test_cases = [
        {
            "name": "Conventional commit with ! marker",
            "commits": "abc1234 | John Doe | 2026-01-07 | feat!: change API format",
            "expected": True
        },
        {
            "name": "BREAKING CHANGE in uppercase",
            "commits": "def5678 | Jane Smith | 2026-01-07 | refactor: update storage\n\nBREAKING CHANGE: Schema changed",
            "expected": True
        },
        {
            "name": "breaking change in lowercase",
            "commits": "ghi9012 | Bob Wilson | 2026-01-07 | fix: remove old API\n\nbreaking change: removed deprecated method",
            "expected": True
        },
        {
            "name": "Multiple commits with one breaking",
            "commits": """abc1234 | John Doe | 2026-01-07 | feat: add new feature
def5678 | Jane Smith | 2026-01-07 | fix!: remove old API
ghi9012 | Bob Wilson | 2026-01-07 | docs: update readme""",
            "expected": True
        },
        {
            "name": "No breaking changes - normal commits",
            "commits": """abc1234 | John Doe | 2026-01-07 | feat: add new feature
def5678 | Jane Smith | 2026-01-07 | fix: resolve bug
ghi9012 | Bob Wilson | 2026-01-07 | docs: update readme""",
            "expected": False
        },
        {
            "name": "perf! with breaking change",
            "commits": "jkl3456 | Alice Chen | 2026-01-07 | perf!: optimize with new algorithm",
            "expected": True
        },
        {
            "name": "refactor! with breaking change",
            "commits": "mno7890 | Tom Brown | 2026-01-07 | refactor!: restructure data layer",
            "expected": True
        },
        {
            "name": "Empty commits",
            "commits": "",
            "expected": False
        }
    ]

    print("=" * 70)
    print("Testing Breaking Changes Detection")
    print("=" * 70)
    print()

    passed = 0
    failed = 0

    for i, test in enumerate(test_cases, 1):
        result = generator.detect_breaking_changes(test["commits"])
        status = "✓ PASS" if result == test["expected"] else "✗ FAIL"

        if result == test["expected"]:
            passed += 1
        else:
            failed += 1

        print(f"Test {i}: {test['name']}")
        print(f"  Expected: {test['expected']}")
        print(f"  Got: {result}")
        print(f"  Status: {status}")
        print()

    print("=" * 70)
    print(f"Results: {passed} passed, {failed} failed out of {len(test_cases)} tests")
    print("=" * 70)

    return failed == 0


if __name__ == "__main__":
    try:
        # Set dummy environment variables for testing
        os.environ["ONEAPI_BASE_URL"] = "http://dummy.test"
        os.environ["ONEAPI_API_KEY"] = "dummy_key"

        success = test_breaking_changes_detection()
        sys.exit(0 if success else 1)
    except Exception as e:
        print(f"Error running tests: {e}", file=sys.stderr)
        import traceback
        traceback.print_exc()
        sys.exit(1)

