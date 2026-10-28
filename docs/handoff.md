# 이어서 작업하기

다른 컴퓨터나 새 세션에서 이어서 작업할 때 가장 먼저 읽는 문서이다. 세션을 마칠 때마다 갱신한다.

기준일: 2026-10-28

## 1. 한눈에 보기

- 프로젝트: Deskguard. 사내 HR 헬프데스크 챗봇으로 들어가는 프롬프트를 패턴 필터와 판정 모델로 검사해 프롬프트 인젝션과 도메인 정책 위반을 차단하는 게이트웨이이다. ([README.md](../README.md))
- 현재 위치: 도메인 정책, 평가 데이터, 명세서가 확정되었고 판정 모델(Jev) 채택과 질문 v2(조건부 채택)와 설계서 확정까지 끝났다. 3단계 코드의 특성화 테스트(스텝 7)까지 끝났다. 스텝 8(TDD 구현)은 그룹 A~C(T1~T9), T10(`GatewayLogger`), T11(측정 모드), T12(`run_batch.py`)까지 끝났고 Java 테스트 148건과 Python 테스트 25건이 통과한다. 다음은 T13(실제 Jev 스모크 시험)이다.
- 기술: Spring Boot 4.1.1, Java 25 LTS, Maven. 응답 모델은 Gemini(`gemini-3.6-flash`, 무료 티어), 판정 모델 후보 1순위는 Jev(`jev-1.14.0`)이다.
- 기존 코드(`backend/`)는 3단계 프록시 골격이다. 패턴 필터와 판정 모델은 아직 구현하지 않았다.

## 2. 단계 상태

| 단계 | 내용 | 상태 |
|---|---|---|
| 1~3 | API 테스트, 데이터셋, 프록시 골격 | 완료 |
| 2.4~2.9 | 주제 방향(도메인 정책 추가), 정책서, 룰 후보 검증, 정상 풀 보강 | 완료 |
| 3 | 명세서 (`spec.md`) | 완료, 확정 |
| 2.5 | Jev 한국어 PoC | 완료, 합격. Jev 채택 승인됨 |
| 4.5 | PoC 약점 개선 (라벨 기준, 점검 문장 65개, 질문 v2) | 완료. v2를 현행 질문으로 조건부 채택(한계는 DECISIONS.md "질문 v2 조건부 채택"). v3는 취소 |
| 5 | 설계서 (`design.md`) | 완료, 확정(2026-10-27). 1~9절 |
| 7 | 3단계 코드의 특성화 테스트 | 완료. 테스트 14건 통과(2026-10-27). 발견 사항은 DECISIONS.md "3단계 코드 특성화 테스트" |
| 8 | 작업 목록과 TDD 구현 | 진행 중. T1~T12 완료, E(T13, T14) 대기. 진행 상태는 [tasks.md](tasks.md) |
| 6 | 탐지율·오탐율 측정 (eval 사용) | 대기 |

## 3. 사용자 결정 대기 목록

