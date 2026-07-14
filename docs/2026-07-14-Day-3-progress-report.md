# Day 3 진행 보고서

- 프로젝트: IntelliPolis AI Urban Planner
- 작성일: 2026년 7월 14일
- 분석 범위: 부산광역시 구·군 단위
- 목적: 도시 데이터를 근거로 현재 상태를 분석하고, 현실적인 시설 및 도로 개선 후보를 비교하는 의사결정 지원 서비스

## 오늘 구현한 핵심 기능

### 1. 부산광역시 중심 데이터 분석

- 서비스 분석 범위를 부산광역시 16개 구·군으로 정리했다.
- 상권, 공원, 학교, 연령별 인구, 고령인구, 예산 CSV를 구 단위로 집계한다.
- 전국 버스정류장 좌표를 실제 구 경계와 공간 결합하여 구별 정류장 수를 계산한다.
- 건강보험심사평가원 병원 Open API를 연동하여 구별 병원 수를 집계한다.

### 2. 실측 교통과 공간데이터 연동

- 부산 교통 Open API와 국가 ITS 데이터를 이용해 실제 링크 속도와 교통량을 조회한다.
- 20km/h 미만 링크를 혼잡 후보로 분류한다.
- 도로 좌표가 확인된 혼잡 링크만 지도에 표시한다.
- 도로별 관측값에 따라 신호 최적화, 차로 운영, 대중교통, 보행 안전, 수요 관리 등의 개선 수단을 연결한다.
- 연속지적도, 도시계획시설, 도시지역 공간데이터를 이용해 500㎡ 이상이며 기존 계획시설과 겹치지 않는 후보 필지를 조회한다.

### 3. 근거 기반 시설 후보 제안

- 모든 시설을 한 개씩 강제로 출력하던 방식을 제거했다.
- 부족 지표에 따라 같은 유형의 시설도 0개부터 여러 개까지 제안한다.
- 추가 시설이 필요하지 않으면 `현재 시설 배치는 적정하여 추가 시설을 제안하지 않습니다.`라고 표시한다.
- 후보지 최대 20개를 비교하고, 제안 시설 간 거리를 최대화하여 한곳에 몰리지 않도록 분산한다.
- 시설마다 부족 지표, 후보 필지 조건, 배치 사유를 함께 표시한다.
- 결과는 행정 결정이나 확정 입지가 아니라 `우선 검토 후보지`로 안내한다.

### 4. 분야별 AI 분석과 fallback

- 동일한 Gemini 모델을 교통, 환경, 경제, 생활의 네 가지 시스템 프롬프트로 분리해 호출한다.
- Java 규칙이 점수와 객관적 수치를 계산하고, AI는 수치를 임의 생성하지 않고 결과를 해석한다.
- 일부 AI 호출이 실패하면 해당 분야만 규칙 기반 설명으로 대체한다.
- 균형형, 환경 중심형, 예산 효율형의 세 가지 계획안을 생성한다.

### 5. 분석 화면과 진행률

- 현재 지도와 AI 개선 지도를 나란히 비교한다.
- 선택한 구의 실제 외곽 경계만 파란색으로 표시한다.
- 실제 혼잡 도로는 주황색으로 표시하고, 클릭 시 개선 수단·근거·현실성·토지 영향을 제공한다.
- 분석 진행률을 실제 비동기 완료 시점과 연결했다.
  - 5%: 분석 요청 시작
  - 25%: 구 경계와 위치 확인 완료
  - 70%: 도시·교통·공간데이터 수집 완료
  - 100%: AI 계획안 생성 및 응답 완료

### 6. 성능 최적화

- 독립적인 도시요약, 위치, 교통, 공간 조회를 병렬 실행한다.
- 전국 CSV를 구마다 반복해서 읽지 않고 부산 전체를 한 번 집계한 뒤 메모리에서 재사용한다.
- 교통 응답, 도로 좌표, 구 경계, 공간 후보를 캐시한다.
- 구 경계 합집합 계산을 1m 정밀도 OverlayNG 방식으로 최적화하고 WKB 파일로 저장한다.
- 부산 16개 구·군의 경계 캐시를 사전 생성했다.
- 외부 API와 Gemini가 응답하지 않을 때의 대기 상한을 6초로 제한했다.

## 현재 진행 상태

