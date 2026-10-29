# 이어서 작업하기

다른 컴퓨터나 새 세션에서 이어서 작업할 때 가장 먼저 읽는 문서이다. 세션을 마칠 때마다 갱신한다.

기준일: 2026-10-29

## 1. 한눈에 보기

- 프로젝트: Deskguard. 사내 HR 헬프데스크 챗봇으로 들어가는 프롬프트를 판정 모델로 검사해 프롬프트 인젝션과 도메인 정책 위반을 차단하는 게이트웨이이다. ([README.md](../README.md))
- 개발 방식: SDD와 TDD를 병행한다. 기획 단계는 SDD(명세서 → 설계서 → 작업 목록), 구현 단계는 TDD(수용 기준 → 테스트 → 코드)이다. ([DECISIONS.md](../DECISIONS.md) "개발 방법론: SDD와 TDD 병행", [constitution.md](constitution.md) 5절)
- 중심 서사: "LLM 챗봇 앞단의 입력 가드레일을 만들고, 사전에 정한 기준으로 측정하고, 놓친 사례와 한계를 기록했다." 포트폴리오의 가치는 구현보다 측정과 기록에 있다(DECISIONS.md "포트폴리오 방향 점검").
- **2026-10-29 주제 재정의(B안): 사내 생성형 AI 사용의 정보 유출 방지 게이트웨이로 방향을 바꿨다.** HR 도메인 정책과 6단계 도메인 eval 구축(라벨링 포함)은 중단했다. 새 문서: [problem.md](problem.md)(문제 정의서, 확정), [policy.md](policy.md)(위협 모델과 데이터 정책, 확정), [spec.md](spec.md)(명세 v2, 확정). 일정은 10-29 ~ 11-04 일주일이고 작업 순서는 [tasks.md](tasks.md) 맨 위 표이다. HR 기준의 명세, 설계, 도메인 정책은 `archive/`로 옮겼다(`spec-v1-hr.md`, `design-v1-hr.md`, `domain-policy-hr.md`). 아래 "현재 위치", 2절, 4절은 재정의 이전 기준이다. 근거는 DECISIONS.md "주제 재정의", "문제 정의서 확정, 일주일 일정과 범위 축소".
- **현재 위치**
  - 완료: 도메인 정책, 평가 데이터, 명세서(확정), Jev 한국어 PoC(채택), 질문 v2(조건부 채택), 설계서 1~9절(확정), 구현 T1~T13, 약관 위험 수용(R8), 외부 도메인 자료 조사.
  - **진행 중: 6단계 측정 설계 승인(2026-10-29). 6-3 도메인 후보 생성 완료(`docs/eval/raw/`, 302개). 다음은 6-3 필터와 블라인드 라벨링 시트 도구, 그리고 6-1 기준선 룰이다.**
  - 구현 상태: 게이트웨이(입력 검증, 패턴 필터(패턴 0개), Jev 판정, 임계값 0.5, 판정 실패 시 차단, 요청별 로그, 측정 모드)와 측정 도구(`run_batch.py`)가 동작한다. Java 테스트 157건과 Python 테스트 25건이 통과한다. 실제 Jev 연동은 2026-10-28 스모크 시험(T13)으로 확인했다.
  - 2026-10-29 브랜치 병합: 집 컴퓨터(10-28, `claude/upbeat-cori-pi0rtq`)와 클라우드 세션(10-29)이 같은 지점에서 따로 T10~T12를 만들었다. 집 컴퓨터 쪽을 기준으로 삼고, 클라우드 쪽에만 있던 개선 4가지(500 오류 로그의 키 노출 수정, 측정 응답의 같은 컴퓨터 제한과 헤더 불신, 로그 수준, 이스케이프 보강)를 옮겼다. **이제 작업 브랜치는 `claude/compassionate-pascal-nw40fb` 하나이다.** `claude/upbeat-cori-pi0rtq`는 여기에 모두 포함되었으므로 더 쓰지 않는다. `main` 병합은 작업이 어느 정도 진행된 뒤 사용자가 검토하고 한다.
  - 마지막으로 멈춘 지점: 병합을 마치고 **모두 푸시되어 있다.**
- 기술: Spring Boot 4.1.1, Java 25 LTS, Maven. 응답 모델은 Gemini(`gemini-3.6-flash`, 무료 티어), 판정 모델은 Jev(`jev-1.14.0`, 채택)이다.

## 2. 단계 상태

