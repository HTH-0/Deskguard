# 작업 일지

[handoff.md](handoff.md)가 "지금 어디이고 다음에 무엇을 하는가"를 짧게 적는다면, 이 문서는 "그동안 무엇을 왜 했고, 지금 코드가 어떻게 생겼고, 이어서 할 때 무엇을 조심해야 하는가"를 적는다. 결정의 상세한 근거는 [../DECISIONS.md](../DECISIONS.md)에 있다.

## 2026-10-29 (클라우드 세션, 브랜치 `claude/compassionate-pascal-nw40fb`)

### 1. 시간순 요약

| 순서 | 한 일 | 결과 | 커밋(대표) |
|---|---|---|---|
| 1 | 오래된 `main`에서 시작해 T10~T12를 다시 만들었다 | 나중에 집 컴퓨터 브랜치(`claude/upbeat-cori-pi0rtq`, 10-28)가 이미 T10~T13을 끝낸 것을 발견했다 | – |
| 2 | 두 브랜치 병합 | 집 컴퓨터 쪽을 기준으로 삼고, 클라우드 쪽에만 있던 개선 4가지(500 오류 로그의 키 노출 수정, 측정 모드의 같은 컴퓨터 제한과 헤더 불신, 로그 수준, 이스케이프)를 옮겼다. 강제 푸시 승인받음 | `368850e`, `66996c3`, `7b3abbc`, `2f519dd` |
| 3 | SDD와 TDD 병행을 방법론으로 선언 | README, `constitution.md` 5절, `handoff.md`에 명시 | `2f519dd` |
| 4 | HR 도메인 평가(6-3) 준비 | 생성기 3종(ChatGPT, 무관한 Claude 새 대화, Gemini) 지시문, 후보 302개 수집, 자동 필터, 블라인드 라벨링 시트와 버튼식 라벨링 페이지(Artifact) | `e1e3e71` ~ `6cabea3` |
| 5 | 주제 재검토 | 사용자가 "결론이 단순하다", "챗봇에 데이터가 없는데 왜 막는가", "처음 구상은 회사 직원이 외부 AI에 보내는 문장을 검사하는 것"이라고 짚었다 | – |
| 6 | **주제 재정의(B안)** | 사내 생성형 AI 사용의 정보 유출 방지 게이트웨이. HR 평가와 라벨링은 중단 | `3362654` |
| 7 | 회고 | "SDD인데 왜 방향이 틀어졌나": 처음 구상이 문제 정의서로 적히지 않았다. 예방책으로 문제 정의서를 최상위 문서로 둔다 | `e3dc2d3` |
| 8 | 1일차: 기획 | 문제 정의서, 위협 모델과 데이터 정책, 명세 v2. HR 문서는 `archive/`로 | `b04e472` ~ `d4663d5` |
| 9 | 2일차: 탐지 | 설계 v2, 더미 회사(처음엔 가온누리테크, 사용자 요청으로 HTH로 변경), D1·D2·D3 탐지기, 가짜 값 생성기와 교차 확인 | `7deaa69` ~ `7d01b37` |
| 10 | 3일차: 게이트웨이 | 처리 결정, 가림과 복원, 게이트웨이 재조립, 감사 로그, 운영 경로에서 Jev와 패턴 필터 삭제 | `5ea869d`, `5cea104` |
| 11 | 4일차: 포털 | 포털 화면, 시연용 응답 모델, 가림 응답에 보낸 문장(AC-6.4). 인수인계 문서 재작성 | `480ac32` |

### 2. 주제가 바뀐 과정 (다음 세션이 헷갈리지 않도록)

| 시기 | 주제 | 지금 상태 |
|---|---|---|
| ~10-26 | 일반 LLM 프롬프트 인젝션 방어 | 데이터(`dataset.csv`)만 남음 |
| 10-26 ~ 10-29 | 사내 HR 헬프데스크 챗봇의 입력 가드레일(Jev 판정, F1~F4) | **중단.** 문서는 `docs/archive/*-hr.md`, 코드는 git 기록(커밋 `5cea104` 이전) |
| 10-29 ~ | **사내 생성형 AI 사용의 정보 유출 방지 게이트웨이(D1~D3, 허용/가림/경고/차단)** | **현재.** 최상위 문서는 `docs/problem.md` |

### 3. 지금 코드 지도 (`backend/src/main/java/com/promptgateway/`)

