#!/usr/bin/env python3
"""
Create or switch to a Jira story branch.
Key resolution order: CLI arg → .jira/payload.json → single .jira/<KEY>.md file → error.
Never commits anything.
"""
import json, re, subprocess, sys
from pathlib import Path


KEY_RE = re.compile(r'^[A-Z][A-Z0-9]+-\d+$')


def resolve_key(arg):
    if arg:
        return arg.strip().upper()

    payload = Path('.jira/payload.json')
    if payload.exists():
        try:
            data = json.loads(payload.read_text())
            key = data.get('key', '')
            if KEY_RE.match(key):
                return key
        except Exception:
            pass

    jira_dir = Path('.jira')
    if jira_dir.is_dir():
        mds = [f for f in jira_dir.glob('*.md') if KEY_RE.match(f.stem)]
        if len(mds) == 1:
            return mds[0].stem

    return None


def run(*cmd, check=True):
    return subprocess.run(list(cmd), capture_output=True, text=True, check=check)


def main():
    key = resolve_key(sys.argv[1] if len(sys.argv) > 1 else None)

    if not key:
        print("ERROR: No Jira key found.")
        print("Pass it as argument (e.g. PANCCAED-1), or ensure .jira/payload.json")
        print("contains a 'key' field, or have exactly one .jira/<KEY>.md file.")
        sys.exit(1)

    if not KEY_RE.match(key):
        print(f"ERROR: '{key}' does not look like a Jira key (expected e.g. PANCCAED-1).")
        sys.exit(1)

    current = run('git', 'branch', '--show-current').stdout.strip()

    if current == key:
        print(f"Already on branch '{key}'. Nothing to do.")
        return

    branch_exists = run('git', 'rev-parse', '--verify', key, check=False).returncode == 0

    if branch_exists:
        run('git', 'switch', key)
        print(f"Switched to existing branch '{key}'.")
    else:
        run('git', 'switch', '-c', key)
        print(f"Created and switched to new branch '{key}'.")

    status = run('git', 'status', '--short').stdout.strip()
    if status:
        print("\nUncommitted changes carried over:")
        print(status)
    else:
        print("Working tree is clean.")


if __name__ == '__main__':
    main()
