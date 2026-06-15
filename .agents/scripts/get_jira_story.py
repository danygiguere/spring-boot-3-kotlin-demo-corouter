#!/usr/bin/env python3
"""
Fetch a Jira story and write .jira/<KEY>.md.
Targets Jira Server/Data Center (Bearer token, API v2).
Falls back to reading .jira/payload.json if no token/key is available.
"""
import json, re, sys, urllib.error, urllib.request
from pathlib import Path


# ── env helpers ──────────────────────────────────────────────────────────────

def read_env(key):
    env_file = Path('.env')
    if not env_file.exists():
        return ''
    for line in env_file.read_text(encoding='utf-8').splitlines():
        line = line.strip()
        if line.startswith(f'{key}='):
            return line[len(key) + 1:].strip()
    return ''


# ── description conversion ────────────────────────────────────────────────────

def wiki_to_md(text):
    if not text:
        return ''
    text = re.sub(r'^h(\d)\. ?', lambda m: '#' * int(m.group(1)) + ' ', text, flags=re.MULTILINE)
    text = re.sub(r'\{code(?::[^}]*)?\}(.*?)\{code\}', r'```\n\1\n```', text, flags=re.DOTALL)
    text = re.sub(r'\[([^|]+)\|([^\]]+)\]', r'[\1](\2)', text)
    text = re.sub(r'\*([^*\n]+)\*', r'**\1**', text)
    text = re.sub(r'^[*#]{1,3} ', '- ', text, flags=re.MULTILINE)
    return text.strip()


def adf_node(node, _depth=0):
    if not isinstance(node, dict):
        return str(node) if node else ''
    t = node.get('type', '')
    content = node.get('content', [])
    text = node.get('text', '')

    def children():
        return ''.join(adf_node(c) for c in content)

    if t == 'doc':
        return children()
    if t == 'paragraph':
        return children() + '\n\n'
    if t == 'heading':
        level = node.get('attrs', {}).get('level', 1)
        return '#' * level + ' ' + children() + '\n'
    if t == 'bulletList':
        return children()
    if t == 'orderedList':
        return children()
    if t == 'listItem':
        return '- ' + children().strip() + '\n'
    if t == 'codeBlock':
        lang = node.get('attrs', {}).get('language', '')
        return f'```{lang}\n{children()}```\n\n'
    if t == 'hardBreak':
        return '\n'
    if t == 'text':
        marks = {m['type'] for m in node.get('marks', [])}
        result = text
        if 'strong' in marks:
            result = f'**{result}**'
        if 'em' in marks:
            result = f'*{result}*'
        if 'code' in marks:
            result = f'`{result}`'
        for m in node.get('marks', []):
            if m['type'] == 'link':
                href = m.get('attrs', {}).get('href', '')
                result = f'[{result}]({href})'
        return result
    return children()


def parse_description(desc):
    if not desc:
        return 'N/A'
    if isinstance(desc, dict) and desc.get('type') == 'doc':
        return adf_node(desc).strip() or 'N/A'
    if isinstance(desc, str):
        return wiki_to_md(desc) or 'N/A'
    return str(desc)


# ── field helpers ─────────────────────────────────────────────────────────────

def find_custom_field(fields, names, pattern):
    if not names:
        return None
    for cf_key, cf_name in names.items():
        if re.search(pattern, cf_name, re.IGNORECASE):
            val = fields.get(cf_key)
            if val is not None:
                return val
    return None


def format_dt(dt_str):
    if not dt_str:
        return 'N/A'
    m = re.match(r'(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})', dt_str)
    return f"{m.group(1)}-{m.group(2)}-{m.group(3)} {m.group(4)}:{m.group(5)}" if m else dt_str


def _sprint_str_field(s, field):
    # Jira Server greenhopper format: ...[id=12,state=ACTIVE,name=Sprint 7,startDate=...]
    m = re.search(rf'\b{field}=(.*?)(?:,\s*\w+=|\])', s)
    return m.group(1).strip() if m else ''


def _sprint_entry(s):
    if isinstance(s, dict):
        return s.get('name', ''), str(s.get('state', '')).lower()
    if isinstance(s, str):
        return (_sprint_str_field(s, 'name') or s), _sprint_str_field(s, 'state').lower()
    return str(s), ''


def resolve_sprint(val):
    if not val:
        return 'N/A'
    if isinstance(val, list):
        parsed = [_sprint_entry(s) for s in val]
        active = [name for name, state in parsed if state == 'active']
        if active:
            return active[0]
        return parsed[-1][0] if parsed else 'N/A'
    if isinstance(val, (dict, str)):
        name = _sprint_entry(val)[0]
        return name or 'N/A'
    return str(val)