표의 번호는 작업 스텝 번호이며, 로드맵의 6~9단계(측정, 실패 분석, 로그와 레이트리밋, 화면)와 이름이 겹치는 곳이 있다.

| 단계 | 내용 | 상태 |
|---|---|---|
| 1~3 | API 테스트, 데이터셋, 프록시 골격 | 완료 |
| 2.4~2.9 | 주제 방향(도메인 정책 추가), 정책서, 룰 후보 검증, 정상 풀 보강 | 완료 |
| 3 | 명세서 (`spec.md`) | 완료, 확정. FR-10(6단계 측정)은 승인 대기 |
| 2.5 | Jev 한국어 PoC | 완료, 합격. Jev 채택 |
| 4.5 | PoC 약점 개선 (라벨 기준, 점검 문장 65개, 질문 v2) | 완료. v2를 현행 질문으로 조건부 채택(한계는 DECISIONS.md "질문 v2 조건부 채택") |
| 5 | 설계서 (`design.md`) | 1~9절 확정(2026-10-27). 10절(6단계 측정 설계)은 승인 대기 |
| 7 | 3단계 코드의 특성화 테스트 | 완료 |
| 8 | 작업 목록과 TDD 구현 | T1~T13 완료(10-29 병합 보강 포함), T14(문서 정리) 대기. [tasks.md](tasks.md) |
| 6 | 탐지율·오탐율 측정 (`eval` 사용) | 설계 승인 대기. 세부는 [tasks.md](tasks.md)의 "6단계 작업 목록" |
| 로드맵 7단계 | 놓친 사례 분석, judge injection 시험 | 6단계 뒤 |

## 3. 사용자 결정 대기 목록 (미결)

| 번호 | 항목 | 권장 | 근거 |
|---|---|---|---|
| 5 | 일정(지원 시점이나 마감) | 알려 준다. 빠듯하면 곁가지를 줄인다 | DECISIONS.md "포트폴리오 방향 점검" |
| 6 | `constitution.md` 확정 | 현재 "초안 (사용자 검토 전)". 검토 후 확정 | DECISIONS.md "개발 방식 점검과 명세 정정" |
| 7 | 확인용 채팅 화면(안 A 마지막, 안 B 즉시, 안 C 앞당김) | 완성 후 곁가지. 안 B 권장 | tasks.md "보류 목록" |
| 8 | 도메인 문장의 최종 데이터 파일화(컬럼 구성, 출처와 검수 기록) | 6-3과 병행 | [archive/review-sheet.md](archive/review-sheet.md), `docs/poc/data.tsv` |
| 9 | 사용자 작성 문장의 IT 편중과 어휘 반복 보정 | 선택 | DECISIONS.md "F-2 사용자 작성 문장 확정" |
| 10 | 저장소를 공개로 바꾸기 전 점검(R12) | 공개 전에 수행. 외부 자료의 출처 표시도 점검 | [open-risks.md](open-risks.md) R12 |
| 11 | KorSET 접근 신청 | **보류(2026-10-28, 메모만).** 재개 조건은 [research/domain-sources.md](research/domain-sources.md) 7절 | 같은 문서 |
| 12 | AI Hub 데이터 확인, RICoTA와 CAGE 라이선스 문의 | 선택. RICoTA는 사용하지 않고, CAGE는 문의 전에는 사용하지 않는다 | 같은 문서 5절 |

### 해결된 결정 (참고)

