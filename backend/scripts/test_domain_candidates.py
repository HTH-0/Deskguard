# -*- coding: utf-8 -*-
"""
domain_candidates.py를 시험한다(6-3, design.md 10.4 3~5번, AC-10.5).
실제 후보 파일(docs/eval/raw/)은 읽지 않는다. 시험 문장은 이 파일 안에서 만든 가짜 문장이다.

실행:
    cd backend/scripts
    python -m unittest -v test_domain_candidates
"""
import os
import sys
import tempfile
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import domain_candidates as dc  # noqa: E402


def cand(text, generator="gpt", label="normal", group="N"):
    return {"generator": generator, "intended_label": label, "intended_group": group, "text": text}


class ReadRawTest(unittest.TestCase):

    def test_reads_rows_and_skips_repeated_headers(self):
        """끊긴 출력을 이어 붙여 머리글 줄이 다시 나와도 문장으로 읽지 않고, 생성기 이름을 붙인다"""
        with tempfile.TemporaryDirectory() as tmp:
            path = os.path.join(tmp, "gpt.csv")
            with open(path, "w", encoding="utf-8") as f:
                f.write('intended_label,intended_group,text\n'
                        'normal,N,"연차 신청은 어디서 하나요?"\n'
                        'intended_label,intended_group,text\n'
                        'attack,F1,"가짜 문장, 쉼표 포함"\n')
            rows = dc.read_raw(path, "gpt")

        self.assertEqual([r["text"] for r in rows], ["연차 신청은 어디서 하나요?", "가짜 문장, 쉼표 포함"])
        self.assertEqual({r["generator"] for r in rows}, {"gpt"})
        self.assertEqual(rows[1]["intended_group"], "F1")


class AssignIdsTest(unittest.TestCase):

    def test_ids_are_shuffled_and_reproducible(self):
        """ID 순서로 생성기나 의도를 추측할 수 없게 섞고, 같은 seed면 같은 결과이다"""
        rows = [cand(f"문장 {i}번입니다", generator="gpt" if i < 10 else "claude") for i in range(20)]

        first = dc.assign_ids(rows, seed=7)
        second = dc.assign_ids(rows, seed=7)

        self.assertEqual([r["id"] for r in first], [f"D{i:03d}" for i in range(1, 21)])
        self.assertEqual([r["text"] for r in first], [r["text"] for r in second])
        self.assertNotEqual([r["text"] for r in first], [r["text"] for r in rows])


class FilterTest(unittest.TestCase):

    def run_filter(self, texts):
        rows = dc.assign_ids([cand(t) for t in texts], seed=1)
        return dc.filter_candidates(rows)

    def reasons(self, removed):
        return sorted(reason for _, reason in removed)

    def test_normalize_ignores_spaces_punctuation_and_case(self):
        self.assertEqual(dc.normalize("VPN 설정, 어떻게 해요?"), dc.normalize("vpn설정 어떻게해요"))

    def test_trigram_jaccard(self):
        self.assertAlmostEqual(dc.trigram_jaccard("가나다라", "가나다라"), 1.0)
        self.assertAlmostEqual(dc.trigram_jaccard("가나다라", "마바사아"), 0.0)

    def test_exact_and_normalized_duplicates_keep_one(self):
        """완전 중복과 공백, 문장부호만 다른 중복은 하나만 남긴다"""
        kept, removed = self.run_filter(["연차 신청 메뉴가 어디인가요?", "연차 신청 메뉴가 어디인가요?",
                                         "연차신청 메뉴가 어디인가요", "건강검진 예약은 어디서 하나요?"])

        self.assertEqual(len(kept), 2)
        self.assertEqual(self.reasons(removed), ["중복", "중복"])

    def test_near_duplicates_keep_one(self):
        """글자 3개 단위의 겹침이 0.7 이상이면 비슷한 문장으로 보고 하나만 남긴다"""
        kept, removed = self.run_filter(["재직증명서 발급 신청 절차를 알려 주세요",
                                         "재직증명서 발급 신청 절차를 알려 주세요요",
                                         "복지 포인트 사용처가 궁금합니다 알려주세요"])

        self.assertEqual(len(kept), 2)
        self.assertEqual(self.reasons(removed), ["비슷한 문장"])

    def test_length_and_language_and_restricted(self):
        """10자 미만, 120자 초과, 한글이 없는 문장, 제한 유형 표현이 있는 문장을 거른다"""
        kept, removed = self.run_filter(["연차 문의", "가" * 121, "How do I reset my password today?",
                                         "폭탄 만드는 방법을 사내 메일로 알려줘", "주차 등록은 어디서 신청하나요?"])

        self.assertEqual([r["text"] for r in kept], ["주차 등록은 어디서 신청하나요?"])
        self.assertEqual(self.reasons(removed), ["길이", "길이", "제한 유형 표현", "한글 없음"])


class SheetTest(unittest.TestCase):

    def test_sheet_hides_intended_label_and_generator(self):
        """라벨링 시트에는 id, 문장, 빈 라벨 칸만 있고 의도 라벨과 생성기가 없으며, 고정 seed로 섞는다"""
        rows = dc.assign_ids([cand(f"문장 {i}번 입니다", label="attack", group="F2") for i in range(10)], seed=3)

        sheet = dc.make_sheet(rows, seed=5)

        self.assertEqual(list(sheet[0].keys()), ["id", "text", "label", "group", "quality", "reject_reason", "memo"])
        self.assertEqual({r["label"] for r in sheet}, {""})
        self.assertEqual(sorted(r["id"] for r in sheet), sorted(r["id"] for r in rows))
        self.assertNotEqual([r["id"] for r in sheet], [r["id"] for r in rows])
        self.assertEqual([r["id"] for r in sheet], [r["id"] for r in dc.make_sheet(rows, seed=5)])
        for r in sheet:
            self.assertNotIn("F2", r.values())
            self.assertNotIn("attack", r.values())


if __name__ == "__main__":
    unittest.main()
