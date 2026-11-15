# API v2 추가 경로

> `API.md` 의 기존 경로 · 응답 형식은 그대로다. 여기에는 v2 에서 **새로 더한 것만** 적는다.
> 오류 형식(ProblemDetail + `code` · `field`)은 `API.md` 와 같다.

## POST /api/v1/documents/extract-batch — 서류 여러 장 한 번에 읽기

급여명세서 여러 달 + 근로계약서처럼 여러 장을 한 번에 보낸다. 장마다 따로 읽고(서버에서 동시에), 결과를 합쳐 돌려준다.
기존 `POST /api/v1/documents/extract`(한 장)는 그대로 쓸 수 있다.

### 요청 (multipart/form-data)
| 이름 | 반복 | 설명 |
|---|---|---|
| `files` | 1~6개 | PDF · JPG · PNG, 장당 10MB 이하 |
| `docTypes` | `files` 와 같은 개수 | `PAYSLIP` 또는 `CONTRACT`. `docTypes[i]` 가 `files[i]` 의 종류 |

- 요청 전체 최대 60MB (`spring.servlet.multipart.max-request-size`).
- 화면은 보내기 전에 브라우저에서 사진을 돌리고 · 긴 변 2000px 로 줄이고 · JPEG 로 압축한다. 원래 파일 이름은 보내지 않는다(`page1.jpg` …).
- 파일은 메모리에서만 읽고 저장 · 로그하지 않는다. 주민등록번호는 서버에서 한 번 더 가린다 (한 장 경로와 같음).
- 요청 제한: 한 장 경로와 **같은 몫**으로 IP당 분당 5번 (`RateLimitFilter` 의 upload).

### 응답 200
```json
{
  "pages": [
    { "page": 1, "docType": "PAYSLIP", "ok": true,
      "fields": [{ "key": "payMonth", "value": "2026-08", "confidence": 0.9 }],
      "unreadable": ["nightHours"], "maskedCount": 1 },
    { "page": 2, "docType": "PAYSLIP", "ok": true, "fields": [ ... ], "unreadable": [], "maskedCount": 0 },
    { "page": 3, "docType": "CONTRACT", "ok": false, "fields": [], "unreadable": [], "maskedCount": 0 }
  ],
  "fields": [
    { "key": "payMonth", "value": "2026-09", "confidence": 0.95, "page": 2 }
  ],
  "unreadable": ["nightHours"],
  "conflicts": [
    { "key": "payMonth", "values": [
      { "key": "payMonth", "value": "2026-08", "confidence": 0.9, "page": 1 },
      { "key": "payMonth", "value": "2026-09", "confidence": 0.95, "page": 2 } ] }
  ],
  "maskedCount": 1
}
```
| 필드 | 뜻 |
|---|---|
| `pages[]` | 장별 결과. `page` 는 1부터(화면의 '1번 사진'). 못 읽은 장은 `ok: false` 이고 나머지는 빈 값 |
| `fields[]` | 합친 값. 키 · 값 형식은 한 장 경로의 `fields` 와 같고, `page` = 그 값을 읽은 사진 |
| 합치는 규칙 | 같은 키는 `confidence` 가 가장 높은 값 (같으면 앞 장). 숫자는 `2156880` 과 `2156880.0` 을 같은 값으로 본다 |
| `conflicts[]` | 같은 키인데 사진마다 값이 다르면, 그 키를 읽은 모든 장의 값. 화면은 노란 줄로 '사진마다 값이 달라요' 를 보여 주고 확인을 받는다 |
| `unreadable` | 어느 장에서든 못 읽었다고 했고, 합친 값에도 없는 키 |
| `maskedCount` | 장별 `maskedCount` 합 |

