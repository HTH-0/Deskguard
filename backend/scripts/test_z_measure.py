# -*- coding: utf-8 -*-
"""Z 측정 도구 시험(명세 AC-10.4a~g, 10.5a). 가짜 Gemini, 게이트웨이, Jev 서버로 키 없이 통과한다(NFR-2)."""
import csv
import json
import os
import sys
import tempfile
import threading
import unittest
from http.server import BaseHTTPRequestHandler, HTTPServer
from pathlib import Path

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import z_measure as z  # noqa: E402

SECRET = "TEST-SECRET-KEY-123"


class FakeServer:
    """세 서비스를 한 서버에 흉내 낸다. calls에 (경로, 본문)을 쌓고, 설정한 횟수째 Gemini 호출에서 429를 낸다."""

    def __init__(self):
        self.calls = []
        self.rate_limit_on = set()      # n번째 Gemini 호출에서 429
        self.gateway_leaves_pseudonym = set()   # 이 요청 문장이면 가명이 남은 답을 준다
        outer = self

        class Handler(BaseHTTPRequestHandler):
            def log_message(self, *a):
                pass

            def _send(self, status, obj):
                data = json.dumps(obj, ensure_ascii=False).encode("utf-8")
                self.send_response(status)
                self.send_header("Content-Type", "application/json; charset=utf-8")
                self.send_header("Content-Length", str(len(data)))
                self.end_headers()
                self.wfile.write(data)

            def do_POST(self):
                body = json.loads(self.rfile.read(int(self.headers["Content-Length"])).decode("utf-8"))
                outer.calls.append((self.path, body, dict(self.headers)))
                if self.path.endswith(":generateContent"):
                    n = sum(1 for c in outer.calls if c[0].endswith(":generateContent"))
                    if n in outer.rate_limit_on:
                        return self._send(429, {"error": "quota"})
                    text = body["contents"][0]["parts"][0]["text"]
                    return self._send(200, {"candidates": [{"content": {"parts": [{"text": "답: " + text}]}}]})
                if self.path == "/api/chat":
                    prompt = body["prompt"]
                    masked = prompt.replace("010-1111-2222", "[전화1]")
                    answer = "답: " + (masked if prompt in outer.gateway_leaves_pseudonym else prompt)
                    return self._send(200, {"action": "MASK", "answer": answer, "sentPrompt": masked,
                                            "findings": [{"category": "D1", "item": "PHONE", "count": 1}]})
                if self.path == "/v1/systemone":
                    state = body["state"]
                    confused = 0.9 if "[전화" in state else 0.1
                    return self._send(200, {"model": body["model"], "answers": {
                        "fulfil": {"noul": 0.8}, "confusion": {"noul": confused}}})
                self._send(404, {})

        self.httpd = HTTPServer(("127.0.0.1", 0), Handler)
        self.url = f"http://127.0.0.1:{self.httpd.server_port}"
        threading.Thread(target=self.httpd.serve_forever, daemon=True).start()

    def close(self):
        self.httpd.shutdown()
        self.httpd.server_close()


def record(i, text=None, item="PHONE"):
    text = text or f"문의{i} 010-1111-2222 확인"
    start = text.index("010-1111-2222") if "010-1111-2222" in text else 0
    return {"id": f"r{i}", "text": text, "kind": "sensitive", "expected_action": "MASK",
            "spans": [{"category": "D1", "item": item, "start": start, "end": start + 13}]}


