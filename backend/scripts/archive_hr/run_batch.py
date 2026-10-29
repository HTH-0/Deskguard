# -*- coding: utf-8 -*-
"""
입력 CSV의 문장을 /api/chat 에 순회 호출하고, 결과를 새 CSV와 요약표로 남기는 측정 도구 (설계서 7.4절).
수용 기준: docs/spec.md AC-6.5, 8.2, 9.5, 9.6.

사용법 (서버를 측정 모드로 먼저 띄운다):
    mvn spring-boot:run "-Dspring-boot.run.arguments=--gateway.measurement-mode=true"
    python run_batch.py --input ../../dataset.csv --output result.csv --base-url http://localhost:8080

안전장치:
  - 기본으로 split=design 행만 보낸다. eval은 --split eval 로 명시해야 한다 (6단계 측정 전에는 열지 않는다).
  - disallowed_content, bias_injection 유형과 영어 hard negative는 서버로 보내지 않는다 (제한을 푸는 옵션은 없다).
  - 시작 전에 정상 문장 1건으로 서버가 측정 모드인지 확인하고, 아니면 한 건도 더 보내지 않고 중단한다.
    (--allow-answer-model 을 주면 일반 모드도 실행한다. 이때 통과한 문장은 응답 모델로 간다.)
"""
import argparse
import csv
import json
import math
import sys
import time
import urllib.error
import urllib.request

# 서버로 보내지 않는 유형 (constitution 1장 3항, handoff 6절 7번)
RESTRICTED_TYPES = ("disallowed_content", "bias_injection")

# 서버가 측정 모드인지 확인할 때 보내는 고정 정상 문장
PROBE_PROMPT = "연차 신청은 어떻게 하나요?"

# 집계에서 쓰는 결과 종류. 정책 차단은 단계가 PATTERN 또는 JUDGE인 차단이다.
OUTCOMES = ("passed", "policy_block", "judge_failure", "unknown_block", "error")

RESULT_COLUMNS = ["result_status", "result_decision", "result_stage", "result_failure_kind", "result_pattern_id",
                  "result_model_version", "result_scores", "result_latency_ms", "result_answer", "result_error"]


class ModeCheckError(Exception):
    """서버의 모드를 확인하지 못했거나, 측정 모드가 아니어서 실행을 중단해야 할 때."""


def select_rows(rows, split):
    """보낼 행과 제외 건수(사유별)를 돌려준다."""
    selected = []
    excluded = {"other_split": 0, "restricted_type": 0, "english_hard_negative": 0}
    for row in rows:
        if row.get("split") != split:
            excluded["other_split"] += 1
        elif row.get("type") in RESTRICTED_TYPES:
            excluded["restricted_type"] += 1
        elif row.get("type") == "hard_negative" and row.get("lang") == "en":
            excluded["english_hard_negative"] += 1
        else:
            selected.append(row)
    return selected, excluded


def classify_response(http_status, body, error=""):
    """서버 응답 하나를 결과 열(status, decision, stage, ...)로 바꾼다."""
    result = {"status": "error", "decision": "", "stage": "", "failure_kind": "", "pattern_id": "",
              "model_version": "", "scores": "", "answer": "", "error": ""}
    if http_status is None:
        return {**result, "error": error or "연결 오류"}
    if http_status == 200 and isinstance(body, dict):
        if "decision" in body:  # 측정 모드 응답
            decision = body["decision"]
            if decision not in ("PASS", "BLOCK"):
                return {**result, "error": f"알 수 없는 decision: {decision}"}
            return {**result,
                    "status": "passed" if decision == "PASS" else "blocked",
                    "decision": decision,
                    "stage": body.get("stage") or "",
                    "failure_kind": body.get("failureKind") or "",
                    "pattern_id": body.get("patternId") or "",
                    "model_version": body.get("modelVersion") or "",
                    "scores": json.dumps(body["scores"], ensure_ascii=False) if body.get("scores") else ""}
        if "answer" in body:  # 일반 모드의 통과
            return {**result, "status": "passed", "answer": body["answer"]}
    if http_status == 403:  # 일반 모드의 차단. 이유를 숨기므로 단계는 알 수 없다.
        return {**result, "status": "blocked", "stage": "UNKNOWN"}
    return {**result, "error": json.dumps(body, ensure_ascii=False) if body else f"HTTP {http_status}"}


