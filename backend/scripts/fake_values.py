# -*- coding: utf-8 -*-
"""
가짜 민감 값 생성기 (docs/dummy-company.md 4장). 평가 세트를 만들 때 업무 요청 틀에 넣는 값을 만든다.

생성한 값은 탐지 규칙(docs/design.md 2장)의 검증을 통과하도록 만든다: 실제 날짜의 생년월일, Luhn 통과 카드번호,
모두 같은 숫자가 아닌 전화번호, 자리 표시자가 아닌 키. 실제로 쓸 수 있는 값이나 실제 개인정보가 아니다.
고정 seed로 같은 결과를 다시 만든다.
"""
import base64
import datetime
import json
import random
import string

SURNAMES = "김이박최정강조윤장임한오서신권황안송류홍"
GIVEN = ["민수", "서연", "지훈", "하은", "도윤", "수빈", "예준", "지민", "현우", "유진", "성호", "다은", "준서", "채원", "태윤", "가은"]
ROMAN = ["minsu", "seoyeon", "jihun", "haeun", "doyun", "subin", "yejun", "jimin", "hyunwoo", "yujin", "sungho", "daeun"]
BANKS = ["국민은행", "신한은행", "우리은행", "하나은행", "기업은행", "농협", "카카오뱅크", "우체국"]
ACCOUNT_SHAPES = [(3, 6, 2, 3), (3, 3, 6), (4, 2, 7), (3, 2, 6)]
ALNUM = string.ascii_letters + string.digits


class Fake:
    def __init__(self, seed):
        self.r = random.Random(seed)

    def _digits(self, n):
        return "".join(self.r.choice(string.digits) for _ in range(n))

    def _birth(self, gender_digits):
        """생년월일-성별 숫자-6자리. 1, 2, 5, 6은 1900년대(1960~1999), 3, 4, 7, 8은 2000년대(2000~2008) 출생이다."""
        gender = self.r.choice(gender_digits)
        year = self.r.randint(1960, 1999) if gender in "1256" else self.r.randint(2000, 2008)
        date = datetime.date(year, 1, 1) + datetime.timedelta(days=self.r.randint(0, 364))
        return f"{date:%y%m%d}-{gender}{self._digits(6)}"

    def rrn(self):
        """주민등록번호 모양. 뒷자리 첫 숫자 1~4. 검증 숫자는 계산하지 않는다(탐지 규칙도 요구하지 않는다)."""
        return self._birth("1234")

    def frn(self):
        """외국인등록번호 모양. 뒷자리 첫 숫자 5~8."""
        return self._birth("5678")

    def phone(self):
        while True:
            mid, last = self._digits(4), self._digits(4)
            if len(set(mid + last)) > 1:
                return f"010-{mid}-{last}"

    def card(self):
        body = self.r.choice("3456") + self._digits(14)
        for check in string.digits:
            if _luhn(body + check):
                d = body + check
                return f"{d[:4]}-{d[4:8]}-{d[8:12]}-{d[12:]}"
        raise AssertionError("Luhn 검증 숫자를 찾지 못했다")

    def account(self):
        shape = self.r.choice(ACCOUNT_SHAPES)
        return self.r.choice(BANKS), "-".join(self._digits(n) for n in shape)

    def name(self):
        return self.r.choice(SURNAMES) + self.r.choice(GIVEN)

    def email(self):
        local = self.r.choice(ROMAN) + "." + self._digits(2)
        return f"{local}@{self.r.choice(['mail', 'hth'])}.example"

    def _random(self, n, alphabet=ALNUM):
        return "".join(self.r.choice(alphabet) for _ in range(n))

    def aws_key(self):
        return "AKIA" + self._random(16, string.ascii_uppercase + string.digits)

    def openai_key(self):
        return "sk-" + self._random(32)

    def github_token(self):
        return "ghp_" + self._random(36)

    def jwt(self):
        def part(obj):
            return base64.urlsafe_b64encode(json.dumps(obj).encode()).decode().rstrip("=")
        return f"{part({'alg': 'HS256', 'typ': 'JWT'})}.{part({'sub': self._digits(6), 'role': 'user'})}.{self._random(43, ALNUM + '-_')}"

    def password(self):
        while True:
            value = self._random(10, ALNUM + "!@#$%^&*")
            if any(c.isdigit() for c in value) and any(c.isalpha() for c in value):
                return value

    def conn_string(self):
        scheme, port = self.r.choice([("postgres", 5432), ("mysql", 3306)])
        user = self.r.choice(["app_user", "svc_order", "batch", "admin"])
        return f"{scheme}://{user}:{self.password()}@db{self._digits(2)}.hth.internal:{port}/{self.r.choice(['orders', 'hr', 'billing'])}"


def _luhn(digits):
    total, dbl = 0, False
    for ch in reversed(digits):
        d = int(ch)
        if dbl:
            d = d * 2 - 9 if d * 2 > 9 else d * 2
        total += d
        dbl = not dbl
    return total % 10 == 0


if __name__ == "__main__":
    # 교차 확인용 표본을 만든다: Java 탐지기가 같은 값을 잡는지 시험한다(FakeValuesCrossCheckTest).
    import sys
    f = Fake(seed=2026)
    out = sys.stdout
    for _ in range(30):
        bank, number = f.account()
        rows = [("RRN", f.rrn()), ("FRN", f.frn()), ("PHONE", f.phone()), ("CARD", f.card()), ("EMAIL", f.email()),
                ("ACCOUNT", f"{bank} {number}"), ("API_KEY", f.aws_key()), ("API_KEY", f.openai_key()),
                ("API_KEY", f.github_token()), ("JWT", f.jwt()), ("PASSWORD", "password=" + f.password()),
                ("CONN_STRING", f.conn_string())]
        for item, value in rows:
            out.write(f"{item}\t{value}\n")
