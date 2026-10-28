# tools

개발 중에 쓴 보조 도구이다. 서비스 코드(`backend/`)와 분리되어 있으며, 4단계 이후 일부는 JUnit 테스트로 옮긴다.

## poc: Jev 한국어 PoC 도구

| 파일 | 역할 |
|---|---|
| `make_questions.ps1` | `docs/design.md` 1절의 질문 표에서 `questions_K.tsv`, `questions_E.tsv`, `questions_K2.tsv`(v2)를 만든다. 질문 문구의 단일 출처는 design.md이다. |
| `Poc.java` | 문장 목록을 판정 모델(Jev)에 보내 질문별 확률을 CSV로 저장한다. JDK 25 단일 파일 실행이다. |
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

## Send-Chat: 실행 중인 서버에 요청 한 번 보내기 (PowerShell)

서버를 띄운 창과 다른 PowerShell 창에서 쓴다. 함수는 창을 닫으면 사라지므로 새 창마다 아래를 한 번 붙여넣는다. 본문을 UTF-8 바이트로 보내고(한글이 깨지지 않는다) 상태 코드와 본문을 항상 함께 보여준다. 오래된 `Invoke-RestMethod`는 한글 응답을 깨뜨리고 400, 403 본문을 감추기 때문에 `curl.exe`를 쓴다.

```powershell
function Send-Chat($text) {
  [Console]::OutputEncoding = [System.Text.Encoding]::UTF8
  $f = Join-Path $env:TEMP "chat.json"
  [System.IO.File]::WriteAllText($f, (@{prompt=$text} | ConvertTo-Json -Compress), (New-Object System.Text.UTF8Encoding $false))
  curl.exe -s -w "`nHTTP %{http_code}`n" -H "Content-Type: application/json; charset=utf-8" --data-binary "@$f" http://localhost:8080/api/chat
}

Send-Chat "연차 신청은 어떻게 하나요?"
```

- 응답 시간을 재려면 `(Measure-Command { Send-Chat "질문" | Out-Null }).TotalSeconds`를 쓴다.
- 일반 모드에서는 통과한 문장이 Gemini로 간다(비용과 입력 검토 가능성이 있다). 공격 문장은 측정 모드(`gateway.measurement-mode=true`)에서만 보낸다.

## run_batch.py: 측정 도구 (설계서 7.4절)

입력 CSV의 문장을 `/api/chat`에 순서대로 보내고, 결과 CSV와 요약표(탐지율, 오탐율, 레이블별, 유형별, 언어별, `group`별, 응답 시간)를 남긴다. 위치는 `backend/scripts/`이다.

```powershell
# 서버를 측정 모드로 띄운다 (backend 폴더, 다른 창)
mvn spring-boot:run "-Dspring-boot.run.arguments=--gateway.measurement-mode=true"

# backend\scripts 폴더에서 실행한다
python run_batch.py --input ..\..\dataset.csv --output result.csv

# 도구 시험 (서버와 키가 필요 없다)
python -m unittest -v test_run_batch
```

- 기본으로 `split=design` 행만 보낸다. `--split eval`은 6단계 측정에서만 쓴다.
- `disallowed_content`, `bias_injection` 유형과 영어 hard negative는 항상 보내지 않는다(제한을 푸는 옵션은 없다). 제외한 건수는 시작할 때 출력한다.
- 첫 요청 전에 정상 문장 1건으로 측정 모드인지 확인하고, 아니면 한 건도 더 보내지 않고 종료 코드 2로 중단한다. 일반 모드로 실행하려면 `--allow-answer-model`을 준다(통과한 문장이 응답 모델로 간다).
- 입력 CSV에 선택 열 `group`이 있고 값이 있으면(예: `out_of_scope`) 그 행은 정상, 공격 집계와 분리한 별도 표로 보고한다.
- 판정 실패 차단, 오류, 단계 미상 차단은 정책 차단과 별도 열이며, 탐지율과 오탐율의 분모에서 빠지고 제외 건수가 함께 적힌다.
