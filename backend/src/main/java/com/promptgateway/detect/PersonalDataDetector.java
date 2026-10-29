package com.promptgateway.detect;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * D1 개인정보 탐지(docs/design.md 2장). 형식에 더해 검증 규칙을 적용해서, 예시 값과 숫자만 비슷한 문장(날짜, 버전, 주문번호)을 걸러 낸다.
 *
 *   주민/외국인등록번호  생년월일이 실제 날짜이고 뒷자리 첫 숫자가 1~4(주민) 또는 5~8(외국인). 검증 숫자는 요구하지 않는다
 *                      (2020년 10월 이후 발급 번호는 검증 숫자 체계가 없다)
 *   휴대전화            010 등으로 시작. 가운데와 끝이 모두 같은 숫자면 예시 값으로 본다
 *   이메일              example.com 같은 예시 도메인은 뺀다
 *   계좌번호            은행명이나 "계좌" 뒤 20자 안에 있을 때만
 *   카드번호            Luhn 검사 통과
 */
public class PersonalDataDetector implements Detector {

    private static final Pattern RESIDENT = Pattern.compile("(?<!\\d)(\\d{2})(\\d{2})(\\d{2})-?([1-8])\\d{6}(?!\\d)");
    private static final Pattern PHONE = Pattern.compile("(?<!\\d)01[016789][-. ]?(\\d{3,4})[-. ]?(\\d{4})(?!\\d)");
    private static final Pattern EMAIL = Pattern.compile("(?<![\\w.+-])[\\w.+-]+@([A-Za-z0-9-]+(?:\\.[A-Za-z0-9-]+)+)");
    private static final Pattern ACCOUNT_CONTEXT = Pattern.compile("(은행|뱅크|계좌|농협|신협|새마을금고|우체국)");
    private static final Pattern ACCOUNT = Pattern.compile("(?<![\\d-])\\d{2,6}(?:-\\d{2,8}){1,3}(?![\\d-])");
    private static final Pattern CARD = Pattern.compile("(?<!\\d)[3-6]\\d{3}(?:[- ]?\\d{4}){2}[- ]?\\d{1,7}(?!\\d)");
    private static final Set<String> EXAMPLE_DOMAINS = Set.of("example.com", "example.org", "example.net", "test.com", "domain.com");

    @Override
    public List<Finding> detect(String text) {
        List<Finding> found = new ArrayList<>();
        resident(text, found);
        phones(text, found);
        emails(text, found);
        accounts(text, found);
        cards(text, found);
        return found;
    }

    private static void resident(String text, List<Finding> found) {
        Matcher m = RESIDENT.matcher(text);
        while (m.find()) {
            int genderDigit = m.group(4).charAt(0) - '0';
            int century = (genderDigit == 1 || genderDigit == 2 || genderDigit == 5 || genderDigit == 6) ? 1900 : 2000;
            if (!isRealDate(century + Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)))) {
                continue;
            }
            found.add(new Finding(genderDigit <= 4 ? Item.RRN : Item.FRN, m.start(), m.end(), m.group()));
        }
    }

    private static boolean isRealDate(int year, int month, int day) {
        try {
            LocalDate.of(year, month, day);
            return true;
        } catch (DateTimeException e) {
            return false;
        }
    }

    private static void phones(String text, List<Finding> found) {
        Matcher m = PHONE.matcher(text);
        while (m.find()) {
            String rest = m.group(1) + m.group(2);
            if (rest.chars().distinct().count() == 1) {
                continue; // 010-0000-0000, 010-1111-1111 같은 예시 값
            }
            found.add(new Finding(Item.PHONE, m.start(), m.end(), m.group()));
        }
    }

    private static void emails(String text, List<Finding> found) {
        Matcher m = EMAIL.matcher(text);
        while (m.find()) {
            String domain = m.group(1).toLowerCase();
            if (EXAMPLE_DOMAINS.contains(domain)) {
                continue;
            }
            found.add(new Finding(Item.EMAIL, m.start(), m.end(), m.group()));
        }
    }

    private static void accounts(String text, List<Finding> found) {
        Matcher context = ACCOUNT_CONTEXT.matcher(text);
        while (context.find()) {
            int windowEnd = Math.min(text.length(), context.end() + 20);
            Matcher m = ACCOUNT.matcher(text).region(context.end(), windowEnd);
            if (m.find()) {
                long digits = m.group().chars().filter(Character::isDigit).count();
                if (digits >= 10 && digits <= 14) {
                    found.add(new Finding(Item.ACCOUNT, m.start(), m.end(), m.group()));
                }
            }
        }
    }

    private static void cards(String text, List<Finding> found) {
        Matcher m = CARD.matcher(text);
        while (m.find()) {
            String digits = m.group().replaceAll("[- ]", "");
            if (digits.length() >= 13 && digits.length() <= 19 && luhn(digits)) {
                found.add(new Finding(Item.CARD, m.start(), m.end(), m.group()));
            }
        }
    }

    static boolean luhn(String digits) {
        int sum = 0;
        boolean dbl = false;
        for (int i = digits.length() - 1; i >= 0; i--) {
            int d = digits.charAt(i) - '0';
            if (dbl) {
                d *= 2;
                if (d > 9) {
                    d -= 9;
                }
            }
            sum += d;
            dbl = !dbl;
        }
        return sum % 10 == 0;
    }
}
