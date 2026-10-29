# -*- coding: utf-8 -*-
"""
fake_values.py 시험(2일차, docs/dummy-company.md 4장). 생성한 값이 탐지 규칙(docs/design.md 2장)의 검증을 통과하는지,
같은 seed로 같은 값이 나오는지 확인한다. 실제 개인정보는 쓰지 않는다.

실행: cd backend/scripts && python -m unittest -v test_fake_values
"""
import datetime
import os
import re
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import fake_values as fv  # noqa: E402


def luhn(digits):
    total, dbl = 0, False
    for ch in reversed(digits):
        d = int(ch)
        if dbl:
            d = d * 2 - 9 if d * 2 > 9 else d * 2
        total += d
        dbl = not dbl
    return total % 10 == 0


class FakeTest(unittest.TestCase):

    def setUp(self):
        self.f = fv.Fake(seed=42)

    def test_reproducible(self):
        a, b = fv.Fake(seed=7), fv.Fake(seed=7)
        self.assertEqual([a.phone(), a.rrn(), a.card()], [b.phone(), b.rrn(), b.card()])

    def test_rrn_and_frn_pass_date_and_gender_rules(self):
        for _ in range(200):
            for value, digits in ((self.f.rrn(), "1234"), (self.f.frn(), "5678")):
                m = re.fullmatch(r"(\d{2})(\d{2})(\d{2})-([1-8])\d{6}", value)
                self.assertIsNotNone(m, value)
                self.assertIn(m.group(4), digits)
                century = 1900 if m.group(4) in "1256" else 2000
                datetime.date(century + int(m.group(1)), int(m.group(2)), int(m.group(3)))

    def test_phone_is_mobile_and_not_uniform(self):
        for _ in range(200):
            m = re.fullmatch(r"010-(\d{4})-(\d{4})", self.f.phone())
            self.assertIsNotNone(m)
            self.assertGreater(len(set(m.group(1) + m.group(2))), 1)

    def test_card_passes_luhn(self):
        for _ in range(200):
            value = self.f.card()
            self.assertRegex(value, r"^[3-6]\d{3}-\d{4}-\d{4}-\d{4}$")
            self.assertTrue(luhn(value.replace("-", "")), value)

    def test_account_has_bank_context_and_length(self):
        for _ in range(100):
            bank, number = self.f.account()
            self.assertRegex(bank, r"(은행|농협|우체국|새마을금고|신협|뱅크)$")
            self.assertTrue(10 <= len(number.replace("-", "")) <= 14, number)

    def test_email_avoids_allowlist_and_example_domains(self):
        for _ in range(100):
            value = self.f.email()
            self.assertRegex(value, r"^[a-z0-9.]+@(mail|hth)\.example$")
            self.assertNotIn(value, ("contact@hth.example", "support@hth.example"))

    def test_secrets_match_known_formats_and_are_not_placeholders(self):
        self.assertRegex(self.f.aws_key(), r"^AKIA[0-9A-Z]{16}$")
        self.assertRegex(self.f.openai_key(), r"^sk-[A-Za-z0-9]{32}$")
        self.assertRegex(self.f.github_token(), r"^ghp_[A-Za-z0-9]{36}$")
        self.assertRegex(self.f.jwt(), r"^eyJ[\w-]{8,}\.[\w-]{8,}\.[\w-]{8,}$")
        self.assertRegex(self.f.password(), r"^[A-Za-z0-9!@#$%^&*]{10}$")
        self.assertRegex(self.f.conn_string(), r"^(postgres|mysql)://\w+:[^@\s/]+@[\w.]+:\d+/\w+$")
        for value in (self.f.password(), self.f.aws_key()):
            self.assertGreaterEqual(len(set(value)), 3)
            self.assertNotIn("xxxx", value.lower())

    def test_korean_name(self):
        self.assertRegex(self.f.name(), r"^[가-힣]{2,4}$")


if __name__ == "__main__":
    unittest.main()
