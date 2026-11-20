# 실제 체불 사례 파일

이 폴더의 `*.json` 파일 하나가 시험 하나다 (`CaseFilesTest`). 파일만 넣으면 `gradlew test` 때 같이 돈다.

## 규칙

- **정답 금액은 출처가 있는 값만** 넣는다: 판결문, 노동청·공개 사례, 고용노동부 계산기 결과.
- 출처를 모르면 넣지 않는다. 금액을 지어내지 않는다.
- 팀이 직접 계산한 예시는 `source` 에 `팀 손계산` 이라고 적고, `handCalculation` 에 계산 과정을 남긴다.
- 개인정보(이름·회사명·주소)는 넣지 않는다.
- 파일 이름: `출처종류-번호-짧은설명.json` (예: `moel-03-under5-night.json`, `court-07-severance.json`)

## 형식

```json
{
  "name": "사례 한 줄 설명",
  "source": "출처 (판결 사건번호, 공개 사례 링크, 계산기 화면 캡처 위치 등)",
  "request": { "...": "POST /api/v1/calculations 요청 그대로 (API.md)" },
  "expected": {
    "totalUnpaid": 4957469,
    "items": [
      { "code": "BASE_WAGE", "month": "2026-09", "amount": 456880 },
      { "code": "SEVERANCE", "month": null, "amount": 4317017 }
    ]
  },
  "handCalculation": ["(선택) 계산 과정 메모"]
}
```

- `expected.items` 에는 출처에 금액이 나온 항목만 적는다. 적은 항목만 비교한다.
- `amount: null` 은 `NO_DATA` / `INCLUDED_IN_WAGE` (금액 없음) 이라는 뜻.
- `month` 는 퇴직금처럼 달이 없는 항목이면 `null`.
- `totalUnpaid` 는 출처에 총액이 있을 때만 적고, 없으면 `null`.
- `basis` (선택): 근거 조문과 출처 주소 · 확인일을 한 줄씩. 시험은 읽지 않지만 사람이 검산할 때 본다 (team-02 ~ team-06 참고).
- 날짜가 든 사례는 공휴일이 끼지 않았는지 확인한다. 5인 이상 사업장에서 공휴일 근무는 연장이 아니라 휴일근로다.