class PureTest(unittest.TestCase):
    def test_가명이_남았는지(self):
        self.assertTrue(z.pseudonym_left("연락처는 [전화1] 입니다"))
        self.assertTrue(z.pseudonym_left("[ 고객사 2 ]"))
        self.assertFalse(z.pseudonym_left("배열 a[0] 과 b[i] 는 가명이 아니다"))
        self.assertFalse(z.pseudonym_left("010-1111-2222"))

    def test_값_보존율(self):
        rec = record(1)
        self.assertEqual(z.value_preservation(rec, "A에 010-1111-2222 있음", "B에도 010-1111-2222"), (1, 1))
        self.assertEqual(z.value_preservation(rec, "A에 010-1111-2222 있음", "B에는 [전화1]"), (0, 1))
        self.assertIsNone(z.value_preservation(rec, "A에는 값이 없다", "B"))

    def test_쓸_수_있음_판정은_충족_0_5_이상이고_혼란_0_5_미만(self):
        self.assertTrue(z.usable({"fulfil": 0.5, "confusion": 0.49}))
        self.assertFalse(z.usable({"fulfil": 0.49, "confusion": 0.1}))
        self.assertFalse(z.usable({"fulfil": 0.9, "confusion": 0.5}))

    def test_state는_3000자에서_자르고_표시한다(self):
        state, cut = z.build_state("요청", "가" * 3500)
        self.assertTrue(cut)
        self.assertIn("[요청]\n요청", state)
        self.assertLessEqual(len(state.split("[답변]\n")[1]), 3000)
        self.assertFalse(z.build_state("요청", "짧음")[1])

    def test_대상은_MASK_문장_전체(self):
        recs = [record(1), dict(record(2), expected_action="BLOCK"), dict(record(3), expected_action="ALLOW", kind="normal")]
        self.assertEqual([r["id"] for r in z.select_targets(recs)], ["r1"])


class PacerTest(unittest.TestCase):
    def make(self, interval=5.0):
        self.now, self.slept = 100.0, []

        def sleep(s):
            self.slept.append(s)
            self.now += s
        return z.Pacer(interval, clock=lambda: self.now, sleep=sleep)

    def test_첫_호출은_기다리지_않는다(self):
        self.make().wait()
        self.assertEqual(self.slept, [])

    def test_직전_호출_시작_후_간격이_안_지났으면_남은_시간만_기다린다(self):
        p = self.make(5.0)
        p.wait()
        self.now += 2.0
        p.wait()
        self.assertEqual(self.slept, [3.0])

    def test_간격이_지났으면_기다리지_않는다(self):
        p = self.make(5.0)
        p.wait()
        self.now += 7.0
        p.wait()
        self.assertEqual(self.slept, [])

    def test_간격은_호출이_끝난_때가_아니라_시작한_때부터_잰다(self):
        p = self.make(5.0)
        p.wait()
        self.now += 16.0  # 호출 하나가 16초 걸렸다
        p.wait()
        self.assertEqual(self.slept, [])


