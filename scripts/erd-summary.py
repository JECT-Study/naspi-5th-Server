#!/usr/bin/env python3
"""draw.io ERD 를 텍스트로 요약하고, 페이지 간 테이블 정의가 일치하는지 검사한다.

사용법
  python3 scripts/erd-summary.py                         전체 페이지 요약 (docs/erd/naspi.drawio)
  python3 scripts/erd-summary.py --page "4. 자산"          특정 페이지만 (이름 일부로 매칭)
  python3 scripts/erd-summary.py --table account,holding  특정 테이블만 (영문 테이블명)
  python3 scripts/erd-summary.py --check                  일관성 검사만 (불일치 시 종료 코드 1)
  python3 scripts/erd-summary.py <파일> ...               다른 drawio 파일

규칙
  - 테이블 = swimlane 도형. 테이블명은 제목의 괄호 안 영문 (예: "현금계좌 (account)" → account).
  - 제목에 "→" 가 있는 도형은 다른 페이지 테이블을 가리키는 참조(PK만 표시)로 보고 정의 비교에서 제외한다.
  - 같은 테이블이 여러 페이지에 정의되어 있으면 (도메인 페이지 + 전체 페이지) 컬럼 목록이 같아야 한다.
"""
import html
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DEFAULT = ROOT / 'docs/erd/naspi.drawio'
NAME = re.compile(r'\(([A-Za-z_][A-Za-z0-9_]*)\)')


def clean(v):
    v = html.unescape(v or '')
    v = re.sub(r'<br\s*/?>', ' / ', v)
    v = re.sub(r'<[^>]+>', '', v)
    return html.unescape(v).replace('\xa0', ' ').strip()


def load(path):
    pages = []
    for d in ET.parse(path).getroot().iter('diagram'):
        cells, label = {}, {}
        for el in d.iter():
            if el.tag == 'UserObject':  # 링크가 걸린 도형은 UserObject 로 감싸져 있다
                c = el.find('mxCell')
                cells[el.get('id')] = c
                label[el.get('id')] = el.get('label')
            elif el.tag == 'mxCell' and el.get('id') is not None and el.get('id') not in cells:
                cells[el.get('id')] = el
                label[el.get('id')] = el.get('value')
        kids = {}
        for cid, c in cells.items():
            kids.setdefault(c.get('parent'), []).append(cid)
        tables = {}
        for cid, c in cells.items():
            if c.get('vertex') == '1' and (c.get('style') or '').startswith('swimlane'):
                title = clean(label[cid])
                m = NAME.search(title)
                cols = [clean(label[k]) for k in kids.get(cid, []) if cells[k].get('vertex') == '1']
                tables[cid] = {'title': title, 'name': m.group(1) if m else title,
                               'ref': '→' in title, 'cols': [x for x in cols if x],
                               'dashed': 'dashed=1' in (c.get('style') or '')}

        def owner(cid):
            while cid in cells and cid not in tables:
                cid = cells[cid].get('parent')
            return tables.get(cid)

        edges = []
        for cid, c in cells.items():
            if c.get('edge') == '1':
                s, t = owner(c.get('source')), owner(c.get('target'))
                st = c.get('style') or ''
                a = re.search(r'startArrow=([^;]+)', st)
                b = re.search(r'endArrow=([^;]+)', st)
                edges.append((s['name'] if s else '?', a.group(1) if a else '-', clean(label[cid]),
                              b.group(1) if b else '-', t['name'] if t else '?'))
        pages.append({'name': d.get('name'), 'tables': list(tables.values()), 'edges': edges})
    return pages


CARD = {'ERmany': 'N', 'ERmandOne': '1', 'ERone': '1', 'ERzeroToOne': '0..1', 'ERoneToMany': '1..N',
        'ERzeroToMany': '0..N', 'open': '→', 'none': ''}


def check(pages):
    defs = {}
    for p in pages:
        for t in p['tables']:
            if not t['ref']:
                defs.setdefault(t['name'], []).append((p['name'], t['cols']))
    problems = []
    for name, ds in defs.items():
        base_page, base = ds[0]
        for page, cols in ds[1:]:
            if cols != base:
                only_a = [c for c in base if c not in cols]
                only_b = [c for c in cols if c not in base]
                problems.append(f'{name}: "{base_page}" 와 "{page}" 의 컬럼이 다릅니다. '
                                f'{base_page}에만: {only_a or "-"} / {page}에만: {only_b or "-"}'
                                + (' (순서만 다름)' if not only_a and not only_b else ''))
    return problems


def main():
    argv = sys.argv[1:]
    opt = lambda k: argv[argv.index(k) + 1] if k in argv else None
    files = [a for i, a in enumerate(argv) if not a.startswith('--') and (i == 0 or argv[i - 1] not in ('--page', '--table'))]
    path = Path(files[0]) if files else DEFAULT
    pages = load(path)

    problems = check(pages)
    if '--check' in argv:
        if problems:
            print('ERD 페이지 간 테이블 정의 불일치:', file=sys.stderr)
            for p in problems:
                print(' - ' + p, file=sys.stderr)
            return 1
        print(f'일관됨 (페이지 {len(pages)}개)')
        return 0

    page_f, table_f = opt('--page'), opt('--table')
    want = set(table_f.split(',')) if table_f else None
    for p in pages:
        if page_f and page_f not in p['name']:
            continue
        print(f'\n######## {p["name"]}')
        for t in p['tables']:
            if want and t['name'] not in want:
                continue
            tag = ' [참조]' if t['ref'] else (' [점선: v2/선택]' if t['dashed'] else '')
            print(f'\n## {t["title"]}{tag}')
            if not t['ref']:
                for c in t['cols']:
                    print(f'    {c}')
        rel = [e for e in p['edges'] if not want or e[0] in want or e[4] in want]
        if rel:
            print('\n-- 관계 (자식 카디널리티 -- 라벨 -- 부모 카디널리티)')
            for s, a, lab, b, t in rel:
                print(f'   {s} [{CARD.get(a, a)}] --{lab}-- [{CARD.get(b, b)}] {t}')
    if problems:
        print('\n-- ⚠️ 페이지 간 불일치')
        for p in problems:
            print('   ' + p)
    return 0


if __name__ == '__main__':
    sys.exit(main())
