# 중단한 6-2 분석 도구 (빨강 단계)

2026-10-28에 6-2(분석 도구: 지표, 윌슨 신뢰구간, 임계값 곡선, 쌍 비교)를 TDD로 시작했다가, 우선순위를 도메인 자료 조사로 바꾸면서 중단했다. 저장소의 Python 테스트가 실패하지 않도록 `.txt`로 보존한다.

- `eval_metrics.py.txt`: 빈 껍데기(함수가 틀린 기본값을 돌려준다).
- `test_eval_metrics.py.txt`: 구현 전 테스트 25건. 빈 껍데기에서 실행하면 기대값 불일치 10건과 `KeyError` 12건 등으로 실패하는 것을 확인했다(빨강).

## 재개 방법

1. `backend/scripts/`에 두 파일을 `.txt` 없이 복사한다(`eval_metrics.py`, `test_eval_metrics.py`).
2. 테스트를 실행해 실패하는 것을 다시 확인한다: `cd backend/scripts` 후 `python -m unittest -v test_eval_metrics`.
3. [design.md](../../design.md) 10.3절의 지표 정의에 맞춰 최소 구현으로 통과시킨다(윌슨 구간, 혼동 행렬, 임계값 곡선, 쌍 비교, 유형과 언어별 표, 로더 2종, 보고).
4. 전체 테스트(Java 148건, Python 기존 25건과 이 파일)를 실행하고 커밋한다.

이 테스트가 정한 입력 형식: `run_batch.py`의 결과 CSV(열 `result_status`, `result_stage`, `result_scores`)와 PoC 결과 CSV(`docs/poc/result_184_K2.csv`, 반복 3회의 평균을 질문별로 낸 뒤 최댓값을 쓴다).
