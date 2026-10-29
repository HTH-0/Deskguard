# -*- coding: utf-8 -*-
"""
측정 도구 v2(명세 AC-10.1a, 10.2, 10.3a~d). 측정 모드 서버(--gateway.measurement-mode=true)에 평가 세트를 보내고
정답(넣은 값의 항목과 위치)과 비교해 X(범주별 탐지율), Y(정상 문장 오탐율), T(지연)를 계산한다.
외부 AI와 Jev는 부르지 않으므로 API 키가 필요 없다. 평가 세트는 가짜 값으로만 만든다.

실행: cd backend/scripts && python measure.py --input 평가세트.jsonl --out 결과.json [--frozen-hash 해시]
"""
import argparse
import hashlib
import json
import math
import sys
import time
import urllib.error
import urllib.request

REQUIRED = ("id", "text", "kind", "expected_action", "spans")
SPAN_KEYS = ("category", "item", "start", "end")
CATEGORIES = ("D1", "D2", "D3")


class FrozenError(Exception):
    """동결한 해시와 평가 세트가 다르다(AC-10.2)."""


class ModeError(Exception):
    """서버가 측정 응답을 주지 않았다(측정 모드가 꺼졌거나 같은 컴퓨터의 요청이 아니다)."""


def _overlap(a, b):
    return min(a["end"], b["end"]) > max(a["start"], b["start"])


class MatchResult:
    def __init__(self):
        self.tp = self.fp = self.fn = self.item_wrong = 0
        self.by_category = {}
        self.by_item = {}
        self.events = []  # (종류, 정답 또는 탐지 span): FN, item_wrong, FP

    def _cat(self, category):
        return self.by_category.setdefault(category, {"gold": 0, "tp": 0, "item_wrong": 0, "fn": 0})


def _item(result, g):
    return result.by_item.setdefault(g["item"], {"category": g["category"], "gold": 0, "tp": 0, "item_wrong": 0, "fn": 0})


def match_spans(gold, predicted):
    """AC-10.3a: 같은 항목이고 1글자 이상 겹치면 TP. 범주만 같으면 항목 오류. 정답과 탐지를 하나씩만 짝짓는다."""
    result = MatchResult()
    used = set()
    for g in gold:
        cat = result._cat(g["category"])
        cat["gold"] += 1
        it = _item(result, g)
        it["gold"] += 1
        hit = next((i for i, p in enumerate(predicted)
                    if i not in used and p["item"] == g["item"] and _overlap(g, p)), None)
        if hit is not None:
            used.add(hit)
            result.tp += 1
            cat["tp"] += 1
            it["tp"] += 1
            continue
        wrong = next((i for i, p in enumerate(predicted)
                      if i not in used and p["category"] == g["category"] and _overlap(g, p)), None)
        if wrong is not None:
            used.add(wrong)
            result.item_wrong += 1
            cat["item_wrong"] += 1
            it["item_wrong"] += 1
            result.events.append(("item_wrong", g))
            continue
        result.fn += 1
        cat["fn"] += 1
        it["fn"] += 1
        result.events.append(("FN", g))
    for i, p in enumerate(predicted):
        if i not in used:
            result.events.append(("FP", p))
    result.fp += len(predicted) - len(used)
    return result


def wilson(k, n, z=1.96):
    """윌슨 95% 구간(AC-10.3)."""
    if n == 0:
        return (0.0, 0.0)
    p = k / n
    denom = 1 + z * z / n
    center = (p + z * z / (2 * n)) / denom
    half = z * math.sqrt(p * (1 - p) / n + z * z / (4 * n * n)) / denom
    return (max(0.0, center - half), min(1.0, center + half))


def _percentile(sorted_values, percent):
    """가장 가까운 순위 방식."""
    rank = max(1, math.ceil(percent / 100 * len(sorted_values)))
    return sorted_values[rank - 1]


def _stats(values):
    values = sorted(v for v in values if v is not None)
    if not values:
        return {"n": 0, "median": None, "p95": None}
    mid = len(values) // 2
    median = values[mid] if len(values) % 2 else (values[mid - 1] + values[mid]) / 2
    return {"n": len(values), "median": median, "p95": _percentile(values, 95)}


