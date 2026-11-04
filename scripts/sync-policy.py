#!/usr/bin/env python3
"""policy.md(원본)를 기준으로 domain/*.md 를 다시 생성한다.

사용법
  python3 scripts/sync-policy.py               모든 버전(docs/policies/v*/)의 domain 파일 생성
  python3 scripts/sync-policy.py v2            특정 버전만 (버전명 또는 docs/policies/v2 경로)
  python3 scripts/sync-policy.py --check [v2]  동기화 여부만 확인 (다르면 종료 코드 1)

규칙 (docs/policies/<버전>/domain-map.json)
  - policy.md 의 "### N.M. 제목" 섹션을 제목으로 매칭해 각 모듈 파일에 순서대로 넣는다.
  - 모든 섹션은 정확히 한 모듈에 속해야 한다. 매핑되지 않은 섹션, 중복 매핑, policy.md 에 없는 섹션이 있으면
    종료 코드 2로 실패하고 목록을 출력한다 (domain-map.json 을 먼저 고쳐야 함).
  - replacements 의 문자열 치환을 적용한다 (원문의 깨진 링크 보정 등).
종료 코드: 0 성공/동기화됨, 1 동기화 필요(--check), 2 매핑 오류
"""
import json
import re
import sys
from pathlib import Path

HEADING = re.compile(r'^###\s+((?:\d+\.)+)\s*(.+?)\s*$')


def parse_sections(text):
    """### 섹션을 {제목: (번호, 본문 줄 목록)} 으로 반환한다."""
    sections, order = {}, []
    cur = None
    for line in text.splitlines():
        m = HEADING.match(line)
        if m:
            cur = m.group(2)
            if cur in sections:
                raise SystemExit(f'policy.md 에 같은 제목의 섹션이 두 번 있습니다: {cur}')
            sections[cur] = (m.group(1).rstrip('.'), [])
            order.append(cur)
        elif line.startswith('## ') or line.startswith('# '):
            cur = None
        elif cur is not None:
            sections[cur][1].append(line)
    for k in sections:
        body = sections[k][1]
        while body and not body[0].strip():
            body.pop(0)
        while body and not body[-1].strip():
            body.pop()
    return sections, order


def build(base):
    cfg = json.loads((base / 'domain-map.json').read_text(encoding='utf-8'))
    sections, order = parse_sections((base / cfg['source']).read_text(encoding='utf-8'))

    mapped = [s for m in cfg['modules'] for s in m['sections']]
    errors = []
    dup = sorted({s for s in mapped if mapped.count(s) > 1})
    if dup:
        errors.append('여러 모듈에 중복 매핑된 섹션: ' + ', '.join(dup))
    missing = [s for s in mapped if s not in sections]
    if missing:
        errors.append('policy.md 에 없는 섹션 (제목이 바뀌었거나 삭제됨): ' + ', '.join(missing))
    unmapped = [f'{sections[s][0]}. {s}' for s in order if s not in mapped]
    if unmapped:
        errors.append('어느 모듈에도 매핑되지 않은 섹션 (domain-map.json 에 추가 필요): ' + ', '.join(unmapped))
    if errors:
        return None, errors

    out = {}
    for m in cfg['modules']:
        nums = ', '.join('§' + sections[s][0] for s in m['sections'])
        lines = [f'# {m["title"]}', '',
                 '> 이 파일은 `policy.md`에서 생성된다. 직접 수정하지 말고 `policy.md`를 고친 뒤 '
                 '`/sync-docs` (또는 `python3 scripts/sync-policy.py`)를 실행한다.',
                 f'> 모듈: `{m["module"]}` · 원본: policy.md {nums}']
        lines += [f'> {n}' for n in m.get('notes', [])]
        for s in m['sections']:
            lines += ['', f'## {s}', ''] + sections[s][1]
        text = '\n'.join(lines) + '\n'
        for r in cfg.get('replacements', []):
            if r['file'] == m['file']:
                text = text.replace(r['from'], r['to'])
        out[base / cfg['target_dir'] / m['file']] = text
    return out, []


def versions(root, args):
    pol = root / 'docs/policies'
    if args:
        a = args[0]
        d = pol / a if (pol / a).is_dir() else root / a
        return [d]
    return sorted((d for d in pol.glob('v*') if (d / 'domain-map.json').exists()),
                  key=lambda d: int(d.name[1:]) if d.name[1:].isdigit() else 0)


def main():
    args = [a for a in sys.argv[1:] if not a.startswith('--')]
    check = '--check' in sys.argv
    root = Path(__file__).resolve().parent.parent
    rel = lambda p: p.relative_to(root)
    bases = versions(root, args)
    if not bases:
        print('동기화할 버전이 없습니다 (docs/policies/v*/domain-map.json).', file=sys.stderr)
        return 2

    code = 0
    for base in bases:
        tag = f'[{base.name}] '
        out, errors = build(base)
        if errors:
            print(tag + '매핑 오류로 동기화할 수 없습니다.', file=sys.stderr)
            for e in errors:
                print(' - ' + e, file=sys.stderr)
            code = 2
            continue
        stale = [p for p, t in out.items() if not p.exists() or p.read_text(encoding='utf-8') != t]
        if check:
            if stale:
                print(tag + 'policy.md 와 domain 파일이 동기화되지 않았습니다: '
                      + ', '.join(str(rel(p)) for p in stale), file=sys.stderr)
                code = max(code, 1)
            else:
                print(tag + '동기화됨')
            continue
        for p in stale:
            p.parent.mkdir(parents=True, exist_ok=True)
            p.write_text(out[p], encoding='utf-8')
        print(tag + '갱신: ' + (', '.join(str(rel(p)) for p in stale) if stale else '없음 (이미 동기화됨)'))
    if check and code == 1:
        print('`/sync-docs` 또는 `python3 scripts/sync-policy.py` 를 실행하세요.', file=sys.stderr)
    return code


if __name__ == '__main__':
    sys.exit(main())