| 항목 | 결정 | 근거 |
|---|---|---|
| Jev 판정 모델 채택 | 승인(2026-10-27) | DECISIONS.md "Jev 채택, 라벨 기준 A1" |
| 라벨 기준 A1과 A-15, A-18, B-13, B-02 처리 | 확정(2026-10-27) | [archive/domain-policy-hr.md](archive/domain-policy-hr.md) 4장 |
| 점검 문장 65개와 합격 기준 N1~N6, 질문 v2 | v2 조건부 채택 | design.md 4.3, `docs/poc/data_n.tsv` |
| 개발사 문의(약관 R8) | 답장 수신, 승인은 받을 수 없어 위험을 수용하고 진행 | [open-risks.md](open-risks.md) R8, 이 문서 9절 |
| 측정 모드를 정식 배포 후에도 유지 | 유지(2026-10-29). 같은 컴퓨터 제한과 기본 꺼짐으로 위험이 작다 | DECISIONS.md "측정 모드 안전장치와 유지" |
| 정상 질문 약 17.6초 지연의 원인 | 응답 모델 구간이 대부분이다. 측정 모드에서 총 0.19~0.20초(첫 호출 0.71초) | DECISIONS.md "T10 GatewayLogger" |
| 완성의 정의와 6단계 베이스라인 포함 | 확정(2026-10-28) | DECISIONS.md "완성의 정의 확정" |
| 인프라 보강(CI, Docker, 헬스 체크) | 완성 후 곁가지 | tasks.md "보류 목록" |
| 6단계 측정 설계(FR-10, design 10절) | 승인(2026-10-29) | DECISIONS.md "6단계 측정 설계 승인" |
| 도메인 생성기와 후보 수 | ChatGPT, Claude(무관한 새 세션), Gemini 3종, 생성기당 100개(2026-10-29) | DECISIONS.md |
| 도메인 최종 세트 크기 | 최소 40/40, 상한 60/60, 균형을 지키는 최대 수(2026-10-29) | design.md 10.4 6번 |
| 모의 레드팀(사람이 쓴 공격 문장) | 폐기(2026-10-29). 외부 인원을 구하기 어렵다. 한계로 기록한다 | DECISIONS.md "모의 레드팀 폐기" |
| 현실성 세트 | KOICA 직원 질문 25개로 확정(2026-10-28) | design.md 10.8, [eval/README.md](eval/README.md) |
| 외부 문장의 사용 | 평가 세트에 그대로 넣지 않는다 | 이 문서 6절 10번 |

## 4. 다음 작업 (순서)

1. **사용자: 6단계 측정 설계 검토와 승인**([archive/spec-v1-hr.md](archive/spec-v1-hr.md) FR-10, [archive/design-v1-hr.md](archive/design-v1-hr.md) 10절). 3절의 1~4번을 함께 정한다.
2. **6-1 기준선 룰 확정**: `design` 데이터로만, 서버 호출 없이 로컬에서 한다. 룰 선정은 사용자가 한다(constitution 6장, [rule-criteria.md](rule-criteria.md)). 예전 후보 4종(P1~P4)의 정규식은 저장소에 없으므로 다시 정한다. PoC 결과(`docs/poc/result_184_K.csv`, `result_184_K2.csv`)에 질문별 점수 10개가 3회 반복으로 있어서 새 호출 없이 `design` 임계값 곡선을 계산할 수 있다(낙관적인 수치로 표시한다).
3. **6-2 분석 도구**: 중단한 작업이 [eval/paused_6_2/](eval/paused_6_2/)에 있다. README의 재개 방법을 따른다. v1 질문 파일(`judge/questions_v1.tsv`)과 일치 시험도 이 단계에서 만든다(design.md O5).
4. **6-3 도메인 `eval` 구축**: 생성 지시문 작성, 생성기로 후보 수집(사용자 작업), 자동 필터 도구, 블라인드 라벨링(사용자 작업), 층화 추출. 층화 틀은 ACCESS DENIED INC의 분류를 차용한다(research 4절).
5. **6-4 동결, 6-5 `eval` 실행(사용자 컴퓨터), 6-6 기록**. 실행 전에 AUP와 MCA를 다시 읽고 크레딧을 확인한다(R8, R9).
6. 로드맵 7단계(놓친 사례 분석, judge injection), 문서 마무리(스텝 8 T14, README를 결과 중심으로 다시 쓰기, 위협 모델 한 장).
7. 곁가지(인프라, 화면 등)는 완성 뒤에 [tasks.md](tasks.md)의 "보류 목록"에서 고른다. 완성이 끝나기 전에는 시작하지 않는다.

완성의 정의(2026-10-28 확정): T13, 6단계(베이스라인과 임계값 곡선 포함), 로드맵 7단계, 문서(README 재작성과 위협 모델).

### 멈추기 좋은 곳과 피할 곳

| 구간 | 멈춤 | 이유 |
|---|---|---|
| 지금(설계 승인 대기) | 매우 좋음 | 모든 변경이 푸시되어 있고 테스트가 통과한다 |
| 6-1, 6-2 완료 직후 | 좋음 | 독립적으로 완결된다 |
| 6-3 작성 도중 | 조심 | 작성 중인 파일을 수시로 커밋한다 |
| 6-4 동결 직후부터 6-5 실행 전 | 좋음 | 이후 변경 금지 시점이다 |
| **6-5 `eval` 실행 도중, 그리고 실행 후 결과를 커밋하기 전** | **피한다** | 일부만 측정되거나 결과가 사용자 컴퓨터에만 남는다. 한 번에 끝까지 하고 바로 커밋한다 |