class CollectTest(unittest.TestCase):
    def setUp(self):
        self.srv = FakeServer()
        self.addCleanup(self.srv.close)
        self.tmp = Path(tempfile.mkdtemp())
        self.out = self.tmp / "collected.jsonl"
        self.gem = z.GeminiClient(self.srv.url + "/v1beta/models", "m", SECRET, sleep=lambda s: None)
        self.gw = z.GatewayClient(self.srv.url)

    def test_답_A와_B를_받아_저장하고_키는_헤더로만_보낸다(self):
        status = z.collect([record(1)], self.out, self.gem, self.gw)
        self.assertEqual(status, {"done": 1, "skipped": 0, "stopped": None})
        row = json.loads(self.out.read_text(encoding="utf-8").splitlines()[0])
        self.assertEqual(row["id"], "r1")
        self.assertIn("010-1111-2222", row["answer_a"])
        self.assertNotIn("010-1111-2222", row["sent_prompt"])
        self.assertEqual(row["action_b"], "MASK")
        path, _, headers = [c for c in self.srv.calls if c[0].endswith(":generateContent")][0]
        self.assertNotIn(SECRET, path)
        self.assertEqual({k.lower(): v for k, v in headers.items()}.get("x-goog-api-key"), SECRET)
        self.assertNotIn(SECRET, self.out.read_text(encoding="utf-8"))

    def test_이미_저장된_문장은_건너뛴다(self):
        z.collect([record(1)], self.out, self.gem, self.gw)
        before = len(self.srv.calls)
        status = z.collect([record(1), record(2)], self.out, self.gem, self.gw)
        self.assertEqual((status["done"], status["skipped"]), (1, 1))
        self.assertEqual(len(self.srv.calls) - before, 2)  # r2의 A와 B만

    def test_속도_제한이_계속되면_저장분을_남기고_멈추고_다시_하면_이어간다(self):
        self.srv.rate_limit_on = {2, 3, 4, 5}  # r2의 A가 재시도 3번까지 모두 429
        status = z.collect([record(1), record(2), record(3)], self.out, self.gem, self.gw)
        self.assertEqual(status["done"], 1)
        self.assertEqual(status["stopped"], "rate_limit")
        self.assertEqual(len(self.out.read_text(encoding="utf-8").splitlines()), 1)
        self.srv.rate_limit_on = set()
        status = z.collect([record(1), record(2), record(3)], self.out, self.gem, self.gw)
        self.assertEqual((status["done"], status["skipped"], status["stopped"]), (2, 1, None))

    def test_속도_제한이면_서버가_회복할_시간을_두고_점점_길게_기다린다(self):
        waits = []
        gem = z.GeminiClient(self.srv.url + "/v1beta/models", "m", SECRET, sleep=waits.append)
        self.srv.rate_limit_on = {1, 2, 3, 4}  # 첫 문장 A가 재시도 3번까지 모두 429
        status = z.collect([record(1)], self.out, gem, self.gw)
        self.assertEqual(status["stopped"], "rate_limit")
        self.assertEqual(len(waits), 3)
        self.assertGreaterEqual(waits[0], 15)
        self.assertEqual(waits, sorted(waits))
        self.assertGreaterEqual(waits[-1], 2 * waits[0])

    def test_조절기는_문장마다_답_A와_B_호출_앞에서_각각_기다린다(self):
        class Counter:
            n = 0

            def wait(self):
                self.n += 1
        pacer = Counter()
        z.collect([record(1), record(2)], self.out, self.gem, self.gw, pacer=pacer)
        self.assertEqual(pacer.n, 4)  # 문장 2개 x (A, B)

    def test_기준선_수집도_호출_앞에서_기다린다(self):
        class Counter:
            n = 0

            def wait(self):
                self.n += 1
        pacer = Counter()
        z.collect_baseline([record(1), record(2)], self.tmp / "a2.jsonl", self.gem, pacer=pacer)
        self.assertEqual(pacer.n, 2)

    def test_인증_오류는_즉시_멈춘다(self):
        class Denied(z.GeminiClient):
            def answer(self, text):
                raise z.FatalError("401")
        status = z.collect([record(1)], self.out, Denied(self.srv.url, "m", SECRET), self.gw)
        self.assertEqual((status["done"], status["stopped"]), (0, "fatal"))


class ScoreAndReportTest(unittest.TestCase):
    def setUp(self):
        self.srv = FakeServer()
        self.addCleanup(self.srv.close)
        self.tmp = Path(tempfile.mkdtemp())
        self.recs = [record(1), record(2), record(3, text="환불 문의 010-1111-2222 요청", item="PHONE")]
        self.col = self.tmp / "c.jsonl"
        self.sco = self.tmp / "s.jsonl"
        self.gem = z.GeminiClient(self.srv.url + "/v1beta/models", "m", SECRET, sleep=lambda s: None)
        self.gw = z.GatewayClient(self.srv.url)
        self.jev = z.JevClient(self.srv.url, "jev-test", SECRET, sleep=lambda s: None)
        self.srv.gateway_leaves_pseudonym = {self.recs[1]["text"]}   # r2는 가명이 남은 답

    def run_all(self):
        z.collect(self.recs, self.col, self.gem, self.gw)
        return z.score(self.col, self.sco, self.jev)

    def test_채점은_A와_B를_각각_하고_저장한다(self):
        status = self.run_all()
        self.assertEqual(status["scored"], 3)
        rows = [json.loads(l) for l in self.sco.read_text(encoding="utf-8").splitlines()]
        self.assertEqual(rows[0]["a"]["usable"], True)
        self.assertEqual(rows[0]["b"]["usable"], True)
        self.assertEqual(rows[1]["b"]["usable"], False)   # 가명이 남은 답은 혼란 질문에서 걸린다
        self.assertEqual(rows[0]["b"]["model"], "jev-test")
        self.assertNotIn(SECRET, self.sco.read_text(encoding="utf-8"))
        again = z.score(self.col, self.sco, self.jev)
        self.assertEqual(again["scored"], 0)           # 이미 채점한 것은 다시 하지 않는다

    def test_보고서_지표(self):
        self.run_all()
        rep = z.report(self.recs, self.col, self.sco)
        self.assertEqual(rep["n"], 3)
        self.assertEqual(rep["restoration"]["success"], 2)         # r2만 가명이 남음
        self.assertEqual(rep["retention"]["a_usable"], 3)
        self.assertEqual(rep["retention"]["b_usable_given_a"], 2)
        self.assertAlmostEqual(rep["retention"]["rate"], 2 / 3)
        self.assertEqual(rep["value_preservation"]["kept"], 2)
        self.assertEqual(rep["value_preservation"]["present_in_a"], 3)
        self.assertEqual(rep["by_item"]["PHONE"]["n"], 3)


