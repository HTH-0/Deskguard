# 이어서 작업하기

다른 컴퓨터나 새 세션에서 이어서 작업할 때 가장 먼저 읽는 문서이다. 세션을 마칠 때마다 갱신한다. 그동안 무엇을 왜 했는지, 지금 코드가 어떻게 생겼는지, 이어서 할 때 조심할 점은 [worklog.md](worklog.md)에 있다. 주제 재정의 이전(HR 헬프데스크 챗봇)의 인수인계 문서는 [archive/handoff-v1-hr.md](archive/handoff-v1-hr.md)에 보관했다.

기준일: 2026-10-29 (일주일 일정의 1일차에 4일차 작업까지 끝냄)

## 1. 한눈에 보기

- **프로젝트:** Deskguard. 회사 직원이 ChatGPT 같은 외부 AI에 보내는 프롬프트를 나가기 전에 검사하는 정보 유출 방지 게이트웨이와 사내 AI 포털이다. 왜 만드는지는 [problem.md](problem.md)(최상위 문서)에 있다.
- **처리:** 개인정보(D1), 자격증명(D2), 회사 기밀(D3)을 게이트웨이 안의 코드로 탐지하고, 허용, 가림(가명으로 바꿔 보내고 답에서 되돌림), 경고(확인 후 가려서 보냄), 차단으로 처리한다. 운영 경로는 외부 판정 서비스를 부르지 않는다.
- **작업 브랜치:** `main`이 기준이다(2026-10-29 정리, 이전 작업 브랜치는 모두 `main`에 포함됨). 클라우드 세션은 지정된 세션 브랜치에서 작업하고, 작업이 끝나면 `main`에 병합한다.
- **개발 방식:** SDD와 TDD를 병행한다. 기획은 SDD(문제 정의 → 정책 → 명세 → 설계 → 작업 목록), 구현은 TDD(수용 기준 → 실패하는 시험 → 코드)이다. 각 작업의 첫 스텝은 명세 점검이고, 컴파일 오류는 빨강으로 치지 않는다.
- **기술:** Spring Boot 4.1.1, Java 25 LTS, Maven, Python 3(측정 도구). 외부 AI는 Gemini(`gemini-3.6-flash`). Jev는 측정 경로에서 Z 채점에만 쓴다(5~6일차).

## 2. 진행 상태 (일주일 계획, [tasks.md](tasks.md) 맨 위 표)

| 일차 | 작업 | 상태 |
|---|---|---|
| 1 | 문제 정의서, 위협 모델과 데이터 정책, 명세 v2, HR 문서 보관 | 완료 |
| 2 | 설계 v2, 더미 회사(HTH), D1·D2·D3 탐지기, 가짜 값 생성기 | 완료 |
| 3 | 처리 결정, 가림과 복원, 게이트웨이 재조립, 감사 로그, 운영 경로에서 Jev 제거 | 완료 |
| 4 | 포털 화면, 시연용 응답 모델, 가림 응답에 보낸 문장(AC-6.4) | 완료 |
| 5 | 평가 세트 생성과 동결, X·Y·T 측정, Z 준비 | **다음** |
| 6 | Z 측정(Jev 검증 후), 우회 소량 시험, README와 결과 | 대기 |
| 7 | 여유분, `main` 병합 검토 | 대기 |

- **시험:** Java 109건이 통과한다(`cd backend`, `mvn -B test`). Python은 `cd backend/scripts`에서 `python -m unittest test_fake_values test_measure`로 실행한다. 옛 HR용 `run_batch.py`와 `domain_candidates.py`는 시험과 함께 `backend/scripts/archive_hr/`로 옮겼다(보관, 실행하지 않는다).
- **멈춘 지점:** 4일차 완료 직후. 모든 변경이 커밋, 푸시되어 있다. 작업 중인 파일은 없다.

## 3. 다음 작업: 5일차 (아직 승인 전)

