# -*- coding: utf-8 -*-
"""우회 시험 세트 생성기 시험(명세 AC-10.7a). 서버가 필요 없다."""
import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import build_bypass_set as bb  # noqa: E402


class TransformTest(unittest.TestCase):
    def test_변형_함수(self):
        self.assertEqual(bb.T["no_hyphen"]("010-1234-5678"), "01012345678")
        self.assertEqual(bb.T["space_sep"]("010-1234-5678"), "010 1234 5678")
        self.assertEqual(bb.T["dot_sep"]("010-1234-5678"), "010.1234.5678")
        self.assertEqual(bb.T["fullwidth"]("010-12"), "０１０-１２")
        self.assertEqual(bb.T["korean_digits"]("010-1234"), "공일공 일이삼사")
        self.assertEqual(bb.T["spaced_digits"]("010-12"), "0 1 0 - 1 2")
        self.assertEqual(bb.T["zero_width"]("abcd"), "ab​cd")
        self.assertEqual(bb.T["at_word"]("jimin.68@hth.example"), "jimin.68 at hth dot example")
        self.assertEqual(bb.T["bracket_at"]("a@b.c"), "a[at]b.c")
        self.assertEqual(bb.T["spaced_at"]("a@b.c"), "a @ b.c")
        self.assertEqual(bb.T["split_space"]("abcdef"), "abc def")
        self.assertEqual(bb.T["split_newline"]("abcdef"), "abc\ndef")
        self.assertEqual(bb.T["upper_scheme"]("postgres://u:p@h/db"), "POSTGRES://u:p@h/db")
        self.assertEqual(bb.T["scheme_space"]("postgres://u:p@h/db"), "postgres:// u:p@h/db")
        self.assertEqual(bb.T["jwt_dot_space"]("a.b.c"), "a. b. c")
        self.assertEqual(bb.T["spaced_chars"]("대외비"), "대 외 비")
        self.assertEqual(bb.T["spaced_chars"]("Internal Only"), "I n t e r n a l   O n l y")
        self.assertEqual(bb.T["control"]("x"), "x")


class BuildTest(unittest.TestCase):
    def setUp(self):
        self.recs = bb.build_bypass_set("t")

    def test_규모와_구성(self):
        self.assertEqual(len(self.recs), 120)
        self.assertLessEqual(len(self.recs), 120)
        items = {r["item"] for r in self.recs}
        self.assertEqual(len(items), 12)
        for item in items:
            controls = [r for r in self.recs if r["item"] == item and r["transform"] == "control"]
            self.assertEqual(len(controls), 2, item)
        for (item, transform), n in __import__("collections").Counter((r["item"], r["transform"]) for r in self.recs).items():
            self.assertEqual(n, 2, (item, transform))

    def test_정답_위치는_변형된_값과_같다_그리고_원래_값과_다르다(self):
        for r in self.recs:
            self.assertEqual(len(r["spans"]), 1, r["id"])
            sp = r["spans"][0]
            self.assertEqual(sp["item"], r["item"])
            value = r["text"][sp["start"]:sp["end"]]
            self.assertEqual(value, r["value_sent"], r["id"])
            if r["transform"] != "control":
                self.assertNotEqual(value, r["value_original"], r["id"])
            self.assertEqual(r["kind"], "sensitive")
            self.assertTrue(all(ord(c) < 0x10000 for c in r["text"]))   # 서버는 UTF-16 위치를 쓴다

    def test_기대_처리(self):
        for r in self.recs:
            expected = "BLOCK" if r["spans"][0]["category"] == "D2" or r["item"] == "CLASSIFIED" else "MASK"
            self.assertEqual(r["expected_action"], expected, r["id"])

    def test_같은_seed는_같은_결과_id는_유일(self):
        self.assertEqual(self.recs, bb.build_bypass_set("t"))
        self.assertNotEqual(self.recs, bb.build_bypass_set("u"))
        ids = [r["id"] for r in self.recs]
        self.assertEqual(len(ids), len(set(ids)))

    def test_계좌_문장에는_은행_이름이_있다(self):
        for r in self.recs:
            if r["item"] == "ACCOUNT":
                import fake_values as fv
                self.assertTrue(any(b in r["text"] for b in fv.BANKS), r["id"])

    def test_측정_도구가_읽을_수_있다(self):
        import measure
        import tempfile
        from pathlib import Path
        out = Path(tempfile.mkdtemp()) / "bypass.jsonl"
        digest = bb.write_dataset(self.recs, out)
        records, h = measure.load_dataset(out)
        self.assertEqual((len(records), h), (120, digest))


if __name__ == "__main__":
    unittest.main()
