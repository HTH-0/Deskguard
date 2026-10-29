# -*- coding: utf-8 -*-
"""
도메인 eval 후보 정리 도구 (6-3, docs/design.md 10.4절 3~5번, AC-10.5).

생성기 원본(docs/eval/raw/*.csv)을 합치고, 무작위 ID를 붙이고, 내용과 무관한 일반 기준으로 거른 뒤,
의도 라벨과 생성기 정보를 지운 블라인드 라벨링 시트를 만든다.

사용법 (저장소 루트에서):
    python backend/scripts/domain_candidates.py

출력:
    docs/eval/domain_candidates.csv     의도 라벨 포함. 라벨링이 끝나기 전에는 사용자가 열지 않는다
    docs/eval/domain_filter_report.md   거른 수와 사유(건수만)
    docs/eval/domain_labeling_sheet.csv 블라인드 라벨링 시트

자동 필터(문장 내용을 보고 정한 기준이 아니라 일반 기준이다):
    길이        코드 포인트 10자 미만 또는 120자 초과 (생성 지시문의 길이 조건)
    한글 없음    한글 음절이 하나도 없음
    제한 유형 표현  폭력, 무기, 마약, 성적 내용 같은 제한 유형 단어 (생성 지시문에서 금지한 내용)
    중복        공백, 문장부호, 대소문자를 지운 문장이 같음
    비슷한 문장  글자 3개 단위(3-gram) 자카드 유사도가 0.7 이상
같은 문장끼리는 ID 순서(무작위)로 앞선 것을 남긴다.
"""
import csv
import os
import random
import re
import sys

GENERATORS = ("gpt", "claude", "gemini")
MIN_LEN, MAX_LEN = 10, 120
ID_SEED, SHEET_SEED = 20261029, 1029
NEAR_THRESHOLD = 0.7
RESTRICTED_WORDS = ("폭탄", "폭발물", "총기", "살인", "살해", "자살", "마약", "필로폰", "대마", "성관계", "음란", "야동",
                    "테러", "납치", "흉기")
SHEET_COLUMNS = ["id", "text", "label", "group", "quality", "reject_reason", "memo"]
CANDIDATE_COLUMNS = ["id", "generator", "intended_label", "intended_group", "text"]


def read_raw(path, generator):
    """생성기 원본 CSV를 읽는다. 이어 붙이며 다시 나온 머리글 줄은 건너뛴다."""
    rows = []
    with open(path, encoding="utf-8-sig", newline="") as f:
        for rec in csv.reader(f):
            if len(rec) < 3 or rec[0].strip() == "intended_label":
                continue
            rows.append({"generator": generator, "intended_label": rec[0].strip(),
                         "intended_group": rec[1].strip(), "text": rec[2].strip()})
    return rows


def assign_ids(rows, seed):
    """고정 seed로 섞은 뒤 D001부터 ID를 붙인다. ID 순서로는 생성기나 의도를 추측할 수 없다."""
    shuffled = [dict(r) for r in rows]
    random.Random(seed).shuffle(shuffled)
    for i, r in enumerate(shuffled, 1):
        r["id"] = f"D{i:03d}"
    return shuffled


def normalize(text):
    """공백과 문장부호를 지우고 소문자로 바꾼다."""
    return re.sub(r"[\W_]+", "", text).lower()


def _trigrams(text):
    t = normalize(text)
    return {t[i:i + 3] for i in range(len(t) - 2)} if len(t) >= 3 else {t}


def trigram_jaccard(a, b):
    sa, sb = _trigrams(a), _trigrams(b)
    union = sa | sb
    return len(sa & sb) / len(union) if union else 1.0


def _reject_reason(text):
    length = len(text)  # 파이썬 문자열 길이는 코드 포인트 수이다
    if length < MIN_LEN or length > MAX_LEN:
        return "길이"
    if not re.search(r"[가-힣]", text):
        return "한글 없음"
    if any(w in text for w in RESTRICTED_WORDS):
        return "제한 유형 표현"
    return None


def filter_candidates(rows, near_threshold=NEAR_THRESHOLD):
    """(남긴 행 목록, (거른 행, 사유) 목록)을 돌려준다. 입력 순서(ID 순서)로 앞선 문장을 남긴다."""
    kept, removed, seen = [], [], set()
    for r in rows:
        reason = _reject_reason(r["text"])
        if reason is None:
            key = normalize(r["text"])
            if key in seen:
                reason = "중복"
            elif any(trigram_jaccard(r["text"], k["text"]) >= near_threshold for k in kept):
                reason = "비슷한 문장"
            else:
                seen.add(key)
        if reason:
            removed.append((r, reason))
        else:
            kept.append(r)
    return kept, removed


def make_sheet(rows, seed):
    """블라인드 라벨링 시트. 의도 라벨과 생성기를 싣지 않고, 고정 seed로 다시 섞는다."""
    sheet = [{c: "" for c in SHEET_COLUMNS} | {"id": r["id"], "text": r["text"]} for r in rows]
    random.Random(seed).shuffle(sheet)
    return sheet


def _write_csv(path, columns, rows):
    with open(path, "w", encoding="utf-8-sig", newline="") as f:
        w = csv.DictWriter(f, fieldnames=columns, extrasaction="ignore")
        w.writeheader()
        w.writerows(rows)


def _report(all_rows, kept, removed):
    lines = ["# 도메인 후보 자동 필터 보고 (6-3)", "",
             "도구: `backend/scripts/domain_candidates.py`. 기준은 도구 머리말에 있다. 건수만 적고 문장은 적지 않는다.", "",
             "| 생성기 | 생성 | 남김 | 거름 |", "|---|---|---|---|"]
    for g in GENERATORS:
        n = sum(1 for r in all_rows if r["generator"] == g)
        k = sum(1 for r in kept if r["generator"] == g)
        lines.append(f"| {g} | {n} | {k} | {n - k} |")
    lines.append(f"| 합계 | {len(all_rows)} | {len(kept)} | {len(removed)} |")
    lines += ["", "| 거른 사유 | 건수 |", "|---|---|"]
    for reason in ("길이", "한글 없음", "제한 유형 표현", "중복", "비슷한 문장"):
        lines.append(f"| {reason} | {sum(1 for _, r in removed if r == reason)} |")
    return "\n".join(lines) + "\n"


def main(root="."):
    raw_dir = os.path.join(root, "docs", "eval", "raw")
    all_rows = []
    for g in GENERATORS:
        all_rows += read_raw(os.path.join(raw_dir, f"{g}.csv"), g)
    rows = assign_ids(all_rows, ID_SEED)
    kept, removed = filter_candidates(rows)

    out = os.path.join(root, "docs", "eval")
    _write_csv(os.path.join(out, "domain_candidates.csv"), CANDIDATE_COLUMNS + ["filter"],
               [r | {"filter": "kept"} for r in kept] + [r | {"filter": reason} for r, reason in removed])
    _write_csv(os.path.join(out, "domain_labeling_sheet.csv"), SHEET_COLUMNS, make_sheet(kept, SHEET_SEED))
    report = _report(all_rows, kept, removed)
    with open(os.path.join(out, "domain_filter_report.md"), "w", encoding="utf-8") as f:
        f.write(report)
    print(report)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1] if len(sys.argv) > 1 else "."))
