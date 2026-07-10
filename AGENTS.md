현재 작업 디렉터리에 `IntelliPolis` 팀 프로젝트의 기본 구조와 개발 문서를 구성하세요.

Ponytail 설정이 이미 적용되어 있으므로 해당 개발 원칙을 반드시 따르세요. 기존 파일이 있다면 삭제하거나 전면 재작성하지 말고 최대한 재사용하세요.

# 프로젝트 개요

* 팀명: IntelliPolis
* 프로젝트 성격: AI 기반 도시계획 의사결정 지원 플랫폼
* 목적: 특정 구 또는 생활권의 도시 데이터를 분석하고, 교통·환경·경제·생활 관점의 AI 에이전트가 의견을 제시한 뒤 현실적인 도시계획 대안을 생성하는 웹 서비스
* AI가 실제 행정 결정을 내리는 서비스가 아니라 도시계획 대안을 비교하고 검토하도록 돕는 시뮬레이션 및 의사결정 지원 서비스
* 기본 분석 범위: 시 전체가 아닌 구 단위 또는 생활권 단위
* 기본 언어: 사용자 화면과 API 오류 메시지는 한국어
* 개발 환경: Windows에서도 실행 가능해야 함

# 기술 스택

## Backend

* Java 21
* Spring Boot
* Gradle
* Spring Web MVC
* Spring AI
* Gemini 3.1 Flash-Lite
* Bean Validation
* JUnit 5
* 초기 MVP는 메모리 저장소
* 필요 시 추후 MySQL 및 Spring Data JPA 추가

## Frontend

* React
* Vite
* TypeScript
* React Router
* MapLibre GL JS
* Recharts
* CSS Modules 또는 일반 CSS
* Fetch API 또는 Axios 중 하나만 사용

# 프로젝트 구조

다음과 같이 프론트엔드와 백엔드를 분리하세요.

```text
intellipolis/
├── README.md
├── .gitignore
├── backend/
└── frontend/
```

이미 현재 디렉터리가 저장소 루트라면 불필요하게 상위 폴더를 하나 더 만들지 마세요.

# 핵심 사용자 흐름

```text
도시 데이터 입력
→ Spring Boot API 요청
→ 규칙 기반 현재 도시 점수 계산
→ 교통·환경·경제·생활 AI 분석
→ 분야별 의견 종합
→ 도시계획 대안 3개 생성
→ 백엔드에서 예상 점수 재계산
→ React 대시보드 표시
→ MapLibre 지도에 시설·도로·개발구역 표시
→ 계획안 비교
```

# 도시계획 대안

다음 세 가지 계획안을 생성합니다.

1. `BALANCED`

   * 균형형
   * 교통·환경·경제·생활을 균형 있게 개선

2. `ECO_FOCUSED`

   * 환경 중심형
   * 공원, 녹지, 보행환경, 대중교통 중심

3. `COST_EFFECTIVE`

   * 예산 효율형
   * 기존 시설과 인프라를 최대한 활용

# AI 에이전트

동일한 Gemini API 키와 모델을 사용하되 서로 다른 시스템 프롬프트를 가진 다음 역할을 사용합니다.

* 교통 분석 에이전트
* 환경 분석 에이전트
* 경제 분석 에이전트
* 생활 편의 분석 에이전트
* 최종 도시계획 조정 에이전트

AI는 다음 작업만 담당합니다.

* 제공된 도시 데이터 해석
* 문제점 설명
* 개선 방향 제안
* 상충 관계 설명
* 계획안의 목적 및 이유 작성
* 시설 및 개발 후보 제안

AI가 다음 작업을 담당해서는 안 됩니다.

* 인구와 시설 수 임의 생성
* 실제 통계 수치 생성
* 예산을 근거 없이 계산
* 접근 거리 직접 계산
* 최종 점수 임의 생성
* 실제 행정 결정처럼 단정

객관적인 수치와 점수는 Java 코드가 계산하고, AI는 결과를 해석하게 하세요.

# MVP 범위

반드시 구현할 기능:

* 도시 데이터 직접 입력
* 샘플 도시 데이터 불러오기
* 현재 도시 점수 계산
* 분야별 AI 분석
* 도시계획 대안 3개 생성
* 계획안별 예상 점수 계산
* 분석 결과 대시보드
* 계획안 비교
* MapLibre 지도
* 시설 Point 표시
* 도로 LineString 표시
* 개발 구역 Polygon 표시
* 간단한 2D/3D 토글
* AI 실패 시 fallback
* 백엔드와 프론트엔드 README

