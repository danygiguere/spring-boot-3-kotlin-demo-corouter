#!/usr/bin/env python3
"""
Gather context for generating a PR title and body.
Compares the current branch (commits AND uncommitted changes) against a base branch.

Usage:
  python3 preflight_pr.py            # prints the detected default base, then context vs it
  python3 preflight_pr.py <base>     # uses <base> as the comparison base
"""
import re, subprocess, sys
from pathlib import Path

TEST_DIFF_LIMIT = 6_000
BASE_CANDIDATES = ('master', 'main', 'develop')


def run(*cmd):
    r = subprocess.run(list(cmd), capture_output=True, text=True)
    return r.stdout.strip(), r.returncode


def read_env(key):
    env_file = Path('.env')
    if not env_file.exists():
        return ''
    for line in env_file.read_text(encoding='utf-8').splitlines():
        line = line.strip()
        if line.startswith(f'{key}='):
            return line[len(key) + 1:].strip()
    return ''


def branch_exists(name):
    _, code = run('git', 'rev-parse', '--verify', '--quiet', name)
    return code == 0


def detect_default_base():
    # Prefer the remote's declared default branch.
    head_ref, code = run('git', 'symbolic-ref', '--quiet', 'refs/remotes/origin/HEAD')
    if code == 0 and head_ref:
        name = head_ref.rsplit('/', 1)[-1]
        if name:
            return name
    for cand in BASE_CANDIDATES:
        if branch_exists(cand):
            return cand
    return 'main'


def main():
    base = sys.argv[1].strip() if len(sys.argv) > 1 else None
    default_base = detect_default_base()

    if not base:
        # No base given yet: report the detected default so the skill can suggest it.
        print(f"=== DEFAULT BASE ===\n{default_base}")
        print("\n(Re-run with a base branch to get the full context, "
              "e.g. python3 .agents/scripts/preflight_pr.py "
              f"{default_base})")
        return

    if not branch_exists(base):
        print(f"ERROR: Base branch '{base}' not found.")
        print(f"Detected default base: {default_base}")
        sys.exit(1)

    branch, _ = run('git', 'branch', '--show-current')
    if branch == base:
        print(f"ERROR: Current branch and base are both '{base}'. Switch to a feature branch.")
        sys.exit(1)

    merge_base, code = run('git', 'merge-base', base, 'HEAD')
    if code != 0 or not merge_base:
        print(f"ERROR: No common ancestor between '{base}' and HEAD.")
        sys.exit(1)

    commits, _ = run('git', 'log', '--oneline', f'{base}..HEAD')
    # Working tree (committed + staged + unstaged) vs the merge base.
    diff_stat, _ = run('git', 'diff', '--stat', merge_base)
    if not commits and not diff_stat:
        print(f"ERROR: No commits ahead of '{base}' and no uncommitted changes. Nothing to describe.")
        sys.exit(1)

    jira_base_url = read_env('JIRA_BASE_URL')

    print(f"=== BASE ===\n{base}")
    print(f"\n=== BRANCH ===\n{branch} (compared to {base})")
    print(f"\n=== COMMITS ===\n{commits or '(no commits yet — uncommitted changes only)'}")
    print(f"\n=== FILES CHANGED (commits + uncommitted) ===\n{diff_stat or '(none)'}")
    print(f"\n=== JIRA_BASE_URL ===\n{jira_base_url or '(not set)'}")

    if re.match(r'^[A-Z][A-Z0-9]+-\d+$', branch):
        jira_md = Path(f'.jira/{branch}.md')
        if jira_md.exists():
            print(f"\n=== JIRA STORY ({branch}) ===")
            print(jira_md.read_text(encoding='utf-8').strip())

    test_diff, _ = run('git', 'diff', merge_base, '--', 'src/test')
    print("\n=== TEST DIFF (src/test/) ===")
    if test_diff:
        if len(test_diff) > TEST_DIFF_LIMIT:
            test_diff = test_diff[:TEST_DIFF_LIMIT] + '\n[... truncated ...]'
        print(test_diff)
    else:
        print("(no changes under src/test/)")


if __name__ == '__main__':
    main()
