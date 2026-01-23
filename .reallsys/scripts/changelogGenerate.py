#!/usr/bin/env python3
"""
Changelog Generator Script
Fetches Git commit history and uses AI to generate a structured changelog.
"""

import os
import sys
import subprocess
from typing import Optional
from google import genai
from google.genai import types


class ChangelogGenerator:
    """Generate changelog from Git commits using Gemini AI API"""

    def __init__(self):
        self.api_key = os.getenv("GEMINI_API_KEY")
        self.model = "gemini-3-flash-preview"

        if not self.api_key:
            raise ValueError("GEMINI_API_KEY environment variable must be set")

        # Initialize Gemini client
        self.client = genai.Client(api_key=self.api_key)

    def get_latest_tag(self, repo_path: str = ".") -> str:
        """
        Get the latest tag in the repository

        Args:
            repo_path: Path to git repository

        Returns:
            Latest tag name

        Raises:
            RuntimeError: If git command fails or no tags found
        """
        try:
            result = subprocess.run(
                ["git", "-C", repo_path, "describe", "--tags", "--abbrev=0"],
                capture_output=True,
                text=True,
                check=True
            )
            return result.stdout.strip()
        except subprocess.CalledProcessError as e:
            raise RuntimeError(f"Git command failed: {e.stderr}. No tags found?")

    def get_git_commits(self, repo_path: str = ".", count: int = 10, branch: str = "HEAD") -> str:
        """
        Get Git commit history

        Args:
            repo_path: Path to git repository
            count: Number of commits to fetch
            branch: Branch name

        Returns:
            Formatted commit history
        """
        format_str = "%h | %an | %ad | %s"

        try:
            result = subprocess.run(
                [
                    "git",
                    "-C", repo_path,
                    "log",
                    f"-{count}",
                    branch,
                    f"--pretty=format:{format_str}",
                    "--date=short"
                ],
                capture_output=True,
                text=True,
                check=True,
                encoding='utf-8'
            )
            return result.stdout.strip()
        except subprocess.CalledProcessError as e:
            raise RuntimeError(f"Git command failed: {e.stderr}")

    def get_git_commit_range(self, repo_path: str, from_commit: str, to_commit: str) -> str:
        """
        Get commits between two references (tags, commits, branches)

        Args:
            repo_path: Path to git repository
            from_commit: Starting commit/tag
            to_commit: Ending commit/tag

        Returns:
            Formatted commit history
        """
        format_str = "%h | %an | %ad | %s"

        try:
            result = subprocess.run(
                [
                    "git",
                    "-C", repo_path,
                    "log",
                    f"{from_commit}..{to_commit}",
                    f"--pretty=format:{format_str}",
                    "--date=short",
                    "--tags"
                ],
                capture_output=True,
                text=True,
                encoding='utf-8'
            )
            return result.stdout.strip()
        except subprocess.CalledProcessError as e:
            raise RuntimeError(f"Git command failed: {e.stderr}")

    def detect_breaking_changes(self, commits: str) -> bool:
        """
        Detect if there are breaking changes in commit messages

        Args:
            commits: Commit history

        Returns:
            True if breaking changes detected, False otherwise
        """
        breaking_indicators = [
            "BREAKING CHANGE:",
            "breaking change:",
            "BREAKING:",
            "breaking:",
            "!:",  # Conventional commit with breaking change marker
        ]

        # Potential breaking change patterns (case-insensitive)
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

        for line in commit_lines:
            line_lower = line.lower()

            # Check for explicit breaking change indicators
            for indicator in breaking_indicators:
                if indicator in line_lower:
                    return True

            # Check for conventional commit with ! (e.g., "feat!: something")
            if " | " in line:
                parts = line.split(" | ")
                if len(parts) >= 4:
                    message = parts[3]
                    if message.strip().startswith(('feat!', 'fix!', 'refactor!', 'perf!')):
                        return True

                    # Check for potential breaking patterns in commit message
                    message_lower = message.lower()
                    for pattern in potential_breaking_patterns:
                        import re
                        if re.search(pattern, message_lower):
                            has_potential_breaking = True
                            break

        return has_potential_breaking

    def build_prompt(self, commits: str) -> str:
        """
        Build the prompt for AI API

        Args:
            commits: Commit history

        Returns:
            Formatted prompt
        """
        prompt = (
            "Analyze the following Git commit history and generate a clean, structured changelog.\n"
            "Format: hash | author | date | message\n\n"
            "IMPORTANT: Detect Breaking Changes by looking for:\n"
            "- Commits with 'BREAKING CHANGE:', 'breaking change:', or '!' in commit messages\n"
            "- API changes that break backward compatibility\n"
            "- Removed features or deprecated functionality\n"
            "- Changes to data structures, configuration formats, or interfaces\n"
            "- Database schema changes requiring migration\n"
            "- Library replacements (e.g., Paper to MMKV, SharedPreferences to MMKV)\n"
            "- Storage migration or data format changes\n"
            "- Package renames or major refactoring\n"
            "- Commits with 'migrate', 'migration', 'replace X with Y' that affect user data\n\n"
            "Output ONLY the categorized changelog in this exact format:\n\n"
            "## Summary\n"
            "A concise summary of the changes (approx. 140 characters).\n\n"
            "## ⚠️ Breaking Changes\n"
            "- [hash] message (detailed explanation of what breaks and migration steps)\n\n"
            "## Features\n"
            "- [hash] message\n\n"
            "## Bug Fixes\n"
            "- [hash] message\n\n"
            "## Performance\n"
            "- [hash] message\n\n"
            "## Documentation\n"
            "- [hash] message\n\n"
            "## Other\n"
            "- [hash] message\n\n"
            "Rules:\n"
            "1. Do NOT include any introductory text, notes, or explanations\n"
            "2. Start directly with the Summary\n"
            "3. Breaking Changes section should be placed FIRST after Summary if detected\n"
            "4. Only include changelog categories that have commits (Summary is mandatory)\n"
            "5. Keep commit messages concise\n"
            "6. Use markdown format only\n"
            "7. If no breaking changes found, omit the Breaking Changes section entirely"
        )
        return f"{prompt}\n\nCommit History:\n{commits}"

    def summarize_changelog(self, commits: str) -> str:
        """
        Call Gemini API to summarize commits

        Args:
            commits: Commit history

        Returns:
            Generated changelog

        Raises:
            RuntimeError: If API request fails
        """
        try:
            response = self.client.models.generate_content(
                model=self.model,
                contents=self.build_prompt(commits),
                config=types.GenerateContentConfig(
                    temperature=0,
                    top_p=1,
                    max_output_tokens=100000,
                    candidate_count=1,
                )
            )

            return response.text

        except Exception as e:
            raise RuntimeError(f"Gemini API request failed: {e}")

    def generate(self, repo_path: str = ".") -> str:
        """
        Main method to generate changelog

        Args:
            repo_path: Path to git repository

        Returns:
            Generated changelog
        """
        print("Fetching Git commit history...")
        latest_tag = self.get_latest_tag(repo_path)
        print(f"Latest tag found: {latest_tag}")

        commits = self.get_git_commit_range(repo_path, latest_tag, "HEAD")

        if not commits:
            raise ValueError("No commit history found")

        print(f"\nFetched commits:\n{commits}")

        # Detect breaking changes
        has_breaking = self.detect_breaking_changes(commits)
        if has_breaking:
            print("\n⚠️  WARNING: Potential BREAKING CHANGES detected in commits!")
            print("    Please review the changelog carefully.\n")
        else:
            print("\n✓ No breaking changes detected in commit messages.\n")

        print("Calling Gemini API for summarization...")

        summary = self.summarize_changelog(commits)

        # Verify if AI detected breaking changes
        if "Breaking Changes" in summary or "⚠️" in summary:
            print("\n⚠️  BREAKING CHANGES DETECTED IN CHANGELOG!")
            print("    This release contains breaking changes that may affect users.\n")

        print(f"\n=== Changelog Summary ===\n{summary}")

        return summary

    def write_changelog(self, content: str, output_file: str = "CHANGELOG.md"):
        """
        Write changelog to file

        Args:
            content: Changelog content
            output_file: Output file path
        """
        with open(output_file, 'w', encoding='utf-8') as f:
            f.write(content)
        print(f"\nChangelog written to {output_file}")


def main():
    """Main entry point"""
    try:
        generator = ChangelogGenerator()
        changelog = generator.generate(".")
        generator.write_changelog(changelog)

    except Exception as e:
        print(f"Error: {e}", file=sys.stderr)
        import traceback
        traceback.print_exc()
        sys.exit(1)


if __name__ == "__main__":
    main()