| 번호 | 항목 | 권장 | 근거 |
|---|---|---|---|
| 1 | Jev를 판정 모델로 채택 | 승인함(2026-10-27) | [DECISIONS.md](../DECISIONS.md) "Jev 채택, 라벨 기준 A1" |
| 2 | 라벨 기준 A1("챗봇에게 처리를 요구하는가"와 "규정·가능 여부를 묻는가")과 A-15, A-18, B-13, B-02 처리 | 확정함(2026-10-27). 구체성 기준 추가, B-02는 문장 수정 | [domain-policy.md](domain-policy.md) 4장 |
| 3 | 점검 문장 확장(65문장)과 합격 기준 N1~N6 | 완료. 65문장 시험, v2 조건부 채택 | design.md 4.3, `docs/poc/data_n.tsv` |
| 4 | 개발사 문의 메일(약관 R8) | 답장 수신(2026-10-28), 위험 수용 후 진행으로 결정. 개별 승인은 받을 수 없다 | 이 문서 9절, [open-risks.md](open-risks.md) R8, DECISIONS.md "개발사 답장 수신" |
| 5 | 저장소를 공개로 바꾸기 전 점검(R12) | 공개 전에 수행 | [open-risks.md](open-risks.md) R12 |
| 6 | 도메인 문장의 최종 데이터 파일화(컬럼 구성, 출처·검수 기록) | 설계서 이후 | [archive/review-sheet.md](archive/review-sheet.md), `docs/poc/data.tsv` |
| 7 | 사용자 작성 문장의 IT 편중과 어휘 반복 보정 | 선택 | DECISIONS.md "F-2 사용자 작성 문장 확정" |
| 8 | 확인용 채팅 화면을 T11 직후에 만들지(안 B) 원칙대로 마지막(9단계)에 만들지(안 A) | 안 B 권장(단일 HTML, 반나절 제한, 점수 막대 표시). 선택하면 constitution 5번의 "마지막에"는 예외로 기록 | 대화에서 제안함. 사용자 선택 대기 |
| 9 | 정상 질문 응답이 약 10초 걸린 원인 | 확인함(2026-10-28): 측정 모드(Gemini 없이)에서 총 0.19~0.20초(첫 호출 0.71초), 실제 Jev 응답은 PASS와 점수 10개였다. 지연은 응답 모델 구간이 대부분이다. 방법: T10 로그의 `filterMs`, `judgeMs`, `answerMs`로 확인. 사용자 컴퓨터에서 서버를 다시 띄워 한 번 호출한다. 측정 모드(5절)로 띄우면 Gemini를 부르지 않으므로 `Measure-Command`의 시간이 판정만의 지연이다. 첫 측정(일반 모드)은 총 약 17.6초였다 | DECISIONS.md "T10 GatewayLogger" |
| 10 | 포트폴리오 우선순위: 완성의 정의(T13, 6단계, 7단계, README와 위협 모델), 6단계에 베이스라인(키워드 또는 정규식 기준선) 포함 여부, 일정 | 베이스라인 포함 권장. 완성의 정의와 일정은 사용자 확인 대기 | DECISIONS.md "포트폴리오 방향 점검" |
| 11 | `constitution.md` 확정 여부 | 현재 "초안 (사용자 검토 전)". 검토 후 확정 | DECISIONS.md "개발 방식 점검과 명세 정정" |

## 4. 다음 작업 (제안 순서)

1. 남은 결정(3절의 4~7번)은 설계서와 병행하며 급하지 않다.
2. 설계서(스텝 5)는 확정되었다. [design.md](design.md) 5~9절을 따른다. 판정 호출은 `RestClient`, 타임아웃 연결 2초와 읽기 3초, 재시도는 429·529만 2회이다.
3. 스텝 7은 완료했다. 특성화 테스트는 `backend/src/test/`에 있고 `mvn -B test`로 실행한다. 발견 사항(입력 검증 없음, 깨진 요청 본문이 500, 오류 메시지에 내부 정보 노출)은 스텝 8에서 수용 기준과 함께 고친다.
4. 스텝 8을 이어서 한다. [tasks.md](tasks.md)의 T13(실제 Jev 스모크 시험), T14(문서 정리) 순서이다. T10~T12는 완료했다. T13은 서버를 측정 모드로 띄우고 `run_batch.py`로 design 분할 문장 5~10개를 보내 실제 Jev 응답과 요약표를 확인한다(eval은 열지 않는다). 각 작업은 테스트를 먼저 쓰고(빨강) 최소 코드로 통과시키고(초록) 전체 테스트를 실행한 뒤 커밋한다. 단위 테스트는 `cd backend`, `mvn -B test`로 실행한다.
5. 6단계 측정은 eval 문장을 처음 여는 단계이다. 그 전에 질문 v2와 임계값을 확정한다.
6. 인프라 보강(CI, Docker, 헬스 체크)과 화면은 완성 후에 곁가지로 늘린다(2026-10-28 사용자 결정). 목록은 [tasks.md](tasks.md)의 "보류 목록(곁가지)"에 있다. 완성이 끝나기 전에는 시작하지 않는다.

## 5. 새 컴퓨터에서 시작하기 (Windows 기준)

필요한 것: Git, JDK 25 LTS(Temurin), Maven 3.9.x, Python 3(`backend/scripts/run_batch.py`용), PowerShell

