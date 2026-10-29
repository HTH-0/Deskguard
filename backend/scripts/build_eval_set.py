# -*- coding: utf-8 -*-
"""
평가 세트 생성기(명세 AC-10.1, 10.1a, 10.1b, 10.2). 생성기 3종이 만든 업무 요청 틀(`docs/eval/templates/*.jsonl`)의
자리 표시자를 가짜 값(`fake_values.py`)으로 채우고, 넣은 값의 범주, 항목, 위치를 정답으로 함께 저장한다.
틀의 `items` 표기는 믿지 않고 자리 표시자에서 항목을 직접 정한다(생성기가 잘못 적은 경우가 있었다).
실제 개인정보는 쓰지 않는다. 같은 틀과 seed는 같은 평가 세트를 만든다.

실행: cd backend/scripts && python build_eval_set.py --templates ../../docs/eval/templates --out ../../docs/eval/set/eval_v1.jsonl
"""
import argparse
import hashlib
import json
import random
import re
import sys
from pathlib import Path

import fake_values as fv

# 회사 사전(docs/dummy-company.md 2, 3장). backend/src/main/resources/company/dictionary.yml과 같아야 한다.
COMPANY = {
    "CODENAME": ["블루웨일", "오로라", "프로젝트 해솔", "NIMBUS-7", "스톤브릿지"],
    "CLIENT": ["한결물산", "다온로지스", "새벽유통", "미르상사", "온빛푸드"],
    "CLASSIFIED": ["대외비", "사내한", "극비", "Confidential", "Internal Only"],
}
HQ_VALUES = {"HQ_PHONE": "02-555-0100", "HQ_EMAIL": "contact@hth.example"}
LIST_SIZE = 25  # 대량 기준(20)을 넘는다

# 자리 표시자 -> (범주, 항목). 탐지 대상이 아닌 것(NAME, BANK, HQ_*)은 여기에 없다.
DETECTED = {
    "RRN": ("D1", "RRN"), "FRN": ("D1", "FRN"), "CARD": ("D1", "CARD"), "PHONE": ("D1", "PHONE"),
    "ACCOUNT": ("D1", "ACCOUNT"), "EMAIL": ("D1", "EMAIL"),
    "PRIVATE_KEY": ("D2", "PRIVATE_KEY"), "CONN_STRING": ("D2", "CONN_STRING"), "API_KEY": ("D2", "API_KEY"),
    "JWT": ("D2", "JWT"), "PASSWORD": ("D2", "PASSWORD"),
    "CODENAME": ("D3", "CODENAME"), "CLIENT": ("D3", "CLIENT"), "CLASSIFIED": ("D3", "CLASSIFIED"),
    "PHONE_LIST": ("D1", "PHONE"), "EMAIL_LIST": ("D1", "EMAIL"),
}
SUPPORTED = set(DETECTED) | {"NAME", "BANK"} | set(HQ_VALUES)
BULK_PLACEHOLDERS = {"PHONE_LIST", "EMAIL_LIST"}
BULK_THRESHOLD = 20  # detection.bulk-threshold. 측정 전에 동결한 값과 같아야 한다.
_TOKEN = re.compile(r"\{([A-Z_]+)\}")


def rng_for(key):
    """키 문자열마다 정해진 난수 생성기를 돌려준다(같은 키는 같은 값)."""
    return fv.Fake(int.from_bytes(hashlib.sha256(str(key).encode("utf-8")).digest()[:8], "big"))


def _unique(make, count):
    seen, out = set(), []
    while len(out) < count:
        value = make()
        if value not in seen:
            seen.add(value)
            out.append(value)
    return out


def _value(token, fake, ctx):
    """자리 표시자 하나의 값을 만든다. 목록은 줄바꿈으로 이은 값 여러 개를 (값, 개별 값 목록)으로 돌려준다."""
    if token == "NAME":
        return fake.name(), None
    if token == "BANK":
        bank = ctx.setdefault("bank", fake.r.choice(fv.BANKS))
        return bank, None
    if token in HQ_VALUES:
        return HQ_VALUES[token], None
    if token in COMPANY:
        return fake.r.choice(COMPANY[token]), None
    if token == "API_KEY":
        return fake.r.choice([fake.aws_key, fake.openai_key, fake.github_token])(), None
    if token == "PHONE_LIST":
        values = _unique(fake.phone, LIST_SIZE)
        return "\n".join(values), values
    if token == "EMAIL_LIST":
        values = _unique(fake.email, LIST_SIZE)
        return "\n".join(values), values
    method = {"RRN": fake.rrn, "FRN": fake.frn, "CARD": fake.card, "PHONE": fake.phone, "EMAIL": fake.email,
              "PRIVATE_KEY": fake.private_key, "CONN_STRING": fake.conn_string, "JWT": fake.jwt,
              "PASSWORD": fake.password}[token]
    return method(), None