# ── gitignore ─────────────────────────────────────────────────────────────────

def ensure_gitignore():
    gi = Path('.gitignore')
    entry = '.jira/'
    if gi.exists():
        if entry in gi.read_text(encoding='utf-8'):
            return
        text = gi.read_text(encoding='utf-8').rstrip()
        gi.write_text(text + f'\n{entry}\n', encoding='utf-8')
    else:
        gi.write_text(f'{entry}\n', encoding='utf-8')


# ── main ──────────────────────────────────────────────────────────────────────

def main():
    key_arg = sys.argv[1].strip().upper() if len(sys.argv) > 1 else None

    jira_base_url = read_env('JIRA_BASE_URL')
    jira_token = read_env('JIRA_API_TOKEN')

    payload_path = Path('.jira/payload.json')
    payload_path.parent.mkdir(exist_ok=True)

    if key_arg and jira_token and jira_base_url:
        url = f"{jira_base_url}/rest/api/2/issue/{key_arg}?expand=names"
        req = urllib.request.Request(url, headers={'Authorization': f'Bearer {jira_token}'})
        try:
            with urllib.request.urlopen(req) as resp:
                payload_path.write_bytes(resp.read())
        except urllib.error.HTTPError as e:
            print(f"ERROR: HTTP {e.code} from Jira API: {e.reason}")
            sys.exit(1)
        except urllib.error.URLError as e:
            print(f"ERROR: Cannot reach Jira ({e.reason})")
            sys.exit(1)
    elif not payload_path.exists() or payload_path.stat().st_size == 0:
        if not jira_token:
            print("ERROR: JIRA_API_TOKEN is not set in .env.")
            print(f"Create a Personal Access Token at {jira_base_url or '<JIRA_BASE_URL>'}/secure/ViewProfile.jspa")
            print("then add: JIRA_API_TOKEN=<token> to .env")
        elif not key_arg:
            print("ERROR: No Jira key provided and .jira/payload.json is absent.")
            print("Usage: python3 .agents/scripts/get_jira_story.py PANCCAED-1")
        sys.exit(1)

    try:
        data = json.loads(payload_path.read_text(encoding='utf-8'))
    except json.JSONDecodeError as e:
        print(f"ERROR: Invalid JSON in {payload_path}: {e}")
        sys.exit(1)

    if 'errorMessages' in data:
        print(f"ERROR from Jira API: {data['errorMessages']}")
        if data.get('errors'):
            print(data['errors'])
        sys.exit(1)

    key = data.get('key') or key_arg or 'UNKNOWN'
    fields = data.get('fields', {})
    names = data.get('names', {})

    title = fields.get('summary', 'N/A')
    issue_type = (fields.get('issuetype') or {}).get('name', 'N/A')
    description = parse_description(fields.get('description'))
    labels = ', '.join(fields.get('labels', [])) or 'N/A'
    fix_versions = ', '.join(
        v.get('name', '') for v in (fields.get('fixVersions') or []) if isinstance(v, dict)
    ) or 'N/A'
    created = format_dt(fields.get('created'))
    updated = format_dt(fields.get('updated'))

    sp_val = find_custom_field(fields, names, r'story point')
    story_points = str(sp_val) if sp_val is not None else 'N/A'

    sprint_val = find_custom_field(fields, names, r'^sprint')
    sprint_name = resolve_sprint(sprint_val)

    out_path = Path(f'.jira/{key}.md')
    out_path.write_text(
        f"# {key} — {title}\n\n"
        f"| Champ | Valeur |\n"
        f"|---|---|\n"
        f"| Type | {issue_type} |\n"
        f"| Story Points | {story_points} |\n"
        f"| Sprint | {sprint_name} |\n"
        f"| Version(s) corrigée(s) | {fix_versions} |\n"
        f"| Étiquettes | {labels} |\n"
        f"| Créé le | {created} |\n"
        f"| Mis à jour le | {updated} |\n\n"
        f"## Description\n\n"
        f"{description}\n",
        encoding='utf-8',
    )

    ensure_gitignore()

    print(f"Written: {out_path}")
    na = [f for f, v in [('Story Points', story_points), ('Sprint', sprint_name), ('Version(s) corrigée(s)', fix_versions), ('Étiquettes', labels)] if v == 'N/A']
    if na:
        print(f"Fields left as N/A: {', '.join(na)}")


if __name__ == '__main__':
    main()
