# -*- coding: utf-8 -*-
"""평가 세트 생성기 시험(명세 AC-10.1, 10.1a, 10.1b, 10.2). 서버가 필요 없다. 가짜 값만 쓴다."""
import json
import os
import re
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import build_eval_set as b  # noqa: E402
import fake_values as fv  # noqa: E402


def tpl(text, kind="sensitive", items=()):
    return {"kind": kind, "items": list(items), "template": text}


class FillTest(unittest.TestCase):
    def fill(self, text, seed="s"):
        return b.fill_template(text, b.rng_for(seed))

    def test_span_은_넣은_값의_위치와_정확히_같다(self):
        text, spans = self.fill("전화 {PHONE} 이고 메일 {EMAIL} 입니다")
        self.assertEqual([s["item"] for s in spans], ["PHONE", "EMAIL"])
        self.assertTrue(re.fullmatch(r"010-\d{4}-\d{4}", text[spans[0]["start"]:spans[0]["end"]]))
        self.assertIn("@", text[spans[1]["start"]:spans[1]["end"]])
        self.assertNotIn("{", text)

    def test_같은_seed는_같은_문장(self):
        self.assertEqual(self.fill("{RRN} {CARD}", "a"), self.fill("{RRN} {CARD}", "a"))
        self.assertNotEqual(self.fill("{RRN} {CARD}", "a"), self.fill("{RRN} {CARD}", "b"))

    def test_같은_자리표시자가_여러_번이면_값은_서로_다르다(self):
        text, spans = self.fill("{PHONE} / {PHONE}")
        self.assertEqual(len(spans), 2)
        values = [text[s["start"]:s["end"]] for s in spans]
        self.assertNotEqual(values[0], values[1])

    def test_이름과_은행은_탐지_대상이_아니라_span이_없다(self):
        text, spans = self.fill("{NAME} 님 {BANK}")
        self.assertEqual(spans, [])
        self.assertNotIn("{", text)

    def test_은행과_계좌는_같은_은행으로_채운다(self):
        text, spans = self.fill("{BANK} {ACCOUNT}")
        bank = text.split(" ")[0]
        self.assertIn(bank, fv.BANKS)
        self.assertEqual(spans[0]["item"], "ACCOUNT")
        self.assertEqual(spans[0]["start"], len(bank) + 1)

    def test_대표_연락처는_허용_목록_값이고_span이_없다(self):
        text, spans = self.fill("{HQ_PHONE} {HQ_EMAIL}")
        self.assertEqual(text, "02-555-0100 contact@hth.example")
        self.assertEqual(spans, [])

    def test_목록은_25개이고_값마다_span이_있다(self):
        text, spans = self.fill("{PHONE_LIST}")
        self.assertEqual(len(spans), 25)
        self.assertEqual(len(set(text[s["start"]:s["end"]] for s in spans)), 25)
        text, spans = self.fill("{EMAIL_LIST}")
        self.assertEqual(len(spans), 25)

    def test_회사_사전_값(self):
        text, spans = self.fill("{CODENAME} {CLIENT} {CLASSIFIED}")
        self.assertEqual([(s["category"], s["item"]) for s in spans],
                         [("D3", "CODENAME"), ("D3", "CLIENT"), ("D3", "CLASSIFIED")])
        self.assertIn(text[spans[0]["start"]:spans[0]["end"]], b.COMPANY["CODENAME"])

    def test_개인_키는_PEM_블록_전체가_span이다(self):
        text, spans = self.fill("키:\n{PRIVATE_KEY}\n끝")
        value = text[spans[0]["start"]:spans[0]["end"]]
        self.assertTrue(value.startswith("-----BEGIN") and value.rstrip().endswith("PRIVATE KEY-----"))
        self.assertEqual(spans[0]["item"], "PRIVATE_KEY")

    def test_정의_밖_중괄호는_그대로_둔다(self):
        text, spans = self.fill("{escape} 와 {PHONE}")
        self.assertTrue(text.startswith("{escape} 와 "))
        self.assertEqual(len(spans), 1)

    def test_자리표시자가_없는_문장은_span이_없다(self):
        self.assertEqual(self.fill("그냥 문장"), ("그냥 문장", []))

    def test_범위_밖_문자(self):
        for p in b.SUPPORTED:
            text, _ = self.fill("{%s}" % p)
            self.assertTrue(all(ord(c) < 0x10000 for c in text), p)  # 서버는 UTF-16 위치를 쓴다


