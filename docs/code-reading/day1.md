# 코드 읽기 1일차: 요청의 전체 흐름

목표: 요청 하나가 들어와서 나가기까지의 흐름과 처리 결정 규칙을 본인 말로 설명할 수 있다. 읽을 코드는 3개이고 합쳐서 약 190줄이다. 한 파일에 15~25분을 잡는다.

읽는 방법은 다음과 같다. 각 파일의 "읽는 순서"를 따라 코드를 읽고, 짝이 되는 시험 파일을 옆에 열어 둔다. 시험은 코드가 무엇을 약속하는지 가장 짧게 보여 준다. 그다음 "확인 질문"에 코드를 보지 않고 먼저 답해 보고, 막히면 코드를 보면서 답해도 된다. 답이 끝난 뒤에 맨 아래 "정답 메모"를 열어 비교한다. 틀린 답은 괜찮다. 어디서 틀렸는지가 이해하지 못한 부분을 알려 준다.

## 먼저 지도 (읽기 전에 1분)

```
직원의 요청 {prompt, confirmed}
   |
   v
ChatController   요청을 받고 결과를 HTTP 응답으로 바꾼다
   |
   v
ChatGateway.handle()   단계의 순서만 정한다
   1. InputValidator     입력이 처리할 수 있는 모양인가
   2. DetectionEngine    민감한 값을 찾는다 (이번에는 읽지 않는다)
   3. ActionDecider      허용, 가림, 경고, 차단 중 무엇인가
   4. Masker             값을 가명으로 바꾼다 (이번에는 읽지 않는다)
   5. AnswerGenerator    외부 AI를 부른다 (Gemini)
   6. Masker.restore     답의 가명을 원래 값으로 되돌린다
   7. GatewayLogger      요청마다 로그 한 줄
```

판단은 각 부품이 하고, `ChatGateway`는 순서만 정한다는 점이 이 구조의 핵심이다.

## 1. `ChatGateway` (127줄)

파일: `backend/src/main/java/com/promptgateway/gateway/ChatGateway.java`
짝이 되는 시험: `backend/src/test/java/com/promptgateway/gateway/GatewayFlowTest.java`

**역할**: 요청 하나가 거치는 단계를 순서대로 부른다.

**읽는 순서**

1. 17~28줄의 설명 주석을 먼저 읽는다. 6단계 순서가 적혀 있다.
2. 30~49줄: 이 클래스가 어떤 부품을 가지고 있는지 본다. 판단하는 코드가 없고 부품만 가진다.
3. 55~70줄: 검증, 탐지, 처리 결정, 경고 확인까지의 앞부분이다.
4. 86~107줄: 차단과 경고, 허용, 가림의 네 갈래를 따라간다. 이 부분이 가장 중요하다.
5. 72~85줄(측정 모드)은 맨 마지막에 읽는다. 평가 때문에 있는 갈림길이라 처음에는 건너뛰어도 된다.
6. 109~126줄: 짧은 도우미 메서드 세 개를 읽는다.

**확인 질문**

1. `handle()`이 하는 일을 순서대로 말해 보자. 단계는 몇 개이고 각각 무엇인가?
2. 차단(BLOCK)이거나 확인 전 경고(WARN)일 때 외부 AI를 부르는가? 코드의 어느 줄에서 알 수 있는가?
3. 경고에서 직원이 확인(`confirmed`가 true)하면 처리가 어떻게 바뀌는가? 그 줄은 어디인가?
4. 가명 대응표(`MaskResult`)는 어디서 만들어지고 어디서 사라지는가? 이 표를 오래 들고 있지 않는 이유는 무엇인가?
5. 외부 AI 호출이 실패하면 로그에 무엇이 남는가? 사용자가 보낸 원문이 그대로 남는가?
6. (생각해 보기) 허용(ALLOW) 갈래는 `prompt`를 그대로 보내고, 가림 갈래는 `mask.maskedText()`를 보낸다. 이 차이를 만드는 변수는 무엇인가?

**시험과 연결해 보기**: `GatewayFlowTest`에서 `@DisplayName`만 훑어 읽어 보자. 각 시험 이름이 위 질문의 어느 갈래에 해당하는지 연결해 보자.

## 2. `ActionDecider` (34줄)