MVP에서 제외할 기능:

* 로그인 및 회원가입
* 관리자 페이지
* 결제
* 실제 행정기관 연동
* 완전한 디지털 트윈
* 실제 교통 시뮬레이션 엔진
* 복잡한 3D 건물 모델
* 실시간 협업
* WebSocket
* MCP
* RAG
* 배포 자동화
* 사용자별 권한 관리

# API 계약

기본 백엔드 주소:

```text
http://localhost:8080
```

기본 프론트엔드 주소:

```text
http://localhost:5173
```

필수 API:

```http
GET /api/health
GET /api/cities/sample
POST /api/city-analyses
GET /api/city-analyses/{analysisId}
POST /api/city-analyses/{analysisId}/plans/{planType}/evaluate
POST /api/city-analyses/{analysisId}/plans/{planType}/explain
```

# 환경변수

백엔드:

```env
GEMINI_API_KEY=
GEMINI_MODEL=gemini-3.1-flash-lite
FRONTEND_ORIGIN=http://localhost:5173
```

프론트엔드:

```env
VITE_API_BASE_URL=http://localhost:8080
VITE_MAP_STYLE_URL=https://demotiles.maplibre.org/style.json
VITE_USE_MOCK=false
```

API 키와 모델명을 Java 또는 TypeScript 코드에 하드코딩하지 마세요.

# 공통 데이터 구조

도시 분석 입력은 최소한 다음 필드를 포함합니다.

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

분석 응답은 최소한 다음 정보를 포함합니다.

```json
{
  "analysisId": "UUID",
  "cityName": "부산광역시",
  "districtName": "가상 해안구",
  "currentScores": {
    "traffic": 70,
    "environment": 65,
    "economy": 80,
    "living": 72,
    "overall": 72
  },
  "agentAnalyses": [],
  "plans": [],
  "warnings": [],
  "createdAt": "ISO_DATE_TIME"
}
```

# Git 관리

루트 `.gitignore`에 다음 항목을 포함하세요.

```text
.env
.env.*
!.env.example
.idea/
.vscode/
node_modules/
dist/
build/
.gradle/
out/
*.log
```

백엔드와 프론트엔드 각각 `.env.example` 또는 필요한 설정 예제를 제공하세요.

# 루트 README

루트 README에는 다음 내용을 작성하세요.

* IntelliPolis 소개
* 프로젝트 목적
* 전체 기술 스택
* 시스템 흐름
* 폴더 구조
* 백엔드 실행 방법
* 프론트엔드 실행 방법
* 환경변수 설정
* MVP 기능
* 제외 범위
* 향후 확장 방향
* 팀명 IntelliPolis의 의미

IntelliPolis는 `Intelligence + Polis`의 합성어이며, AI를 통해 도시계획 의사결정을 지원한다는 의미로 설명하세요.

# 실행 목표

백엔드:

```bash
cd backend
./gradlew bootRun
```

Windows PowerShell:

```powershell
cd backend
.\gradlew.bat bootRun
```

프론트엔드:

```bash
cd frontend
npm install
npm run dev
```

# 작업 방법

1. 현재 저장소 상태를 먼저 확인하세요.
2. 이미 생성된 백엔드 또는 프론트엔드 프로젝트가 있다면 재사용하세요.
3. 비어 있다면 최소한의 기본 프로젝트 구조만 생성하세요.
4. 백엔드와 프론트엔드의 DTO 필드명을 통일하세요.
5. 루트 README와 환경변수 예제를 작성하세요.
6. 실제 세부 기능은 이후 별도 프롬프트에서 구현할 수 있도록 기본 구조만 구성하세요.
7. 불필요한 샘플 페이지와 사용하지 않는 파일은 제거하세요.
8. 완료 후 생성 및 변경한 파일을 요약하세요.

현재 단계에서는 전체 기능을 한꺼번에 구현하려 하지 말고, 백엔드와 프론트엔드가 들어갈 수 있는 정상적인 프로젝트 뼈대와 공통 계약을 만드는 데 집중하세요.
