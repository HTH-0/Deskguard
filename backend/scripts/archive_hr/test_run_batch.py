# -*- coding: utf-8 -*-
"""
run_batch.py 시험 (T12, AC-6.5, 8.2, 9.5, 9.6).
실행: cd backend/scripts && python3 -m unittest -v test_run_batch

순수 함수(행 선택, 응답 분류, 집계)는 입력과 출력만 비교한다. 서버가 필요한 시험은 이 파일 안에서 띄우는 가짜 로컬 서버를 쓰며,
실제 Jev나 Gemini는 부르지 않는다. 가짜 서버가 받은 문장을 모두 기록해서 "무엇이 서버로 갔는지"를 확인한다.
"""
import contextlib
import csv
import io
import json
import os
import tempfile
import threading
import unittest
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

import run_batch

SCORES = {"I1": 0.01, "F3": 0.9}


class RowSelectionTest(unittest.TestCase):
    ROWS = [
        {"prompt": "p1", "label": "normal", "type": "", "split": "design", "lang": "ko"},
        {"prompt": "p2", "label": "attack", "type": "jailbreak", "split": "design", "lang": "ko"},
        {"prompt": "p3", "label": "attack", "type": "disallowed_content", "split": "design", "lang": "ko"},
        {"prompt": "p4", "label": "attack", "type": "bias_injection", "split": "design", "lang": "en"},
        {"prompt": "p5", "label": "normal", "type": "hard_negative", "split": "design", "lang": "en"},
        {"prompt": "p6", "label": "normal", "type": "hard_negative", "split": "design", "lang": "ko"},
        {"prompt": "p7", "label": "normal", "type": "", "split": "eval", "lang": "ko"},
    ]

    def test_AC_9_5_기본은_design_분할만_고르고_제한_유형과_영어_hard_negative를_뺀다(self):
        selected, excluded = run_batch.select_rows(self.ROWS, "design")

        self.assertEqual([r["prompt"] for r in selected], ["p1", "p2", "p6"])
        self.assertEqual(excluded, {"other_split": 1, "restricted_type": 2, "english_hard_negative": 1})

    def test_AC_9_5_eval을_명시하면_eval_분할의_행을_고른다(self):
        selected, excluded = run_batch.select_rows(self.ROWS, "eval")

        self.assertEqual([r["prompt"] for r in selected], ["p7"])
        self.assertEqual(excluded["other_split"], 6)

    def test_AC_9_5_eval에서도_제한_유형은_뺀다(self):
        rows = [{"prompt": "e1", "label": "attack", "type": "disallowed_content", "split": "eval", "lang": "ko"},
                {"prompt": "e2", "label": "attack", "type": "jailbreak", "split": "eval", "lang": "ko"}]

        selected, excluded = run_batch.select_rows(rows, "eval")

        self.assertEqual([r["prompt"] for r in selected], ["e2"])
        self.assertEqual(excluded["restricted_type"], 1)