def outcome_of(status, stage):
    """결과 한 건을 집계 종류로 나눈다. 판정 실패와 단계 미상 차단은 정책 차단과 별도이다 (AC-6.5)."""
    if status == "error":
        return "error"
    if status == "passed":
        return "passed"
    if stage in ("PATTERN", "JUDGE"):
        return "policy_block"
    if stage == "JUDGE_FAILURE":
        return "judge_failure"
    return "unknown_block"


def _new_counts():
    counts = {name: 0 for name in OUTCOMES}
    counts["n"] = 0
    return counts


def _add(counts, outcome):
    counts[outcome] += 1
    counts["n"] += 1


def _rate(counts):
    """정책 차단 건수를 판정을 마친 건(정책 차단 + 통과)으로 나눈다. 나머지는 제외 건수로 함께 적는다."""
    counts = counts or _new_counts()
    hit = counts["policy_block"]
    base = hit + counts["passed"]
    excluded = counts["judge_failure"] + counts["unknown_block"] + counts["error"]
    return {"hit": hit, "base": base, "excluded": excluded, "rate": hit / base if base else None}


def _percentile(sorted_values, percent):
    """가장 가까운 순위 방식의 백분위."""
    index = max(0, math.ceil(percent / 100 * len(sorted_values)) - 1)
    return sorted_values[index]


def summarize(records):
    """결과 행들을 집계한다. group 열에 값이 있는 행은 정상, 공격 집계와 분리해서 groups에만 센다 (AC-8.2)."""
    by_label, by_type, by_lang, groups = {}, {}, {}, {}
    latencies = []
    for r in records:
        outcome = outcome_of(r.get("result_status", ""), r.get("result_stage", ""))
        group = (r.get("group") or "").strip()
        if group:
            _add(groups.setdefault(group, _new_counts()), outcome)
        else:
            label = r.get("label", "")
            _add(by_label.setdefault(label, _new_counts()), outcome)
            _add(by_type.setdefault((label, r.get("type", "")), _new_counts()), outcome)
            _add(by_lang.setdefault((label, r.get("lang", "")), _new_counts()), outcome)
        latency = str(r.get("result_latency_ms") if r.get("result_latency_ms") is not None else "")
        if latency.isdigit():
            latencies.append(int(latency))
    latencies.sort()
    return {
        "total": len(records),
        "by_label": by_label, "by_type": by_type, "by_lang": by_lang, "groups": groups,
        "detection_rate": _rate(by_label.get("attack")),
        "false_positive_rate": _rate(by_label.get("normal")),
        "latency_ms": {"n": len(latencies),
                       "p50": _percentile(latencies, 50) if latencies else None,
                       "p95": _percentile(latencies, 95) if latencies else None},
    }


def _format_rate(name, r):
    percent = "계산 불가" if r["rate"] is None else f"{r['rate'] * 100:.1f}%"
    return f"{name}: {percent} ({r['hit']}/{r['base']}, 제외 {r['excluded']}건)"


def _format_table(title, table):
    lines = [f"[{title}]", "구분 | 건수 | 통과 | 정책 차단 | 판정 실패 | 단계 미상 | 오류"]
    for key in sorted(table, key=str):
        c = table[key]
        name = "/".join(key) if isinstance(key, tuple) else key
        lines.append(f"{name} | {c['n']} | {c['passed']} | {c['policy_block']} | {c['judge_failure']} | "
                     f"{c['unknown_block']} | {c['error']}")
    return lines


def format_summary(summary):
    """집계를 사람이 읽는 글로 바꾼다."""
    lines = [f"요약: 전체 {summary['total']}건",
             _format_rate("탐지율", summary["detection_rate"]),
             _format_rate("오탐율", summary["false_positive_rate"])]
    unknown = sum(c["unknown_block"] for c in summary["by_label"].values())
    if unknown:
        lines.append("주의: 일반 모드의 차단은 정책 차단과 판정 실패를 구분할 수 없어 비율에서 제외했다.")
    lat = summary["latency_ms"]
    if lat["n"]:
        lines.append(f"응답 시간(도구가 잰 값): 중앙값 {lat['p50']}ms, 95백분위 {lat['p95']}ms ({lat['n']}건)")
    lines += _format_table("레이블별", summary["by_label"])
    lines += _format_table("유형별", summary["by_type"])
    lines += _format_table("언어별", summary["by_lang"])
    if summary["groups"]:
        lines += _format_table("별도 집합(group)", summary["groups"])
    return "\n".join(lines)


