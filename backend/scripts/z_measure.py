# -*- coding: utf-8 -*-
"""
Z 측정 도구(명세 AC-10.4a~g, 10.5a~b). 가림 처리가 외부 AI 답의 쓸모를 해치지 않는지 잰다.
  collect   답 A(원문을 응답 모델에 직접)와 답 B(게이트웨이 경유, 복원까지)를 받아 저장한다. 중단 후 이어서 할 수 있다.
  baseline  표본에 대해 A를 한 번 더 받는다(A2, 응답 모델의 답이 매번 다른 정도를 재는 기준선).
  score     답마다 Jev로 채점한다("쓸 수 있음" = 충족 0.5 이상, 혼란 0.5 미만).
  report    복원 성공률, 값 보존율, 쓸모 유지율을 윌슨 구간과 함께 계산한다.
  sample    사람 판정용 표본 시트(순서 섞음)와 대응표를 만든다.
  agree     사람 판정과 Jev 판정의 일치율을 계산한다.
입력은 평가 세트의 가짜 데이터뿐이다. 키는 환경변수 GEMINI_API_KEY, TYPESAFE_API_KEY에서만 읽고 어디에도 쓰지 않는다.
게이트웨이는 일반 모드(측정 모드 꺼짐)로 GEMINI_API_KEY와 함께 실행해 둔다.
"""
import argparse
import csv
import json
import os
import random
import re
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path

import measure

PSEUDONYM = re.compile(r"[\[［]\s*([가-힣A-Za-z]+)\s*(\d+)\s*[\]］]")   # Masker의 가명 모양과 같다
ANSWER_LIMIT = 3000
THRESHOLD = 0.5            # 측정 전에 고정한 기준(AC-10.4d)
QUESTIONS = {
    "fulfil": {
        "type": "noul",
        "instructions": "[요청]에 대한 [답변]이 요청한 작업을 실제로 수행했는지 판단한다.",
        "criteria": {
            "true": "답변이 요청한 작업(요약, 번역, 표 정리, 분석, 초안 작성, 코드 검토 등)을 구체적인 내용으로 수행했다.",
            "false": "답변이 작업을 수행하지 않았거나 거절했거나, 내용이 요청과 무관하다.",
        },
    },
    "confusion": {
        "type": "noul",
        "instructions": "[답변]에 가명이나 자리 표시자가 남아 있거나 값이 앞뒤가 맞지 않아 내용을 그대로 쓸 수 없는지 판단한다.",
        "criteria": {
            "true": "답변에 [전화1] 같은 대괄호 가명이나 자리 표시자가 그대로 남아 있거나, 값이 앞뒤가 맞지 않아 그대로 쓸 수 없다.",
            "false": "답변의 값과 표현이 자연스럽고 그대로 쓸 수 있다.",
        },
    },
}


class FatalError(Exception):
    """인증 오류, 요청 형식 오류처럼 다시 해도 소용없는 실패."""


class RateLimitError(Exception):
    """속도 제한이 재시도 후에도 계속된다."""


class TransientError(Exception):
    """서버 오류가 재시도 후에도 계속된다."""


# ---------- 순수 계산 ----------

def pseudonym_left(text):
    return bool(PSEUDONYM.search(text or ""))


def value_preservation(record, answer_a, answer_b):
    """A의 답에 나온 정답 값 중 B의 답에도 나온 개수. A에 정답 값이 없으면 None."""
    values = [record["text"][s["start"]:s["end"]] for s in record["spans"]]
    present = [v for v in values if v in answer_a]
    if not present:
        return None
    return (sum(1 for v in present if v in answer_b), len(present))


def usable(scores):
    return scores["fulfil"] >= THRESHOLD and scores["confusion"] < THRESHOLD


def build_state(request, answer):
    cut = len(answer) > ANSWER_LIMIT
    return f"[요청]\n{request}\n[답변]\n{answer[:ANSWER_LIMIT]}", cut


def select_targets(records):
    return [r for r in records if r["expected_action"] == "MASK"]


# ---------- 외부 서비스 클라이언트 ----------