class ClassifyResponseTest(unittest.TestCase):
    def test_일반_모드의_200_답변은_passed이다(self):
        r = run_batch.classify_response(200, {"answer": "안녕"})

        self.assertEqual((r["status"], r["answer"]), ("passed", "안녕"))

    def test_일반_모드의_403은_blocked이고_단계는_알_수_없다(self):
        r = run_batch.classify_response(403, {"blocked": True, "message": "차단"})

        self.assertEqual((r["status"], r["stage"]), ("blocked", "UNKNOWN"))

    def test_400과_5xx와_연결_오류는_error이다(self):
        r400 = run_batch.classify_response(400, {"error": "INVALID_PROMPT", "message": "비었음"})
        r500 = run_batch.classify_response(500, {"error": "INTERNAL_ERROR", "message": "내부"})
        rnet = run_batch.classify_response(None, None, error="연결 실패")

        self.assertEqual([r400["status"], r500["status"], rnet["status"]], ["error"] * 3)
        self.assertIn("INVALID_PROMPT", r400["error"])
        self.assertIn("연결 실패", rnet["error"])

    def test_AC_9_2_측정_모드의_통과_응답을_읽는다(self):
        body = {"decision": "PASS", "stage": "NONE", "scores": SCORES, "modelVersion": "jev-1.14.0",
                "failureKind": None, "patternId": None}

        r = run_batch.classify_response(200, body)

        self.assertEqual((r["status"], r["decision"], r["stage"]), ("passed", "PASS", "NONE"))
        self.assertEqual(r["model_version"], "jev-1.14.0")
        self.assertEqual(json.loads(r["scores"]), SCORES)
        self.assertEqual((r["failure_kind"], r["pattern_id"]), ("", ""))

    def test_AC_9_3_측정_모드의_판정_차단은_blocked이고_점수를_담는다(self):
        body = {"decision": "BLOCK", "stage": "JUDGE", "scores": SCORES, "modelVersion": "jev-1.14.0",
                "failureKind": None, "patternId": None}

        r = run_batch.classify_response(200, body)

        self.assertEqual((r["status"], r["stage"]), ("blocked", "JUDGE"))
        self.assertEqual(json.loads(r["scores"])["F3"], 0.9)

    def test_AC_9_4_측정_모드의_판정_실패는_실패_종류가_있다(self):
        body = {"decision": "BLOCK", "stage": "JUDGE_FAILURE", "scores": None, "modelVersion": None,
                "failureKind": "TIMEOUT", "patternId": None}

        r = run_batch.classify_response(200, body)

        self.assertEqual((r["status"], r["stage"], r["failure_kind"]), ("blocked", "JUDGE_FAILURE", "TIMEOUT"))
        self.assertEqual(r["scores"], "")

    def test_측정_모드의_패턴_차단은_패턴_ID가_있다(self):
        body = {"decision": "BLOCK", "stage": "PATTERN", "scores": None, "modelVersion": None,
                "failureKind": None, "patternId": "P-01"}

        r = run_batch.classify_response(200, body)

        self.assertEqual((r["stage"], r["pattern_id"]), ("PATTERN", "P-01"))


def rec(label, status, stage="", type_="", lang="ko", group="", latency=None):
    return {"label": label, "type": type_, "lang": lang, "group": group, "result_status": status,
            "result_stage": stage, "result_latency_ms": latency}


class SummarizeTest(unittest.TestCase):
    def test_AC_6_5_판정_실패와_오류는_정책_차단과_별도로_센다(self):
        records = [
            rec("attack", "blocked", "JUDGE"), rec("attack", "blocked", "PATTERN"),
            rec("attack", "passed", "NONE"), rec("attack", "blocked", "JUDGE_FAILURE"),
            rec("attack", "error"), rec("attack", "blocked", "UNKNOWN"),
        ]

        s = run_batch.summarize(records)

        attack = s["by_label"]["attack"]
        self.assertEqual((attack["policy_block"], attack["passed"], attack["judge_failure"],
                          attack["error"], attack["unknown_block"]), (2, 1, 1, 1, 1))

    def test_AC_6_5_탐지율의_분모는_판정을_마친_건이다(self):
        records = [rec("attack", "blocked", "JUDGE"), rec("attack", "blocked", "PATTERN"),
                   rec("attack", "passed", "NONE"), rec("attack", "blocked", "JUDGE_FAILURE")]

        d = run_batch.summarize(records)["detection_rate"]

        self.assertEqual((d["hit"], d["base"], d["excluded"]), (2, 3, 1))
        self.assertAlmostEqual(d["rate"], 2 / 3)

    def test_오탐율은_정상_문장_중_정책_차단_비율이다(self):
        records = [rec("normal", "passed", "NONE")] * 3 + [rec("normal", "blocked", "JUDGE")]

        f = run_batch.summarize(records)["false_positive_rate"]

        self.assertEqual((f["hit"], f["base"]), (1, 4))

    def test_분모가_0이면_비율은_None이다(self):
        s = run_batch.summarize([rec("attack", "blocked", "JUDGE_FAILURE")])

        self.assertIsNone(s["detection_rate"]["rate"])
        self.assertIsNone(s["false_positive_rate"]["rate"])

    def test_AC_8_2_group_열이_있는_행은_정상_문장_집계와_분리해서_보고한다(self):
        records = [rec("normal", "passed", "NONE"),
                   rec("normal", "blocked", "JUDGE", group="out_of_scope"),
                   rec("normal", "passed", "NONE", group="out_of_scope")]

        s = run_batch.summarize(records)

        self.assertEqual(s["groups"]["out_of_scope"]["policy_block"], 1)
        self.assertEqual(s["groups"]["out_of_scope"]["passed"], 1)
        self.assertEqual(s["by_label"]["normal"]["policy_block"], 0)
        self.assertEqual((s["false_positive_rate"]["hit"], s["false_positive_rate"]["base"]), (0, 1))

    def test_유형별과_언어별로_나누어_센다(self):
        records = [rec("attack", "blocked", "JUDGE", type_="jailbreak", lang="ko"),
                   rec("attack", "passed", "NONE", type_="jailbreak", lang="en"),
                   rec("attack", "blocked", "JUDGE", type_="prompt_leaking", lang="ko")]

        s = run_batch.summarize(records)

        self.assertEqual(s["by_type"][("attack", "jailbreak")]["n"], 2)
        self.assertEqual(s["by_lang"][("attack", "ko")]["policy_block"], 2)

    def test_응답_시간은_중앙값과_95백분위를_낸다(self):
        records = [rec("normal", "passed", "NONE", latency=ms) for ms in range(10, 110, 10)]

        lat = run_batch.summarize(records)["latency_ms"]

        self.assertEqual((lat["n"], lat["p50"], lat["p95"]), (10, 50, 100))

    def test_요약_글에_탐지율과_제외_건수와_판정_실패가_나온다(self):
        records = [rec("attack", "blocked", "JUDGE"), rec("attack", "blocked", "PATTERN"),
                   rec("attack", "passed", "NONE"), rec("attack", "blocked", "JUDGE_FAILURE")]

        text = run_batch.format_summary(run_batch.summarize(records))

        self.assertIn("탐지율: 66.7% (2/3", text)
        self.assertIn("제외 1건", text)
        self.assertIn("판정 실패", text)


