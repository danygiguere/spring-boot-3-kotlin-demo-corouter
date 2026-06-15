#!/usr/bin/env python3
"""
Publish a markdown file to Confluence as a page.
Args: <KEY> <body.md>

- Validates Étiquettes from .jira/<KEY>.md (fails if N/A).
- Converts markdown to Confluence storage format (XHTML).
- Creates the page if it doesn't exist, updates it if it does.
- Applies Étiquettes as Confluence page labels.
- Uses Confluence Server/DC REST API v1 (Bearer token in memory — never in ps).

Required .env vars:
  CONFLUENCE_BASE_URL      e.g. https://confluence.example.com
  CONFLUENCE_API_TOKEN     Personal Access Token
  CONFLUENCE_SPACE_KEY     e.g. DEV
Optional:
  CONFLUENCE_PARENT_PAGE_ID  numeric ID of the parent page for new pages
"""
import json, re, sys, urllib.error, urllib.parse, urllib.request
from pathlib import Path

KEY_RE = re.compile(r'^[A-Z][A-Z0-9]+-\d+$')


# ── env ───────────────────────────────────────────────────────────────────────

def read_env(key):
    env_file = Path('.env')
    if not env_file.exists():
        return ''
    for line in env_file.read_text().splitlines():
        line = line.strip()
        if line.startswith(f'{key}='):
            return line[len(key) + 1:].strip()
    return ''


# ── Jira .md helpers ──────────────────────────────────────────────────────────

def read_etiquettes(key):
    md_path = Path(f'.jira/{key}.md')
    if not md_path.exists():
        return None, f'.jira/{key}.md not found — run /get-jira-story first'
    m = re.search(r'\|\s*Étiquettes\s*\|\s*(.+?)\s*\|', md_path.read_text())
    if not m or m.group(1).strip() in ('N/A', ''):
        return None, 'Étiquettes is N/A — add labels to the Jira story before publishing'
    return [l.strip() for l in m.group(1).split(',') if l.strip()], None


# ── markdown → Confluence storage format ──────────────────────────────────────

def escape_html(text):
    return text.replace('&', '&amp;').replace('<', '&lt;').replace('>', '&gt;')


def inline_md(text):
    text = escape_html(text)
    text = re.sub(r'\*\*(.+?)\*\*', r'<strong>\1</strong>', text)
    text = re.sub(r'__(.+?)__', r'<strong>\1</strong>', text)
    text = re.sub(r'\*(.+?)\*', r'<em>\1</em>', text)
    text = re.sub(r'_(.+?)_', r'<em>\1</em>', text)
    text = re.sub(r'`([^`]+)`', r'<code>\1</code>', text)
    text = re.sub(r'\[([^\]]+)\]\(([^)]+)\)', r'<a href="\2">\1</a>', text)
    return text


def md_to_storage(md):
    lines = md.split('\n')
    out = []
    in_code, code_lang, code_lines = False, '', []
    in_table, table_rows = False, []
    list_stack = []  # list of ('ul'|'ol', indent)

    def close_lists():
        while list_stack:
            out.append(f'</{list_stack.pop()[0]}>')

    def flush_table():
        nonlocal in_table, table_rows
        if not table_rows:
            in_table = False
            return
        out.append('<table><tbody>')
        for i, row in enumerate(table_rows):
            cells = [c.strip() for c in row.strip('|').split('|')]
            tag = 'th' if i == 0 else 'td'
            out.append('<tr>' + ''.join(f'<{tag}>{inline_md(c)}</{tag}>' for c in cells) + '</tr>')
        out.append('</tbody></table>')
        in_table = False
        table_rows = []

    for line in lines:
        # code block
        if line.startswith('```'):
            if in_code:
                content = escape_html('\n'.join(code_lines))
                out.append(
                    f'<ac:structured-macro ac:name="code">'
                    f'<ac:parameter ac:name="language">{code_lang}</ac:parameter>'
                    f'<ac:plain-text-body><![CDATA[{content}]]></ac:plain-text-body>'
                    f'</ac:structured-macro>'
                )
                code_lines, code_lang, in_code = [], '', False
            else:
                code_lang, in_code = line[3:].strip(), True
            continue
        if in_code:
            code_lines.append(line)
            continue

        # table
        if line.startswith('|'):
            if re.match(r'^[\|:\-\s]+$', line):
                continue
            if not in_table:
                close_lists()
                in_table = True
            table_rows.append(line)
            continue
        elif in_table:
            flush_table()

        # empty line
        if not line.strip():
            close_lists()
            out.append('')
            continue

        # heading
        m = re.match(r'^(#{1,6})\s+(.*)', line)
        if m:
            close_lists()
            lvl = len(m.group(1))
            out.append(f'<h{lvl}>{inline_md(m.group(2))}</h{lvl}>')
            continue

        # hr
        if re.match(r'^[-*_]{3,}\s*$', line):
            close_lists()
            out.append('<hr/>')
            continue

        # unordered list
        m = re.match(r'^(\s*)[-*]\s+(.*)', line)
        if m:
            indent = len(m.group(1))
            while list_stack and list_stack[-1][1] > indent:
                out.append(f'</{list_stack.pop()[0]}>')
            if not list_stack or list_stack[-1] != ('ul', indent):
                out.append('<ul>')
                list_stack.append(('ul', indent))
            out.append(f'<li><p>{inline_md(m.group(2))}</p></li>')
            continue

        # ordered list
        m = re.match(r'^(\s*)\d+\.\s+(.*)', line)
        if m:
            indent = len(m.group(1))
            while list_stack and list_stack[-1][1] > indent:
                out.append(f'</{list_stack.pop()[0]}>')
            if not list_stack or list_stack[-1] != ('ol', indent):
                out.append('<ol>')
                list_stack.append(('ol', indent))
            out.append(f'<li><p>{inline_md(m.group(2))}</p></li>')
            continue

        # paragraph
        close_lists()
        out.append(f'<p>{inline_md(line)}</p>')

    close_lists()
    if in_table:
        flush_table()

    return '\n'.join(out)