| 패키지 | 파일 | 하는 일 |
|---|---|---|
| `detect` | `PersonalDataDetector` | D1: 주민·외국인등록번호(생년월일, 성별 숫자), 휴대전화(예시 값 제외), 이메일(예시 도메인 제외), 계좌(은행 문맥), 카드(Luhn) |
| | `SecretDetector` | D2: API 키 형식, 개인 키, 비밀번호 할당, 접속 문자열(비밀번호에 `@`가 있어도), JWT. 자리 표시자 제외 |
| | `DictionaryDetector` | D3: 회사 사전(코드명, 고객사, 등급 표시) |
| | `DetectionEngine` | 탐지기 묶음, 허용 목록 제외, 겹침 정리(긴 것 우선, D2 > D3 > D1) |
| | `ActionDecider` | 처리 결정: D2·등급 표시 → 차단, D1 20개 이상 → 경고, 그 밖의 탐지 → 가림, 없음 → 허용 |
| | `Item`, `Category`, `Finding`, `FindingCount`, `Action` | 탐지 항목과 결과 값 |
| `mask` | `Masker`, `MaskResult`, `RestoreResult` | 가명 `[전화1]`으로 바꾸기, 같은 값 같은 가명, 원문의 가명 모양 피하기, 변형된 가명도 복원 |
| `gateway` | `ChatGateway` | 검증 → 탐지 → 결정 → 가림 → 외부 AI → 복원 → 로그. 경고는 확인하면 가림으로 보냄. 측정 처리는 외부 AI 없음 |
| | `GatewayResult`, `InputValidator`(4,000자) | 결과 값, 입력 검증 |
| `log` | `GatewayLogger` | 요청당 한 줄: 처리, 범주별 개수, 길이와 해시, 지연. 원문과 값 없음 |
| `controller` | `ChatController` | 허용·가림·경고 200, 차단 403, 측정 모드는 같은 컴퓨터에만 |
| `dto` | `ChatRequest`(`prompt`, `confirmed`), `ChatResponse`(`sentPrompt` 포함), `MeasurementResponse`(위치, 가린 문장, 지연) | 요청과 응답 형식 |
| `service` | `GeminiService`, `DemoAnswerGenerator` | 외부 AI. `gateway.demo-answer=true`이면 시연용 |
| `config` | `GatewayConfig`, `GatewayProperties`, `DetectionProperties`, `CompanyProperties` | 부품 조립과 설정 검증. 회사 사전은 `resources/company/dictionary.yml` |
| `exception` | `GlobalExceptionHandler` | 오류 형식. 외부 응답 오류는 상태 코드와 예외 종류만 로그에 남김 |

- 화면: `backend/src/main/resources/static/index.html`
- 측정 도구(Python, `backend/scripts/`): `fake_values.py`(가짜 값, 새 주제용), `run_batch.py`와 `domain_candidates.py`(옛 HR 형식, 5일차에 바꾸거나 보관)
- 시험: Java 107건, 교차 확인 표본 `src/test/resources/fake_values_sample.tsv`(생성기와 탐지기가 어긋나면 깨진다)

### 4. 이어서 할 때 조심할 것