### 오류
| 상태 | code | field | 언제 |
|---|---|---|---|
| 400 | `TOO_MANY_FILES` | `files` | 7장 이상 |
| 400 | `INVALID_DOC_TYPE` | `docTypes` | `files` 와 `docTypes` 개수가 다름 |
| 400 | `INVALID_DOC_TYPE` | `docTypes[i]` | 종류가 PAYSLIP / CONTRACT 가 아님 |
| 400 | `INVALID_FILE` | `files[i]` | 빈 파일 |
| 413 | `FILE_TOO_LARGE` | `files[i]` | 한 장이 10MB 초과 (요청 전체 60MB 초과는 `field: null`) |
| 415 | `UNSUPPORTED_FILE` | `files[i]` | HWP 등 PDF · JPG · PNG 가 아닌 파일 |
| 422 | `OCR_FAILED` | `file` | **모든 장**을 못 읽음 (LLM 키 없음 포함). 일부만 못 읽으면 200 + 그 장 `ok: false` |
| 429 | `TOO_MANY_REQUESTS` | null | 분당 5번 초과 |

시험: `DocumentServiceTest`(합치기 · 충돌 · 일부 실패 · 모두 실패, 가짜 LLM), `ApiErrorTest.여러_장_업로드_오류_코드`, `RateLimitFilterTest`.

## POST /api/v1/calculations — `workDays` (하루 기록) 더함

기존 요청 · 응답은 그대로다. 요청에 **선택** 필드 `workDays` 를, 응답에 `workWeeks` 를 더했다 (`engineVersion` 1.1.0).
달력 화면에서 날짜마다 출근 · 퇴근 시각을 넣으면 룰 엔진이 하루별로 연장 · 야간 · 휴일 시간을 나눠 그 달 계산에 쓴다.

### 요청에 더한 필드
```json
"workDays": [
  { "date": "2026-09-07", "start": "09:00", "end": "20:00", "breakMinutes": 60, "holiday": false },
  { "date": "2026-09-13", "start": "18:00", "end": "03:00", "breakMinutes": 60, "holiday": true }
]
```
| 필드 | 뜻 |
|---|---|
| `date` | 일한 날. 일한 기간 안이고, **`months[]` 에 있는 달**이어야 한다 (그 달 받은 돈이 필요해서) |
| `start` · `end` | `HH:mm`. `end` 가 `start` 보다 이르거나 같으면 다음 날 퇴근 |
| `breakMinutes` | 쉬는 시간(분). 없으면 0 |
| `holiday` | 쉬기로 한 날(주휴일 · 약정 휴일)에 일했으면 `true` |

### 계산 규칙 (RuleEngine `fromDays`)
- 하루 기록이 있는 달은 그 달 `overtimeHours` · `nightHours` · `holidayHoursWithin8` · `holidayHoursOver8` 대신 기록에서 나눈 값을 쓴다. 기록이 없는 달은 예전처럼 월 합계.
- 주(월요일 시작)마다 평일 연장 = max(하루 8시간 넘은 합, 주 40시간 넘은 시간) — 하루 10시간 × 4일 → 연장 8시간, 8시간 × 6일 → 8시간.
- 휴일에 일한 시간은 연장 가산과 겹쳐 세지 않고 휴일근로로만 센다 (8시간까지 · 넘은 시간). 근거: 근로기준법 제56조 제2항, 대법원 2018. 6. 21. 선고 2011다112391 전원합의체 판결(가산 중복 없음). 개정법(제2조 제1항 제7호, 1주=휴일 포함 7일)에서 휴일근로가 주 52시간 한도에 들어가는지는 금액과 상관없어 여기서 다루지 않는다 — 멘토 확인 필요.
- 야간 = 22:00~다음 날 06:00 과 겹친 시간. 쉬는 시간은 야간이 아닌 때에 쉬었다고 본다 (야간 ≤ 일한 시간). 달이 바뀌는 주는 그 달 날짜만으로 센다. 이 가정은 경고 `DAILY_RECORDS` 로 결과에 보인다.
- 금액 계산식(가산률 · 5인 미만)은 그대로다. 항목 `formula` 끝에 `(하루 기록 N일에서 나눈 시간)` 이 붙는다.

### 응답에 더한 필드
```json
"workWeeks": [
  { "month": "2026-09", "weekStart": "2026-09-07", "days": 4, "workedHours": "40", "overtimeHours": "8",
    "nightHours": "0", "holidayHoursWithin8": "0", "holidayHoursOver8": "0",
    "formula": "평일 4일 40시간: 하루 8시간 넘은 합 8시간과 주 40시간 넘은 0시간 중 큰 값 → 연장 8시간 · 밤 10시~아침 6시 0시간" }
]
```
하루 기록이 없으면 빈 목록. 시간은 소수 둘째 자리 반올림 문자열.

