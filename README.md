[발표 자료 HTML 다운로드](https://github.com/IntelliPolis/IntelliPolis-AI-Urban-Planner/raw/refs/heads/main/docs/index.html?download=)

발표용 html 올렸습니다. 클릭 후 컨트롤 s 하시고 열람 부탁드립니다.

# IntelliPolis-AI-Urban-Planner

# IntelliPolis

> AI 기반 도시계획 의사결정 지원 플랫폼

IntelliPolis는 도시계획 담당자와 지역 주민을 위해 인구, 교통, 환경, 공공시설, 예산 데이터를 분석하고, Spring AI 기반 멀티 에이전트가 여러 관점의 도시계획 대안을 제안하는 웹 서비스입니다.

AI가 실제 행정 결정을 대신하는 것이 아니라, 다양한 계획안을 비교하고 검토할 수 있도록 지원하는 것을 목표로 합니다.

---

## 프로젝트 소개

도시계획은 교통, 환경, 경제성, 생활 편의성처럼 서로 충돌할 수 있는 여러 요소를 함께 고려해야 합니다.

예를 들어 도로를 확장하면 교통 흐름은 개선될 수 있지만 녹지가 줄어들 수 있고, 대형 공공시설을 추가하면 주민 편의성은 높아지지만 많은 예산이 필요합니다.

IntelliPolis는 이러한 도시 데이터를 바탕으로 분야별 AI 에이전트가 각각의 관점에서 문제를 분석하고, 최종적으로 현실적인 도시계획 대안을 생성합니다.

---

## 프로젝트 설명

> 우리 프로젝트는 도시계획 담당자와 지역 주민을 위해 인구, 교통, 환경, 공공시설과 예산 데이터를 받아 Spring AI가 분야별 분석, 문제 분류, 개선안 추천과 계획안 생성을 수행하고, 사용자에게 지도 기반 도시계획 대안과 계획별 예상 효과를 제공하는 서비스입니다.

---

## 팀명 의미

**IntelliPolis**는 다음 두 단어를 결합한 이름입니다.

* **Intelligence**: 인공지능과 데이터 기반 분석
* **Polis**: 고대 그리스어로 도시 또는 도시국가

즉, IntelliPolis는 AI를 활용해 더 나은 도시계획 의사결정을 지원한다는 의미를 담고 있습니다.

---

## 팀원 및 역할 분담

| 이름      | 담당 역할                   | 주요 업무                                                                                                   |
| ------- | ----------------------- | ------------------------------------------------------------------------------------------------------- |
| **정혁**  | 팀장 · GitHub 관리 · 백엔드 개발 | GitHub Organization 및 Repository 관리, 브랜치·PR·Merge 관리, Spring Boot API 개발, 데이터 검증, 도시 점수 계산 로직, 프론트엔드 연동 |
| **유서하** | 프론트엔드 개발 · Spring AI 관리 | React 주요 화면 및 기능 개발, 백엔드 API 연동, Spring AI 설정, Gemini 모델 연결, 멀티 에이전트 프롬프트 및 AI 응답 관리                    |
| **이민재** | 프론트엔드 UI·UX · 디자인       | CSS 스타일링, 반응형 레이아웃, 지도 및 대시보드 화면 구성, 색상·컴포넌트 디자인, 발표용 화면 완성도 개선                                         |

## 주요 기능

### 도시 데이터 입력

사용자는 분석할 지역의 주요 도시 데이터를 입력할 수 있습니다.

* 도시 및 행정구역 이름
* 총인구와 면적
* 연령별 인구 비율
* 공원 면적 비율
* 병원과 학교 수
* 대중교통 거점 수
* 주요 혼잡 도로
* 시설별 평균 접근 거리
* 도시계획 예산
* 우선 개선 목표

### 현재 도시 상태 평가

입력된 데이터를 바탕으로 Java 기반 규칙 계산기가 도시의 현재 상태를 평가합니다.

* 교통 점수
* 환경 점수
* 경제 점수
* 생활 편의 점수
* 종합 점수

AI가 점수를 임의로 생성하지 않으며, 객관적인 점수는 백엔드 계산 로직이 담당합니다.

### AI 멀티 에이전트 분석

동일한 Gemini 모델을 사용하되, 시스템 프롬프트를 다르게 설정한 AI 에이전트가 분야별 분석을 수행합니다.

| 에이전트    | 역할                         |
| ------- | -------------------------- |
| 교통 에이전트 | 혼잡 도로, 대중교통, 보행 및 이동 편의 분석 |
| 환경 에이전트 | 공원, 녹지, 보행환경, 지속 가능성 분석    |
| 경제 에이전트 | 예산, 비용 대비 효과, 기존 시설 활용 분석  |
| 생활 에이전트 | 병원, 학교, 문화시설, 주민 생활 편의 분석  |
| 조정 에이전트 | 분야별 의견을 종합해 최종 계획안 생성      |

### 도시계획 대안 생성

AI는 서로 다른 방향을 가진 세 가지 도시계획 대안을 생성합니다.

* **균형형**: 교통, 환경, 경제, 생활을 균형 있게 개선
* **환경 중심형**: 녹지, 보행환경, 대중교통 중심
* **예산 효율형**: 기존 시설과 인프라를 최대한 활용

### 지도 기반 시각화

생성된 계획안을 지도 위에서 확인할 수 있습니다.

* 제안 시설 위치
* 신규 도로 및 대중교통 노선
* 공원 및 개발 구역
* 기존 도시와 AI 계획안 비교
* GeoJSON 기반 공간 데이터 표현
* 간단한 2D 및 3D 전환

---

## 서비스 흐름

```text
도시 데이터 입력
        ↓
백엔드 데이터 검증
        ↓
현재 도시 점수 계산
        ↓
교통·환경·경제·생활 AI 분석
        ↓
분야별 의견 종합
        ↓
도시계획 대안 3개 생성
        ↓
백엔드 예상 점수 재계산
        ↓
React 대시보드 및 지도 시각화
```

---

## AI와 백엔드의 역할

IntelliPolis는 모든 판단을 AI에게 맡기지 않습니다.

### Spring AI

* 도시 데이터 해석
* 도시 문제점 설명
* 개선 방향 제안
* 분야별 상충 관계 분석
* 계획안 설명 생성
* 시설 및 개발 후보 제안

### Java 백엔드

* 입력 데이터 검증
* 접근성 및 점수 계산
* 예산 초과 검사
* 좌표 유효성 검사
* 계획안 예상 점수 계산
* AI 응답 구조 검증
* AI 실패 시 대체 결과 제공

```text
정확한 수치 계산 → Java

수치에 대한 해석과 제안 → Spring AI
```

---

## 기술 스택

### Backend

* Java 21
* Spring Boot
* Spring Web MVC
* Spring AI
* Gemini 3.1 Flash-Lite
* Bean Validation
* Gradle
* JUnit 5
* MySQL 선택 적용

### Frontend

* React
* Vite
* TypeScript
* React Router
* MapLibre GL JS
* Recharts
* GeoJSON
* CSS

---

## 프로젝트 구조

```text
intellipolis/
├── README.md
├── backend/
│   ├── src/
│   ├── build.gradle
│   └── README.md
└── frontend/
    ├── src/
    ├── package.json
    └── README.md
```

---

## 주요 API

| Method | Endpoint                                                    | 설명             |
| ------ | ----------------------------------------------------------- | -------------- |
| GET    | `/api/health`                                               | 백엔드 상태 확인      |
| GET    | `/api/cities/sample`                                        | 샘플 도시 데이터 조회   |
| POST   | `/api/city-analyses`                                        | 도시 분석 및 계획안 생성 |
| GET    | `/api/city-analyses/{analysisId}`                           | 분석 결과 조회       |
| POST   | `/api/city-analyses/{analysisId}/plans/{planType}/evaluate` | 수정된 계획안 재평가    |
| POST   | `/api/city-analyses/{analysisId}/plans/{planType}/explain`  | 계획안 설명 생성      |

---

## 분석 요청 예시

```json
{
  "cityName": "부산광역시",
  "districtName": "가상 해안구",
  "population": 320000,
  "areaKm2": 48.5,
  "elderlyRatio": 22.4,
  "youthRatio": 14.2,
  "parkAreaRatio": 8.1,
  "hospitalCount": 12,
  "schoolCount": 34,
  "transitHubCount": 8,
  "averageHospitalDistanceKm": 2.3,
  "averageParkDistanceKm": 1.8,
  "averageTransitDistanceKm": 1.1,
  "congestedRoads": [
    "해안대로",
    "중앙로"
  ],
  "totalBudget": 80000000000,
  "priorityGoals": [
    "교통 혼잡 개선",
    "녹지 접근성 향상"
  ],
  "mapCenter": {
    "latitude": 35.16,
    "longitude": 129.16
  },
  "boundary": []
}
```

---

## 실행 방법

### Backend

```bash
cd backend
```

Windows PowerShell:

```powershell
.\gradlew.bat bootRun
```

macOS 또는 Linux:

```bash
./gradlew bootRun
```

기본 실행 주소:

```text
http://localhost:8080
```

### Frontend

```bash
cd frontend
npm install
npm run dev
```

기본 실행 주소:

```text
http://localhost:5173
```

---

## 환경변수

### Backend

```env
GEMINI_API_KEY=YOUR_API_KEY
GEMINI_MODEL=gemini-3.1-flash-lite
FRONTEND_ORIGIN=http://localhost:5173
```

### Frontend

```env
VITE_API_BASE_URL=http://localhost:8080
VITE_MAP_STYLE_URL=https://demotiles.maplibre.org/style.json
VITE_USE_MOCK=false
```

API 키와 비밀번호는 코드 또는 GitHub 저장소에 직접 업로드하지 않습니다.

---

## MVP 범위

현재 MVP에서 구현할 핵심 기능입니다.

* 도시 데이터 입력
* 샘플 데이터 불러오기
* 현재 도시 점수 계산
* 분야별 AI 분석
* 도시계획 대안 3개 생성
* 계획안별 예상 점수 계산
* 분석 결과 대시보드
* 계획안 비교
* 지도 기반 시설 및 개발구역 표시
* 간단한 2D 및 3D 표현
* AI 호출 실패 시 fallback

---

## 현재 제외 범위

초기 버전에서는 다음 기능을 구현하지 않습니다.

* 로그인 및 회원가입
* 관리자 페이지
* 실제 행정기관 시스템 연동
* 완전한 도시 디지털 트윈
* 정밀 교통 시뮬레이션
* 실제 법적 효력이 있는 도시계획 수립
* 복잡한 3D 건축 모델
* 실시간 협업
* MCP 및 RAG
* 결제 기능

---

## 프로젝트의 차별점

일반적인 생성형 AI 서비스는 사용자 질문에 텍스트 답변만 제공합니다.

IntelliPolis는 AI가 생성한 내용을 구조화된 도시계획 데이터로 변환하고, 백엔드 알고리즘으로 다시 검증한 뒤 지도에 시각화합니다.

```text
AI의 창의적인 제안
+
Java의 규칙 기반 계산
+
지도 기반 시각화
```

이를 통해 단순한 AI 챗봇이 아니라 도시계획 의사결정을 지원하는 웹 플랫폼을 구현합니다.

---

## 향후 확장 방향

* 공공데이터 API 연동
* 실제 행정구역 경계 데이터 적용
* MySQL 기반 분석 결과 저장
* 사용자 계획안 직접 수정
* 시설 위치 드래그 및 재평가
* 장래 인구 변화 예측
* 재난 및 침수 위험 분석
* 실제 생활인구 데이터 분석
* 도시계획 보고서 PDF 생성
* 공공데이터 조회 MCP 도구 확장

---

## 주의사항

IntelliPolis에서 제공하는 도시계획 대안과 점수는 프로젝트용 규칙과 AI 분석을 기반으로 생성된 참고 결과입니다.

실제 도시계획, 행정 결정 또는 전문적인 도시공학 분석을 대신하지 않습니다.

---

## Team IntelliPolis

**Designing Smarter Cities with AI**

# 설계·계약 문서 (Day 2 산출물) — 팀 IntelliPolis

> 저장 위치: `docs/design.md`  
> 프론트엔드와 백엔드는 이 문서의 변수명·타입·URL·상태 코드·JSON 구조를 그대로 사용한다.  
> 계약을 변경할 때는 **문서를 먼저 수정하고**, 같은 PR에서 Java DTO와 TypeScript 타입을 함께 변경한다.

---

## 0. 메타

| 항목 | 내용 |
|---|---|
| 팀 | IntelliPolis |
| 작성일 | 2026-07-13 |
| 관련 Issue / PR | 미정 — Issue 생성 후 번호 연결 |
| 스택 | Java 21 · Spring Boot 4.1.x · Spring AI 2.0.0 · Gemini(`gemini-3.1-flash-lite`) · React(Vite) · base 패키지 `com.study` |
| MVP 분석 범위 | 서울특별시와 6개 광역시의 **자치구·군 단위** |
| 현재 구현 상태 | React UI·샘플 데이터 모드 구현 완료, 실제 공공데이터·Spring Boot·지도·AI 연동 전 |

---

## 1. 문제·사용자 (한 문장)

- **사용자**: 서울 및 광역시의 자치구·군 단위 도시 현황을 비교하고 개선안을 검토해야 하는 도시계획 담당자·연구자·행정 실무자
- **문제**: 인구·교통·병원·학교·공원 데이터가 여러 기관과 형식으로 흩어져 있어 같은 기준으로 비교하기 어렵고, 계산 결과를 계획안으로 설명하는 작업이 반복된다.
- **한 문장 정의**:

  > "우리 팀은 **자치구·군 단위 도시계획 담당자**를 위해 **흩어진 도시 데이터를 같은 기준으로 비교하고 개선 대안을 검토하기 어려운 문제**를 **AI가 Java 계산 결과를 설명하고 세 가지 도시계획안을 구조화해 제안하는 방식**으로 풀어 준다."

### 프로젝트 원칙

1. **숫자·비율·점수는 Java가 계산한다.**
2. **AI는 계산된 값을 변경하지 않고 설명·개선안·계획안을 생성한다.**
3. 실제 공공데이터가 연결되기 전에는 화면에 **샘플 데이터**임을 표시한다.
4. 공공데이터 API 키는 브라우저와 Git 저장소에 노출하지 않는다.

---

## 2. 기능 범위 — MoSCoW

| 구분 | 기능 |
|---|---|
| **Must** (절대 사수) | ① 사용자가 자치구·군의 정규화된 도시 지표를 입력한다. ② Spring Boot가 입력을 검증하고 도시 점수를 Java 규칙으로 계산한다. ③ Spring AI가 계산 결과를 설명하고 `BALANCED`·`ECO`·`BUDGET` 계획안 3개를 구조화 출력한다. ④ 분석 입력·점수·AI 결과를 단일 DB 테이블에 저장한다. ⑤ React가 점수·인사이트·계획안 비교 결과와 로딩·오류 상태를 표시한다. |
| **Should** (시간 허락 시) | 분석 결과 단건 재조회, 최근 분석 목록, 실제 2D 지도에 시설·개발 후보 구역 표시, 공공데이터 CSV 1종 연결 |
| **Could** (이번 MVP에서 과감히 포기) | 실시간 전국 API 자동 수집, 완전한 3D 도시 모델, 월별 자동 배치, 행정동 단위 분석, 복잡한 공간 거리 계산, 사용자 인증·권한 관리 |

- **오늘(Day 2) 코드로 증명할 1개**: `POST /api/analyses` 요청으로 Gemini를 1회 호출하고 `UrbanAiResult` 구조로 응답받기

---

## 3. 핵심 시나리오 (MVP 한 흐름)

| 구분 | 흐름 |
|---|---|
| 입력 (User) | 사용자가 도시·자치구, 기준일, 인구·면적·시설·교통 지표를 입력하고 **AI 도시 분석 시작**을 클릭한다. |
| 전송 (System) | React가 백엔드 `POST /api/analyses`로 `UrbanAnalysisRequest` JSON을 전송한다. |
| 처리 (System) | Spring Boot가 입력을 검증하고 `UrbanScoreCalculator`로 점수를 계산한 뒤, Spring AI가 점수의 의미·인사이트·계획안 3개를 생성한다. |
| 저장 (System) | 입력 스냅샷, 점수 스냅샷, AI 구조화 결과, 점수 규칙 버전과 생성 시각을 `urban_analysis`에 저장한다. |
| 출력 (System) | React에 종합·교통·환경·경제·생활 점수, AI 인사이트, `BALANCED`·`ECO`·`BUDGET` 계획안이 표시된다. |

### 화면 상태

| 상태 | React 표시 |
|---|---|
| `idle` | 입력 폼과 샘플 데이터 안내 |
| `loading` | 분석 버튼 비활성화 + "도시 데이터를 분석하고 있습니다" |
| `success` | 점수·인사이트·계획안 비교 화면 |
| `error` | 서버의 `message` 표시 + 다시 시도 버튼 |

---

## 4. 아키텍처 · 책임 분리 (Separation of Concerns)

```text
React (Vite) ──HTTP/JSON──> Spring Boot (com.study.intellipolis)
                                     │
                                     ├──> Java 점수 계산
                                     ├──> Spring AI ──> Gemini
                                     └──> DB (urban_analysis)
```

| 레이어 | 책임 |
|---|---|
| **React** | 사용자 입력 수신, 요청 JSON 생성, API 호출, 로딩·성공·오류 상태 관리, 점수·인사이트·계획안 표시 |
| **Controller** | HTTP 요청 수신, Bean Validation 실행, Service 호출, 계약된 상태 코드 반환 |
| **Service** | 트랜잭션 경계, Java 점수 계산 호출, AI 프롬프트 구성, AI 구조화 출력 검증, 저장 지시 |
| **Score Calculator** | 비율·시설·교통 입력을 기반으로 0~100 점수 계산. AI 호출 금지 |
| **Spring AI** | `ChatClient`로 Gemini 호출, `UrbanAiResult` 구조화 출력 생성 |
| **Repository** | `urban_analysis` 저장 및 단건 조회 |

### 권장 패키지 구조

```text
com.study.intellipolis
├── controller
├── service
├── score
├── dto
│   ├── request
│   └── response
├── domain
├── repository
├── exception
└── config
```

### 데이터 처리 흐름

```text
공공 CSV/API 또는 샘플 입력
→ 정규화
→ Java 점수 계산
→ Spring AI 설명·대안 생성
→ DB 저장
→ React 대시보드 표시
```

---

## 5. API 계약 ⭐

### 5.1 공통 규칙

- Base URL: `http://localhost:8080`
- Content-Type: `application/json`
- 날짜: `YYYY-MM-DD`
- 날짜·시간: ISO-8601 (`YYYY-MM-DDTHH:mm:ss`)
- JSON 필드명: **camelCase**
- 점수 범위: 정수 `0`~`100`
- 계획안 배열 순서: `priority` 오름차순
- 성공 응답에서 `plans`는 반드시 3개이며 `BALANCED`, `ECO`, `BUDGET`을 각각 1개씩 포함한다.
- 값이 없는 접근 거리 필드는 임의 생성하지 않고 `null`로 전송한다.

### 5.2 엔드포인트 목록

| Method | 경로 | 설명 | MVP 구분 |
|---|---|---|---|
| `POST` | `/api/analyses` | 도시 지표 검증 → 점수 계산 → AI 분석 → DB 저장 | Must |
| `GET` | `/api/analyses/{id}` | 저장된 분석 결과 단건 조회 | Should |
| `GET` | `/api/analyses` | 최근 분석 결과 목록 조회 | Could |

---

### 5.3 상세 — `POST /api/analyses`

#### 요청 (Request Body)

```json
{
  "regionCode": "1168000000",
  "cityName": "서울특별시",
  "districtName": "강남구",
  "baseDate": "2026-06-30",
  "population": 560000,
  "areaKm2": 39.5,
  "elderlyRatio": 16.79,
  "youthRatio": 14.64,
  "parkAreaRatio": 9.2,
  "hospitalCount": 120,
  "schoolCount": 80,
  "transitHubCount": 25,
  "congestionRoadCount": 8,
  "averageHospitalDistanceKm": 0.8,
  "averageParkDistanceKm": 1.1,
  "averageTransitDistanceKm": 0.6
}
```

| 필드 | JSON 타입 | Java 타입 | 필수 | 검증·설명 |
|---|---|---|---|---|
| `regionCode` | string | `String` | ✅ | 10자리 행정구역 코드 |
| `cityName` | string | `String` | ✅ | 1~30자, 예: `서울특별시` |
| `districtName` | string | `String` | ✅ | 1~30자, 예: `강남구` |
| `baseDate` | string | `LocalDate` | ✅ | 데이터 기준일, `YYYY-MM-DD` |
| `population` | integer | `Long` | ✅ | `0` 이상 |
| `areaKm2` | number | `Double` | ✅ | `0` 초과 |
| `elderlyRatio` | number | `Double` | ✅ | `0`~`100` |
| `youthRatio` | number | `Double` | ✅ | `0`~`100`, 프로젝트에서 정한 연령 기준 사용 |
| `parkAreaRatio` | number | `Double` | ✅ | `0`~`100` |
| `hospitalCount` | integer | `Integer` | ✅ | `0` 이상 |
| `schoolCount` | integer | `Integer` | ✅ | `0` 이상 |
| `transitHubCount` | integer | `Integer` | ✅ | `0` 이상 |
| `congestionRoadCount` | integer | `Integer` | ✅ | `0` 이상 |
| `averageHospitalDistanceKm` | number 또는 null | `Double` | ❌ | 아직 계산하지 못했으면 `null`, 값이 있으면 `0` 이상 |
| `averageParkDistanceKm` | number 또는 null | `Double` | ❌ | 아직 계산하지 못했으면 `null`, 값이 있으면 `0` 이상 |
| `averageTransitDistanceKm` | number 또는 null | `Double` | ❌ | 아직 계산하지 못했으면 `null`, 값이 있으면 `0` 이상 |

#### 응답 — 성공 `200 OK`

```json
{
  "id": 1,
  "regionCode": "1168000000",
  "cityName": "서울특별시",
  "districtName": "강남구",
  "baseDate": "2026-06-30",
  "sampleData": false,
  "scoreVersion": "MVP_RULE_V1",
  "scores": {
    "overall": 72,
    "transport": 70,
    "environment": 65,
    "economy": 80,
    "living": 72
  },
  "summary": "교통 거점은 충분하지만 공원 접근성 보완이 필요한 지역입니다.",
  "insights": [
    {
      "category": "TRANSPORT",
      "severity": "MEDIUM",
      "message": "혼잡 도로 주변의 대중교통 연결을 강화할 필요가 있습니다."
    },
    {
      "category": "ENVIRONMENT",
      "severity": "HIGH",
      "message": "공원 접근성이 낮은 생활권을 우선 개선해야 합니다."
    }
  ],
  "plans": [
    {
      "type": "BALANCED",
      "title": "균형형 계획안",
      "description": "교통과 생활 인프라를 함께 개선합니다.",
      "actions": [
        "혼잡 구간과 환승 거점을 연결합니다.",
        "공원 접근성이 낮은 지역에 생활권 공원을 배치합니다."
      ],
      "expectedEffects": [
        "대중교통 접근성 개선",
        "생활권 녹지 접근성 개선"
      ],
      "priority": 1
    },
    {
      "type": "ECO",
      "title": "환경 중심형 계획안",
      "description": "녹지와 보행 연결을 우선 확충합니다.",
      "actions": ["공원과 주거지를 잇는 녹지 보행축을 만듭니다."],
      "expectedEffects": ["공원 접근성 개선"],
      "priority": 2
    },
    {
      "type": "BUDGET",
      "title": "예산 효율형 계획안",
      "description": "기존 시설의 연결성과 이용률을 먼저 높입니다.",
      "actions": ["기존 환승 거점의 연결 동선을 개선합니다."],
      "expectedEffects": ["신규 건설을 줄이고 기존 시설 활용도 향상"],
      "priority": 3
    }
  ],
  "createdAt": "2026-07-13T10:30:00"
}
```

| 필드 | 타입 | 설명 |
|---|---|---|
| `id` | number | 저장된 분석 식별자 |
| `regionCode` | string | 요청과 동일한 행정구역 코드 |
| `cityName` | string | 광역자치단체명 |
| `districtName` | string | 자치구·군명 |
| `baseDate` | string | 분석 데이터 기준일 |
| `sampleData` | boolean | 샘플 데이터 사용 여부 |
| `scoreVersion` | string | Java 점수 계산 규칙 버전. MVP는 `MVP_RULE_V1` |
| `scores` | object | Java가 계산한 점수 객체 |
| `scores.overall` | integer | 종합 점수 `0`~`100` |
| `scores.transport` | integer | 교통 점수 `0`~`100` |
| `scores.environment` | integer | 환경 점수 `0`~`100` |
| `scores.economy` | integer | 경제·예산 효율 점수 `0`~`100` |
| `scores.living` | integer | 생활 점수 `0`~`100` |
| `summary` | string | AI의 전체 분석 요약 |
| `insights` | array | 분야별 AI 인사이트 |
| `insights[].category` | enum | `TRANSPORT`, `ENVIRONMENT`, `ECONOMY`, `LIVING` |
| `insights[].severity` | enum | `HIGH`, `MEDIUM`, `LOW` |
| `insights[].message` | string | 근거 점수에 대한 설명 |
| `plans` | array | 정확히 3개의 계획안 |
| `plans[].type` | enum | `BALANCED`, `ECO`, `BUDGET` |
| `plans[].title` | string | 화면에 표시할 계획안 제목 |
| `plans[].description` | string | 계획안 요약 |
| `plans[].actions` | string[] | 실행 항목, 1~5개 |
| `plans[].expectedEffects` | string[] | 예상 효과, 1~5개 |
| `plans[].priority` | integer | 표시 순서 `1`~`3` |
| `createdAt` | string | 서버 저장 시각, ISO-8601 |

#### 응답 — 오류 (모든 API 공통 형태)

```json
{
  "code": "INVALID_INPUT",
  "message": "population은 0 이상이어야 합니다.",
  "timestamp": "2026-07-13T10:30:00",
  "path": "/api/analyses"
}
```

| 상태 코드 | `code` | 상황 | 프론트 처리 |
|---|---|---|---|
| `400 Bad Request` | `INVALID_INPUT` | 필수값 누락, 범위 오류, 형식 오류 | `message`를 입력 폼 위에 표시 |
| `404 Not Found` | `ANALYSIS_NOT_FOUND` | 요청한 분석 ID가 없음 | 목록으로 이동할 수 있는 버튼 표시 |
| `500 Internal Server Error` | `AI_ANALYSIS_FAILED` | Gemini 호출 실패 또는 AI 구조화 출력 검증 실패 | "AI 분석에 실패했습니다. 잠시 후 다시 시도해주세요." 표시 |
| `500 Internal Server Error` | `INTERNAL_ERROR` | 저장 또는 알 수 없는 서버 오류 | 공통 오류 메시지와 다시 시도 버튼 표시 |

> AI 호출·구조화 출력 검증·DB 저장은 하나의 Service 흐름으로 처리한다. 실패하면 `urban_analysis` 저장은 **롤백**한다.

---

### 5.4 상세 — `GET /api/analyses/{id}`

- Path Variable: `id` (`Long`, 1 이상)
- 성공: `200 OK`, `POST /api/analyses` 성공 응답과 동일한 JSON
- 실패: `404 Not Found`, `code = ANALYSIS_NOT_FOUND`

---

### 5.5 TypeScript 계약

```ts
export type UrbanAnalysisRequest = {
  regionCode: string;
  cityName: string;
  districtName: string;
  baseDate: string;
  population: number;
  areaKm2: number;
  elderlyRatio: number;
  youthRatio: number;
  parkAreaRatio: number;
  hospitalCount: number;
  schoolCount: number;
  transitHubCount: number;
  congestionRoadCount: number;
  averageHospitalDistanceKm: number | null;
  averageParkDistanceKm: number | null;
  averageTransitDistanceKm: number | null;
};

export type UrbanScores = {
  overall: number;
  transport: number;
  environment: number;
  economy: number;
  living: number;
};

export type InsightCategory =
  | "TRANSPORT"
  | "ENVIRONMENT"
  | "ECONOMY"
  | "LIVING";

export type InsightSeverity = "HIGH" | "MEDIUM" | "LOW";
export type PlanType = "BALANCED" | "ECO" | "BUDGET";

export type UrbanInsight = {
  category: InsightCategory;
  severity: InsightSeverity;
  message: string;
};

export type UrbanPlan = {
  type: PlanType;
  title: string;
  description: string;
  actions: string[];
  expectedEffects: string[];
  priority: number;
};

export type UrbanAnalysisResponse = {
  id: number;
  regionCode: string;
  cityName: string;
  districtName: string;
  baseDate: string;
  sampleData: boolean;
  scoreVersion: string;
  scores: UrbanScores;
  summary: string;
  insights: UrbanInsight[];
  plans: UrbanPlan[];
  createdAt: string;
};

export type ApiErrorResponse = {
  code:
    | "INVALID_INPUT"
    | "ANALYSIS_NOT_FOUND"
    | "AI_ANALYSIS_FAILED"
    | "INTERNAL_ERROR";
  message: string;
  timestamp: string;
  path: string;
};

export type AnalysisUiState = "idle" | "loading" | "success" | "error";
```

---

### 5.6 Java 응답 record 계약

```java
public record UrbanScores(
    int overall,
    int transport,
    int environment,
    int economy,
    int living
) {}

public record UrbanInsight(
    String category,
    String severity,
    String message
) {}

public record UrbanPlan(
    String type,
    String title,
    String description,
    List<String> actions,
    List<String> expectedEffects,
    int priority
) {}

public record UrbanAiResult(
    String summary,
    List<UrbanInsight> insights,
    List<UrbanPlan> plans
) {}
```

---

## 6. 데이터 모델 (DB 스키마)

> 5일 MVP에서는 단일 테이블을 사용한다. 입력·점수·AI 결과는 JSON 문자열로 스냅샷 저장하여 재조회 시 당시 결과를 그대로 재현한다.

### `urban_analysis`

| 컬럼 | Java 타입 | DB 타입 예시 | 필수 | 설명 |
|---|---|---|---|---|
| `id` | `Long` | `BIGINT` PK, auto | ✅ | 식별자 |
| `regionCode` | `String` | `VARCHAR(10)` | ✅ | 행정구역 코드 |
| `cityName` | `String` | `VARCHAR(30)` | ✅ | 광역자치단체명 |
| `districtName` | `String` | `VARCHAR(30)` | ✅ | 자치구·군명 |
| `baseDate` | `LocalDate` | `DATE` | ✅ | 입력 데이터 기준일 |
| `sampleData` | `Boolean` | `BOOLEAN` | ✅ | 샘플 데이터 여부 |
| `scoreVersion` | `String` | `VARCHAR(30)` | ✅ | 점수 계산 규칙 버전 |
| `inputJson` | `String` (`@Lob`) | `TEXT` | ✅ | `UrbanAnalysisRequest` 전체 스냅샷 |
| `scoreJson` | `String` (`@Lob`) | `TEXT` | ✅ | `UrbanScores` 스냅샷 |
| `aiResultJson` | `String` (`@Lob`) | `TEXT` | ✅ | `UrbanAiResult` 스냅샷 |
| `createdAt` | `LocalDateTime` | `TIMESTAMP` | ✅ | 생성 시각 |

### 저장 규칙

- AI 호출과 출력 검증이 성공한 뒤에만 저장한다.
- `inputJson`, `scoreJson`, `aiResultJson`은 Jackson `ObjectMapper`로 직렬화한다.
- AI 호출 실패 시 실패 레코드를 남기지 않고 전체 트랜잭션을 롤백한다.
- 실제 공공데이터 원본 CSV는 DB가 아닌 `backend/data/raw`에 별도 보관하고 Git 포함 여부를 팀이 결정한다.

---

## 7. AI 연동 설계

### 7.1 프롬프트 해부학

`[시스템 역할]` + `[작업 지시]` + `[출력 규칙]` + `[Java 계산 결과·사용자 입력]` = 완성 프롬프트

| 파트 | 내용 |
|---|---|
| 역할 | "당신은 한국의 자치구·군 단위 도시 지표를 분석하는 도시계획 의사결정 지원 전문가입니다." |
| 작업 | "Java가 계산한 점수와 입력 지표를 근거로 지역의 핵심 문제를 설명하고 균형형·환경 중심형·예산 효율형 계획안을 각각 1개씩 작성하세요." |
| 출력 형식 | "`summary`, `insights`, `plans`를 포함하는 JSON 구조로 반환하세요. `plans`는 `BALANCED`, `ECO`, `BUDGET`을 각각 하나씩 포함하세요." |
| 금지 규칙 | "제공되지 않은 통계·시설·비용을 사실처럼 만들지 마세요. 점수를 다시 계산하거나 변경하지 마세요. 값이 `null`이면 측정되지 않았다고 판단하세요." |
| 사용자 입력 (동적) | `UrbanAnalysisRequest`와 Java가 만든 `UrbanScores` JSON |

### 7.2 프롬프트 예시

```text
[SYSTEM]
당신은 한국의 자치구·군 단위 도시 지표를 분석하는 도시계획 의사결정 지원 전문가입니다.
모든 답변은 한국어로 작성합니다.
Java가 계산한 점수는 변경하거나 다시 계산하지 않습니다.
제공되지 않은 사실과 수치를 만들지 않습니다.

[USER]
아래 도시 입력과 계산 점수를 근거로 분석하세요.

도시 입력:
{requestJson}

Java 계산 점수:
{scoresJson}

다음을 생성하세요.
1. 전체 요약 1개
2. 분야별 인사이트 2~4개
3. BALANCED, ECO, BUDGET 계획안 각각 1개
```

### 7.3 구조화 출력 (평문 아님)

- AI 반환 형식: `UrbanAiResult(String summary, List<UrbanInsight> insights, List<UrbanPlan> plans)`
- `.content()` 평문 사용 ❌
- `.entity(UrbanAiResult.class)` 구조화 출력 사용 ✅

```java
UrbanAiResult aiResult = chatClient.prompt()
    .system(systemPrompt)
    .user(userPrompt)
    .call()
    .entity(UrbanAiResult.class);
```

### 7.4 AI 출력 검증

Service는 저장 전에 다음을 검증한다.

- `summary`가 null 또는 빈 문자열이 아님
- `insights`가 1개 이상
- `plans.size() == 3`
- `plans.type` 집합이 정확히 `BALANCED`, `ECO`, `BUDGET`
- `priority`가 `1`, `2`, `3`이며 중복 없음
- 각 계획안의 `actions`와 `expectedEffects`가 1개 이상
- 검증 실패 시 `AI_ANALYSIS_FAILED`로 처리하고 저장하지 않음

### 7.5 Spring AI 기능 매핑 (최소 2개)

| 우리 기능 | 쓰는 Spring AI 기능 |
|---|---|
| 점수 설명과 도시계획안 생성 | 챗 (`ChatClient`) |
| 프론트·DB와 동일한 JSON 구조 보장 | 구조화 출력 (`.entity(UrbanAiResult.class)`) |
| 향후 공공데이터 자동 조회 | Tool Calling — 이번 MVP에서는 제외 |

### 7.6 모델·키 설정

- 모델: `gemini-3.1-flash-lite`
- 키 환경변수: `GOOGLE_API_KEY`
- API 키는 React의 `VITE_` 환경변수로 만들지 않는다.
- `.env`, `application-local.yml` 등 실제 키 파일은 `.gitignore` 처리한다.

---

## 8. 예외 설계 (실패를 먼저 설계)

| 입력·상황 | 백엔드 반응 | 프론트 반응 |
|---|---|---|
| `cityName`, `districtName` 등 필수값 누락 | `400 INVALID_INPUT` + "필수 입력값을 확인해주세요." | 입력 폼 상단에 오류 표시 |
| 비율이 `0`~`100` 범위를 벗어남 | `400 INVALID_INPUT` + 해당 필드 안내 | 잘못된 입력 항목 강조 |
| 인구·시설 수가 음수 | `400 INVALID_INPUT` | 해당 입력값 수정 유도 |
| 접근 거리 미측정 | 오류 아님. `null` 허용 | "아직 측정되지 않음" 표시 |
| AI API 호출 실패 | `500 AI_ANALYSIS_FAILED`, DB 저장 취소 | 재시도 버튼 표시 |
| AI가 계획안 3개를 반환하지 않음 | `500 AI_ANALYSIS_FAILED`, DB 저장 취소 | 재시도 안내 |
| DB 저장 실패 | `500 INTERNAL_ERROR`, 트랜잭션 롤백 | 공통 오류 표시 |
| 존재하지 않는 ID 조회 | `404 ANALYSIS_NOT_FOUND` | 분석 목록으로 이동 안내 |
| API Key 누락 | 서버 시작 또는 AI 호출 시 실패. 프론트 노출 금지 | 서버 설정 오류로 처리 |

### 재시도 정책

- 사용자가 직접 누르는 재시도는 허용한다.
- Day 2 MVP에서는 서버 자동 재시도는 구현하지 않는다.
- 같은 요청의 중복 저장 방지는 Should 기능으로 둔다.

---

## 9. 프론트·백엔드 병렬 개발 계약

### 프론트엔드

1. `src/types/analysis.ts`에 §5.5 타입을 그대로 작성한다.
2. 개발 초기에는 §5.3 성공 JSON을 mock으로 사용한다.
3. API 주소는 `VITE_API_BASE_URL=http://localhost:8080`으로 관리한다.
4. 실제 API 키는 프론트 환경변수에 저장하지 않는다.
5. `idle`, `loading`, `success`, `error` 상태를 모두 구현한다.
6. `sampleData=true`이면 화면에 **샘플 데이터** 배지를 표시한다.

### 백엔드

1. `com.study.intellipolis` 아래 Controller·Service·DTO·Repository를 분리한다.
2. §5.3의 필드명과 JSON 타입을 변경하지 않는다.
3. 점수는 `UrbanScoreCalculator`, AI는 `UrbanAiService`로 분리한다.
4. 모든 오류는 `ApiErrorResponse` 한 형태로 반환한다.
5. AI 구조화 출력 검증 후 저장한다.

### 계약 변경 규칙

- 필드 추가·삭제·이름 변경은 구두로만 합의하지 않는다.
- `docs/design.md` → Java DTO → TypeScript 타입 → mock JSON 순서로 같은 PR에서 변경한다.
- `plans[].type` 등 enum 문자열은 대소문자까지 계약이다.

---

## 10. 오늘의 완료 기준 (공식 Execution Checklist)

- [x] 1. 해결할 문제와 사용자가 한 문장으로 정의됨 (§1)
- [x] 2. Must 기능 5개와 핵심 시나리오 확정 (§2·§3)
- [x] 3. API 계약(URL·Method·JSON·오류) 문서화 (§5)
- [ ] 4. Spring AI 핵심 호출이 코드로 1회 성공 (§7 기반, `curl`로 확인)

### Day 2 확인용 curl

```bash
curl -X POST http://localhost:8080/api/analyses \
  -H "Content-Type: application/json" \
  -d '{
    "regionCode":"1168000000",
    "cityName":"서울특별시",
    "districtName":"강남구",
    "baseDate":"2026-06-30",
    "population":560000,
    "areaKm2":39.5,
    "elderlyRatio":16.79,
    "youthRatio":14.64,
    "parkAreaRatio":9.2,
    "hospitalCount":120,
    "schoolCount":80,
    "transitHubCount":25,
    "congestionRoadCount":8,
    "averageHospitalDistanceKm":0.8,
    "averageParkDistanceKm":1.1,
    "averageTransitDistanceKm":0.6
  }'
```

### 성공 판정

- HTTP 상태가 `200`
- 응답에 `scores`, `summary`, `insights`, `plans`가 존재
- `plans`가 정확히 3개
- `plans[].type`이 `BALANCED`, `ECO`, `BUDGET`
- DB `urban_analysis`에 1행 저장
- AI 호출 실패 시 DB에 새 행이 저장되지 않음

---

## 11. 다음 작업 순서

1. 백엔드에서 `UrbanAnalysisRequest`, `UrbanScores`, `UrbanAiResult` record 생성
2. `POST /api/analyses` Controller와 validation 구현
3. `ChatClient` + `.entity(UrbanAiResult.class)` 호출 성공
4. 프론트에서 §5.5 TypeScript 타입과 mock JSON 연결
5. API 연결 후 샘플 데이터를 실제 응답으로 교체
6. 전국 공통 CSV 1종을 정규화 입력 형식으로 변환
7. 실제 지도와 월별 데이터 수집은 MVP 성공 후 확장

> 최종 결과물은 각자 만든 코드의 모음이 아니라, 이 계약대로 연결되어 실행되는 하나의 서비스다.