## 5. 새 컴퓨터에서 시작하기 (Windows 기준)

필요한 것: Git, JDK 25 LTS(Temurin 25.0.5), Maven 3.9.x(3.9.16 사용), Python 3(3.15.2 사용), PowerShell

```powershell
git clone https://github.com/HTH-0/Deskguard.git
cd Deskguard

# 작업 내용은 작업 브랜치에 있다(main은 아직 오래된 상태). 시작할 때마다 모든 원격 브랜치를 확인한다.
git fetch origin
git branch -r --sort=-committerdate --format="%(committerdate:short) %(refname:short)"
git checkout claude/compassionate-pascal-nw40fb
git pull origin claude/compassionate-pascal-nw40fb
git log --oneline -5

# 이 저장소에만 적용하는 커밋 정보 (전역 설정은 건드리지 않는다)
git config user.name "HTH-0"
git config user.email "192852807+HTH-0@users.noreply.github.com"

# API 키는 저장소에 없다. 각 콘솔에서 발급해 환경변수로만 설정한다 (새 터미널부터 적용)
setx GEMINI_API_KEY "발급받은_키"
setx TYPESAFE_API_KEY "발급받은_키"

# 빌드와 테스트 확인 (Java 157건)
cd backend
mvn -B -q test

# 측정 도구 시험 (Python 25건, 서버와 키가 필요 없다)
cd scripts
python -m unittest -v test_run_batch
```

- Gemini 키: aistudio.google.com, Jev 키: https://console.typesafe.ai/keys. 키는 컴퓨터마다 따로 발급해도 된다(같은 계정이면 크레딧을 공유한다). 대화나 파일에 붙여넣지 않는다.
- 서버 실행: `backend` 폴더에서 `mvn spring-boot:run`. 두 키가 모두 있어야 시작된다. 측정 모드(개발용, Gemini를 부르지 않고 판정 결과를 200으로 돌려준다)는 PowerShell에서 `mvn spring-boot:run "-Dspring-boot.run.arguments=--gateway.measurement-mode=true"`로 켠다.
- 측정 도구(측정 모드 서버가 떠 있어야 한다): `backend/scripts` 폴더에서 `python run_batch.py --input ../../dataset.csv --output result.csv`. 기본은 design 분할만 보낸다. 스모크 시험 입력은 [poc/smoke_t13.csv](poc/smoke_t13.csv)이다.
- 요청 확인(PowerShell): `curl.exe`로 UTF-8 본문을 보내는 `Send-Chat` 함수를 새 창마다 다시 만들어야 한다. 함수 본문은 [../tools/README.md](../tools/README.md)의 "Send-Chat" 절에 있다.
- Windows에서 Python: python.org의 설치 관리자로 설치한다(3.15.2에서 시험). 설치 중 PATH 추가를 켠다. Microsoft Store의 `python.exe` 별칭(`WindowsApps` 경로)이 가짜 `python`으로 가로채면 설정, 앱, 고급 앱 설정, 앱 실행 별칭에서 `python.exe`와 `python3.exe`를 끈다. 3.13은 보안 수정 단계라 Windows 설치 파일이 없을 수 있다.
- Maven은 zip을 `C:\tools`에 풀고 `bin` 폴더를 PATH에 넣는다. 설치 후 새 터미널에서 `mvn -version`이 Java 25와 함께 나와야 한다.
- 클라우드 세션은 JDK 21이다. `mvn -B test -Djava.version=21`로 실행하거나(pom은 바꾸지 않는다), Temurin 25를 GitHub 릴리스(`github.com/adoptium/temurin25-binaries`)에서 세션 임시 폴더로 받아 `JAVA_HOME`으로 지정한다(2026-10-29에는 이 방법으로 JDK 25에서 확인했다). `api.adoptium.net`은 허용 도메인이 아니다.
- 조사에 쓴 외부 자료는 저장소에 없다. 2026-10-28에 **집 컴퓨터에서 작업한 세션**이 내려받았으므로, 남아 있다면 그 컴퓨터에만 있다. 다른 컴퓨터에서는 다시 받는다. 다시 받는 방법은 [research/domain-sources.md](research/domain-sources.md) 8절이다(공공데이터포털, 로그인 불필요). 클라우드 세션에서 외부 사이트를 열려면 환경 설정의 허용 도메인에 추가해야 한다(`www.data.go.kr`, `huggingface.co`, `arxiv.org`는 2026-10-28에 추가했다).
- 저장소는 비공개이므로 clone과 push에 GitHub 로그인이 필요하다.
- Claude Code로 작업하면 저장소 루트의 `CLAUDE.md`와 `AGENTS.md`가 자동으로 적용된다. 응답 규칙은 한국어, 계획 후 승인, 작업 스텝 단위 보고이다. 새 세션의 첫 지시는 "docs/handoff.md를 읽고 현재 상태와 다음 작업을 알려 줘"로 시작하면 된다.
- PoC 도구의 실행 방법은 [../tools/README.md](../tools/README.md)에 있다.

