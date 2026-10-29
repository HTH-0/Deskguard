# 외부 자료의 출처와 라이선스

이 저장소에는 외부 공개 자료가 들어 있다. 출처, 라이선스, 가공 내용을 아래에 적는다. 라이선스는 2026-10-29에 각 자료의 공식 페이지에서 확인했다. 이 저장소의 자체 코드와 문서에는 아직 라이선스를 정하지 않았다(`DECISIONS.md` 참고).

## 1. deepset/prompt-injections (`dataset.csv`의 일부 행)

- 출처: deepset, 「prompt-injections」, Hugging Face. <https://huggingface.co/datasets/deepset/prompt-injections>
- 라이선스: Apache License 2.0 (카드 메타데이터 `apache-2.0`). 전문: <https://www.apache.org/licenses/LICENSE-2.0>
- 가공: 일부 행을 골라 `label`, `type`, `split`, `lang` 열을 붙여 `dataset.csv`에 합쳤다. 원문 문장은 고치지 않았다. 영어와 독일어 문장이다.
- 이 저장소의 `dataset.csv`는 원 데이터셋과 다른 파일이며, 원 데이터셋의 보증이나 승인을 받은 것이 아니다.

## 2. JailbreakBench JBB-Behaviors (`dataset.csv`의 일부 행)

- 출처: JailbreakBench, 「JBB-Behaviors」, Hugging Face. <https://huggingface.co/datasets/JailbreakBench/JBB-Behaviors>
- 라이선스: MIT License. 전문은 아래와 같다(원문: <https://github.com/JailbreakBench/jailbreakbench/blob/main/LICENSE>).
- 가공: 유해 행동 목록 98건과 각각의 정상 짝 100건을 골라 `source_category`(원본의 세부 범주) 열과 함께 `dataset.csv`에 합쳤다. 문장은 고치지 않았다.
- 주의: 일부 내용은 불쾌할 수 있다. 시험용으로만 쓰며 실행 가능한 지침을 담지 않는다.
- 인용 요청: 카드가 벤치마크 논문의 인용을 요청한다. P. Chao, E. Debenedetti, A. Robey, M. Andriushchenko, F. Croce, V. Sehwag, E. Dobriban, N. Flammarion, G. J. Pappas, F. Tramèr, H. Hassani, E. Wong, "JailbreakBench: An Open Robustness Benchmark for Jailbreaking Large Language Models", NeurIPS 2024 Datasets and Benchmarks Track. 카드는 구성 데이터셋(AdvBench: Zou et al., 2023, arXiv:2307.15043, TDC와 HarmBench: Mazeika et al., 2024, arXiv:2402.04249)도 함께 인용하기를 권한다.

```
MIT License

Copyright (c) 2023 JailbreakBench Team

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

## 3. KOICA 인사규정 질의응답 세트 (`docs/eval/koica_hr_questions_raw.csv`)

- 출처: 한국국제협력단, 「인사규정 질의 응대 서비스 질의응답 세트」(2025-10-31 기준), 공공데이터포털. <https://www.data.go.kr/data/15152718/fileData.do>
- 이용허락범위: 제한 없음(2026-10-28 확인). 이 파일을 쓰는 글에는 출처를 표시한다.
- 가공: 원본에서 직원이 실제로 묻는 질문 25개를 골랐고 원본은 고치지 않았다(`docs/eval/README.md`). 주제 재정의(2026-10-29) 이후 이 파일은 평가에 쓰지 않는다.

## 4. 생성형 AI가 만든 자료

- `docs/eval/templates/`의 업무 요청 틀은 ChatGPT, Claude, Gemini가 만든 문장이다. 사람이 고르거나 고치지 않았다.
- 코드와 문서의 상당 부분은 Claude Code가 작성했고 사용자가 검토했다. 커밋 메시지에 공동 작성자가 표시되어 있다.
- 평가 세트의 값(이름, 전화, 카드, 키 등)은 `backend/scripts/fake_values.py`가 무작위로 만든 가짜 값이며 실제 사람과 관계없다. 무작위 값이 우연히 실제 번호와 같을 수는 있다.

## 5. 측정에 쓰는 외부 서비스 (자료가 아님)

- 응답 모델: Google Gemini API(무료 티어). 판정 채점: TypeSafe의 Jev(`jev-1.14.0`, Z 측정에서만). 각 서비스의 약관을 따른다. 입력은 가짜 데이터뿐이다(`docs/open-risks.md` R8, R11).

## 5-1. 사용하지 않은 자료

- KorSET(`datumo/KorSET`), CAGE, RICoTA는 접근 승인과 비상업 조건이 있어 사용하지 않았고 저장소에도 없다(`docs/research/domain-sources.md`).