| 항목 | 상태 | 비고 |
| --- | --- | --- |
| 부산 구·군 선택 | 완료 | 16개 구·군 지원 |
| 구 단위 도시요약 | 완료 | 상권·공원·학교·인구·예산 |
| 버스정류장 구별 공간 집계 | 완료 | 좌표와 실제 구 경계 사용 |
| 병원 Open API | 완료 | 강서구 129개 실측 확인 |
| 실시간 교통 분석 | 완료 | 속도·교통량·혼잡 링크 |
| 후보 필지 공간 분석 | 완료 | 지적도·계획시설·도시지역 사용 |
| 분야별 Gemini 분석 | 완료 | 실패 시 분야별 fallback |
| 계획안 3종 생성 | 완료 | 균형형·환경 중심형·예산 효율형 |
| 지도 비교 및 2D/3D | 완료 | 구 경계·시설·혼잡 도로 표시 |
| 실제 단계 기반 진행률 | 완료 | 5·25·70·100% |
| 성능 최적화 | 완료 | 병렬 실행 및 메모리·디스크 캐시 |

검증 결과는 백엔드 전체 테스트와 프론트엔드 TypeScript 검사를 통과했다. 강서구 도시요약은 최초 약 9초, 같은 구 재조회는 약 0.004초로 측정됐다. 구 경계는 최초 생성 후 디스크 캐시에서 약 0.08초에 반환됐다.

## 계획한 API 계약

기본 주소는 `http://localhost:8080`이다.

| API | 사용 시점 | 사용 방법 및 역할 |
| --- | --- | --- |
| `GET /api/health` | 서버 실행 확인 | 프론트 또는 Bruno에서 백엔드 상태를 점검한다. |
| `GET /api/urban-data/regions` | 화면 최초 진입 | 지원 도시와 구·군 선택 목록을 불러온다. |
| `GET /api/urban-data/summary` | 분석 시작 | 선택 구의 상권·공원·학교·인구·버스·병원·예산을 집계한다. |
| `GET /api/spatial/boundary` | 분석 시작 및 지도 표시 | 선택 구의 실제 Polygon/MultiPolygon과 지도 bounds를 반환한다. |
| `GET /api/vworld/search` | 분석 중심 결정 | 지역 검색 좌표를 조회한다. 실패하면 구 경계 중심을 사용한다. |
| `GET /api/vworld/tiles/{z}/{y}/{x}.png` | 지도 렌더링 | VWorld 배경지도 타일을 MapLibre에 전달한다. |
| `GET /api/traffic/summary` | 위치 확인 후 | 분석 중심 주변의 실측 교통과 혼잡 링크를 반환한다. |
| `GET /api/spatial/candidates` | 위치 확인 후 | 건설 가능성을 검토할 후보 필지를 반환한다. |
| `POST /api/city-analyses` | 도시·교통·공간 수집 후 | 점수 계산, 분야별 AI 분석, 계획안 3개 생성을 수행한다. |
| `GET /api/city-analyses/{analysisId}` | 결과 재조회 | 메모리 저장소에 저장된 분석 결과를 조회한다. |
| `POST /api/city-analyses/{analysisId}/plans/{planType}/evaluate` | 계획 수정 후 | 수정 계획의 좌표를 검증하고 예상 점수를 재계산한다. |
| `POST /api/city-analyses/{analysisId}/plans/{planType}/explain` | 계획 상세 설명 요청 | 최종 조정 AI가 선택 계획의 목적과 상충 관계를 설명한다. |
| `GET /api/external-data/status` | 환경 설정 확인 | 공공데이터 및 나이스 API 키 설정 여부를 확인한다. |

프론트엔드는 구 경계와 위치 조회를 먼저 실행한 뒤, 도시요약·교통·공간 후보를 가능한 범위에서 병렬로 수집한다. 모든 객관적 수치는 Java가 계산하며 Gemini는 입력과 계산 결과의 해석만 담당한다.

## 완료한 기능, 미완료 기능

### 완료한 기능

- 부산광역시 실제 구·군 데이터 선택 및 요약
- 실제 구 경계와 버스정류장 공간 집계
- 병원 Open API 연동
- 실측 혼잡 도로 탐지 및 도로별 개선 수단 제안
- 필요량에 따른 시설 0개 이상 제안과 배치 사유 표시
- 후보 시설의 분산 배치
- 교통·환경·경제·생활 AI 분석과 fallback
- 계획안 3종 및 예상 점수 계산
- MapLibre 지도 비교, 시설·도로 표시, 2D/3D 전환
- 실제 처리 단계 기반 진행률
- 데이터·공간·도로 캐시 및 분석시간 최적화
- main 브랜치 최신 변경 병합

