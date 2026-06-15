#!/usr/bin/env python3
"""
Validate and gather context for Confluence publishing.
Fails fast if Étiquettes are missing — before Claude generates any content.
"""
import re, subprocess, sys
from pathlib import Path

KEY_RE = re.compile(r'^[A-Z][A-Z0-9]+-\d+$')
DIFF_LIMIT = 6_000


def run(*cmd):
    return subprocess.run(list(cmd), capture_output=True, text=True).stdout.strip()


def resolve_key():
    branch = run('git', 'branch', '--show-current')
    if KEY_RE.match(branch):
        return branch
    return None


def read_etiquettes(key):
    md_path = Path(f'.jira/{key}.md')
    if not md_path.exists():
        return None, f'.jira/{key}.md not found — run /get-jira-story first'
    text = md_path.read_text()
    m = re.search(r'\|\s*Étiquettes\s*\|\s*(.+?)\s*\|', text)
    if not m or m.group(1).strip() in ('N/A', ''):
        return None, 'Étiquettes is N/A — add labels to the Jira story before publishing to Confluence'
    labels = [l.strip() for l in m.group(1).split(',') if l.strip()]
    return labels, None


def main():
    key = resolve_key()
    if not key:
        print('ERROR: Not on a Jira story branch (expected e.g. PANCCAED-1).')
        sys.exit(1)

    labels, err = read_etiquettes(key)
    if err:
        print(f'ERROR: {err}')
        sys.exit(1)

    jira_md = Path(f'.jira/{key}.md').read_text().strip()
    commits = run('git', 'log', '--oneline', 'main..HEAD')
    diff_stat = run('git', 'diff', '--stat', 'main...HEAD')
    test_diff = run('git', 'diff', 'main...HEAD', '--', 'src/test')
    if len(test_diff) > DIFF_LIMIT:
        test_diff = test_diff[:DIFF_LIMIT] + '\n[... truncated ...]'

    print(f'=== KEY ===\n{key}')
    print(f'\n=== ÉTIQUETTES ===\n{", ".join(labels)}')
    print(f'\n=== JIRA STORY ===\n{jira_md}')
    print(f'\n=== COMMITS (main..HEAD) ===\n{commits or "(none)"}')
    print(f'\n=== FILES CHANGED ===\n{diff_stat or "(none)"}')
    print(f'\n=== TEST DIFF (src/test/) ===\n{test_diff or "(no changes under src/test/)"}')


if __name__ == '__main__':
    main()