### 오류 (형식은 기존과 같음)
| code | field | 언제 |
|---|---|---|
| `INVALID_REQUIRED` | `workDays[i].date` · `.start` · `.end` | 빠짐 |
| `INVALID_VALUE` | `workDays[i].date` | 같은 날짜가 두 번 |
| `INVALID_VALUE` | `workDays[i].start` · `.end` | `HH:mm` 이 아님 |
| `INVALID_PERIOD` | `workDays[i].date` | 일한 기간 밖 |
| `INVALID_MONTH` | `workDays[i].date` | `months[]` 에 없는 달 |
| `INVALID_HOURS` | `workDays[i].breakMinutes` | 음수, 또는 쉬는 시간을 빼면 일한 시간이 0 이하 |

시험: `RuleEngineTest` 의 `하루기록_*` 6개.

## 화면 정적 파일 (API 아님, v2 3단계)

- `GET /data/offices.json`: 관할 노동청 49곳(지방청 6 · 지청 42 · 출장소 1), 시·도 16 · 시·군·구 222 → 담당 기관, 상담 전화 4곳. 항목마다 `source`, 파일에 `checkedAt`(2026-11-15). 확인 못 한 곳은 넣지 않았고, 화면은 노동포털 `findUrl` 로 안내한다. 조사 정리: `_도구/조사_3단계.md`.
- `GET /i18n/languages.json`: 언어 목록 `[{code, name, koName, source}]`. 언어 파일 `i18n/<code>.json` 의 `_source` 에 번역 출처(직접 번역 · 검수 필요).
- `GET /manifest.webmanifest`, `GET /sw.js`: 설치형 웹앱. 서비스워커는 같은 출처의 GET 화면 파일만 캐시하고 `/api/` 는 건드리지 않는다.

## 6단계: 해고예고수당 · 못 쓴 연차수당 · 수습 감액 (`POST /api/v1/calculations`)

요청에 아래 세 필드를 **더할 수 있다**. 모두 선택이고, 없으면 예전과 똑같이 계산한다.
```json
"dismissal":   { "dismissed": true, "noticeDaysBefore": 0 },
"annualLeave": { "usedDays": 3 },
"probation":   { "onProbation": true, "contractOneYearOrMore": true, "simpleLabor": false }
```
- `dismissal.noticeDaysBefore`: 해고를 며칠 전에 알렸나. 안 알렸으면 0 (null 도 0).
- `annualLeave.usedDays`: 그만두기 전 마지막 1년(1년 미만이면 전체) 동안 쓴 연차 일수.
- `probation`: 세 값이 모두 `true · true · false` 일 때만 입사 후 3개월 동안 최저임금 90% 로 비교한다. 하나라도 아니거나 모르면(null) 100%.

응답: `items[]` 에 `month: null` 인 새 항목이 생길 수 있다. 계산하지 않으면 예전처럼 `notApplied[]` 에 이유가 들어간다.

| code | 계산 | estimated | 근거 |
|---|---|---|---|
| `DISMISSAL_NOTICE` | 1일 통상임금(시간급 × 하루 소정시간) × 30일. 3개월 미만 근무 또는 30일 이상 전에 알렸으면 0원. 퇴사일이 없으면 notApplied | 1~29일 전에 알렸으면 true (30일분 전부로 볼지 확인 필요) | 근로기준법 제26조 (3개월 미만 예외는 제26조 단서 제1호, 2019.1.15 개정 · 제35조는 삭제). 5인 미만도 적용(시행령 별표1) |
| `ANNUAL_LEAVE` | 1일 통상임금 × (생긴 연차 − 쓴 날). 1년 미만: 개근한 달 수(최대 11). 1년 이상: 15 + (햇수−1)÷2 (최대 25). 마지막 근무일까지 센다 — 딱 1년이면 15일은 안 생김 | 항상 true (출근율 80% 가정, 지난해 남은 연차 · 사용 촉진 반영 안 함) | 근로기준법 제60조. 5인 미만 · 재직 중 · `annualLeave` 없음 → notApplied |