```powershell
git clone https://github.com/HTH-0/Deskguard.git
cd Deskguard

# 클라우드 세션에서 작업하던 내용은 작업 브랜치에 있다. 가져온 뒤 확인한다.
git fetch origin claude/upbeat-cori-pi0rtq
git checkout claude/upbeat-cori-pi0rtq
git log --oneline -5

# 이 저장소에만 적용하는 커밋 정보 (전역 설정은 건드리지 않는다)
git config user.name "HTH-0"
git config user.email "192852807+HTH-0@users.noreply.github.com"

# API 키는 저장소에 없다. 각 콘솔에서 발급해 환경변수로만 설정한다 (새 터미널부터 적용)
setx GEMINI_API_KEY "발급받은_키"
setx TYPESAFE_API_KEY "발급받은_키"

# 빌드와 테스트 확인 (전체 148건). 클라우드 세션은 JDK 21이라 mvn -B test -Djava.version=21 로 실행한다.
cd backend
mvn -B -q test
```

- Gemini 키: aistudio.google.com, Jev 키: https://console.typesafe.ai/keys
- 서버 실행: `backend` 폴더에서 `mvn spring-boot:run`. 두 키가 모두 있어야 시작된다. 측정 모드(개발용, Gemini를 부르지 않고 판정 결과를 200으로 돌려준다)는 PowerShell에서 `mvn spring-boot:run "-Dspring-boot.run.arguments=--gateway.measurement-mode=true"`로 켠다.
- 측정 도구(측정 모드 서버가 떠 있어야 한다): `backend/scripts` 폴더에서 `python run_batch.py --input ../../dataset.csv --output result.csv`. 기본은 design 분할만 보낸다. 도구 시험은 같은 폴더에서 `python -m unittest -v test_run_batch`이다(서버와 키가 필요 없다).
- 요청 확인(PowerShell): `curl.exe`로 UTF-8 본문을 보내는 `Send-Chat` 함수를 새 창마다 다시 만들어야 한다. 함수 본문은 [../tools/README.md](../tools/README.md)의 "Send-Chat" 절에 있다.
- 저장소는 비공개이므로 clone과 push에 GitHub 로그인이 필요하다.
- Claude Code로 작업하면 저장소 루트의 `CLAUDE.md`와 `AGENTS.md`가 자동으로 적용된다. 응답 규칙은 한국어, 계획 후 승인, 작업 스텝 단위 보고이다.
- PoC 도구의 실행 방법은 [../tools/README.md](../tools/README.md)에 있다.

## 6. 지켜야 할 규칙

1. `dataset.csv`의 `split=eval` 행은 6단계 측정 전까지 열지 않는다. 룰, 질문, 임계값은 design 데이터와 사전 정의로 정한다.
2. API 키를 파일, 로그, 대화에 붙여넣지 않는다.
3. 패턴 필터와 판정을 붙이기 전에는 `run_batch.py`로 공격 문장을 Gemini에 보내지 않는다. 무료 티어는 입력을 제품 개선에 쓰고 사람이 검토할 수 있다(R8, R11). 지금의 `run_batch.py`는 서버가 측정 모드가 아니면 시작 전에 중단하고, 제한 유형(`disallowed_content`, `bias_injection`)과 영어 hard negative와 `split=eval`은 기본으로 보내지 않는다(제한을 푸는 옵션 없음, eval은 `--split eval`로만).
4. 확정한 합격 기준은 낮추지 않는다. 부족하면 점검 문장을 늘린다.
5. 외부 LLM이 낸 결과는 코드나 실험으로 검증한 뒤 채택한다. 지금까지 두 번 모두 검증 후 결론이 달라졌다.
6. 결정이 생기면 그 자리에서 [DECISIONS.md](../DECISIONS.md)에 후보, 선택, 이유를 기록한다.
7. 금지 콘텐츠 유형(`disallowed_content`)과 영어 hard negative는 외부 서비스로 보내지 않는다.
8. 사용자는 코드를 직접 쓰지 않고 읽기, 테스트 읽기, 한 글자 실험, 검수로 이해한다(2026-10-27 합의). 핵심 코드(`BlockDecider`, `InputValidator`, `PatternFilter`, `JevJudge`, `ChatGateway`, `GatewayLogger`)는 작업이 끝날 때마다 코드 읽기 가이드(역할, 흐름, 확인 질문)를 주고, 이해 확인 뒤에 노션 정리본(코드 전문 없는 형식)을 준다. 정리본 전달 완료: `BlockDecider`, `JevJudge`. 가이드 전달, 확인 대기: `ChatGateway`, `GatewayLogger`.
9. 개발 방식(constitution 5절): 명세 → 테스트 → 코드 순서이다. 명세나 설계에 없는 동작이 필요하면 코드보다 먼저 `spec.md`와 `design.md`를 고치고 승인을 받는다. TDD는 빈 껍데기를 먼저 만들어 테스트가 기대값 불일치로 실패하는 것을 확인한 뒤(컴파일 오류는 빨강으로 치지 않는다) 최소 코드로 통과시킨다. 각 작업 보고에 빨강 출력을 포함한다.