def call_chat_api(base_url, prompt, timeout=35):
    """/api/chat 에 POST 요청을 보내고 상태 코드, 본문, 오류, 걸린 시간(ms)을 돌려준다."""
    req = urllib.request.Request(
        f"{base_url}/api/chat", data=json.dumps({"prompt": prompt}).encode("utf-8"), method="POST",
        headers={"Content-Type": "application/json; charset=utf-8"})
    start = time.monotonic()
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            code, raw = resp.status, resp.read()
    except urllib.error.HTTPError as e:
        code, raw = e.code, e.read()
    except Exception as e:  # 연결 거부, 타임아웃 등
        return {"http": None, "body": None, "error": str(e), "latency_ms": int((time.monotonic() - start) * 1000)}
    try:
        body = json.loads(raw.decode("utf-8"))
    except Exception:
        body = None
    return {"http": code, "body": body, "error": "", "latency_ms": int((time.monotonic() - start) * 1000)}


def check_mode(base_url, allow_answer_model):
    """서버가 측정 모드인지 정상 문장 1건으로 확인한다. 측정 모드이면 "measurement", 허용된 일반 모드이면 "answer"를 돌려준다."""
    result = call_chat_api(base_url, PROBE_PROMPT)
    cls = classify_response(result["http"], result["body"], result["error"])
    if cls["status"] == "error":
        raise ModeCheckError(f"서버의 모드를 확인하지 못했습니다: {cls['error']}")
    if cls["decision"]:
        return "measurement"
    if allow_answer_model:
        return "answer"
    raise ModeCheckError("서버가 측정 모드가 아닙니다. 응답 모델로 문장이 가지 않도록 한 건도 더 보내지 않고 중단합니다. "
                         "서버를 --gateway.measurement-mode=true 로 다시 시작하세요. "
                         "일반 모드로 실행하려면 --allow-answer-model 을 주세요.")


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--input", default="dataset.csv")
    parser.add_argument("--output", default="batch_result.csv")
    parser.add_argument("--base-url", default="http://localhost:8080")
    parser.add_argument("--delay", type=float, default=1.0, help="요청 사이 대기 시간(초)")
    parser.add_argument("--split", choices=["design", "eval"], default="design",
                        help="보낼 분할. eval은 6단계 측정에서만 명시한다.")
    parser.add_argument("--allow-answer-model", action="store_true",
                        help="서버가 측정 모드가 아니어도 실행한다(통과한 문장은 응답 모델로 간다).")
    args = parser.parse_args(argv)

    with open(args.input, encoding="utf-8-sig") as f:
        reader = csv.DictReader(f)
        input_fields = list(reader.fieldnames or [])
        rows = list(reader)

    selected, excluded = select_rows(rows, args.split)
    print(f"입력 {len(rows)}건 중 {len(selected)}건을 보냅니다 (split={args.split}). 제외: "
          f"다른 분할 {excluded['other_split']}건, 제한 유형 {excluded['restricted_type']}건, "
          f"영어 hard negative {excluded['english_hard_negative']}건")

    try:
        mode = check_mode(args.base_url, args.allow_answer_model)
    except ModeCheckError as e:
        print(str(e), file=sys.stderr)
        return 2
    if mode == "answer":
        print("경고: 서버가 측정 모드가 아닙니다. 통과한 문장은 응답 모델로 전달됩니다.")

    fieldnames = input_fields + [c for c in RESULT_COLUMNS if c not in input_fields]
    records = []
    with open(args.output, "w", newline="", encoding="utf-8-sig") as out_f:
        writer = csv.DictWriter(out_f, fieldnames=fieldnames)
        writer.writeheader()
        for i, row in enumerate(selected, 1):
            result = call_chat_api(args.base_url, row["prompt"])
            cls = classify_response(result["http"], result["body"], result["error"])
            record = dict(row)
            record.update({f"result_{key}": value for key, value in cls.items()})
            record["result_latency_ms"] = result["latency_ms"]
            writer.writerow(record)
            out_f.flush()
            records.append(record)
            print(f"[{i}/{len(selected)}] {cls['status']} {cls['stage']} - {row['prompt'][:40]}")
            time.sleep(args.delay)

    print(f"완료: {args.output} 에 저장됨")
    print(format_summary(summarize(records)))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
