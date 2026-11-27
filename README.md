# 월급지킴이

저장소와 코드 이름은 `wageback` 그대로 쓴다.

임금을 못 받은 근로자가 받아야 할 돈을 근로기준법 기준으로 계산하고, 쉬운 말로 설명하고, 노동청 진정서 초안까지 만들 수 있게 돕는 모바일 중심 웹 서비스.

2026 미래내일 일경험 프로젝트 · 팀 노란돌고래

## 실행

JDK 21이 필요하다.

```bash
./gradlew bootRun
```

Windows 명령 프롬프트에서는 `gradlew.bat bootRun`. 실행 후 http://localhost:8080 에 접속하면 화면이 열린다.

브랜치: 작업은 `develop`에서 `feat/작업이름` 브랜치를 따서 하고 PR은 `develop`으로 보낸다. `main`은 시연·제출용이라 정해진 날에만 `develop`을 합친다.

테스트:

```bash
./gradlew test
```

## 구조

| 위치 | 내용 | 담당 |
|---|---|---|
| `calculation/` | 룰 엔진, `POST /api/v1/calculations` | 이명균 |
| `document/` | 서류 글자 인식, `POST /api/v1/documents/extract` | 이수환 |
| `explanation/` | 산정 결과 해설, `POST /api/v1/calculations/{id}/explanation` | 정상연 |
| `complaint/` | 진정서 초안, `POST /api/v1/calculations/{id}/complaint-draft` | 정상연 |
| `src/main/resources/static/` | 화면 | 유지환 |
| `application.properties`, 공통 예외 처리·요청 제한 | 서버 공통 설정, 배포 | 이수환 |
| `src/main/resources/samples/` | 기능이 붙기 전까지 쓰는 예시 응답 | — |

자바 코드는 `src/main/java/com/yellowdolphin/wageback/` 아래에 있다.

지금 API는 입력과 상관없이 `samples/`의 예시 응답을 돌려준다. 각자 맡은 기능을 붙이면서 예시 응답을 실제 결과로 바꾼다. 응답 형식은 바꾸지 않는다.

## v2 기능 (feat/v2)

v2에서 더한 기능은 다음과 같다.
- 서류 여러 장 촬영·인식(`POST /documents/extract-batch`)
- 근무 달력(하루 기록 `workDays` → 룰 엔진이 주별로 연장·야간·휴일 시간을 나눔, 응답 `workWeeks`)과 여러 달 입력
- 결과 화면: 달별 막대 그래프(직접 SVG), 항목별 '왜 이 금액인가요?' 단계 풀이, '18만 4천 원' 같은 읽기 쉬운 금액
- 쉬운 말 사전(밑줄 친 말을 누르면 풀이)
- 읽어 주기(Web Speech API, 지원하지 않으면 숨김)
- 모든 화면 한국어·영어·베트남어. `i18n/<언어>.json`을 더하고 `i18n/languages.json`에 한 줄 넣으면 언어가 늘어난다. 번역은 직접 번역이라 검수가 필요하다.
- 상담원에게 보여 주는 한국어 큰 글씨 요약과 인쇄
- 관할 노동청 찾기·도움받을 곳(`static/data/offices.json`, 고용노동부·노동포털에서 확인한 것만 넣고 출처·확인일을 함께 적음)
- 진정 준비물 체크리스트
- 대지급금 안내(금액 판정은 하지 않음)
- 이 휴대폰에만 저장하는 '이어서 하기'와 '내 정보 지우기'
- 설치형 웹앱(`manifest.webmanifest`, `sw.js`: 화면 파일만 캐시하고 `/api/` 응답은 캐시하지 않음)

새 API는 `_문서/API_v2_추가.md`에 적었다. 시험은 `gradlew.bat test`로 돌린다(LLM 키 없이 통과). 실행은 `gradlew.bat bootRun --args=--server.port=18095`로 하고 http://localhost:18095 를 연다. `bootRun`은 시작할 때 화면 파일을 복사하므로, 화면을 고친 뒤에는 서버를 다시 켜야 한다.
