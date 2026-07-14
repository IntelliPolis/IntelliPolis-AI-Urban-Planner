# IntelliPolis Backend

AI 기반 도시계획 의사결정 지원 플랫폼의 Spring Boot API입니다. 실제 행정 결정을 대신하지 않으며, 입력 데이터를 바탕으로 대안을 비교하는 MVP입니다.

## 기술 스택

Java 21, Spring Boot 4, Gradle, Spring Web MVC, Bean Validation, Spring AI Google GenAI, JUnit 5를 사용합니다. 저장소는 `ConcurrentHashMap`이며 MySQL/JPA는 아직 적용하지 않았습니다.

## 실행

```powershell
Copy-Item .env.example .env
.\gradlew.bat bootRun
```

환경변수는 `GEMINI_API_KEY`, `GEMINI_MODEL`, `FRONTEND_ORIGIN`입니다. Gemini를 켤 때는 `SPRING_AI_MODEL_CHAT=google-genai`도 설정합니다. 키가 없거나 AI 호출이 실패하면 규칙 기반 fallback으로 API가 계속 동작합니다.

## API

- `GET /api/health`
- `GET /api/cities/sample`
- `POST /api/city-analyses`
- `GET /api/city-analyses/{analysisId}`
- `POST /api/city-analyses/{analysisId}/plans/{planType}/evaluate`
- `POST /api/city-analyses/{analysisId}/plans/{planType}/explain`
- `GET /api/urban-data/regions`
- `GET /api/urban-data/summary?city=부산광역시&district=해운대구`
- `GET /api/vworld/status`
- `GET /api/vworld/search?query=해운대구`

샘플 요청은 `GET /api/cities/sample` 응답을 그대로 `POST /api/city-analyses`에 전송할 수 있습니다.

점수는 접근 거리, 시설 수, 공원 비율, 혼잡 도로, 예산을 이용한 프로젝트용 규칙 기반 휴리스틱입니다. 정밀한 도시계획·교통 모델이 아닙니다. Java가 객관 지표와 예상 점수를 계산하고 AI는 해석과 참고 설명만 담당합니다.

향후 영속 저장소, 정교한 공간 검증, 에이전트 구조화 출력과 운영 관측 기능을 확장할 수 있습니다.

## 도시 원본 데이터

원본 CSV/TXT는 Git에 올리지 않고 `data/raw`에 둡니다. `URBAN_DATA_RAW_PATH`로 다른 경로를 지정할 수 있습니다. 현재 요약 API는 법정동, 상권, 공원, 학교, 버스정류장, 지방재정 파일을 스트리밍 집계하며 첫 조회 결과를 메모리에 캐시합니다. CP949와 UTF-8 원본은 파일별 인코딩으로 읽습니다.