1. **측정 도구 v2(완료, `measure.py`):** 새 측정 응답(`action`, `findings`, `spans`, `maskedPrompt`, `timings`)에 맞게 바꾸고, 정답(넣은 값의 항목과 위치)과 비교해 X(범주별 탐지율), Y(정상 문장의 오탐율), T(지연)를 계산한다. 옛 HR용 `domain_candidates.py`와 그 시험은 보관한다.
2. **업무 요청 틀 받기(사용자 작업, 약 15분):** 생성기 3종(ChatGPT, 프로젝트와 무관한 Claude 새 대화, Gemini)에 "자리 표시자가 있는 업무 요청 틀"을 받는 지시문을 새로 쓴다(예: `아래 명단 표로 정리해 줘: {명단}`). 기존 `docs/eval/generation_prompt.md`는 HR용이므로 보관한다.
3. **평가 세트 생성기:** 틀과 가짜 값(`fake_values.py`)을 합쳐 문장을 만들고, 넣은 값의 항목과 위치를 정답으로 저장한다. 민감 정보가 없는 정상 업무 문장(KOICA 실제 질문 25개와 생성기의 일반 요청)도 넣는다. 대량 기준 20과 함께 해시로 동결한다.
4. **X·Y·T 측정:** 측정 모드 서버(`--gateway.measurement-mode=true`)에 보낸다. 외부 호출이 없으므로 키가 필요 없다.
5. **Z 준비:** 가림 문장마다 원문 답(A)과 가림 후 복원 답(B)을 Gemini로 받는 도구, Jev 채점 도구, 사람 판정 40~50쌍(라벨링 페이지 재사용)을 준비한다. Gemini와 Jev 키가 필요하다.

## 4. 사용자 결정 대기

| 번호 | 항목 | 비고 |
|---|---|---|
| 1 | Z 측정(Gemini와 Jev 호출)을 어디서 실행할지 | 사용자는 클라우드 환경에도 키가 있다고 했으나 2026-10-29 세션의 환경변수에서는 찾지 못했다. 클라우드 환경 설정에 넣거나 집 컴퓨터에서 실행한다 |
| 2 | `constitution.md` 확정 | "초안" 상태. 1장은 problem.md가 대신한다고 적어 두었다 |
| 3 | 확장 후보의 우선순위 | 로컬 모델 문맥 판정(D3 문맥, D4), 브라우저 확장, 파일 업로드. 완성 뒤에 고른다 |
| 4 | 저장소 공개 전 점검(R12) | 공개 전에 수행 |

## 5. 새 컴퓨터에서 시작하기 (Windows 기준)

필요한 것: Git, JDK 25 LTS(Temurin), Maven 3.9.x, Python 3, PowerShell

```powershell
git clone https://github.com/HTH-0/Deskguard.git
cd Deskguard

# 기준은 main이다. 시작할 때마다 모든 원격 브랜치를 확인한다.
git fetch origin
git branch -r --sort=-committerdate --format="%(committerdate:short) %(refname:short)"
git checkout main
git pull origin main

# 이 저장소에만 적용하는 커밋 정보
git config user.name "HTH-0"
git config user.email "192852807+HTH-0@users.noreply.github.com"

# 시험 (키 없이 통과한다)
cd backend
mvn -B -q test
cd scripts
python -m unittest -v test_fake_values
```

**포털 시연 (키 없이):** `backend` 폴더에서 아래를 실행하고 브라우저로 http://localhost:8080 을 연다. 시연용 응답 모델은 외부 AI를 부르지 않고 받은 문장을 되돌려 준다. 예시 버튼으로 가림, 차단, 경고, 허용을 확인한다.

```powershell
mvn spring-boot:run "-Dspring-boot.run.arguments=--gateway.demo-answer=true"
```

**실제 외부 AI로 실행:** 환경변수 `GEMINI_API_KEY`를 설정하고 `mvn spring-boot:run`. 키는 aistudio.google.com에서 발급하고 파일이나 대화에 붙여넣지 않는다. Jev 키(`TYPESAFE_API_KEY`)는 Z 측정 때만 필요하다.

**측정 모드:** `--gateway.measurement-mode=true`. 외부 AI를 부르지 않고 탐지 결과, 위치, 가린 문장, 지연을 돌려준다. 같은 컴퓨터에서 보낸 요청에만 적용된다.