### 미완료 또는 후속 검증 기능

- 생활권별 세부 인구 격자와 기존 시설 접근거리 기반의 최종 입지 최적화
- 실제 인허가, 토지보상, 사업비 산정 및 교통 시뮬레이션
- MySQL 영구 저장소와 사용자별 분석 이력
- 실제 배포 환경 구성

현재 시설 위치와 도로 개선안은 행정 결정을 대신하는 확정안이 아니라 데이터 기반 우선 검토안이다.

## 요청-응답 스크린샷

아래 항목은 Bruno에서 호출한 뒤 요청 URL, Params 또는 Body, 상태 코드, 응답 JSON이 함께 보이도록 캡처한다.

### 스크린샷 1. 서버 상태

- 요청: `GET http://localhost:8080/api/health`
- 확인 항목: HTTP 200, `status: UP`
- 이미지: `![GET health 요청·응답](./images/day3-01-health.png)`

### 스크린샷 2. 부산 강서구 도시요약

- 요청: `GET http://localhost:8080/api/urban-data/summary`
- Query Params: `city=부산광역시`, `district=강서구`
- 확인 항목: 인구, 공원, 학교, 버스정류장, 병원 수, 예산, warnings
- 이미지: `![GET urban summary 요청·응답](./images/day3-02-urban-summary.png)`

### 스크린샷 3. 실제 혼잡 교통 링크

- 요청: `GET http://localhost:8080/api/traffic/summary`
- Query Params: `city=부산광역시`, `district=강서구`, `longitude=128.98`, `latitude=35.21`
- 확인 항목: 평균속도, 혼잡 링크 수, 도로명, 링크 좌표
- 이미지: `![GET traffic summary 요청·응답](./images/day3-03-traffic.png)`

### 스크린샷 4. 공간 후보지

- 요청: `GET http://localhost:8080/api/spatial/candidates`
- Query Params: `city=부산광역시`, `district=강서구`, `longitude=128.98`, `latitude=35.21`
- 확인 항목: 후보 필지 수, PNU, 면적, 거리, 좌표
- 이미지: `![GET spatial candidates 요청·응답](./images/day3-04-spatial.png)`

### 스크린샷 5. AI 도시계획 분석

- 요청: `POST http://localhost:8080/api/city-analyses`
- 확인 항목: HTTP 201, analysisId, 분야별 분석 4개, 계획안 3개, 시설·도로 사유, 예상 점수
- 이미지: `![POST city analyses 요청·응답](./images/day3-05-city-analysis.png)`

## Bruno 요청 예시

### 1. 도시요약

```http
GET http://localhost:8080/api/urban-data/summary?city=부산광역시&district=강서구
```

응답에서 확인할 핵심 필드:

```json
{
  "cityName": "부산광역시",
  "districtName": "강서구",
  "businessCount": 0,
  "parkCount": 0,
  "schoolCount": 0,
  "busStopCount": 1537,
  "hospitalCount": 129,
  "population": 0,
  "elderlyRatio": 0,
  "youthRatio": 0,
  "budgetByCategory": {},
  "warnings": []
}
```

위 예시의 `0` 값은 응답 구조 설명용이며 실제 값은 로컬 원본 데이터에 따라 달라진다.

### 2. 도시계획 생성

Bruno에서 `Body > JSON`을 선택하고 아래 예시를 사용한다.

```http
POST http://localhost:8080/api/city-analyses
Content-Type: application/json
```

```json
{
  "cityName": "부산광역시",
  "districtName": "강서구",
  "population": 150000,
  "areaKm2": 181.5,
  "elderlyRatio": 18.5,
  "youthRatio": 12.4,
  "parkAreaRatio": 8.2,
  "hospitalCount": 129,
  "schoolCount": 50,
  "transitHubCount": 1537,
  "averageHospitalDistanceKm": 0,
  "averageParkDistanceKm": 0,
  "averageTransitDistanceKm": 0,
  "congestedRoads": ["낙동북로", "공항로"],
  "totalBudget": 80000000000,
  "priorityGoals": ["교통 혼잡 개선", "생활시설 접근성 개선"],
  "mapCenter": {
    "latitude": 35.21,
    "longitude": 128.98
  },
  "boundary": [],
  "candidateSites": [
    {"latitude": 35.205, "longitude": 128.975},
    {"latitude": 35.220, "longitude": 128.990},
    {"latitude": 35.190, "longitude": 128.960}
  ],
  "roadObservations": [
    {
      "linkId": "BRUNO-DEMO-1",
      "roadName": "공항로",
      "speedKmh": 14.2,
      "volume": 1250,
      "intersectionName": "공항입구교차로",
      "queueLength": 42,
      "pedestrianCount": 80,
      "coordinates": [
        [128.970, 35.205],
        [128.985, 35.215]
      ]
    }
  ]
}
```