## 6. 지켜야 할 규칙

1. `dataset.csv`의 `split=eval` 행은 6단계 측정 전까지 열지 않는다. 룰, 질문, 임계값은 design 데이터와 사전 정의로 정한다.
2. API 키를 파일, 로그, 대화에 붙여넣지 않는다.
3. 패턴 필터와 판정을 붙이기 전에는 `run_batch.py`로 공격 문장을 Gemini에 보내지 않는다. 무료 티어는 입력을 제품 개선에 쓰고 사람이 검토할 수 있다(R8, R11). 지금의 `run_batch.py`는 서버가 측정 모드가 아니면 시작 전에 중단하고, 제한 유형(`disallowed_content`, `bias_injection`)과 영어 hard negative와 `split=eval`은 기본으로 보내지 않는다(제한을 푸는 옵션 없음, eval은 `--split eval`로만).
4. 확정한 합격 기준은 낮추지 않는다. 부족하면 점검 문장을 늘린다.
5. 외부 LLM이 낸 결과는 코드나 실험으로 검증한 뒤 채택한다. 지금까지 두 번 모두 검증 후 결론이 달라졌다.
6. 결정이 생기면 그 자리에서 [DECISIONS.md](../DECISIONS.md)에 후보, 선택, 이유를 기록한다. 범위나 무대를 바꾸는 결정은 문제 정의서(`docs/problem.md`, 작성 예정)와 맞는지 한 줄로 확인한다(2026-10-29 회고).
7. 금지 콘텐츠 유형(`disallowed_content`)과 영어 hard negative는 외부 서비스로 보내지 않는다.
8. 사용자는 코드를 직접 쓰지 않고 읽기, 테스트 읽기, 한 글자 실험, 검수로 이해한다(2026-10-27 합의). 핵심 코드(`BlockDecider`, `InputValidator`, `PatternFilter`, `JevJudge`, `ChatGateway`, `GatewayLogger`)는 작업이 끝날 때마다 코드 읽기 가이드(역할, 흐름, 확인 질문)를 주고, 이해 확인 뒤에 노션 정리본(코드 전문 없는 형식)을 준다. 정리본 전달 완료(노션 기준, 2026-10-29 사용자 확인): `BlockDecider`, `ChatGateway`, `GatewayResult`, `ChatController`, `Harness`. `JevJudge`는 예전에 읽은 것 같으나 노션에 기록이 없어 다시 진행한다.
   - 코드 읽기 보류 목록(2026-10-29 사용자 결정: 구현을 먼저 진행하고 코드 읽기는 나중에 몰아서 한다). 작업이 끝날 때마다 추가한다: `GatewayLogger`(T10과 10-29 보강), `JevJudge`, `InputValidator`, `PatternFilter`, `ChatController`의 측정 모드 부분(같은 컴퓨터 판단 `isLocal`과 헤더를 믿지 않는 이유), `run_batch.py`(T12).
9. 개발 방식(constitution 5절): SDD와 TDD를 병행한다. 명세 → 테스트 → 코드 순서이다. 각 작업의 첫 스텝은 명세 점검(해당 수용 기준만으로 테스트를 쓸 수 있는가)이다. 명세나 설계에 없는 동작이 필요하면 코드보다 먼저 `spec.md`와 `design.md`를 고치고 승인을 받는다. TDD는 빈 껍데기를 먼저 만들어 테스트가 기대값 불일치로 실패하는 것을 확인한 뒤(컴파일 오류는 빨강으로 치지 않는다) 최소 코드로 통과시킨다. 각 작업 보고에 빨강 출력을 포함한다.
10. 외부 자료(2026-10-28): 어떤 외부 문장도 평가 세트에 그대로 넣지 않는다. 씨앗으로 쓴 문장은 평가 세트에서 뺀다. 이용허락과 접근 조건을 지킨다(KorSET은 비상업과 접근 승인제). 직원 이름 같은 개인 정보가 있는 파일은 저장소에 넣지 않는다. 쓴 자료는 출처를 표시한다. 상세는 [research/domain-sources.md](research/domain-sources.md)이다.
11. 브랜치(2026-10-29): 세션을 시작하면 `handoff.md`를 읽기 전에 `git fetch origin`으로 모든 원격 브랜치의 최근 커밋을 확인한다. `main`이 최신이 아닐 수 있다. 여러 컴퓨터에서 작업하므로 작업 브랜치는 하나만 쓰고, 세션을 마칠 때 이 문서의 브랜치 이름을 갱신한다.

