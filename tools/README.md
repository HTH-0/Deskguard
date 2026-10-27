# tools

개발 중에 쓴 보조 도구이다. 서비스 코드(`backend/`)와 분리되어 있으며, 4단계 이후 일부는 JUnit 테스트로 옮긴다.

## poc: Jev 한국어 PoC 도구

| 파일 | 역할 |
|---|---|
| `make_questions.ps1` | `docs/design.md` 1절의 질문 표에서 `questions_K.tsv`, `questions_E.tsv`, `questions_K2.tsv`(v2)를 만든다. 질문 문구의 단일 출처는 design.md이다. |
| `Poc.java` | 문장 목록을 판정 모델(Jev)에 보내 질문별 확률을 CSV로 저장한다. JDK 17 단일 파일 실행이다. |
| `Analyze.ps1` | 합격 기준 C1~C6을 사전 정의대로 계산해 `report.txt`를 만든다. |
| `Analyze2.ps1` | 같은 결과로 질문별 확률, 질문별 임계값 시나리오, 회색 구간을 다시 계산한다. 새 호출은 없다. |

### 실행 순서

PowerShell, 저장소 루트에서 실행한다. `작업폴더`는 임시 결과를 둘 폴더이다.

```powershell
# 1) 질문 파일 만들기
powershell -File tools\poc\make_questions.ps1 -proj . -out 작업폴더

# 2) 외부 호출 (비용이 든다. 환경변수 TYPESAFE_API_KEY가 있어야 한다)
java '-Dfile.encoding=UTF-8' tools\poc\Poc.java docs\poc\data.tsv 작업폴더\questions_K.tsv 작업폴더\result_K.csv 3 ALL K 100

# 3) 분석 (작업폴더에 data.tsv, result_K.csv, result_E.csv가 있어야 한다)
Copy-Item docs\poc\data.tsv 작업폴더\data.tsv
powershell -File tools\poc\Analyze.ps1 -run 작업폴더
```

- 인자 순서: 데이터, 질문, 출력 CSV, 반복 횟수, 문장 ID 목록 또는 `ALL`, 문구 이름, 호출 간격(ms)
- 2026-10-26 실행 결과는 `docs/poc/`에 있다. 전체 1,104회 호출 비용은 약 0.06달러였다.
- 합격 기준은 바꾸지 않는다. 점검 문장을 늘리는 방향으로 보강한다. ([../DECISIONS.md](../DECISIONS.md) 참고)

## rulecheck: 정규식 시험 하네스

패턴 필터 후보 정규식을 정상 문장 풀과 공격 문장 풀에 돌려 오탐과 탐지를 계산하는 Java 도구이다. 패턴은 현재 0개이므로 쓰이지 않지만, 패턴을 추가할 때의 시험 방식을 보여주는 참고용이다. 4단계 TDD에서 JUnit 테스트로 옮긴다.