파일: `backend/src/main/java/com/promptgateway/detect/ActionDecider.java`
짝이 되는 시험: `backend/src/test/java/com/promptgateway/detect/ActionDeciderTest.java`

**역할**: 탐지 결과 목록을 받아서 처리를 하나 정한다. 외부에 의존하지 않는 순수한 판단 코드이다.

**읽는 순서**: 위쪽 설명 주석(규칙 네 줄)을 먼저 읽고, `decide()` 메서드의 코드가 그 규칙과 똑같은지 대조한다. 그다음 시험 파일의 `@DisplayName`을 읽는다.

**확인 질문**

1. 한 문장에 전화번호(D1)와 API 키(D2)가 같이 있으면 어떤 처리가 나오는가?
2. D1 개인정보가 19개이면 처리가 무엇이고, 20개이면 무엇인가? 20이라는 숫자는 어디서 바뀌는가?
3. 코드명(D3 CODENAME)만 있을 때와 등급 표시(D3 CLASSIFIED)만 있을 때의 처리는 각각 무엇인가? 같은 D3인데 왜 다른가?
4. `bulkThreshold`가 0이나 음수로 들어오면 어떻게 되는가? 이 검사가 왜 생성자에 있는가?
5. 코드명이 30개 있으면 경고가 되는가? 어느 줄이 이것을 결정하는가?

**한 번 고쳐 보기(선택, 10분)**: 시험 `bulkPersonalIsWarn`을 읽고, 숫자를 한 글자만 바꿔서 시험이 실패하는지 확인해 보자(예: 하나 모자라는 값을 기준과 같게). 실패하는 것을 확인한 뒤 되돌린다. 실행은 아래 "시험 실행"을 본다.

## 3. `InputValidator` (24줄)

파일: `backend/src/main/java/com/promptgateway/gateway/InputValidator.java`
짝이 되는 시험: `backend/src/test/java/com/promptgateway/gateway/InputValidatorTest.java`

**역할**: 요청의 `prompt`가 처리할 수 있는 입력인지 검사한다.

**읽는 순서**: 주석, 생성자, `validate()` 순서로 읽는다. 24줄이라 천천히 한 줄씩 읽는다.

**확인 질문**

1. 어떤 입력이 거부되는가? 조건을 모두 말해 보자.
2. 글자 수를 `length()`가 아니라 `codePointCount`로 세는 이유는 무엇인가?
3. 거부되면 어떤 예외가 던져지는가? 이 예외는 `ChatGateway`에서 무엇이 되고, 최종 HTTP 상태는 무엇인가? (힌트: `ChatGateway` 56~61줄과 `GlobalExceptionHandler`)
4. **찾아보기:** 위쪽 주석에는 "기본 1,000자"라고 적혀 있다. 실제로 서버가 쓰는 값은 얼마이고 어디에 적혀 있는가? 주석과 값이 다르면 어느 쪽을 믿어야 하는가?

**한 번 고쳐 보기(선택, 5분)**: 4번에서 찾은 불일치를 직접 고쳐 보자. 주석만 바꾸면 되는 작은 수정이다. 바꾼 뒤 시험을 돌려서 아무것도 깨지지 않는지 확인한다. (시험의 1,000은 생성자에 직접 넣는 값이라 서버 설정과 무관하다.)

## 4. 덤: `ChatController` (67줄, 10분)

파일: `backend/src/main/java/com/promptgateway/controller/ChatController.java`

`ChatGateway`를 읽은 직후에 흐름만 훑어본다. 확인 질문은 둘이다.

1. 측정 응답은 언제 주는가? 조건 두 가지를 말해 보자.
2. 접속 주소를 헤더(`X-Forwarded-For`)가 아니라 `getRemoteAddr()`로 판단하는 이유는 무엇인가?

## 시험 실행 (선택, JDK 25와 Maven이 설치된 컴퓨터)

```bash
cd backend
mvn -B test -Dtest=ActionDeciderTest
mvn -B test -Dtest=InputValidatorTest
mvn -B test -Dtest=GatewayFlowTest
```

## 정답 메모 (직접 답해 본 뒤에 연다)

### `ChatGateway`