## 7. 문서 지도

| 문서 | 질문 | 수명 |
|---|---|---|
| [problem.md](problem.md) | 왜, 누구를 위해 만드는가 (최상위, 2026-10-29) | 영구, 확정 |
| [policy.md](policy.md) | 무엇으로부터 무엇을 지키고 어떻게 처리하는가 (위협 모델과 데이터 정책) | 영구, 초안 |
| [constitution.md](constitution.md) | 바뀌지 않는 원칙 | 영구, 초안(확정 대기) |
| [archive/domain-policy-hr.md](archive/domain-policy-hr.md) | 무엇을 허용하고 금지하는가 | 영구 |
| [spec.md](spec.md) | 무엇을 만드는가 (명세 v2, 수용 기준). v1은 archive/spec-v1-hr.md | 영구, v2 초안 |
| design.md | 어떻게 만드는가 (설계 v2, 2일차 작성). v1은 archive/design-v1-hr.md | 작성 예정 |
| [tasks.md](tasks.md) | 무엇을 어떤 순서로 하는가 (스텝 8 작업, 6단계 작업 목록, 보류 목록) | 진행 상태를 갱신 |
| [rule-criteria.md](rule-criteria.md) | 패턴 필터에 패턴을 넣는 기준 | 영구 |
| [open-risks.md](open-risks.md) | 무엇이 불확실한가 (R1~R12) | 누적 |
| [../DECISIONS.md](../DECISIONS.md) | 왜 그렇게 정했는가 | 누적 |
| [research/domain-sources.md](research/domain-sources.md) | 도메인 문구를 구할 외부 자료(직접 확인과 검색 요약, 활용 제안, KorSET 보류 메모, 다시 받는 방법) | 조사 기록 |
| [eval/README.md](eval/README.md) | 평가용 데이터 폴더(현실성 세트 원본, 중단한 6-2 보존본) | 측정 전에 동결 |
| [poc/](poc/) | PoC 데이터와 결과 | 보존 |
| [학습노트/](학습노트/) | 1단계, 3단계 학습 기록 | 참고 |
| [archive/프로젝트_정리.md](archive/프로젝트_정리.md) | 변경 전 로드맵. 6~9단계의 정의는 여기를 본다. 4~5단계는 패턴 필터 0개와 Jev로 대체됨 | 보관, 갱신 안 함 |
| [archive/AI보안_프로젝트_주제_후보.md](archive/AI보안_프로젝트_주제_후보.md) | 주제 후보 12개 비교 (선정 완료) | 보관 |
| [archive/rule-selection.md](archive/rule-selection.md) | 룰 선정표 (종료, 근거 문장 보존) | 보관 |
| [archive/review-sheet.md](archive/review-sheet.md) | 시험 문장 판정 기록 (도메인 문장 원본 포함) | 보관, 데이터 파일화 때 참고 |
| [archive/llm_guardrail_rule_filters.md](archive/llm_guardrail_rule_filters.md) | 불채택된 외부 LLM 룰 후보 | 보관 |

## 8. 작업용 파일이 있는 곳

- 게이트웨이 코드: `backend/src/main/`, 테스트: `backend/src/test/`. 측정 도구와 시험: `backend/scripts/`.
- PoC 도구: `tools/poc/`, PoC 데이터와 결과: `docs/poc/` (`data.tsv`는 도메인 문장 184개의 ID와 원문이 있는 유일한 합본이다. 질문별 점수는 `result_184_K.csv`, `result_184_K2.csv`에 있다).
- 정규식 시험 하네스: `tools/rulecheck/`
- 평가 데이터: `docs/eval/`(현실성 세트 원본, 중단한 6-2 보존본). 도메인 후보와 동결 기록은 6-3, 6-4에서 이 폴더에 만든다.
- 이전 세션의 임시 작업 폴더(컴퓨터의 임시 폴더)는 그 컴퓨터에만 있으므로 의존하지 않는다. 내려받은 외부 자료와 외부 저장소 복제본은 저장소에 없다.

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
