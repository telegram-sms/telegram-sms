#!/usr/bin/env python3
"""
Changelog Generator Script
Fetches Git commit history and uses AI to generate a structured changelog.
"""

import os
import sys
import subprocess
import json
import requests
from typing import Optional


class ChangelogGenerator:
    """Generate changelog from Git commits using AI API"""

    def __init__(self):
        self.api_base_url = os.getenv("ONEAPI_BASE_URL")
        self.api_key = os.getenv("ONEAPI_API_KEY")
        self.model = "gpt-5-mini"

        if not self.api_base_url or not self.api_key:
            raise ValueError("ONEAPI_BASE_URL and ONEAPI_API_KEY environment variables must be set")

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
            "Output ONLY the categorized changelog in this exact format:\n\n"
            "## Summary\n"
            "A concise summary of the changes (approx. 140 characters).\n\n"
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
            "3. Only include changelog categories that have commits (Summary is mandatory)\n"
            "4. Keep commit messages concise\n"
            "5. Use markdown format only"
        )
        return f"{prompt}\n\nCommit History:\n{commits}"

    def summarize_changelog(self, commits: str) -> str:
        """
        Call OneAPI to summarize commits

        Args:
            commits: Commit history

        Returns:
            Generated changelog

        Raises:
            RuntimeError: If API request fails
        """
        headers = {
            "Content-Type": "application/json",
            "Authorization": f"Bearer {self.api_key}"
        }

        payload = {
            "model": self.model,
            "messages": [
                {
                    "role": "system",
                    "content": "You are a changelog generator. Output only the formatted changelog without any additional commentary."
                },
                {
                    "role": "user",
                    "content": self.build_prompt(commits)
                }
            ],
            "max_completion_tokens": 100000,
            "top_p": 1,
            "presence_penalty": 0,
            "frequency_penalty": 0,
            "stream": False
        }

        try:
            response = requests.post(
                self.api_base_url,
                headers=headers,
                json=payload,
                timeout=60
            )
            response.raise_for_status()

            data = response.json()

            # Extract content from response
            if "choices" in data and len(data["choices"]) > 0:
                return data["choices"][0]["message"]["content"]
            else:
                return data.get("content", str(data))

        except requests.exceptions.RequestException as e:
            raise RuntimeError(f"API request failed: {e}")

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
        print("\nCalling OneAPI for summarization...")

        summary = self.summarize_changelog(commits)
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