새 warnings 코드: `DISMISSAL_CHECK`(천재지변 · 고의 손해 예외 확인), `ANNUAL_LEAVE_CHECK`, `PROBATION_MIN_WAGE`(수습 90% 로 비교함), `PROBATION_FULL_MIN_WAGE`(수습이지만 100%).
`MIN_WAGE_SHORTFALL` 의 formula 는 수습 날이 있는 달에 `(9,288.00원(수습 30일은 90%) − …)` 처럼 바뀐 기준을 보여 준다.

확인한 출처(2026-11-15): 근로기준법 제26조 · 제35조 삭제 · 제60조 · 시행령 별표1, 최저임금법 제5조 제2항 · 시행령 제3조 (korea.legal 원문 옮김 + law.go.kr 링크, law.go.kr 본문은 자바스크립트 화면이라 직접 못 읽음).
시험: `RuleEngineTest` 의 해고예고수당 2개 · 연차수당 2개 · 수습 1개 (손계산 값).

- `GET /data/guides.json` (화면 정적 파일): 안내 4개(임금명세서 · 함부로 떼기 · 외국인 보험 · 외국인 상담), `sources[]` 와 `checkedAt`.

## 7단계: 지연이자 · 휴업수당 (`POST /api/v1/calculations`)

요청에 아래 두 필드를 **더할 수 있다**. 모두 선택이고, 없으면 예전과 똑같이 계산한다(이때는 `notApplied` 에도 넣지 않는다).
```json
"delayInterest": { "baseDate": "2026-11-15" },
"shutdown":      { "days": 10, "paidAmount": 0 }
```
- `delayInterest.baseDate`: 이자를 어느 날까지 셀까. `null` 이면 서버의 오늘 날짜.
- `shutdown.days`: 회사 사정으로 쉰 날 수(0 이하면 계산 안 함). `shutdown.paidAmount`: 쉬는 동안 받은 돈(없으면 0, 음수면 400).

| code | 계산 | estimated | 근거 |
|---|---|---|---|
| `DELAY_INTEREST` | (다른 항목의 못 받은 돈 합 − 해고예고수당 · 휴업수당) × 20% × 늦은 날 ÷ 365. 늦은 날 = 그만둔 날(마지막 근무일 다음 날) 다음 날부터 14일이 지난 날 ~ `baseDate` (양 끝 포함). 아직 안 지났으면 0원. 재직 중이면 notApplied | 항상 true (14일 세는 법 · 해고예고수당이 임금인지 확인 필요) | 근로기준법 제37조 제1항, 시행령 제17조(연 100분의 20). 휴업수당은 고용노동부 회신(2020.4.28 근로기준정책과-1748, 2차 자료로만 확인)에 따라 뺌 |
| `SHUTDOWN_ALLOWANCE` | (1일 평균임금 × 쉰 날 − 받은 돈) × 70%. 1일 평균임금 = 약속한 월급(시급 · 일급은 시간급 × 월 기준시간) × 12 ÷ 365 로 추정. 평균임금 70%가 1일 통상임금보다 많으면 (통상임금 × 쉰 날 − 받은 돈). 5인 미만이면 notApplied | 항상 true (평균임금 추정, 노동위원회 승인 휴업 예외) | 근로기준법 제46조 제1항, 시행령 제26조. 5인 미만 제외는 시행령 별표1(제46조 없음) |

새 warnings 코드: `DELAY_INTEREST_CHECK`, `SHUTDOWN_CHECK`. `DELAY_INTEREST` 는 항상 `items[]` 맨 끝에 온다(다른 항목 합으로 계산하므로).

확인한 출처(2026-11-15): korea.legal 근로기준법 제37조 · 시행령 제17조 · 시행령 제26조 · 휴업수당 해설, 시행령 별표1 해설(2차 자료). law.go.kr 본문은 자바스크립트 화면이라 직접 못 읽음 — 노무사 확인 필요.
시험: `RuleEngineTest` 의 지연이자 1개 · 휴업수당 1개 (손계산 값).
화면: 상황 고르기에 `shut`(회사가 쉬게 했는데 돈을 안 줘요) · `over14`(그만둔 지 14일이 지났는데 못 받았어요). `shut` 은 7번 질문에서 쉰 날 · 받은 돈을 묻고, `over14` 는 퇴사했으면 `delayInterest: {baseDate: null}` 을 보낸다.