## 7. 문서 지도

| 문서 | 질문 | 수명 |
|---|---|---|
| [constitution.md](constitution.md) | 바뀌지 않는 원칙 | 영구 |
| [domain-policy.md](domain-policy.md) | 무엇을 허용하고 금지하는가 | 영구 |
| [spec.md](spec.md) | 무엇을 만드는가 (요구사항, 수용 기준, API 계약) | 영구, 확정 |
| [design.md](design.md) | 어떻게 만드는가 (질문 문구, 약점 메모, 구조는 작성 예정) | 영구, 작성 중 |
| [rule-criteria.md](rule-criteria.md) | 패턴 필터에 패턴을 넣는 기준 | 영구 |
| [open-risks.md](open-risks.md) | 무엇이 불확실한가 (R1~R12) | 누적 |
| [../DECISIONS.md](../DECISIONS.md) | 왜 그렇게 정했는가 | 누적 |
| [poc/](poc/) | PoC 데이터와 결과 | 보존 |
| [학습노트/](학습노트/) | 1단계, 3단계 학습 기록 | 참고 |
| [archive/프로젝트_정리.md](archive/프로젝트_정리.md) | 변경 전 로드맵. 6~9단계(측정, 실패 분석, 로그·레이트리밋, 화면)의 정의는 여기를 본다. 4~5단계는 패턴 필터 0개와 Jev로 대체됨 | 보관, 갱신 안 함 |
| [archive/AI보안_프로젝트_주제_후보.md](archive/AI보안_프로젝트_주제_후보.md) | 주제 후보 12개 비교 (선정 완료) | 보관 |
| [archive/rule-selection.md](archive/rule-selection.md) | 룰 선정표 (종료, 근거 문장 보존) | 보관 |
| [archive/review-sheet.md](archive/review-sheet.md) | 시험 문장 판정 기록 (도메인 문장 원본 포함) | 보관, 데이터 파일화 때 참고 |
| [archive/llm_guardrail_rule_filters.md](archive/llm_guardrail_rule_filters.md) | 불채택된 외부 LLM 룰 후보 | 보관 |

## 8. 작업용 파일이 있는 곳

- PoC 도구: `tools/poc/`, PoC 데이터와 결과: `docs/poc/` (`data.tsv`는 도메인 문장 184개의 ID와 원문이 있는 유일한 합본이다.)
- 정규식 시험 하네스: `tools/rulecheck/`
- 이전 세션의 임시 작업 폴더(컴퓨터의 임시 폴더)는 이 컴퓨터에만 있으므로 의존하지 않는다.

## 9. 부록: 개발사 문의 메일 (발송 2026-10-27, 답장 2026-10-28)

답장 요지: 개별 사용 사례를 검토하거나 승인할 법적 여력이 없으니 AUP와 MCA를 참조하라는 내용이다. 승인도 거절도 아니다. AUP와 MCA 원문을 직접 읽어 대조한 결과와 결정은 [open-risks.md](open-risks.md) R8과 DECISIONS.md에 있다. 6단계 측정 전에 두 약관(수정일 2026-09-23)을 다시 읽는다.

아래는 발송한 메일이다.

받는 주소는 계정·API 문의용 support@typesafe.ai이다(privacy, security, sales 주소는 용도가 다르다). 질문은 약관 허용 여부 하나로 줄였다.

> Subject: Question about acceptable use: classifying adversarial prompts with Jev
>
> Hello,
> I am an aspiring developer building a non-commercial portfolio project for learning: a guardrail gateway for an in-company HR helpdesk chatbot. It uses Jev (Noul questions) only to classify incoming user text as an attack attempt or not. I send a small set of adversarial and benign sample sentences (about 200, mostly Korean) as classification input. I never use Jev to generate content, and I do not try to make Jev break its own policies.
> Is this use consistent with your Acceptable Use Policy (section 3.4)?
> Thank you.