1. 순서: 입력 검증(57줄) → 탐지(64줄) → 처리 결정(65줄) → 경고 확인 반영(69~70줄) → 차단과 확인 전 경고는 여기서 끝(86~89줄) → 허용이면 원문으로 외부 AI(90~95줄) → 가림이면 가명으로 바꿔 외부 AI(97~101줄) → 답의 가명 복원(103~104줄) → 모든 경로에서 로그 한 줄(`logged`).
2. 부르지 않는다. 86~89줄의 `return` 안에 `callAnswer` 호출이 없고, 그 `return` 이후에야 외부 AI 호출 코드가 나온다.
3. 69~70줄이다. `decided`가 WARN이고 `confirmed`가 true이면 `confirmedWarn`이 참이 되고 `action`이 MASK로 바뀐다. 그러면 가림 갈래(97줄부터)로 가서 가려서 보낸다.
4. 98줄에서 `masker.mask()`가 만들고, 메서드가 끝나면 지역 변수라 사라진다. 응답이나 로그에 가명과 원래 값의 대응이 남으면 그 자체가 새로운 유출이 되기 때문이다(주석의 AC-4.4).
5. `callAnswer`의 `catch`(113~115줄)가 `logger.logError(prompt, "ANSWER", e)`를 부른다. 설명 주석(109줄)대로 원문이 아니라 길이와 해시를 남긴다. 구체적으로 무엇이 찍히는지는 `GatewayLogger.logError`에서 확인한다(다음 읽기 날짜의 후보).
6. `action`이다. ALLOW와 MASK의 처리 갈래가 갈라지고, MASK 갈래에서만 `masker.mask()`가 호출되어 `maskedText()`가 생긴다.

### `ActionDecider`

1. 차단(BLOCK). D2나 등급 표시가 하나라도 있으면 다른 탐지와 섞여도 가장 먼저 차단이다.
2. 19개는 가림(MASK), 20개는 경고(WARN). 기준은 `detection.bulk-threshold`(application.yml, 기본 20)이고 생성자로 들어온다.
3. 둘 다 D3인데 코드명은 가림, 등급 표시는 차단이다. 코드는 범주가 아니라 항목(`f.item() == Item.CLASSIFIED`)으로 등급 표시를 따로 걸러 차단으로 보낸다. 등급 표시가 붙은 문서는 외부로 나가면 안 된다는 정책이기 때문이다.
4. `IllegalArgumentException`이 던져져 서버가 시작되지 않는다. 잘못된 설정을 실행 중에 발견하지 않고 시작 단계에서 막으려는 것이다.
5. 되지 않는다. 대량 기준은 D1(`category() == Category.D1`)만 센다. 코드명이 많아도 가림이다(시험 `bulkCountsOnlyPersonal`).

### `InputValidator`

1. `null`이거나 공백뿐인 문자열, 그리고 최대 길이를 넘는 문자열이다.
2. `length()`는 UTF-16 단위로 세어서 이모지 같은 보충 문자를 두 글자로 센다. `codePointCount`는 사람이 보는 한 글자를 하나로 센다.
3. `InvalidPromptException`이 던져진다. `ChatGateway`가 먼저 `logError`로 로그를 남기고 다시 던지며, `GlobalExceptionHandler`가 400으로 응답한다.
4. 서버가 쓰는 값은 `application.yml`의 `gateway.max-prompt-length: 4000`이다(명세 S1에서 1,000자를 4,000자로 바꿨다). 주석의 "기본 1,000자"는 바뀌기 전의 내용이 남은 것이다. 설정 파일의 값이 실제 동작을 정하므로 값을 믿는다. 이런 불일치는 코드를 읽어야만 발견된다.

### `ChatController`

1. 측정 모드가 켜져 있고, 접속 주소가 같은 컴퓨터(루프백)인 경우이다.
2. 헤더는 요청을 보내는 쪽이 마음대로 쓸 수 있어서 조작할 수 있다. `getRemoteAddr()`는 실제 TCP 연결의 주소라 조작할 수 없다(주석의 AC-9.8).

## 끝나면

- 막힌 질문 번호와 이해가 안 된 줄을 알려 주면 해당 부분을 풀어서 설명한다.
- 확인 질문에 답을 끝낸 파일은 `docs/code-reading/progress.md`에 날짜와 함께 기록하면 "이해한 코드" 목록이 된다(다음 읽기 날짜의 후보: `PersonalDataDetector`, `SecretDetector`, `DetectionEngine`).