class FakeServer:
    """가짜 /api/chat 서버. 받은 문장을 모두 기록한다.
    mode="measurement": BLOCKME는 판정 차단, FAILME는 판정 실패, PATTERNME는 패턴 차단, 나머지는 통과.
    mode="normal": BLOCKME는 403, 나머지는 200 답변."""

    def __init__(self, mode):
        self.mode = mode
        self.received = []
        outer = self

        class Handler(BaseHTTPRequestHandler):
            def log_message(self, *args):
                pass

            def do_POST(self):
                length = int(self.headers.get("Content-Length", "0"))
                prompt = json.loads(self.rfile.read(length).decode("utf-8"))["prompt"]
                outer.received.append(prompt)
                status, body = outer.respond(prompt)
                data = json.dumps(body, ensure_ascii=False).encode("utf-8")
                self.send_response(status)
                self.send_header("Content-Type", "application/json; charset=utf-8")
                self.send_header("Content-Length", str(len(data)))
                self.end_headers()
                self.wfile.write(data)

        self.server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
        self.url = f"http://127.0.0.1:{self.server.server_address[1]}"
        threading.Thread(target=self.server.serve_forever, daemon=True).start()

    def respond(self, prompt):
        if self.mode == "normal":
            if "BLOCKME" in prompt:
                return 403, {"blocked": True, "message": "차단"}
            return 200, {"answer": "ok"}
        if "BLOCKME" in prompt:
            return 200, {"decision": "BLOCK", "stage": "JUDGE", "scores": SCORES, "modelVersion": "jev-1.14.0",
                         "failureKind": None, "patternId": None}
        if "FAILME" in prompt:
            return 200, {"decision": "BLOCK", "stage": "JUDGE_FAILURE", "scores": None, "modelVersion": None,
                         "failureKind": "TIMEOUT", "patternId": None}
        if "PATTERNME" in prompt:
            return 200, {"decision": "BLOCK", "stage": "PATTERN", "scores": None, "modelVersion": None,
                         "failureKind": None, "patternId": "P-01"}
        return 200, {"decision": "PASS", "stage": "NONE", "scores": SCORES, "modelVersion": "jev-1.14.0",
                     "failureKind": None, "patternId": None}

    def close(self):
        self.server.shutdown()
        self.server.server_close()


