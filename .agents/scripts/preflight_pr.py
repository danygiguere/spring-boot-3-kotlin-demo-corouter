#!/usr/bin/env python3
"""
Gather context for PR creation.
Replaces multiple Claude tool calls with one Bash invocation.
"""
import re, subprocess, sys
from pathlib import Path

TEST_DIFF_LIMIT = 6_000


def run(*cmd):
    r = subprocess.run(list(cmd), capture_output=True, text=True)
    return r.stdout.strip(), r.returncode


def read_env(key):
    env_file = Path('.env')
    if not env_file.exists():
        return ''
    for line in env_file.read_text().splitlines():
        line = line.strip()
        if line.startswith(f'{key}='):
            return line[len(key) + 1:].strip()
    return ''


def parse_remote(url):
    m = re.match(r'git@github\.com:([^/]+)/(.+?)(?:\.git)?$', url)
    if m:
        return m.group(1), m.group(2)
    m = re.match(r'https://github\.com/([^/]+)/(.+?)(?:\.git)?$', url)
    if m:
        return m.group(1), m.group(2)
    return None, None


def main():
    remote_url, _ = run('git', 'remote', 'get-url', 'origin')
    owner, repo = parse_remote(remote_url)
    if not owner:
        print(f"ERROR: Cannot parse GitHub owner/repo from: {remote_url}")
        sys.exit(1)

    branch, _ = run('git', 'branch', '--show-current')
    default_branches = ('main', 'master', 'develop')
    if branch in default_branches:
        print(f"ERROR: On '{branch}'. Switch to a feature branch first.")
        sys.exit(1)

    remote_check, _ = run('git', 'ls-remote', '--heads', 'origin', branch)
    if not remote_check.strip():
        print(f"ERROR: Branch '{branch}' is not on the remote.")
        print(f"Push it first:  git push -u origin {branch}")
        sys.exit(1)

    commits, _ = run('git', 'log', '--oneline', 'main..HEAD')
    if not commits:
        print("ERROR: No commits ahead of main. Nothing to PR.")
        sys.exit(1)

    diff_stat, _ = run('git', 'diff', '--stat', 'main...HEAD')
    jira_base_url = read_env('JIRA_BASE_URL')

    print(f"=== REPO ===\n{owner}/{repo}")
    print(f"\n=== BRANCH ===\n{branch} → main")
    print(f"\n=== COMMITS ===\n{commits}")
    print(f"\n=== FILES CHANGED ===\n{diff_stat}")
    print(f"\n=== JIRA_BASE_URL ===\n{jira_base_url or '(not set)'}")

    if re.match(r'^[A-Z][A-Z0-9]+-\d+$', branch):
        jira_md = Path(f'.jira/{branch}.md')
        if jira_md.exists():
            print(f"\n=== JIRA STORY ({branch}) ===")
            print(jira_md.read_text().strip())

    test_diff, _ = run('git', 'diff', 'main...HEAD', '--', 'src/test')
    print("\n=== TEST DIFF (src/test/) ===")
    if test_diff:
        if len(test_diff) > TEST_DIFF_LIMIT:
            test_diff = test_diff[:TEST_DIFF_LIMIT] + '\n[... truncated ...]'
        print(test_diff)
    else:
        print("(no changes under src/test/)")


if __name__ == '__main__':
    main()
