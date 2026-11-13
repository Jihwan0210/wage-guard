# wageback

임금을 못 받은 근로자가 받아야 할 돈을 근로기준법 기준으로 계산하고, 쉬운 말로 설명하고, 노동청 진정서 초안까지 만들 수 있게 돕는 모바일 중심 웹 서비스.

2026 미래내일 일경험 프로젝트 · 팀 노란돌고래

## 실행

JDK 21이 필요하다.

```bash
./gradlew bootRun
```

Windows 명령 프롬프트에서는 `gradlew.bat bootRun`. 실행 후 http://localhost:8080 에 접속하면 화면이 열린다.

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