1. **브랜치:** `main`에서 시작한다(2026-10-29 `main`을 최신으로 갱신하고 옛 작업 브랜치를 정리했다).
2. **JDK:** 프로젝트는 Java 25이다. 클라우드 컨테이너는 JDK 21이라 그대로는 빌드가 실패한다(`release version 25 not supported`). `handoff.md` 5절의 방법을 쓴다.
3. **키:** 2026-10-29 세션의 환경변수에는 `GEMINI_API_KEY`, `TYPESAFE_API_KEY`가 없었다. 시험과 시연, X·Y·T 측정은 키 없이 된다. Z 측정만 키가 필요하다.
4. **옛 Python 시험:** `test_run_batch.py`와 `test_domain_candidates.py`는 HR 시절 형식으로 통과한다. 새 측정 응답 형식과 맞지 않으므로 5일차에 바꾼다.
5. **삭제된 코드:** Jev 호출(`JevJudge`)과 패턴 필터는 운영 코드에서 삭제했다. Z 채점에 Jev 호출 코드가 필요하면 커밋 `5ea869d`(삭제 직전)의 `backend/src/main/java/com/promptgateway/judge/jev/JevJudge.java`를 참고하되, 측정 경로는 Python으로 새로 짠다.
6. **HR 자료:** `docs/eval/`의 후보 302개, 라벨링 시트, 라벨링 페이지(https://claude.ai/artifact/EENrSdwa2pJA8KJS4GhxsX)는 HR 시절 자료이며 쓰지 않는다. 사용자가 라벨링한 2건만 있다. `docs/eval/raw/`의 CSV는 의도 라벨이 들어 있으니 열지 않아도 된다(이제 블라인드가 필요 없지만 쓰지 않는다).
7. **보관 태그 없음:** 병합 때 만든 로컬 태그 `backup/compassionate-2026-10-29`는 클라우드 세션에만 있었고 푸시하지 않았다. 버린 코드(오늘 다시 만든 T10~T12)는 필요 없다.
8. **결정 대기:** Z 측정 위치(키), `constitution.md` 확정, 확장 후보 우선순위, 공개 전 점검(R12). `handoff.md` 4절.

### 5. 배운 점

- **문제 정의를 먼저 적는다.** SDD는 "명세대로 만들었나"는 지키지만 "옳은 것을 명세했나"는 지키지 못한다. 처음 구상 문장을 문제 정의서에 그대로 적고, 범위를 바꿀 때마다 대조한다.
- **세션 시작 때 원격 브랜치를 확인한다.** 두 컴퓨터에서 같은 작업을 따로 하는 일이 생겼다.
- **시험이 잡은 결함:** 500 오류 로그의 키 노출, 자리 표시자 규칙이 실제 약한 비밀번호를 놓침, 비밀번호에 `@`가 든 접속 문자열을 놓침(교차 확인 시험). 모두 구현 전에 시험으로 재현하고 고쳤다.
- **사용자 지적이 설계를 바꿨다:** 측정 응답의 정보 노출(같은 컴퓨터 제한), 헤더 위조(Burp Suite), 결론의 단순함(주제 재정의), 후보를 여유 있게 뽑기.

## 2026-10-29 후반 (클라우드 세션, 브랜치 `claude/inspiring-shannon-xbhl20`)

### 한 일

| 순서 | 한 일 | 결과 |
|---|---|---|
| 1 | 브랜치 정리 | `main`을 최신으로 당기고, `upbeat-cori`와 `compassionate-pascal`은 사용자가 웹에서 삭제. 이후 작업은 `inspiring-shannon`에 쌓았다 |
| 2 | 5일차: 측정 도구 v2(`measure.py`), 명세 AC-9.4와 AC-10.1~10.3 보완, 평가 세트 생성기, 틀 3종 수신, 평가 세트 596문장 | 동결 후 X·Y·T 측정: D1 98.1%, D2 81.7%, D3 100%, Y 0/106. 놓침은 비밀번호(값만 있는 형태)와 계좌(문맥어 없음) |
| 3 | Z 도구(`z_measure.py`)와 명세 AC-10.4, 10.5 | 가짜 서버로 시험 통과. 실제 호출은 사용자 컴퓨터에서(`handoff.md` 3절) |
| 4 | 우회 시험(소량) | 대조 24/24, 변형 96개 중 25개 탐지(26%). 접속 문자열 변형은 일부만 가려지는 경우도 있었다 |
| 5 | R12 점검, 출처 표기(`THIRD_PARTY_NOTICES.md`), README 정리 | 저장소는 공개 상태. 실제 키와 개인정보는 없음. 작업이 끝나면 비공개로 전환 예정 |
| 6 | 코드 읽기 1일차 | `docs/code-reading/day1.md`, 진행은 `progress.md` |

### 이어서 할 때 조심할 것

1. **브랜치:** `claude/inspiring-shannon-xbhl20`에서 작업한다. `main`은 오래되었다.
2. **클라우드 환경:** 컨테이너는 JDK 21이다. Temurin(GitHub, Adoptium)은 프록시가 막아서, Oracle JDK 25(`download.oracle.com/java/25/latest/jdk-25_linux-x64_bin.tar.gz`)를 세션 임시 폴더에 받아 `JAVA_HOME`으로 지정했다. 환경변수에 API 키가 없고, `ai.google.dev` 같은 일부 호스트는 네트워크 허용 목록에 없다.
3. **동결 파일을 바꾸지 않는다:** `docs/eval/set/freeze.json`, `freeze_bypass.json`과 거기에 적힌 해시 대상(평가 세트, 우회 세트, `detect/` 코드, `dictionary.yml`, `application.yml`). 바꾸면 이미 한 측정과 비교할 수 없다. 탐지 규칙 개선은 새 문장으로 검증한 뒤에만 한다.
4. **측정 도구를 고쳐도 되는 것과 안 되는 것:** `measure.py` 같은 도구(보고 방식)는 고칠 수 있지만, 고친 뒤 같은 세트로 다시 측정해 숫자가 같은지 확인한다(우회 시험 때 이렇게 했다).
5. **공개 상태에서는** 키, 실제 개인정보, 실행 로그를 올리지 않는다. Z 결과(`docs/eval/results/z/`)는 가짜 데이터라서 올려도 된다.
6. **셸에서** `pkill -f`처럼 넓은 패턴으로 프로세스를 죽이면 자신의 셸 명령도 죽는다(이번에 한 번 겪음). 서버는 PID 파일로 종료한다.
7. **보고서의 숫자를 쓰기 전에 코드로 확인한다.** 이번에도 문장으로 적은 건수(접속 문자열 가림 3건, 계좌 문맥어 분포)가 틀려서 고쳤다.