def evaluate(records, responses, rtts_ms):
    """정답과 응답을 비교해 X, Y, 처리 정확도, T를 돌려준다(AC-10.3a~d)."""
    if not (len(records) == len(responses) == len(rtts_ms)):
        raise ValueError("평가 세트, 응답, 왕복 시간의 개수가 다르다")
    x = {c: {"gold": 0, "tp": 0, "item_wrong": 0, "fn": 0} for c in CATEGORIES}
    x_by_item, misses, action_mismatches = {}, [], []
    extra_fp = 0
    y_fp = y_n = correct = 0
    timings = {"detectMs": [], "maskMs": [], "restoreMs": []}
    for rec, resp in zip(records, responses):
        predicted = resp.get("spans") or []
        if rec["kind"] == "normal":
            y_n += 1
            if resp["action"] != "ALLOW" or predicted:
                y_fp += 1
                for p in predicted or [{"item": None}]:
                    misses.append({"id": rec["id"], "type": "FP_normal", "item": p["item"], "action": resp["action"]})
        else:
            m = match_spans(rec["spans"], predicted)
            extra_fp += m.fp
            for kind, sp in m.events:
                misses.append({"id": rec["id"], "type": kind, "item": sp["item"], "start": sp["start"], "end": sp["end"]})
            for item, v in m.by_item.items():
                agg = x_by_item.setdefault(item, {"category": v["category"], "gold": 0, "tp": 0, "item_wrong": 0, "fn": 0})
                for key in ("gold", "tp", "item_wrong", "fn"):
                    agg[key] += v[key]
            for cat, v in m.by_category.items():
                for key in v:
                    x.setdefault(cat, {"gold": 0, "tp": 0, "item_wrong": 0, "fn": 0})[key] += v[key]
        if resp["action"] == rec["expected_action"]:
            correct += 1
        else:
            action_mismatches.append({"id": rec["id"], "expected": rec["expected_action"], "actual": resp["action"]})
        for name in timings:
            timings[name].append((resp.get("timings") or {}).get(name))
    for cat, v in x.items():
        v["rate"] = v["tp"] / v["gold"] if v["gold"] else None
        v["ci"] = wilson(v["tp"], v["gold"])
    return {
        "n": len(records),
        "x": x,
        "x_by_item": x_by_item,
        "misses": misses,
        "action_mismatches": action_mismatches,
        "x_extra_false_positives": extra_fp,
        "y": {"n": y_n, "false_positives": y_fp, "rate": y_fp / y_n if y_n else None, "ci": wilson(y_fp, y_n)},
        "action_accuracy": {"correct": correct, "n": len(records), "ci": wilson(correct, len(records))},
        "t": {**{k: _stats(v) for k, v in timings.items()}, "rttMs": _stats(rtts_ms)},
    }


def load_dataset(path):
    """JSONL을 읽고 형식을 검사한다(AC-10.1a). 파일의 SHA-256도 돌려준다."""
    with open(path, "rb") as f:
        raw = f.read()
    records = []
    for lineno, line in enumerate(raw.decode("utf-8").splitlines(), 1):
        if not line.strip():
            continue
        rec = json.loads(line)
        missing = [k for k in REQUIRED if k not in rec]
        if missing:
            raise ValueError(f"{lineno}행: 필드가 없다 {missing}")
        if rec["kind"] not in ("sensitive", "normal"):
            raise ValueError(f"{lineno}행: kind는 sensitive 또는 normal이다")
        for s in rec["spans"]:
            if any(k not in s for k in SPAN_KEYS):
                raise ValueError(f"{lineno}행: spans 필드가 부족하다")
        if rec["kind"] == "normal" and rec["spans"]:
            raise ValueError(f"{lineno}행: 정상 문장은 spans가 비어야 한다")
        records.append(rec)
    return records, hashlib.sha256(raw).hexdigest()


def verify_frozen(path, expected_hash):
    """AC-10.2: 측정 전에 동결한 해시와 같은지 확인한다."""
    with open(path, "rb") as f:
        actual = hashlib.sha256(f.read()).hexdigest()
    if actual != expected_hash:
        raise FrozenError(f"해시가 다르다: 기대 {expected_hash[:12]}, 실제 {actual[:12]}")


def check_measurement_response(body):
    if "spans" not in body or "timings" not in body:
        raise ModeError("측정 응답이 아니다. 서버를 --gateway.measurement-mode=true로 같은 컴퓨터에서 실행했는지 확인한다")


def send(base_url, text, timeout=10):
    """측정 모드 서버에 한 문장을 보낸다. (응답 본문, 왕복 ms)를 돌려준다."""
    data = json.dumps({"prompt": text, "confirmed": False}).encode("utf-8")
    req = urllib.request.Request(base_url.rstrip("/") + "/api/chat", data=data,
                                 headers={"Content-Type": "application/json"})
    start = time.perf_counter()
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            body = json.loads(r.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        raise ModeError(f"HTTP {e.code}: 측정 모드 응답이 아니다") from e
    rtt = (time.perf_counter() - start) * 1000
    check_measurement_response(body)
    return body, rtt


def main(argv=None):
    ap = argparse.ArgumentParser(description="측정 모드 서버로 X, Y, T를 잰다")
    ap.add_argument("--input", required=True, help="평가 세트 JSONL")
    ap.add_argument("--out", required=True, help="결과 JSON")
    ap.add_argument("--base-url", default="http://localhost:8080")
    ap.add_argument("--frozen-hash", help="동결한 SHA-256(없으면 해시만 출력하고 측정은 한다)")
    args = ap.parse_args(argv)
    records, digest = load_dataset(args.input)
    if args.frozen_hash:
        verify_frozen(args.input, args.frozen_hash)
    responses, rtts = [], []
    for rec in records:
        body, rtt = send(args.base_url, rec["text"])
        responses.append(body)
        rtts.append(rtt)
    report = evaluate(records, responses, rtts)
    report["dataset_sha256"] = digest
    with open(args.out, "w", encoding="utf-8") as f:
        json.dump(report, f, ensure_ascii=False, indent=2)
    print(f"완료: {len(records)}건, 해시 {digest[:12]}, 결과 {args.out}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
