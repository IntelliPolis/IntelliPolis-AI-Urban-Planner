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