# ── Confluence API ─────────────────────────────────────────────────────────────

def confluence_call(method, url, token, data=None):
    body = json.dumps(data).encode() if data else None
    headers = {
        'Authorization': f'Bearer {token}',
        'Content-Type': 'application/json',
        'Accept': 'application/json',
    }
    req = urllib.request.Request(url, method=method, data=body, headers=headers)
    try:
        with urllib.request.urlopen(req) as resp:
            return json.loads(resp.read())
    except urllib.error.HTTPError as e:
        detail = e.read().decode(errors='replace')
        raise RuntimeError(f'HTTP {e.code} {e.reason}: {detail}')
    except urllib.error.URLError as e:
        raise RuntimeError(f'Cannot reach Confluence: {e.reason}')


def find_page(base_url, token, space_key, title):
    qs = urllib.parse.urlencode({'spaceKey': space_key, 'title': title, 'expand': 'version'})
    result = confluence_call('GET', f'{base_url}/rest/api/content?{qs}', token)
    results = result.get('results', [])
    return results[0] if results else None


def create_page(base_url, token, space_key, parent_id, title, storage_body):
    data = {
        'type': 'page',
        'title': title,
        'space': {'key': space_key},
        'body': {'storage': {'value': storage_body, 'representation': 'storage'}},
    }
    if parent_id:
        data['ancestors'] = [{'id': str(parent_id)}]
    return confluence_call('POST', f'{base_url}/rest/api/content', token, data)


def update_page(base_url, token, page_id, version, title, storage_body):
    data = {
        'type': 'page',
        'title': title,
        'version': {'number': version + 1},
        'body': {'storage': {'value': storage_body, 'representation': 'storage'}},
    }
    return confluence_call('PUT', f'{base_url}/rest/api/content/{page_id}', token, data)


def add_labels(base_url, token, page_id, labels):
    data = [{'prefix': 'global', 'name': lbl} for lbl in labels]
    confluence_call('POST', f'{base_url}/rest/api/content/{page_id}/label', token, data)


# ── main ──────────────────────────────────────────────────────────────────────

def main():
    if len(sys.argv) < 3:
        print('Usage: python3 publish_to_confluence.py <KEY> <body.md>')
        sys.exit(1)

    key = sys.argv[1].upper()
    body_file = Path(sys.argv[2])

    if not KEY_RE.match(key):
        print(f'ERROR: "{key}" is not a valid Jira key.')
        sys.exit(1)
    if not body_file.exists():
        print(f'ERROR: Body file not found: {body_file}')
        sys.exit(1)

    labels, err = read_etiquettes(key)
    if err:
        print(f'ERROR: {err}')
        sys.exit(1)

    base_url = read_env('CONFLUENCE_BASE_URL').rstrip('/')
    token = read_env('CONFLUENCE_API_TOKEN')
    space_key = read_env('CONFLUENCE_SPACE_KEY')
    parent_id = read_env('CONFLUENCE_PARENT_PAGE_ID') or None

    for name, val in [('CONFLUENCE_BASE_URL', base_url), ('CONFLUENCE_API_TOKEN', token), ('CONFLUENCE_SPACE_KEY', space_key)]:
        if not val:
            print(f'ERROR: {name} is not set in .env')
            sys.exit(1)

    # Extract title from first # heading; rest becomes body
    md = body_file.read_text()
    title_m = re.match(r'^#\s+(.+)$', md, re.MULTILINE)
    if title_m:
        title = title_m.group(1).strip()
        md_body = md[title_m.end():].lstrip('\n')
    else:
        title = f'{key} — PR'
        md_body = md

    storage_body = md_to_storage(md_body)

    try:
        existing = find_page(base_url, token, space_key, title)
        if existing:
            page_id = existing['id']
            version = existing['version']['number']
            update_page(base_url, token, page_id, version, title, storage_body)
            print(f'Updated: {base_url}/pages/viewpage.action?pageId={page_id}')
        else:
            result = create_page(base_url, token, space_key, parent_id, title, storage_body)
            page_id = result['id']
            print(f'Created: {base_url}/pages/viewpage.action?pageId={page_id}')

        add_labels(base_url, token, page_id, labels)
        print(f'Labels applied: {", ".join(labels)}')

    except RuntimeError as e:
        print(f'ERROR: {e}')
        sys.exit(1)


if __name__ == '__main__':
    main()
