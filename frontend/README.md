# IntelliPolis Frontend

구·생활권 도시 데이터를 입력하고 Spring Boot 분석 결과, 도시 점수, AI 분야별 의견, 세 가지 계획안과 지도 레이어를 비교하는 React 프론트엔드입니다.

## 기술 스택

React, Vite, TypeScript, React Router, MapLibre GL JS, Recharts, Fetch API, 일반 CSS를 사용합니다.

## 실행

백엔드를 먼저 `http://localhost:8080`에서 실행한 뒤 다음 명령을 실행합니다.

```bash
npm install
npm run dev
```

프로덕션 빌드는 `npm run build`로 확인합니다. 기본 프론트엔드 주소는 `http://localhost:5173`입니다.

## 환경변수

`.env.example`을 `.env`로 복사해 사용합니다.

```env
VITE_API_BASE_URL=http://localhost:8080
VITE_MAP_STYLE_URL=https://demotiles.maplibre.org/style.json
VITE_USE_MOCK=false
```

변수가 없으면 위 API 주소와 MapLibre 데모 스타일을 기본값으로 사용합니다. 현재 로컬 mock 데이터는 제공하지 않으며 `VITE_USE_MOCK=false`가 기본입니다.

## 페이지와 지도

- `/`: 입력, 샘플 데이터 조회, 분석 생성
- `/analyses/:analysisId`: 점수, 분야별 분석, 계획안, 비교표, 지도, 경고
- 지도는 시설 Point, 도로 LineString, 구역 Polygon을 표시하며 잘못된 좌표를 제외합니다.
- 3D는 계획 구역의 `fill-extrusion`과 지도 pitch 변경만 지원합니다.

## 현재 제한사항

- 백엔드 분석 응답에 원본 입력(인구, 면적, 예산, 우선 목표, 지도 중심)이 없어 생성 직후에는 세션 저장 값으로 표시하지만 새 브라우저 세션에서 복원할 수 없습니다.
- 백엔드 메모리 저장소를 사용하므로 백엔드를 재시작하면 기존 분석 ID를 조회할 수 없습니다.
- `전체 비교` 지도 모드는 현재 선택 계획안을 반투명하게 표시합니다. 세 계획안 동시 레이어 구분은 데이터가 확장될 때 추가할 수 있습니다.
- 지도 스타일 URL 접근에는 인터넷 연결이 필요합니다.