def _fill_once(template, fake):
    ctx, out, spans, pos = {}, [], [], 0
    tokens = [m for m in _TOKEN.finditer(template) if m.group(1) in SUPPORTED]
    # {BANK}가 {ACCOUNT}보다 앞에 있어도 같은 은행을 쓰도록 먼저 계좌의 은행을 정한다
    if any(m.group(1) == "ACCOUNT" for m in tokens):
        ctx["bank"] = fake.r.choice(fv.BANKS)
    account_bank = ctx.get("bank")
    cursor = 0
    for m in tokens:
        out.append(template[cursor:m.start()])
        offset = sum(len(x) for x in out)
        token = m.group(1)
        if token == "ACCOUNT":
            _, number = fake.account()
            value, parts = number, None
        elif token == "BANK" and account_bank:
            value, parts = account_bank, None
        else:
            value, parts = _value(token, fake, ctx)
        if token in DETECTED:
            category, item = DETECTED[token]
            if parts:
                at = offset
                for part in parts:
                    spans.append({"category": category, "item": item, "start": at, "end": at + len(part)})
                    at += len(part) + 1
            else:
                spans.append({"category": category, "item": item, "start": offset, "end": offset + len(value)})
        out.append(value)
        cursor = m.end()
    out.append(template[cursor:])
    return "".join(out), spans


def fill_template(template, fake):
    """틀의 자리 표시자를 가짜 값으로 채운다. (문장, 정답 span 목록)을 돌려준다."""
    return _fill_once(template, fake)


def expected_action(spans):
    """게이트웨이가 정답 span만 알았을 때 내야 하는 처리(docs/design.md 3장)."""
    if any(s["category"] == "D2" or s["item"] == "CLASSIFIED" for s in spans):
        return "BLOCK"
    if sum(1 for s in spans if s["category"] == "D1") >= BULK_THRESHOLD:
        return "WARN"
    return "MASK" if spans else "ALLOW"


def build_dataset(template_dir, base_seed="deskguard-eval-v1"):
    """틀 파일들에서 평가 세트 기록 목록을 만든다. 파일 이름순, 줄 순서대로 id를 매긴다."""
    records, seen = [], {}
    for path in sorted(Path(template_dir).glob("*.jsonl")):
        source = path.stem
        lines = [l for l in path.read_text(encoding="utf-8").splitlines() if l.strip()]
        for n, line in enumerate(lines, 1):
            row = json.loads(line)
            template, kind = row["template"], row["kind"]
            if template in seen:
                raise ValueError(f"{path.name} {n}행: 같은 틀이 {seen[template]}에 이미 있다")
            tokens = {m.group(1) for m in _TOKEN.finditer(template) if m.group(1) in SUPPORTED}
            detected = tokens & set(DETECTED)
            if kind == "normal" and detected:
                raise ValueError(f"{path.name} {n}행: 정상 틀에 민감 자리 표시자가 있다 {sorted(detected)}")
            declared = kind
            if kind == "sensitive" and not detected:
                kind = "normal"  # 정의상 D1~D3 값이 없으면 정상 문장이다(은행 이름만 있는 경우 등). 틀 문구는 고치지 않는다
            rec_id = f"{source}-{n:03d}"
            seen[template] = rec_id
            text, spans = fill_template(template, rng_for(f"{base_seed}:{rec_id}"))
            record = {"id": rec_id, "text": text, "kind": kind, "expected_action": expected_action(spans),
                      "spans": spans, "source": source}
            if declared != kind:
                record["declared_kind"] = declared
            records.append(record)
    return records


def write_dataset(records, out_path):
    """JSONL과 SHA-256 해시 파일(.sha256)을 쓰고 해시를 돌려준다(AC-10.2 동결)."""
    out = Path(out_path)
    out.parent.mkdir(parents=True, exist_ok=True)
    data = "".join(json.dumps(r, ensure_ascii=False, separators=(",", ":")) + "\n" for r in records).encode("utf-8")
    out.write_bytes(data)
    digest = hashlib.sha256(data).hexdigest()
    out.with_suffix(".sha256").write_text(digest + "\n", encoding="utf-8")
    return digest


def check_quota(records):
    """AC-10.1b 규모 기준을 점검하고 부족한 항목을 문장으로 돌려준다."""
    problems = []
    items = sorted({v[1] for v in DETECTED.values()})
    for item in items:
        n = sum(1 for r in records if any(s["item"] == item for s in r["spans"]))
        if n < 30:
            problems.append(f"{item} 문장 {n}개(30개 이상 필요)")
    normal = sum(1 for r in records if r["kind"] == "normal")
    if normal < 100:
        problems.append(f"정상 문장 {normal}개(100개 이상 필요)")
    bulk = sum(1 for r in records if sum(1 for s in r["spans"] if s.get("category", "D1") == "D1") >= BULK_THRESHOLD)
    if bulk < 10:
        problems.append(f"대량 문장 {bulk}개(10개 이상 필요)")
    return problems


def main(argv=None):
    ap = argparse.ArgumentParser(description="업무 요청 틀에서 평가 세트를 만든다")
    ap.add_argument("--templates", required=True)
    ap.add_argument("--out", required=True)
    ap.add_argument("--seed", default="deskguard-eval-v1")
    args = ap.parse_args(argv)
    records = build_dataset(args.templates, args.seed)
    problems = check_quota(records)
    digest = write_dataset(records, args.out)
    print(f"만든 문장 {len(records)}개, 해시 {digest}")
    for p in problems:
        print("규모 기준 미달:", p)
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