FIELDS = ["prompt", "label", "type", "split", "lang", "source_category"]
DATA_ROWS = [
    ["정상 문장", "normal", "", "design", "ko", ""],
    ["BLOCKME 공격", "attack", "jailbreak", "design", "ko", ""],
    ["FAILME 문장", "attack", "prompt_injection", "design", "ko", ""],
    ["제한 유형 문장", "attack", "disallowed_content", "design", "ko", ""],
    ["eval 문장", "normal", "", "eval", "ko", ""],
]


class MainTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.input = os.path.join(self.tmp.name, "in.csv")
        self.output = os.path.join(self.tmp.name, "out.csv")
        with open(self.input, "w", newline="", encoding="utf-8-sig") as f:
            w = csv.writer(f)
            w.writerow(FIELDS)
            w.writerows(DATA_ROWS)

    def run_main(self, server_url, *extra):
        out = io.StringIO()
        with contextlib.redirect_stdout(out), contextlib.redirect_stderr(out):
            code = run_batch.main(["--input", self.input, "--output", self.output, "--base-url", server_url,
                                   "--delay", "0", *extra])
        return code, out.getvalue()

    def read_output(self):
        self.assertTrue(os.path.exists(self.output), "출력 파일이 만들어지지 않았다")
        with open(self.output, encoding="utf-8-sig") as f:
            return list(csv.DictReader(f))

    def test_AC_9_5_측정_모드_서버에는_제한_유형과_eval을_보내지_않고_모드_확인_문장을_먼저_보낸다(self):
        server = FakeServer("measurement")
        self.addCleanup(server.close)

        code, out = self.run_main(server.url)

        self.assertEqual(code, 0)
        self.assertEqual(server.received, [run_batch.PROBE_PROMPT, "정상 문장", "BLOCKME 공격", "FAILME 문장"])
        self.assertIn("제외", out)

    def test_측정_모드_결과_CSV에_입력_열과_결과_열이_있다(self):
        server = FakeServer("measurement")
        self.addCleanup(server.close)

        self.run_main(server.url)
        rows = self.read_output()

        self.assertEqual(len(rows), 3)
        self.assertEqual([r["result_status"] for r in rows], ["passed", "blocked", "blocked"])
        self.assertEqual([r["result_stage"] for r in rows], ["NONE", "JUDGE", "JUDGE_FAILURE"])
        self.assertEqual(rows[2]["result_failure_kind"], "TIMEOUT")
        self.assertEqual(rows[1]["prompt"], "BLOCKME 공격")
        self.assertTrue(rows[0]["result_latency_ms"].isdigit())

    def test_요약을_출력한다(self):
        server = FakeServer("measurement")
        self.addCleanup(server.close)

        _, out = self.run_main(server.url)

        self.assertIn("탐지율", out)
        self.assertIn("판정 실패", out)

    def test_AC_9_6_측정_모드가_아니면_모드_확인_1건만_보내고_중단한다(self):
        server = FakeServer("normal")
        self.addCleanup(server.close)

        code, out = self.run_main(server.url)

        self.assertEqual(code, 2)
        self.assertEqual(server.received, [run_batch.PROBE_PROMPT])
        self.assertFalse(os.path.exists(self.output))
        self.assertIn("측정 모드", out)

    def test_AC_9_6_명시_옵션을_주면_일반_모드도_실행하고_403을_blocked로_분류한다(self):
        server = FakeServer("normal")
        self.addCleanup(server.close)

        code, _ = self.run_main(server.url, "--allow-answer-model")
        rows = self.read_output()

        self.assertEqual(code, 0)
        self.assertEqual([r["result_status"] for r in rows], ["passed", "blocked", "passed"])
        self.assertEqual(rows[1]["result_stage"], "UNKNOWN")

    def test_서버에_연결할_수_없으면_아무것도_보내지_않고_중단한다(self):
        code, _ = self.run_main("http://127.0.0.1:9")

        self.assertEqual(code, 2)
        self.assertFalse(os.path.exists(self.output))

    def test_AC_9_5_eval은_명시해야만_보낸다(self):
        server = FakeServer("measurement")
        self.addCleanup(server.close)

        code, _ = self.run_main(server.url, "--split", "eval")

        self.assertEqual(code, 0)
        self.assertEqual(server.received, [run_batch.PROBE_PROMPT, "eval 문장"])


if __name__ == "__main__":
    unittest.main()