**클라우드 세션 참고:** 클라우드 컨테이너는 JDK 21이다. Temurin 25를 GitHub 릴리스(`github.com/adoptium/temurin25-binaries`)에서 세션 임시 폴더로 받아 `JAVA_HOME`으로 지정하면 JDK 25로 시험할 수 있다. Claude Code로 작업하면 `CLAUDE.md`와 `AGENTS.md`가 적용된다(한국어 응답, 계획 후 승인, 작업 스텝 보고). 새 세션의 첫 지시는 "docs/handoff.md를 읽고 현재 상태와 다음 작업을 알려 줘"면 된다.

## 6. 지켜야 할 규칙

1. 범위나 무대를 바꾸는 결정은 [problem.md](problem.md)와 맞는지 한 줄로 확인하고 [DECISIONS.md](../DECISIONS.md)에 후보, 선택, 이유를 기록한다.
2. 운영 경로는 외부 판정 서비스를 부르지 않는다(NFR-4). Jev는 측정 경로에서 가짜 데이터로만 쓴다.
3. 로그와 응답에 원문, 탐지한 값, 가명 대응표, API 키를 남기지 않는다(측정 모드의 위치와 가린 문장은 같은 컴퓨터에만).
4. 평가 데이터에는 실제 개인정보를 쓰지 않는다. 가짜 값은 `fake_values.py`로 만든다.
5. 평가 세트, 대량 기준, 탐지 규칙은 측정 전에 해시로 동결하고, 측정 결과를 보고 바꾸지 않는다.
6. API 키를 파일, 로그, 대화에 붙여넣지 않는다.
7. 외부 LLM이 낸 결과(Jev 채점 포함)는 검증한 뒤 채택한다(Z는 사람 판정과 85% 이상 일치).
8. 사용자는 코드를 직접 쓰지 않고 읽기, 시험 읽기, 검수로 이해한다. 코드 읽기는 구현을 먼저 진행하고 나중에 몰아서 한다(2026-10-29 결정). 노션 정리본 완료: `BlockDecider`, `ChatGateway`(옛 버전), `GatewayResult`(옛 버전), `ChatController`, `Harness`. 보류 목록: 새 구조의 `ChatGateway`, `PersonalDataDetector`, `SecretDetector`, `DetectionEngine`, `ActionDecider`, `Masker`, `GatewayLogger`, `InputValidator`. (`JevJudge`, `PatternFilter`는 삭제되어 읽을 필요가 없다.)
9. 세션을 시작하면 `git fetch origin`으로 모든 원격 브랜치를 먼저 확인한다(`CLAUDE.md` 0번).

## 7. 문서 지도

| 문서 | 질문 | 상태 |
|---|---|---|
| [problem.md](problem.md) | 왜, 누구를 위해 만드는가 | 확정 |
| [policy.md](policy.md) | 무엇으로부터 무엇을 지키고 어떻게 처리하는가 (위협 모델, 데이터 정책) | 확정 |
| [spec.md](spec.md) | 무엇을 만드는가 (명세 v2, 수용 기준) | 확정 |
| [design.md](design.md) | 어떻게 만드는가 (설계 v2) | 확정 |
| [dummy-company.md](dummy-company.md) | 가상 회사 HTH와 가짜 데이터 형식 | 확정 |
| [tasks.md](tasks.md) | 일주일 계획과 진행 상태 | 갱신 |
| [constitution.md](constitution.md) | 바뀌지 않는 원칙 | 초안(1장은 problem.md가 대신) |
| [open-risks.md](open-risks.md) | 무엇이 불확실한가 | 재정의 이전 기준, 6일차에 정리 |
| [../DECISIONS.md](../DECISIONS.md) | 왜 그렇게 정했는가 | 누적 |
| [worklog.md](worklog.md) | 무엇을 했고, 코드는 어떻게 생겼고, 무엇을 조심하는가 | 세션마다 추가 |
| [images/portal-demo.png](images/portal-demo.png) | 포털 시연 화면 | 4일차 |
| [archive/](archive/) | 재정의 이전 문서(HR 명세, 설계, 정책, 인수인계) | 보관 |
| [eval/](eval/) | 평가 데이터. 지금 있는 것은 HR 시절 자료(후보 302개, 라벨링 시트, KOICA 25개)이며, KOICA 25개는 정상 업무 문장으로 재사용한다 | 5일차에 정리 |