class SampleTest(unittest.TestCase):
    def test_사람_판정_표본은_항목별로_고르게_뽑고_순서를_섞고_대응표를_따로_둔다(self):
        srv = FakeServer()
        self.addCleanup(srv.close)
        tmp = Path(tempfile.mkdtemp())
        recs = [record(i, item="PHONE") for i in range(1, 7)] + [record(i, item="EMAIL") for i in range(7, 13)]
        col = tmp / "c.jsonl"
        z.collect(recs, col, z.GeminiClient(srv.url + "/v1beta/models", "m", SECRET), z.GatewayClient(srv.url))
        sheet, key = tmp / "sheet.csv", tmp / "key.json"
        n = z.make_human_sample(recs, col, sheet, key, count=6, seed="s")
        self.assertEqual(n, 6)
        with sheet.open(encoding="utf-8", newline="") as f:
            rows = list(csv.DictReader(f))
        self.assertEqual(len(rows), 6)
        self.assertEqual({"id", "request", "answer_1", "answer_2", "usable_1", "usable_2"}, set(rows[0]))
        self.assertTrue(all(r["usable_1"] == "" and r["usable_2"] == "" for r in rows))
        mapping = json.loads(key.read_text(encoding="utf-8"))
        self.assertEqual(set(mapping), {r["id"] for r in rows})
        self.assertEqual({v for v in mapping.values()}, {"1", "2"})   # 순서가 섞여 있다
        items = {next(r2 for r2 in recs if r2["id"] == r["id"])["spans"][0]["item"] for r in rows}
        self.assertEqual(items, {"PHONE", "EMAIL"})
        n2 = z.make_human_sample(recs, col, tmp / "s2.csv", tmp / "k2.json", count=6, seed="s")
        self.assertEqual(tmp.joinpath("s2.csv").read_text(encoding="utf-8"), sheet.read_text(encoding="utf-8"))
        self.assertEqual(n2, 6)

    def test_일치율은_Jev와_사람_판정을_대응표로_맞춘다(self):
        key = {"r1": "2", "r2": "1"}            # B가 어느 쪽 칸인지
        human = [{"id": "r1", "usable_1": "예", "usable_2": "아니오"}, {"id": "r2", "usable_1": "아니오", "usable_2": "예"}]
        scores = {"r1": {"a": {"usable": True}, "b": {"usable": False}}, "r2": {"a": {"usable": True}, "b": {"usable": False}}}
        res = z.agreement(human, key, scores)
        self.assertEqual(res["all"], {"agree": 4, "n": 4})
        self.assertEqual(res["b_only"], {"agree": 2, "n": 2})
        res = z.agreement([{"id": "r1", "usable_1": "예", "usable_2": "예"}], key, scores)
        self.assertEqual(res["all"], {"agree": 1, "n": 2})


if __name__ == "__main__":
    unittest.main()