class ActionTest(unittest.TestCase):
    def span(self, category, item):
        return {"category": category, "item": item, "start": 0, "end": 1}

    def test_기대_처리(self):
        self.assertEqual(b.expected_action([]), "ALLOW")
        self.assertEqual(b.expected_action([self.span("D1", "PHONE")]), "MASK")
        self.assertEqual(b.expected_action([self.span("D3", "CLIENT")]), "MASK")
        self.assertEqual(b.expected_action([self.span("D2", "API_KEY")]), "BLOCK")
        self.assertEqual(b.expected_action([self.span("D3", "CLASSIFIED")]), "BLOCK")
        self.assertEqual(b.expected_action([self.span("D1", "PHONE")] * 20), "WARN")
        self.assertEqual(b.expected_action([self.span("D1", "PHONE")] * 19), "MASK")
        self.assertEqual(b.expected_action([self.span("D1", "PHONE")] * 25 + [self.span("D2", "JWT")]), "BLOCK")


class DatasetTest(unittest.TestCase):
    def make_dir(self, files):
        d = Path(tempfile.mkdtemp())
        for name, rows in files.items():
            (d / name).write_text("\n".join(json.dumps(r, ensure_ascii=False) for r in rows) + "\n", encoding="utf-8")
        return d

    def test_items_표기를_믿지_않고_자리표시자에서_항목을_정한다(self):
        d = self.make_dir({"gemini.jsonl": [tpl("{CARD} 확인", items=["RRN"])]})
        rec = b.build_dataset(d, base_seed="t")[0]
        self.assertEqual([s["item"] for s in rec["spans"]], ["CARD"])
        self.assertEqual(rec["expected_action"], "MASK")

    def test_정상_틀은_허용_목록_자리표시자를_써도_정상이다(self):
        d = self.make_dir({"claude.jsonl": [tpl("문의는 {HQ_PHONE}", kind="normal")]})
        rec = b.build_dataset(d, base_seed="t")[0]
        self.assertEqual((rec["kind"], rec["spans"], rec["expected_action"]), ("normal", [], "ALLOW"))

    def test_정상_틀에_민감_자리표시자가_있으면_거부(self):
        d = self.make_dir({"x.jsonl": [tpl("{PHONE}", kind="normal")]})
        with self.assertRaises(ValueError):
            b.build_dataset(d, base_seed="t")

    def test_민감이라_적었어도_탐지_대상_자리표시자가_없으면_정상으로_분류하고_기록한다(self):
        d = self.make_dir({"x.jsonl": [tpl("{BANK} 식별 코드는 뭐야?")]})
        rec = b.build_dataset(d, base_seed="t")[0]
        self.assertEqual((rec["kind"], rec["spans"], rec["expected_action"], rec["declared_kind"]),
                         ("normal", [], "ALLOW", "sensitive"))

    def test_id는_출처와_순번이고_유일하다_같은_틀은_거부(self):
        d = self.make_dir({"a.jsonl": [tpl("{PHONE} 1"), tpl("{EMAIL} 2")], "b.jsonl": [tpl("{CARD} 3")]})
        recs = b.build_dataset(d, base_seed="t")
        self.assertEqual([r["id"] for r in recs], ["a-001", "a-002", "b-001"])
        d2 = self.make_dir({"a.jsonl": [tpl("{PHONE} 1")], "b.jsonl": [tpl("{PHONE} 1")]})
        with self.assertRaises(ValueError):
            b.build_dataset(d2, base_seed="t")

    def test_같은_입력과_seed는_같은_결과(self):
        d = self.make_dir({"a.jsonl": [tpl("{PHONE} {RRN}"), tpl("끝", kind="normal")]})
        self.assertEqual(b.build_dataset(d, base_seed="t"), b.build_dataset(d, base_seed="t"))

    def test_기록_형식은_측정_도구가_읽을_수_있다(self):
        import measure
        d = self.make_dir({"a.jsonl": [tpl("{PHONE} 전화"), tpl("그냥 문장", kind="normal")]})
        out = d / "set.jsonl"
        digest = b.write_dataset(b.build_dataset(d, base_seed="t"), out)
        records, loaded_hash = measure.load_dataset(out)
        self.assertEqual(digest, loaded_hash)
        self.assertEqual(len(records), 2)
        self.assertEqual(out.with_suffix(".sha256").read_text(encoding="utf-8").strip(), digest)


class QuotaTest(unittest.TestCase):
    def test_규모_점검(self):
        recs = [{"kind": "sensitive", "spans": [{"item": "PHONE"}] * 25, "id": "x"}] * 10
        problems = b.check_quota(recs)
        self.assertTrue(any("정상" in p for p in problems))
        self.assertTrue(any("RRN" in p for p in problems))
        self.assertFalse(any("대량" in p for p in problems))


if __name__ == "__main__":
    unittest.main()
