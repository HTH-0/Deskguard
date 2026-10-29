# -*- coding: utf-8 -*-
"""
우회 시험 세트 생성기(명세 AC-10.7a). 평가 세트와 별도로, 같은 종류의 가짜 값에 사람이 읽으면 같은 값으로 알아볼 수 있는
변형(구분자 제거, 공백, 전각 숫자, 한글 숫자, 보이지 않는 문자 등)을 가해 탐지기가 놓치는지 본다.
항목마다 변형하지 않은 대조 문장 2개와, 변형 유형마다 서로 다른 값 2개를 넣는다(총 120문장). 합격 기준은 없다.
실제 개인정보는 쓰지 않는다. 같은 seed는 같은 세트를 만든다.

실행: cd backend/scripts && python build_bypass_set.py --out ../../docs/eval/set/bypass_v1.jsonl
"""
import argparse
import sys

import build_eval_set as be

ZWSP = "​"
_DIGITS_FULL = str.maketrans("0123456789", "０１２３４５６７８９")
_DIGITS_KO = str.maketrans({"0": "공", "1": "일", "2": "이", "3": "삼", "4": "사", "5": "오", "6": "육", "7": "칠", "8": "팔", "9": "구", "-": " "})


def _mid(v, piece):
    i = len(v) // 2
    return v[:i] + piece + v[i:]


T = {
    "control": lambda v: v,
    "no_hyphen": lambda v: v.replace("-", ""),
    "space_sep": lambda v: v.replace("-", " "),
    "dot_sep": lambda v: v.replace("-", "."),
    "fullwidth": lambda v: v.translate(_DIGITS_FULL),
    "korean_digits": lambda v: v.translate(_DIGITS_KO),
    "zero_width": lambda v: _mid(v, ZWSP),
    "spaced_digits": lambda v: " ".join(v),
    "at_word": lambda v: v.split("@")[0] + " at " + v.split("@")[1].replace(".", " dot "),
    "bracket_at": lambda v: v.replace("@", "[at]"),
    "spaced_at": lambda v: v.replace("@", " @ "),
    "upper": lambda v: v.upper(),
    "lower": lambda v: v.lower(),
    "split_space": lambda v: _mid(v, " "),
    "split_newline": lambda v: _mid(v, "\n"),
    "upper_scheme": lambda v: v.split("://")[0].upper() + "://" + v.split("://", 1)[1],
    "scheme_space": lambda v: v.replace("://", ":// ", 1),
    "jwt_dot_space": lambda v: v.replace(".", ". "),
    "spaced_chars": lambda v: " ".join(v),
}

# 항목 -> (범주, 변형 목록)
PLAN = {
    "PHONE": ("D1", ["no_hyphen", "space_sep", "dot_sep", "fullwidth", "korean_digits", "zero_width", "spaced_digits"]),
    "RRN": ("D1", ["no_hyphen", "space_sep", "fullwidth", "korean_digits", "zero_width"]),
    "FRN": ("D1", ["no_hyphen", "fullwidth", "korean_digits"]),
    "CARD": ("D1", ["no_hyphen", "space_sep", "dot_sep", "fullwidth", "zero_width"]),
    "EMAIL": ("D1", ["at_word", "bracket_at", "upper", "spaced_at", "zero_width"]),
    "ACCOUNT": ("D1", ["no_hyphen", "space_sep", "dot_sep", "fullwidth"]),
    "API_KEY": ("D2", ["split_space", "split_newline", "lower", "zero_width"]),
    "JWT": ("D2", ["jwt_dot_space", "split_newline", "zero_width"]),
    "CONN_STRING": ("D2", ["upper_scheme", "scheme_space", "zero_width"]),
    "CODENAME": ("D3", ["spaced_chars", "zero_width", "lower"]),
    "CLIENT": ("D3", ["spaced_chars", "zero_width"]),
    "CLASSIFIED": ("D3", ["lower", "upper", "spaced_chars", "zero_width"]),
}
CARRIERS = ["다음 내용을 정리해 줘: {V}", "{V} 이 값이 맞는지 확인해 줘", "메모에 적힌 내용입니다. {V} 형식을 점검해 주세요"]
ACCOUNT_CARRIERS = ["입금 계좌는 {B} {V} 입니다", "{B} {V} 로 보내 달라고 했어"]
REPLICATES = 2


def _base_value(item, fake):
    """(원래 값, 은행 이름 또는 None)"""
    if item == "RRN":
        return fake.rrn(), None
    if item == "FRN":
        return fake.frn(), None
    if item == "CARD":
        return fake.card(), None
    if item == "PHONE":
        return fake.phone(), None
    if item == "EMAIL":
        return fake.email(), None
    if item == "ACCOUNT":
        bank, number = fake.account()
        return number, bank
    if item == "API_KEY":
        return fake.r.choice([fake.aws_key, fake.openai_key, fake.github_token])(), None
    if item == "JWT":
        return fake.jwt(), None
    if item == "CONN_STRING":
        return fake.conn_string(), None
    return fake.r.choice(be.COMPANY[item]), None


def _make(item, category, transform, k, seed):
    fake = be.rng_for(f"{seed}:bypass:{item}:{transform}:{k}")
    for _ in range(50):
        original, bank = _base_value(item, fake)
        sent = T[transform](original)
        if transform == "control" or sent != original:
            break
    else:
        raise AssertionError(f"{item}/{transform}: 값이 바뀌는 원래 값을 찾지 못했다")
    carriers = ACCOUNT_CARRIERS if item == "ACCOUNT" else CARRIERS
    carrier = fake.r.choice(carriers).replace("{B}", bank or "")
    prefix, suffix = carrier.split("{V}")
    text = prefix + sent + suffix
    span = {"category": category, "item": item, "start": len(prefix), "end": len(prefix) + len(sent)}
    return {"id": f"bypass-{item.lower()}-{transform}-{k}", "text": text, "kind": "sensitive",
            "expected_action": be.expected_action([span]), "spans": [span], "item": item, "transform": transform,
            "value_original": original, "value_sent": sent}


def build_bypass_set(seed="deskguard-bypass-v1"):
    records = []
    for item, (category, transforms) in PLAN.items():
        for transform in ["control", *transforms]:
            for k in range(1, REPLICATES + 1):
                records.append(_make(item, category, transform, k, seed))
    return records


write_dataset = be.write_dataset


def main(argv=None):
    ap = argparse.ArgumentParser(description="우회 시험 세트를 만든다")
    ap.add_argument("--out", required=True)
    ap.add_argument("--seed", default="deskguard-bypass-v1")
    args = ap.parse_args(argv)
    records = build_bypass_set(args.seed)
    digest = write_dataset(records, args.out)
    print(f"만든 문장 {len(records)}개, 해시 {digest}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
