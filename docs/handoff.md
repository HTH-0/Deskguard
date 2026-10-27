# 이어서 작업하기

다른 컴퓨터나 새 세션에서 이어서 작업할 때 가장 먼저 읽는 문서이다. 세션을 마칠 때마다 갱신한다.

기준일: 2026-10-26

## 1. 한눈에 보기

- 프로젝트: Deskguard. 사내 HR 헬프데스크 챗봇으로 들어가는 프롬프트를 패턴 필터와 판정 모델로 검사해 프롬프트 인젝션과 도메인 정책 위반을 차단하는 게이트웨이이다. ([README.md](../README.md))
- 현재 위치: 도메인 정책, 평가 데이터, 명세서가 확정되었고 판정 모델(Jev) 채택과 질문 v2(조건부 채택)와 설계서 확정까지 끝났다. 다음은 3단계 코드의 특성화 테스트(스텝 7)이다.
- 기술: Spring Boot 4.1.1, Java 17, Maven. 응답 모델은 Gemini(`gemini-3.6-flash`, 무료 티어), 판정 모델 후보 1순위는 Jev(`jev-1.14.0`)이다.
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
| 7 | 3단계 코드의 특성화 테스트 | 대기 |
| 8 | 작업 목록과 TDD 구현 | 대기 |
| 6 | 탐지율·오탐율 측정 (eval 사용) | 대기 |

## 3. 사용자 결정 대기 목록

| 번호 | 항목 | 권장 | 근거 |
|---|---|---|---|
| 1 | Jev를 판정 모델로 채택 | 승인함(2026-10-27) | [DECISIONS.md](../DECISIONS.md) "Jev 채택, 라벨 기준 A1" |
| 2 | 라벨 기준 A1("챗봇에게 처리를 요구하는가"와 "규정·가능 여부를 묻는가")과 A-15, A-18, B-13, B-02 처리 | 확정함(2026-10-27). 구체성 기준 추가, B-02는 문장 수정 | [domain-policy.md](domain-policy.md) 4장 |
| 3 | 점검 문장 확장(65문장)과 합격 기준 N1~N6 | 완료. 65문장 시험, v2 조건부 채택 | design.md 4.3, `docs/poc/data_n.tsv` |
| 4 | 개발사 문의 메일(약관 R8) | 발송함(2026-10-27). 답변이 오면 R8 갱신 | 이 문서 9절 |
| 5 | 저장소를 공개로 바꾸기 전 점검(R12) | 공개 전에 수행 | [open-risks.md](open-risks.md) R12 |
| 6 | 도메인 문장의 최종 데이터 파일화(컬럼 구성, 출처·검수 기록) | 설계서 이후 | [archive/review-sheet.md](archive/review-sheet.md), `docs/poc/data.tsv` |
| 7 | 사용자 작성 문장의 IT 편중과 어휘 반복 보정 | 선택 | DECISIONS.md "F-2 사용자 작성 문장 확정" |

## 4. 다음 작업 (제안 순서)

1. 남은 결정(3절의 4~7번)은 설계서와 병행하며 급하지 않다.
2. 설계서(스텝 5)는 확정되었다. [design.md](design.md) 5~9절을 따른다. 판정 호출은 `RestClient`, 타임아웃 연결 2초와 읽기 3초, 재시도는 429·529만 2회이다.
3. 3단계 코드의 특성화 테스트(스텝 7): `mvn test`가 통과하는지 확인하고, 현재 동작을 테스트로 고정한다. 테스트 의존성은 Spring Boot 4에서 모듈이 나뉘었을 수 있으므로 컴파일로 확인한다. 설계서 9절의 O1(응답 본문에 Gemini 키가 노출되는지)도 이때 확인한다.
4. 작업 목록(`tasks.md`)을 쓰고 TDD로 구현한다(스텝 8). 첫 후보는 임계값 경계 시험(AC-3.6~AC-3.8)이다.
5. 6단계 측정은 eval 문장을 처음 여는 단계이다. 그 전에 질문 v2와 임계값을 확정한다.

## 5. 새 컴퓨터에서 시작하기 (Windows 기준)

필요한 것: Git, JDK 17(Temurin), Maven 3.9.x, Python 3(`backend/scripts/run_batch.py`용), PowerShell

```powershell
git clone https://github.com/HTH-0/Deskguard.git
cd Deskguard

# 이 저장소에만 적용하는 커밋 정보 (전역 설정은 건드리지 않는다)
git config user.name "HTH-0"
git config user.email "192852807+HTH-0@users.noreply.github.com"

# API 키는 저장소에 없다. 각 콘솔에서 발급해 환경변수로만 설정한다 (새 터미널부터 적용)
setx GEMINI_API_KEY "발급받은_키"
setx TYPESAFE_API_KEY "발급받은_키"

# 빌드 확인 (현재 테스트는 0건)
cd backend
mvn -B -q test
```

- Gemini 키: aistudio.google.com, Jev 키: https://console.typesafe.ai/keys
- 저장소는 비공개이므로 clone과 push에 GitHub 로그인이 필요하다.
- Claude Code로 작업하면 저장소 루트의 `CLAUDE.md`와 `AGENTS.md`가 자동으로 적용된다. 응답 규칙은 한국어, 계획 후 승인, 작업 스텝 단위 보고이다.
- PoC 도구의 실행 방법은 [../tools/README.md](../tools/README.md)에 있다.

## 6. 지켜야 할 규칙

1. `dataset.csv`의 `split=eval` 행은 6단계 측정 전까지 열지 않는다. 룰, 질문, 임계값은 design 데이터와 사전 정의로 정한다.
2. API 키를 파일, 로그, 대화에 붙여넣지 않는다.
3. 패턴 필터와 판정을 붙이기 전에는 `run_batch.py`로 공격 문장을 Gemini에 보내지 않는다. 무료 티어는 입력을 제품 개선에 쓰고 사람이 검토할 수 있다(R8, R11).
4. 확정한 합격 기준은 낮추지 않는다. 부족하면 점검 문장을 늘린다.
5. 외부 LLM이 낸 결과는 코드나 실험으로 검증한 뒤 채택한다. 지금까지 두 번 모두 검증 후 결론이 달라졌다.
6. 결정이 생기면 그 자리에서 [DECISIONS.md](../DECISIONS.md)에 후보, 선택, 이유를 기록한다.
7. 금지 콘텐츠 유형(`disallowed_content`)과 영어 hard negative는 외부 서비스로 보내지 않는다.

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

## 9. 부록: 개발사 문의 메일 (발송 완료, 2026-10-27)

받는 주소는 계정·API 문의용 support@typesafe.ai이다(privacy, security, sales 주소는 용도가 다르다). 질문은 약관 허용 여부 하나로 줄였다.

> Subject: Question about acceptable use: classifying adversarial prompts with Jev
>
> Hello,
> I am an aspiring developer building a non-commercial portfolio project for learning: a guardrail gateway for an in-company HR helpdesk chatbot. It uses Jev (Noul questions) only to classify incoming user text as an attack attempt or not. I send a small set of adversarial and benign sample sentences (about 200, mostly Korean) as classification input. I never use Jev to generate content, and I do not try to make Jev break its own policies.
> Is this use consistent with your Acceptable Use Policy (section 3.4)?
> Thank you.
