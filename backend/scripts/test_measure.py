"""측정 도구 v2 시험(명세 AC-10.3a~d). 서버 없이 순수 계산만 확인한다."""
import json
import os
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import measure  # noqa: E402


def span(category, item, start, end):
    return {"category": category, "item": item, "start": start, "end": end}


def rec(id_, kind="sensitive", action="MASK", spans=()):
    return {"id": id_, "text": "x", "kind": kind, "expected_action": action, "spans": list(spans)}


def resp(action="MASK", spans=(), timings=None):
    return {"action": action, "spans": list(spans),
            "timings": timings or {"detectMs": 1, "maskMs": 0, "answerMs": None, "restoreMs": 0}}


class MatchTest(unittest.TestCase):
    def test_같은_항목이고_1글자_이상_겹치면_탐지(self):
        m = measure.match_spans([span("D1", "PHONE", 0, 13)], [span("D1", "PHONE", 12, 20)])
        self.assertEqual((m.tp, m.fp, m.fn, m.item_wrong), (1, 0, 0, 0))

    def test_겹치지_않으면_FN과_FP(self):
        m = measure.match_spans([span("D1", "PHONE", 0, 5)], [span("D1", "PHONE", 5, 9)])
        self.assertEqual((m.tp, m.fp, m.fn), (0, 1, 1))

    def test_범주만_같고_항목이_다르면_항목_오류로_따로_센다(self):
        m = measure.match_spans([span("D1", "PHONE", 0, 13)], [span("D1", "EMAIL", 0, 13)])
        self.assertEqual((m.tp, m.fp, m.fn, m.item_wrong), (0, 0, 0, 1))

    def test_정답_하나에_탐지_둘이면_하나만_맞고_나머지는_FP(self):
        m = measure.match_spans([span("D1", "PHONE", 0, 13)],
                                [span("D1", "PHONE", 0, 6), span("D1", "PHONE", 7, 13)])
        self.assertEqual((m.tp, m.fp, m.fn), (1, 1, 0))

    def test_범주별_집계는_정답의_범주를_따른다(self):
        m = measure.match_spans([span("D1", "PHONE", 0, 5), span("D2", "API_KEY", 10, 20)],
                                [span("D1", "PHONE", 0, 5)])
        self.assertEqual(m.by_category["D1"], {"gold": 1, "tp": 1, "item_wrong": 0, "fn": 0})
        self.assertEqual(m.by_category["D2"], {"gold": 1, "tp": 0, "item_wrong": 0, "fn": 1})


class DetailTest(unittest.TestCase):
    def test_항목별_집계와_놓친_사례_목록(self):
        records = [
            rec("a", spans=[span("D1", "PHONE", 0, 5), span("D2", "API_KEY", 10, 20)]),
            rec("n", kind="normal", action="ALLOW"),
        ]
        responses = [
            resp("MASK", spans=[span("D1", "PHONE", 0, 5)]),
            resp("MASK", spans=[span("D3", "CLIENT", 0, 3)]),
        ]
        r = measure.evaluate(records, responses, rtts_ms=[1, 1])
        self.assertEqual(r["x_by_item"]["PHONE"], {"category": "D1", "gold": 1, "tp": 1, "item_wrong": 0, "fn": 0})
        self.assertEqual(r["x_by_item"]["API_KEY"]["fn"], 1)
        kinds = {(m["id"], m["type"], m["item"]) for m in r["misses"]}
        self.assertIn(("a", "FN", "API_KEY"), kinds)
        self.assertIn(("n", "FP_normal", "CLIENT"), kinds)
        self.assertEqual(r["action_mismatches"], [{"id": "a", "expected": "MASK", "actual": "MASK"}][:0]
                         + [{"id": "n", "expected": "ALLOW", "actual": "MASK"}])


class WilsonTest(unittest.TestCase):
    def test_알려진_값(self):
        lo, hi = measure.wilson(8, 10)
        self.assertAlmostEqual(lo, 0.4902, places=3)
        self.assertAlmostEqual(hi, 0.9433, places=3)

    def test_경계(self):
        self.assertEqual(measure.wilson(0, 0), (0.0, 0.0))
        lo, hi = measure.wilson(0, 10)
        self.assertEqual(lo, 0.0)
        self.assertGreater(hi, 0.2)
        lo, hi = measure.wilson(10, 10)
        self.assertEqual(hi, 1.0)