def _post(url, body, headers, timeout=120):
    data = json.dumps(body, ensure_ascii=False).encode("utf-8")
    req = urllib.request.Request(url, data=data, headers={"Content-Type": "application/json; charset=utf-8", **headers})
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            return r.status, json.loads(r.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        try:
            payload = json.loads(e.read().decode("utf-8"))
        except (ValueError, UnicodeDecodeError):
            payload = {}
        return e.code, payload
    except (urllib.error.URLError, TimeoutError, OSError) as e:
        raise TransientError("연결 실패 또는 시간 초과") from e


class _Client:
    RETRY_STATUS = (429, 503)
    BACKOFF = (15, 45, 120)

    def __init__(self, sleep=time.sleep):
        self.sleep = sleep

    def _request(self, url, body, headers, ok_extra=()):
        """429, 503은 대기 후 최대 3번 다시 한다. 4xx(429 제외)는 즉시 FatalError, 5xx는 TransientError이다."""
        for attempt in range(len(self.BACKOFF) + 1):
            status, payload = _post(url, body, headers)
            if status == 200 or status in ok_extra:
                return status, payload
            if status in self.RETRY_STATUS:
                if attempt < len(self.BACKOFF):
                    self.sleep(self.BACKOFF[attempt])
                    continue
                raise RateLimitError(f"HTTP {status}")
            if 400 <= status < 500:
                raise FatalError(f"HTTP {status}")
            raise TransientError(f"HTTP {status}")


class Pacer:
    """응답 모델을 부르는 호출 사이에 최소 간격을 둔다(분당 요청 한도 RPM 대비).
    간격은 직전 호출을 시작한 때부터 잰다. 답 A(직접)와 답 B(게이트웨이 경유)가 같은 한도를 쓰므로 한 조절기를 함께 쓴다."""

    def __init__(self, min_interval, clock=time.monotonic, sleep=time.sleep):
        self.min_interval, self.clock, self.sleep = min_interval, clock, sleep
        self._last = None

    def wait(self):
        if self._last is not None:
            remaining = self.min_interval - (self.clock() - self._last)
            if remaining > 0:
                self.sleep(remaining)
        self._last = self.clock()


class GeminiClient(_Client):
    def __init__(self, base_url, model, api_key, sleep=time.sleep):
        super().__init__(sleep)
        self.base_url, self.model, self._key = base_url.rstrip("/"), model, api_key

    def answer(self, text):
        _, payload = self._request(f"{self.base_url}/{self.model}:generateContent",
                                   {"contents": [{"parts": [{"text": text}]}]}, {"x-goog-api-key": self._key})
        try:
            return payload["candidates"][0]["content"]["parts"][0]["text"]
        except (KeyError, IndexError, TypeError) as e:
            raise TransientError("응답 형식 오류") from e


class GatewayClient(_Client):
    """게이트웨이(일반 모드). 차단(403)은 오류가 아니라 처리 결과로 돌려준다."""

    def __init__(self, base_url, sleep=time.sleep):
        super().__init__(sleep)
        self.base_url = base_url.rstrip("/")

    def chat(self, text):
        _, payload = self._request(self.base_url + "/api/chat", {"prompt": text, "confirmed": False}, {}, ok_extra=(403,))
        return payload


class JevClient(_Client):
    def __init__(self, base_url, model, api_key, sleep=time.sleep):
        super().__init__(sleep)
        self.base_url, self.model, self._key = base_url.rstrip("/"), model, api_key

    def score(self, state):
        _, payload = self._request(self.base_url + "/v1/systemone",
                                   {"model": self.model, "state": state, "questions": QUESTIONS},
                                   {"Authorization": "Bearer " + self._key})
        try:
            answers = payload["answers"]
            scores = {q: float(answers[q]["noul"]) for q in QUESTIONS}
        except (KeyError, TypeError, ValueError) as e:
            raise TransientError("Jev 응답 형식 오류") from e
        if any(not 0.0 <= v <= 1.0 for v in scores.values()):
            raise TransientError("Jev 확률 범위 오류")
        return {**scores, "model": payload.get("model") or self.model}


# ---------- 단계 ----------

def _read_jsonl(path):
    p = Path(path)
    if not p.exists():
        return []
    return [json.loads(l) for l in p.read_text(encoding="utf-8").splitlines() if l.strip()]


def _append(path, row):
    with open(path, "a", encoding="utf-8") as f:
        f.write(json.dumps(row, ensure_ascii=False, separators=(",", ":")) + "\n")


def _stop_reason(exc):
    return {RateLimitError: "rate_limit", TransientError: "server_error", FatalError: "fatal"}[type(exc)]


def collect(records, out_path, gemini, gateway, pacer=None):
    done_ids = {r["id"] for r in _read_jsonl(out_path)}
    done = skipped = 0
    for rec in records:
        if rec["id"] in done_ids:
            skipped += 1
            continue
        try:
            if pacer:
                pacer.wait()
            answer_a = gemini.answer(rec["text"])
            if pacer:
                pacer.wait()
            b = gateway.chat(rec["text"])
        except (FatalError, RateLimitError, TransientError) as e:
            return {"done": done, "skipped": skipped, "stopped": _stop_reason(e)}
        _append(out_path, {"id": rec["id"], "request": rec["text"], "answer_a": answer_a,
                           "answer_b": b.get("answer"), "sent_prompt": b.get("sentPrompt"), "action_b": b.get("action")})
        done += 1
    return {"done": done, "skipped": skipped, "stopped": None}


def collect_baseline(records, a2_path, gemini, pacer=None):
    done_ids = {r["id"] for r in _read_jsonl(a2_path)}
    done = 0
    for rec in records:
        if rec["id"] in done_ids:
            continue
        try:
            if pacer:
                pacer.wait()
            answer = gemini.answer(rec["text"])
        except (FatalError, RateLimitError, TransientError) as e:
            return {"done": done, "stopped": _stop_reason(e)}
        _append(a2_path, {"id": rec["id"], "answer_a2": answer})
        done += 1
    return {"done": done, "stopped": None}


def _score_answer(jev, request, answer):
    state, cut = build_state(request, answer or "")
    s = jev.score(state)
    return {"fulfil": s["fulfil"], "confusion": s["confusion"], "usable": usable(s), "truncated": cut, "model": s["model"]}


def score(collected_path, scores_path, jev, a2_path=None):
    scored_ids = {r["id"] for r in _read_jsonl(scores_path)}
    a2_by_id = {r["id"]: r["answer_a2"] for r in _read_jsonl(a2_path)} if a2_path else {}
    scored = 0
    for row in _read_jsonl(collected_path):
        if row["id"] in scored_ids or row.get("answer_b") is None:
            continue
        try:
            out = {"id": row["id"], "a": _score_answer(jev, row["request"], row["answer_a"]),
                   "b": _score_answer(jev, row["request"], row["answer_b"])}
            if row["id"] in a2_by_id:
                out["a2"] = _score_answer(jev, row["request"], a2_by_id[row["id"]])
        except (FatalError, RateLimitError, TransientError) as e:
            return {"scored": scored, "stopped": _stop_reason(e)}
        _append(scores_path, out)
        scored += 1
    return {"scored": scored, "stopped": None}


def _rate(k, n):
    return {"k": k, "n": n, "rate": (k / n if n else None), "ci": measure.wilson(k, n)}


def report(records, collected_path, scores_path):
    by_id = {r["id"]: r for r in records}
    rows = [r for r in _read_jsonl(collected_path) if r.get("answer_b") is not None and r["id"] in by_id]
    scores = {r["id"]: r for r in _read_jsonl(scores_path)}
    out = {"n": len(rows)}
    ok = sum(1 for r in rows if not pseudonym_left(r["answer_b"]))
    a_hits = sum(1 for r in rows if pseudonym_left(r["answer_a"]))
    out["restoration"] = {"success": ok, "n": len(rows), "rate": ok / len(rows) if rows else None,
                          "ci": measure.wilson(ok, len(rows)), "a_pseudonym_like": a_hits}
    kept = present = 0
    for r in rows:
        vp = value_preservation(by_id[r["id"]], r["answer_a"], r["answer_b"])
        if vp:
            kept += vp[0]
            present += vp[1]
    out["value_preservation"] = {"kept": kept, "present_in_a": present, "rate": kept / present if present else None,
                                 "ci": measure.wilson(kept, present)}
    sc = [(r, scores[r["id"]]) for r in rows if r["id"] in scores]
    a_ok = [s for _, s in sc if s["a"]["usable"]]
    b_ok = [s for s in a_ok if s["b"]["usable"]]
    out["retention"] = {"scored": len(sc), "a_usable": len(a_ok), "a_unusable": len(sc) - len(a_ok),
                        "b_usable_given_a": len(b_ok), "rate": len(b_ok) / len(a_ok) if a_ok else None,
                        "ci": measure.wilson(len(b_ok), len(a_ok))}
    by_item = {}
    for r, s in sc:
        for item in {sp["item"] for sp in by_id[r["id"]]["spans"]}:
            d = by_item.setdefault(item, {"n": 0, "a_usable": 0, "b_usable_given_a": 0})
            d["n"] += 1
            if s["a"]["usable"]:
                d["a_usable"] += 1
                d["b_usable_given_a"] += 1 if s["b"]["usable"] else 0
    out["by_item"] = by_item
    base = [s for _, s in sc if "a2" in s]
    out["baseline"] = {"n": len(base), "agree": sum(1 for s in base if s["a"]["usable"] == s["a2"]["usable"])}
    out["truncated"] = sum(1 for _, s in sc for k in ("a", "b") if s[k]["truncated"])
    return out


def make_human_sample(records, collected_path, sheet_path, key_path, count=45, seed="deskguard-z-v1"):
    by_id = {r["id"]: r for r in records}
    rows = [r for r in _read_jsonl(collected_path) if r.get("answer_b") is not None and r["id"] in by_id]
    rng = random.Random(seed)
    groups = {}
    for r in sorted(rows, key=lambda r: r["id"]):
        groups.setdefault(by_id[r["id"]]["spans"][0]["item"], []).append(r)
    for g in groups.values():
        rng.shuffle(g)
    picked = []
    order = sorted(groups)
    while len(picked) < count and any(groups.values()):
        for item in order:
            if groups[item] and len(picked) < count:
                picked.append(groups[item].pop())
    rng.shuffle(picked)
    key = {}
    with open(sheet_path, "w", encoding="utf-8", newline="") as f:
        w = csv.DictWriter(f, fieldnames=["id", "request", "answer_1", "answer_2", "usable_1", "usable_2"])
        w.writeheader()
        for r in picked:
            b_slot = rng.choice(["1", "2"])
            answers = {b_slot: r["answer_b"], ("2" if b_slot == "1" else "1"): r["answer_a"]}
            key[r["id"]] = b_slot
            w.writerow({"id": r["id"], "request": r["request"], "answer_1": answers["1"], "answer_2": answers["2"],
                        "usable_1": "", "usable_2": ""})
    Path(key_path).write_text(json.dumps(key, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
    return len(picked)


def agreement(human_rows, key, scores):
    allc = {"agree": 0, "n": 0}
    bonly = {"agree": 0, "n": 0}
    for h in human_rows:
        slot_b = key[h["id"]]
        for slot in ("1", "2"):
            verdict = h[f"usable_{slot}"].strip()
            if verdict not in ("예", "아니오"):
                continue
            which = "b" if slot == slot_b else "a"
            same = (verdict == "예") == scores[h["id"]][which]["usable"]
            allc["n"] += 1
            allc["agree"] += 1 if same else 0
            if which == "b":
                bonly["n"] += 1
                bonly["agree"] += 1 if same else 0
    return {"all": allc, "b_only": bonly}


# ---------- 명령행 ----------

def _env(name):
    value = os.environ.get(name)
    if not value:
        sys.exit(f"환경변수 {name}가 없다. 키는 파일이나 명령행에 쓰지 않고 환경변수로만 설정한다.")
    return value


def main(argv=None):
    ap = argparse.ArgumentParser(description="Z 측정 도구")
    ap.add_argument("command", choices=["collect", "baseline", "score", "report", "sample", "agree"])
    ap.add_argument("--set", default="../../docs/eval/set/eval_v1.jsonl")
    ap.add_argument("--dir", default="../../docs/eval/results/z", help="중간 결과와 보고서 폴더")
    ap.add_argument("--gateway", default="http://localhost:8080")
    ap.add_argument("--gemini-base", default="https://generativelanguage.googleapis.com/v1beta/models")
    ap.add_argument("--gemini-model", default="gemini-3.5-flash-lite",
                    help="게이트웨이의 gemini.model과 반드시 같아야 한다(A와 B가 같은 응답 모델이어야 비교가 성립한다)")
    ap.add_argument("--min-interval", type=float, default=5.0,
                    help="응답 모델 호출 사이 최소 간격(초). 분당 15회 한도라면 4초 이상, 분당 5회라면 12초 이상")
    ap.add_argument("--jev-base", default="https://api.typesafe.ai")
    ap.add_argument("--jev-model", default="jev-1.14.0")
    ap.add_argument("--limit", type=int, default=0, help="앞에서 몇 문장만(시험 실행용, 0이면 전체)")
    ap.add_argument("--count", type=int, default=45, help="표본 개수(sample, baseline)")
    ap.add_argument("--human", help="agree: 사람이 채운 시트 CSV")
    args = ap.parse_args(argv)

    d = Path(args.dir)
    d.mkdir(parents=True, exist_ok=True)
    records, _ = measure.load_dataset(args.set)
    targets = select_targets(records)
    if args.limit:
        targets = targets[:args.limit]
    collected, scores, a2 = d / "collected.jsonl", d / "scores.jsonl", d / "a2.jsonl"

    if args.command == "collect":
        res = collect(targets, collected, GeminiClient(args.gemini_base, args.gemini_model, _env("GEMINI_API_KEY")),
                      GatewayClient(args.gateway), pacer=Pacer(args.min_interval))
        print(res)
        return 0 if res["stopped"] is None else 2
    if args.command == "baseline":
        picked = [r for r in targets if r["id"] in {x["id"] for x in _pick_ids(targets, args.count)}]
        res = collect_baseline(picked, a2, GeminiClient(args.gemini_base, args.gemini_model, _env("GEMINI_API_KEY")),
                               pacer=Pacer(args.min_interval))
        print(res)
        return 0 if res["stopped"] is None else 2
    if args.command == "score":
        res = score(collected, scores, JevClient(args.jev_base, args.jev_model, _env("TYPESAFE_API_KEY")),
                    a2 if a2.exists() else None)
        print(res)
        return 0 if res["stopped"] is None else 2
    if args.command == "report":
        rep = report(targets, collected, scores)
        (d / "report.json").write_text(json.dumps(rep, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        print(json.dumps(rep, ensure_ascii=False, indent=2))
        return 0
    if args.command == "sample":
        n = make_human_sample(targets, collected, d / "human_sheet.csv", d / "human_key.json", count=args.count)
        print(f"사람 판정 시트 {n}쌍: {d / 'human_sheet.csv'} (대응표는 human_key.json, 판정이 끝나기 전에 열지 않는다)")
        return 0
    if args.command == "agree":
        if not args.human:
            sys.exit("--human 에 사람이 채운 시트 CSV 경로를 준다")
        with open(args.human, encoding="utf-8", newline="") as f:
            human = list(csv.DictReader(f))
        key = json.loads((d / "human_key.json").read_text(encoding="utf-8"))
        sc = {r["id"]: r for r in _read_jsonl(scores)}
        res = agreement(human, key, sc)
        print(json.dumps(res, ensure_ascii=False))
        return 0
    return 1


def _pick_ids(targets, count):
    """항목별로 고르게 뽑은 기준선 표본(seed 고정)."""
    rng = random.Random("deskguard-z-baseline-v1")
    groups = {}
    for r in targets:
        groups.setdefault(r["spans"][0]["item"], []).append(r)
    for g in groups.values():
        rng.shuffle(g)
    picked = []
    while len(picked) < count and any(groups.values()):
        for item in sorted(groups):
            if groups[item] and len(picked) < count:
                picked.append(groups[item].pop())
    return picked


if __name__ == "__main__":
    sys.exit(main())
