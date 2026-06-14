#!/usr/bin/env python3
"""
Gather git context for commit message suggestion.
Replaces multiple Claude tool calls with one Bash invocation.
"""
import re, subprocess, sys
from pathlib import Path

DIFF_LIMIT = 12_000


def run(*cmd):
    return subprocess.run(list(cmd), capture_output=True, text=True).stdout.strip()


def main():
    status = run('git', 'status', '--short')
    if not status:
        print("NOTHING TO COMMIT: working tree is clean.")
        sys.exit(0)

    diff = run('git', 'diff', 'HEAD')
    log = run('git', 'log', '--oneline', '-15')
    branch = run('git', 'branch', '--show-current')

    print("=== STATUS ===")
    print(status)

    print("\n=== RECENT COMMITS (style reference) ===")
    print(log or "(no commits yet)")

    print("\n=== DIFF (HEAD) ===")
    if len(diff) > DIFF_LIMIT:
        diff = diff[:DIFF_LIMIT] + '\n\n[... diff truncated ...]'
    print(diff or "(no diff)")

    if re.match(r'^[A-Z][A-Z0-9]+-\d+$', branch):
        jira_md = Path(f'.jira/{branch}.md')
        if jira_md.exists():
            print(f"\n=== JIRA CONTEXT ({branch}) ===")
            print(jira_md.read_text().strip())


if __name__ == '__main__':
    main()