class EvaluateTest(unittest.TestCase):
    def test_X_Y_처리정확도(self):
        records = [
            rec("a", spans=[span("D1", "PHONE", 0, 5)]),
            rec("b", spans=[span("D1", "PHONE", 0, 5)]),
            rec("n1", kind="normal", action="ALLOW"),
            rec("n2", kind="normal", action="ALLOW"),
        ]
        responses = [
            resp(spans=[span("D1", "PHONE", 0, 5)]),
            resp("ALLOW"),                                    # 놓침, 처리도 틀림
            resp("ALLOW"),
            resp("MASK", spans=[span("D3", "CLIENT", 0, 3)]),  # 정상 문장 오탐
        ]
        r = measure.evaluate(records, responses, rtts_ms=[5, 6, 7, 8])
        self.assertEqual(r["x"]["D1"]["tp"], 1)
        self.assertEqual(r["x"]["D1"]["gold"], 2)
        self.assertEqual(r["x"]["D1"]["rate"], 0.5)
        self.assertEqual(r["y"]["false_positives"], 1)
        self.assertEqual(r["y"]["n"], 2)
        self.assertEqual(r["action_accuracy"]["correct"], 2)
        self.assertEqual(r["action_accuracy"]["n"], 4)

    def test_정상_문장은_ALLOW여도_탐지가_있으면_오탐(self):
        r = measure.evaluate([rec("n", kind="normal", action="ALLOW")],
                             [resp("ALLOW", spans=[span("D1", "EMAIL", 0, 3)])], rtts_ms=[1])
        self.assertEqual(r["y"]["false_positives"], 1)

    def test_T는_빈_값을_빼고_중앙값과_95백분위(self):
        records = [rec(str(i)) for i in range(4)]
        responses = [resp(timings={"detectMs": d, "maskMs": 0, "answerMs": None, "restoreMs": None if d == 4 else 1})
                     for d in (1, 2, 3, 4)]
        r = measure.evaluate(records, responses, rtts_ms=[10, 20, 30, 40])
        self.assertEqual(r["t"]["detectMs"]["median"], 2.5)
        self.assertEqual(r["t"]["detectMs"]["p95"], 4)
        self.assertEqual(r["t"]["restoreMs"]["n"], 3)
        self.assertEqual(r["t"]["rttMs"]["median"], 25)

    def test_개수가_다르면_오류(self):
        with self.assertRaises(ValueError):
            measure.evaluate([rec("a")], [], rtts_ms=[])


class DatasetTest(unittest.TestCase):
    def write(self, lines):
        d = Path(tempfile.mkdtemp())
        p = d / "set.jsonl"
        p.write_text("\n".join(lines) + "\n", encoding="utf-8")
        return p

    def test_읽기와_해시_확인(self):
        p = self.write([json.dumps(rec("a")), json.dumps(rec("n", kind="normal", action="ALLOW"))])
        records, digest = measure.load_dataset(p)
        self.assertEqual(len(records), 2)
        self.assertEqual(len(digest), 64)
        p.with_suffix(".sha256").write_text(digest + "\n", encoding="utf-8")
        measure.verify_frozen(p, digest)
        with self.assertRaises(measure.FrozenError):
            measure.verify_frozen(p, "0" * 64)

    def test_필수_필드가_없으면_거부(self):
        p = self.write([json.dumps({"id": "a", "text": "x"})])
        with self.assertRaises(ValueError):
            measure.load_dataset(p)

    def test_정상_문장에_정답이_있으면_거부(self):
        p = self.write([json.dumps(rec("n", kind="normal", action="ALLOW", spans=[span("D1", "PHONE", 0, 3)]))])
        with self.assertRaises(ValueError):
            measure.load_dataset(p)


class ResponseCheckTest(unittest.TestCase):
    def test_측정_모드가_아니면_오류(self):
        with self.assertRaises(measure.ModeError):
            measure.check_measurement_response({"action": "ALLOW", "answer": "x", "findings": []})

    def test_측정_응답은_통과(self):
        measure.check_measurement_response({"action": "ALLOW", "spans": [], "timings": {"detectMs": 0}})


if __name__ == "__main__":
    unittest.main()