응답에서 확인할 핵심 구조:

```json
{
  "analysisId": "UUID",
  "cityName": "부산광역시",
  "districtName": "강서구",
  "currentScores": {
    "traffic": 0,
    "environment": 0,
    "economy": 0,
    "living": 0,
    "overall": 0
  },
  "agentAnalyses": [
    {"domain": "TRAFFIC"},
    {"domain": "ENVIRONMENT"},
    {"domain": "ECONOMY"},
    {"domain": "LIVING"}
  ],
  "plans": [
    {"planType": "BALANCED"},
    {"planType": "ECO_FOCUSED"},
    {"planType": "COST_EFFECTIVE"}
  ],
  "warnings": [],
  "createdAt": "ISO_DATE_TIME"
}
```

점수와 UUID는 서버가 계산·생성하므로 실제 응답 값을 캡처한다.

## 문제와 해결 과정

### 1. 분석 시간이 지나치게 오래 걸림

- 문제: 전국 CSV 반복 스캔, 구 경계 필지 합집합, 외부 API와 AI의 직렬 대기로 첫 분석이 오래 걸렸다.
- 해결: 부산 데이터 일괄 집계, 메모리 캐시, WKB 경계 캐시, OverlayNG 합집합, API 병렬화, 6초 timeout을 적용했다.
- 결과: 강서구 도시요약은 최초 약 9초, 캐시 후 약 0.004초로 단축됐다.

### 2. 시설이 한곳에 몰리고 종류별 한 개씩 출력됨

- 문제: 가까운 후보지 5개에 시설 유형을 순서대로 배치했다.
- 해결: 후보를 20개까지 비교하고 시설 간 거리가 최대가 되도록 분산했다. 부족 지표에 따라 시설 수를 0개 이상으로 계산했다.
- 한계: 생활권별 인구 격자와 기존 시설의 상세 접근성이 없으므로 결과를 확정 입지가 아닌 우선 검토 후보로 표시한다.

### 3. 지도 경계 내부에 불필요한 파란선 표시

- 문제: 행정경계 GeoJSON의 내부 링까지 모두 선으로 렌더링했다.
- 해결: Polygon과 MultiPolygon의 외부 링만 파란색 구 전체 경계로 표시했다.

### 4. 병원 Open API 집계 실패

- 문제: API 응답의 구 이름은 `부산강서구`였지만 코드는 `강서구`와 완전 일치하는 값만 찾았다.
- 해결: 시군구명을 접미사 기준으로 매칭하도록 수정했다.
- 결과: 강서구 병원 129개가 정상 집계됐고 warnings가 비어 있음을 확인했다.

### 5. main 병합 시 프론트 충돌

- 문제: main의 진행률용 App과 현재 부산 지도 App이 같은 파일을 수정해 충돌했다.
- 해결: 실제 백엔드 계약과 지도 기능을 사용하는 현재 App을 유지하고, main의 미사용 레거시 파일 삭제를 반영했다. 진행률은 실제 비동기 완료 시점에 연결했다.

## 내일 할 일 계획

1. Bruno에서 도시요약, 교통, 공간 후보, 도시계획 생성 API의 요청·응답을 캡처해 본 문서의 이미지 링크를 교체한다.
2. 생활권별 인구 및 기존 시설 접근성 데이터를 추가해 후보지별 필요도 점수를 고도화한다.
3. 부산 여러 구에서 교통·시설 제안의 결과와 사유를 교차 검증한다.
4. Gemini 성공·부분 실패·전체 fallback 화면을 각각 확인한다.
5. 팀원 변경과 최종 병합 후 프론트 빌드 및 백엔드 전체 테스트를 다시 실행한다.
6. README 또는 Notion에 Day 3 보고서 링크를 공유한다.

---

IntelliPolis는 실제 행정 결정을 자동으로 내리는 서비스가 아니다. 데이터와 AI 해석을 통해 여러 도시계획 대안을 비교하고 검토하도록 돕는 시뮬레이션 및 의사결정 지원 플랫폼이다.
