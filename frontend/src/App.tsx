import { useEffect, useRef, useState } from "react";
import type { CSSProperties } from "react";
import maplibregl from "maplibre-gl";
import "maplibre-gl/dist/maplibre-gl.css";
import "./map-enhancements.css";
import type {
  CityAnalysisRequest,
  CityPlan,
  PlannedFacility,
} from "./types/city";
const API = import.meta.env.VITE_API_BASE_URL || "http://localhost:8080";
type Regions = Record<string, string[]>;
type TrafficSummary = {
  measuredLinkCount: number;
  averageSpeedKmh: number;
  congestedLinkCount: number;
  congestedRoads: {
    linkId: string;
    roadName: string;
    speedKmh: number;
    volume: number;
    intersectionName: string | null;
    queueLength: number | null;
    pedestrianCount: number | null;
    coordinates: number[][];
  }[];
  warnings: string[];
};
type SpatialSummary = {
  eligibleParcelCount: number;
  nearbyPlanningFacilityCount: number;
  averageParkDistanceKm: number;
  averageTransitDistanceKm: number;
  candidates: {
    parcelId: string;
    longitude: number;
    latitude: number;
    areaM2: number;
    activityCount: number;
    population: number;
  }[];
  warnings: string[];
};
type DistrictBoundary = {
  cityName: string;
  districtName: string;
  type: "Polygon" | "MultiPolygon";
  coordinates: any;
  bounds: [number, number, number, number];
  areaKm2: number;
};
type Summary = {
  cityName: string;
  districtName: string;
  businessCount: number;
  parkingCount: number;
  seniorCareCount: number;
  sportsFacilityCount: number;
  parkCount: number;
  totalParkAreaM2: number;
  schoolCount: number | null;
  busStopCount: number | null;
  hospitalCount: number | null;
  averageHospitalDistanceKm: number | null;
  population: number;
  elderlyRatio: number;
  youthRatio: number;
  budgetByCategory: Record<string, number>;
  warnings: string[];
  traffic?: TrafficSummary;
  spatial?: SpatialSummary;
  boundary?: DistrictBoundary;
};
async function json<T>(url: string, init?: RequestInit): Promise<T> {
  const r = await fetch(url, init);
  if (!r.ok)
    throw new Error(
      (await r.json().catch(() => ({}))).message || `요청 실패 (${r.status})`,
    );
  return r.json();
}
const escapeHtml = (value: unknown) =>
  String(value ?? "").replace(
    /[&<>"']/g,
    (x) =>
      ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[
        x
      ]!,
  );
export default function App() {
  const [regions, setRegions] = useState<Regions>({}),
    [city, setCity] = useState(""),
    [district, setDistrict] = useState("");
  const [data, setData] = useState<Summary>(),
    [plans, setPlans] = useState<CityPlan[]>([]),
    [selected, setSelected] = useState(0),
    [center, setCenter] = useState<[number, number]>([127.0475, 37.5176]);
  const [loading, setLoading] = useState(true),
    [progress, setProgress] = useState(0),
    [error, setError] = useState("");
  useEffect(() => {
    if (!(loading && progress > 0)) return;
    const previous = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    return () => {
      document.body.style.overflow = previous;
    };
  }, [loading, progress]);
  useEffect(() => {
    json<Regions>(`${API}/api/urban-data/regions`)
      .then((x) => {
        setRegions(x);
        const c = x["부산광역시"] ? "부산광역시" : Object.keys(x)[0] || "";
        setCity(c);
        setDistrict(x[c]?.includes("강서구") ? "강서구" : x[c]?.[0] || "");
      })
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false));
  }, []);
  const analyze = async () => {
    setLoading(true);
    setProgress(5);
    setError("");
    try {
      const summaryUrl = `${API}/api/urban-data/summary?city=${encodeURIComponent(city)}&district=${encodeURIComponent(district)}`;
      const boundaryUrl = `${API}/api/spatial/boundary?city=${encodeURIComponent(city)}&district=${encodeURIComponent(district)}`;
      const summaryPromise = json<Summary>(summaryUrl);
      const boundary = await json<DistrictBoundary>(boundaryUrl).catch(
        () => undefined,
      );
      setProgress(25);
      const c: [number, number] = boundary
        ? [
            (boundary.bounds[0] + boundary.bounds[2]) / 2,
            (boundary.bounds[1] + boundary.bounds[3]) / 2,
          ]
        : center;
      const bounds = boundary?.bounds;
      const spatialUrl = `${API}/api/spatial/candidates?city=${encodeURIComponent(city)}&district=${encodeURIComponent(district)}`;
      const [summary, spatial] = await Promise.all([
        summaryPromise,
        json<SpatialSummary>(spatialUrl).catch(() => undefined),
      ]);
      setProgress(70);
      const candidateSites = (spatial?.candidates || [])
        .slice(0, 20)
        .map((x) => ({
          longitude: x.longitude,
          latitude: x.latitude,
          areaM2: x.areaM2,
          activityCount: x.activityCount,
          population: x.population,
        }));
      const budget = Object.values(summary.budgetByCategory).reduce(
        (a, b) => a + b,
        0,
      );
      const candidate = candidateSites[0];
      const boundaryPoints = bounds
        ? [
            { longitude: bounds[0], latitude: bounds[1] },
            { longitude: bounds[2], latitude: bounds[1] },
            { longitude: bounds[2], latitude: bounds[3] },
            { longitude: bounds[0], latitude: bounds[3] },
          ]
        : [];
      const area = boundary?.areaKm2 || 50;
      const request: CityAnalysisRequest = {
        cityName: city,
        districtName: district,
        population: summary.population,
        areaKm2: area,
        elderlyRatio: summary.elderlyRatio,
        youthRatio: summary.youthRatio,
        parkAreaRatio:
          area > 0
            ? Math.min(
                100,
                (summary.totalParkAreaM2 / (area * 1_000_000)) * 100,
              )
            : 0,
        hospitalCount: summary.hospitalCount || 0,
        schoolCount: summary.schoolCount || 0,
        transitHubCount: summary.parkingCount || 0,
        averageHospitalDistanceKm: summary.averageHospitalDistanceKm || 0,
        averageParkDistanceKm: spatial?.averageParkDistanceKm || 0,
        averageTransitDistanceKm: 0,
        congestedRoads: [],
        totalBudget: budget,
        priorityGoals: [
          "녹지 접근성 향상",
          "생활 인프라 보완",
          `노인요양시설:${summary.seniorCareCount || 0}`,
          `공공체육시설:${summary.sportsFacilityCount || 0}`,
        ],
        mapCenter: {
          longitude: candidate?.longitude || c[0],
          latitude: candidate?.latitude || c[1],
        },
        boundary: boundaryPoints,
        candidateSites,
        roadObservations: [],
      };
      const analysis = await json<{ plans: CityPlan[] }>(
        `${API}/api/city-analyses`,
        {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(request),
        },
      );
      const warnings = [
        ...summary.warnings,
        ...(spatial?.warnings || [
          "공간 후보지를 불러오지 못해 시설 배치는 제외했습니다.",
        ]),
      ];
      setProgress(100);
      setData({
        ...summary,
        spatial,
        boundary,
        warnings: [...new Set(warnings)],
      });
      setCenter(c);
      setPlans(analysis.plans || []);
      setSelected(0);
    } catch (e) {
      setError(e instanceof Error ? e.message : "도시 분석에 실패했습니다.");
    } finally {
      setLoading(false);
      setTimeout(() => setProgress(0), 500);
    }
  };
  return (
    <>
      <LandingHero />
      <CoveragePanel />
      <FeatureCards />
      <main className="live-app" id="analysis">
        <DistrictPicker
          districts={regions[city] || []}
          district={district}
          loading={loading}
          onDistrict={(x) => {
            setDistrict(x);
            setData(undefined);
          }}
          onAnalyze={analyze}
        />
        {error && <p className="live-error">{error}</p>}
        {data ? (
          <Dashboard
            data={data}
            center={center}
            plans={plans}
            selected={selected}
            onSelect={setSelected}
          />
        ) : (
          <section className="empty-live">
            <b>지역을 선택하고 도시 분석을 시작하세요.</b>
            <span>
              VWorld 지역 좌표의 실제 지도 위에 AI 계획 시설을 강조합니다.
            </span>
          </section>
        )}
      </main>
      <SiteClosing />
      {loading && progress > 0 && (
        <LoadingOverlay progress={progress} city={city} district={district} />
      )}
    </>
  );
}
function LoadingOverlay({
  progress,
  city,
  district,
}: {
  progress: number;
  city: string;
  district: string;
}) {
  const stage = progress < 25 ? 0 : progress < 70 ? 1 : progress < 100 ? 2 : 3;
  const stages = [
    [
      "도시 기초 데이터 수집",
      "행정구역·인구·생활 인프라 데이터를 불러오고 있습니다.",
    ],
    ["공간 후보지 데이터 연결", "경계와 후보 필지를 분석하고 있습니다."],
    [
      "AI 도시계획 생성",
      "수집한 근거를 바탕으로 실행 가능한 계획안을 비교하고 있습니다.",
    ],
    ["분석 결과 정리", "지도와 보고서 화면을 마지막으로 구성하고 있습니다."],
  ];
  return (
    <div
      className="loading-overlay"
      role="dialog"
      aria-modal="true"
      aria-live="polite"
    >
      <div className="loading-orbit" aria-hidden="true">
        <i />
        <i />
        <i />
        <span>AI</span>
      </div>
      <div className="loading-copy">
        <small>INTELLIPOLIS ANALYSIS ENGINE</small>
        <h2>
          {city} {district}
          <br />
          <em>{stages[stage][0]}</em>
        </h2>
        <p>{stages[stage][1]}</p>
        <div className="loading-meter">
          <span style={{ width: `${progress}%` }} />
        </div>
        <div className="loading-meta">
          <b>{progress}%</b>
          <span>{stage + 1} / 4 STEP</span>
        </div>
        <ol>
          {stages.map(([title], i) => (
            <li
              key={title}
              className={i < stage ? "done" : i === stage ? "active" : ""}
            >
              <i>{i < stage ? "✓" : i + 1}</i>
              <span>{title}</span>
            </li>
          ))}
        </ol>
        <small className="loading-note">
          분석이 끝나면 자동으로 결과 화면을 표시합니다.
        </small>
      </div>
    </div>
  );
}
function DistrictPicker({
  districts,
  district,
  loading,
  onDistrict,
  onAnalyze,
}: {
  districts: string[];
  district: string;
  loading: boolean;
  onDistrict: (x: string) => void;
  onAnalyze: () => void;
}) {
  return (
    <section className="district-picker">
      <div className="district-picker-head">
        <p>
          <span>⌁</span>분석 실행
        </p>
        <h2>분석할 구·군을 선택하세요</h2>
        <small>
          선택한 지역의 도시 데이터를 불러와 필요한 시설 후보를 산출합니다.
        </small>
      </div>
      <div className="district-picker-body">
        <div className="district-chip-list">
          {districts.map((x) => (
            <button
              key={x}
              className={x === district ? "active" : ""}
              onClick={() => onDistrict(x)}
            >
              {x}
            </button>
          ))}
        </div>
        <button
          className="district-run"
          disabled={!district || loading}
          onClick={onAnalyze}
        >
          ▷ {district || "지역"} {loading ? "분석 중" : "다시 분석"}
        </button>
      </div>
    </section>
  );
}
function LandingHero() {
  const start = () =>
    document.getElementById("analysis")?.scrollIntoView({ behavior: "smooth" });
  return (
    <section className="hero v0-image-hero">
      <div className="hero-copy">
        <div className="eyebrow">
          <span /> AI URBAN DECISION SUPPORT
        </div>
        <h1>
          도시의 가능성을
          <br />
          <em>더 현명하게 설계합니다.</em>
        </h1>
        <p>
          환경·경제·생활 데이터를 하나의 시선으로 분석하고,
          <br className="desktop" /> AI와 함께 현실적인 도시계획 대안을
          비교하세요.
        </p>
        <div className="hero-actions">
          <button className="primary" onClick={start}>
            새 도시 분석 시작 <span>↗</span>
          </button>
        </div>
        <div className="trust-row">
          <div>
            <strong>3</strong>
            <span>핵심 데이터 분석</span>
          </div>
          <div>
            <strong>1</strong>
            <span>AI 추천 도시계획안</span>
          </div>
          <div>
            <strong>100%</strong>
            <span>설명 가능한 근거</span>
          </div>
        </div>
      </div>
      <div className="hero-visual" aria-label="미래 스마트시티 조감도">
        <div className="image-glow" />
        <img src="/intellipolis-hero.png" alt="미래형 도시계획 조감도" />
        <div className="float-card card-one">
          <span className="pulse" />
          <b>도시 종합 점수</b>
          <strong>72</strong>
          <small>분석 예시</small>
        </div>
        <div className="float-card card-two">
          <span>AI INSIGHT</span>
          <b>녹지 접근성 개선</b>
          <small>우선 검토가 필요합니다</small>
        </div>
        <div className="gold-line line-a" />
        <div className="gold-line line-b" />
      </div>
      <div className="scroll-cue">
        <span />
        SCROLL TO EXPLORE
      </div>
    </section>
  );
}
function CoveragePanel() {
  return (
    <section className="coverage-only">
      <aside className="clean-coverage" aria-label="분석 커버리지">
        <p>분석 커버리지</p>
        <div>
          <strong>16</strong>
          <b>부산 구·군</b>
          <span>전 행정구역</span>
        </div>
        <div>
          <strong>3종</strong>
          <b>핵심 데이터</b>
          <span>인구·상업·녹지</span>
        </div>
        <div>
          <strong>1안</strong>
          <b>AI 추천 도시계획안</b>
          <span>최적 계획 자동 생성</span>
        </div>
        <div>
          <strong>2D/3D</strong>
          <b>개선 지도</b>
          <span>MapLibre 기반</span>
        </div>
      </aside>
    </section>
  );
}
function FeatureCards() {
  return (
    <section className="clean-features">
      <article>
        <i>▱</i>
        <h3>다각도 도시 분석</h3>
        <p>
          인구·상업·녹지 데이터를 한 번에 교차 분석하여 구·군의 구조적 문제를
          다각도로 진단합니다.
        </p>
      </article>
      <article>
        <i>♙</i>
        <h3>검증 가능한 근거</h3>
        <p>
          모든 제안은 공공 데이터 출처와 산출 사유를 함께 제시해, 담당자가
          판단하고 설명할 수 있습니다.
        </p>
      </article>
      <article>
        <i>⌖</i>
        <h3>지도 기반 계획</h3>
        <p>
          시설 후보를 지도 위에 시각화하여, 위치 맥락 속에서 의사결정할 수
          있습니다.
        </p>
      </article>
    </section>
  );
}
function SiteClosing() {
  return (
    <footer>
      <button
        className="brand"
        onClick={() => scrollTo({ top: 0, behavior: "smooth" })}
      >
        <span className="brand-mark">
          <i />
          <i />
          <i />
        </span>
        <span>
          INTELLI<span>POLIS</span>
        </span>
      </button>
      <p>AI 기반 도시계획 의사결정 지원 플랫폼</p>
      <small>
        © 2026 Team IntelliPolis. For simulation and decision support only.
      </small>
    </footer>
  );
}
function Dashboard({
  data,
  center,
  plans,
}: {
  data: Summary;
  center: [number, number];
  plans: CityPlan[];
  selected: number;
  onSelect: (i: number) => void;
}) {
  const plan = plans[0] || null;
  const limits = data.warnings.filter((x) =>
    /(실패|없어|없음|부족|제외|미수집|오류|못했|않았)/.test(x),
  );
  const evidence = data.warnings.filter((x) => !limits.includes(x));
  return (
    <section id="analysis-result" className="analysis-result">
      <div className="result-toolbar">
        <div>
          <small>EXPORT REPORT</small>
          <b>{data.districtName} 분석 결과</b>
        </div>
        <button type="button" onClick={() => window.print()}>
          PDF로 저장
        </button>
      </div>
      <section className="metric-row refined-metrics">
        <Metric
          icon="☊"
          label="인구"
          value={`${data.population.toLocaleString()}명`}
          description="주민등록 인구"
        />
        <Metric
          icon="▥"
          label="상업 시설 수"
          value={`${data.businessCount.toLocaleString()}개`}
          description="분석 지역 내 상업 시설 수"
        />
        <Metric
          icon="P"
          label="공영주차장 수"
          value={`${(data.parkingCount || 0).toLocaleString()}개`}
          description="구 단위 공영주차장 집계"
        />
        <Metric
          icon="W"
          label="노인요양시설 수"
          value={`${(data.seniorCareCount || 0).toLocaleString()}개`}
          description="SHP 기준 구 단위 집계"
        />
        <Metric
          icon="SP"
          label="공공체육시설 수"
          value={`${(data.sportsFacilityCount || 0).toLocaleString()}개`}
          description="SHP 기준 구 단위 집계"
        />
        <Metric
          icon="♧"
          label="공원·녹지 수"
          value={`${data.parkCount.toLocaleString()}개소`}
          description="공원 및 녹지 시설 수"
        />
      </section>
      {plan && (
        <section className="best-plan-label">
          <small>AI BEST RECOMMENDATION</small>
          <h2>AI 추천 도시계획안</h2>
          <p>
            현재 지역 데이터를 기반으로 AI가 가장 적합한 도시계획안을
            제안합니다.
          </p>
        </section>
      )}
      <section className="comparison-intro">
        <span />
        <div>
          <small>CITY PLAN COMPARISON</small>
          <h2>현재 도시와 AI 계획안을 한눈에 비교합니다.</h2>
          <p>
            현재 도시와 AI 계획안을 지도에서 직접 비교해 더 나은 선택을 위한
            근거를 확인하세요.
          </p>
        </div>
        <span />
      </section>
      <section className="map-compare refined-map-compare">
        <MapView
          center={center}
          boundary={data.boundary}
          plan={plan}
          title={`${data.districtName} AI 개선지도 · ${plan?.name || ""}`}
        />
      </section>
      {plan && <PlanDetails plan={plan} />}
      {plan && <DetailedReport plan={plan} roads={[]} />}
      <section className="agent-explanation">
        <small>MULTI-AGENT REVIEW</small>
        <h2>분야별 검토 의견</h2>
        <div>
          <p>
            <b>환경</b> 공원 수와 면적을 바탕으로 녹지 연결 가능성을 검토합니다.
          </p>
          <p>
            <b>경제</b> 기존 상권을 훼손하지 않고 생활 서비스 거점을 연계하는
            방향입니다.
          </p>
          <p>
            <b>생활</b> 병원·학교·인구 API 값이 없는 항목은 생성하지 않고 미수집
            상태로 둡니다.
          </p>
        </div>
      </section>
      {evidence.length > 0 && (
        <section className="live-warnings data-evidence">
          <b>분석 데이터 적용 근거</b>
          {evidence.map((x) => (
            <span key={x}>{x}</span>
          ))}
        </section>
      )}
      {limits.length > 0 && (
        <section className="live-warnings">
          <b>데이터 한계</b>
          {limits.map((x) => (
            <span key={x}>{x}</span>
          ))}
        </section>
      )}
    </section>
  );
}
function TrafficOverview({
  roads,
}: {
  roads: TrafficSummary["congestedRoads"];
}) {
  const preview = roads.slice(0, 5);
  return (
    <section className="traffic-overview">
      <div className="traffic-overview-title">
        <small>LIVE TRAFFIC REVIEW</small>
        <h2>
          교통 혼잡 후보 <em>(요약)</em>
        </h2>
        <p>가장 우선적으로 확인할 도로만 간단히 표시합니다.</p>
      </div>
      <div className="traffic-card-strip">
        {preview.length ? (
          preview.map((x) => {
            const status = trafficStatus(x.speedKmh);
            const statusIcon =
              status === "혼잡" ? "●" : status === "주의" ? "△" : "✓";
            return (
              <article key={x.linkId}>
                <b>{x.roadName}</b>
                <span>{displayTrafficSpeed(x.speedKmh)}</span>
                <small>링크 {x.linkId}</small>
                <em className={`traffic-status ${status}`}>
                  <i>{statusIcon}</i>
                  {status}
                </em>
              </article>
            );
          })
        ) : (
          <p className="traffic-empty-light">
            현재 조회 범위에 혼잡 후보가 없습니다.
          </p>
        )}
      </div>
    </section>
  );
}
function PlanDetails({ plan }: { plan: CityPlan }) {
  return (
    <section className="plan-details decision-plan">
      <div className="decision-tabs">
        <article className="active">
          <span>🤖</span>
          <b>AI 추천안</b>
          <small>현재 데이터를 기반으로 생성된 최적의 도시계획안</small>
        </article>
      </div>
      <div className="decision-body">
        <div className="decision-counts">
          <div>
            <strong>{plan.facilities.length}</strong>
            <span>필요한 시설 수 (개소)</span>
          </div>
          <div>
            <strong>{plan.roads.length}</strong>
            <span>도로 개선 수 (건)</span>
          </div>
          <div>
            <strong>{plan.zones.length}</strong>
            <span>개발 구역 수 (구역)</span>
          </div>
        </div>
        <p className="decision-summary">
          {plan.purpose}
          <br />
          시설 위치는 우선 검토 권역이며, 최종 입지는 현장 조사와 부지 검토 후
          확정해야 합니다.
        </p>
        <div className="decision-columns">
          <section>
            <h3>⌖ 추천 시설</h3>
            {plan.facilities.length ? (
              plan.facilities.map((x) => {
                const [, icon] = facilityVisual(x);
                const priority = facilityPriority(x);
                return (
                  <article className="decision-item" key={x.id}>
                    <div>
                      <b>
                        {icon} {x.name}
                      </b>
                      <em className={`priority-${priority}`}>
                        우선순위 {priority}
                      </em>
                    </div>
                    <p>{cleanReason(x.reason)}</p>
                  </article>
                );
              })
            ) : (
              <article className="decision-item">
                <p>
                  현재 데이터에서는 새 시설이 꼭 필요한 지역이 확인되지
                  않았습니다.
                </p>
              </article>
            )}
          </section>
          <section className="road-suggestion-list">
            <h3>⌘ 도로 개선 제안</h3>
            {plan.roads.length ? (
              plan.roads.map((x) => (
                <article className="road-suggestion-card" key={x.id}>
                  <b>{x.name}</b>
                  <span>교차로 개선 + 우회 동선 신설</span>
                  <p>{x.reason}</p>
                </article>
              ))
            ) : (
              <article className="road-suggestion-card">
                <p>
                  현재 교통 흐름을 유지할 수 있어 별도의 도로 개선이 필요하지
                  않습니다.
                </p>
              </article>
            )}
          </section>
        </div>
      </div>
    </section>
  );
}
const cleanReason = (value: string) =>
  value
    .replace(
      /기존 도시계획시설과 겹치지 않지만 최종 입지는 현장 조사 후 확정해야 합니다\.?/g,
      "",
    )
    .trim();
const trafficStatus = (speed: number) =>
  speed < 20 ? "혼잡" : speed < 30 ? "주의" : "원활";
const displayTrafficSpeed = (speed: number) => `${speed}km/h`;
function DetailedReport({
  plan,
  roads,
}: {
  plan: CityPlan;
  roads: TrafficSummary["congestedRoads"];
}) {
  const evidence = [
    "통계청 주민등록 인구 및 세대 현황 (구·군 단위)",
    "부산광역시 상가·업소 인허가 개방 데이터",
    "국가공간정보포털 도시계획 용도지역 및 공원·녹지 현황",
    "공공 교통 API 도로 링크별 관측 속도",
  ];
  const limits = [
    "교통 속도는 특정 시간대 관측값 기반으로, 상시 혼잡과 차이가 있을 수 있습니다.",
    "시설 수요는 행정구 인구 기준이며 유동·관광 수요는 일부만 반영됩니다.",
    "행정 경계 및 부지 가용성은 데모용 근사치로 실제 지적과 다를 수 있습니다.",
  ];
  return (
    <section className="analysis-report-grid">
      <header>
        <span>▤</span>
        <div>
          <h2>상세 분석 보고서</h2>
          <p>{plan.name} · 근거와 한계까지 포함한 의사결정 자료</p>
        </div>
      </header>
      <div className="report-grid-body">
        <section className="report-cell traffic-list">
          <h3>교통 혼잡 후보 전체 목록</h3>
          <div>
            {roads.length ? (
              roads.map((x) => {
                const status = trafficStatus(x.speedKmh);
                return (
                  <article key={x.linkId}>
                    <div>
                      <b>{x.roadName}</b>
                      <small>{x.linkId}</small>
                    </div>
                    <strong>{displayTrafficSpeed(x.speedKmh)}</strong>
                    <em>{status}</em>
                  </article>
                );
              })
            ) : (
              <p>현재 조회 범위에 혼잡 후보가 없습니다.</p>
            )}
          </div>
        </section>
        <section className="report-cell reason-cell">
          <h3>시설 배치 사유</h3>
          {plan.facilities.length ? (
            plan.facilities.map((x) => (
              <p key={x.id}>
                <b>{x.name}</b> — {cleanReason(x.reason)}
              </p>
            ))
          ) : (
            <p>
              현재 데이터에서는 새 시설이 꼭 필요한 지역이 확인되지 않았습니다.
            </p>
          )}
          <h3 className="subhead">도로 개선 사유</h3>
          {plan.roads.length ? (
            plan.roads.map((x) => (
              <p key={x.id}>
                <b>{x.name}</b> — {x.reason}
              </p>
            ))
          ) : (
            <p>공공 교통 API 관측값 기준 혼잡 후보가 없습니다.</p>
          )}
        </section>
        <section className="report-cell">
          <h3>데이터 적용 근거</h3>
          {evidence.map((x) => (
            <p className="check" key={x}>
              ✓ {x}
            </p>
          ))}
        </section>
        <section className="report-cell">
          <h3>데이터 한계</h3>
          {limits.map((x) => (
            <p className="warn" key={x}>
              ⚠ {x}
            </p>
          ))}
        </section>
      </div>
    </section>
  );
}
function Metric({
  icon,
  label,
  value,
  description,
}: {
  icon: string;
  label: string;
  value: string;
  description: string;
}) {
  return (
    <article>
      <i>{icon}</i>
      <div>
        <span>{label}</span>
        <strong>{value}</strong>
        <small>{description}</small>
      </div>
    </article>
  );
}
const facilityVisual = (f: PlannedFacility): [string, string, string] =>
  (
    ({
      HOSPITAL: ["의료", "H", "#df4e5b"],
      PARK: ["공원", "P", "#2e9d68"],
      SCHOOL: ["교육", "S", "#4c78d0"],
      TRANSIT_HUB: ["교통", "T", "#e07832"],
      CULTURE: ["문화", "C", "#8b63c7"],
      PUBLIC_SERVICE: ["공공", "G", "#3b7d84"],
      CHILDCARE: ["보육", "C", "#e08a32"],
      SENIOR_CARE: ["돌봄", "W", "#8a6f4d"],
      SPORTS_CENTER: ["체육", "SP", "#2f8c7c"],
      LIBRARY: ["학습", "L", "#6f63c7"],
      GREEN_SHELTER: ["녹지", "G", "#2e9d68"],
      HEALTH_CENTER: ["건강", "HC", "#d75c71"],
      PARKING: ["주차", "P", "#60758a"],
    }) as Record<string, [string, string, string]>
  )[f.facilityType] || ["시설", "●", "#d2a93f"];
const facilityPriority = (f: PlannedFacility) =>
  ["CHILDCARE", "SENIOR_CARE", "HEALTH_CENTER", "HOSPITAL"].includes(
    f.facilityType,
  )
    ? "높음"
    : ["SPORTS_CENTER", "TRANSIT_HUB", "PARKING", "PUBLIC_SERVICE"].includes(
          f.facilityType,
        )
      ? "중간"
      : "낮음";
const improvementLabel: Record<string, string> = {
  OPERATION_DIAGNOSIS: "원인 진단",
  SIGNAL_OPTIMIZATION: "신호체계 개선",
  LANE_OPERATION: "차로 운영 개선",
  PUBLIC_TRANSIT: "대중교통 개선",
  PEDESTRIAN_SAFETY: "보행 안전 개선",
  DEMAND_MANAGEMENT: "교통 수요 관리",
  EXPANSION_REVIEW: "도로 확장 검토",
  NEW_ROAD_REVIEW: "도로 신설 검토",
};
const feasibilityLabel: Record<string, string> = {
  HIGH: "높음",
  MEDIUM: "보통",
  LOW: "낮음",
};
function districtOutline(boundary: DistrictBoundary) {
  return boundary.type === "Polygon"
    ? { type: "Polygon", coordinates: [boundary.coordinates[0]] }
    : {
        type: "MultiPolygon",
        coordinates: boundary.coordinates.map((polygon: any[]) => [polygon[0]]),
      };
}
function MapView({
  center,
  boundary,
  plan,
  title,
}: {
  center: [number, number];
  boundary?: DistrictBoundary;
  plan: CityPlan | null;
  title: string;
}) {
  const shell = useRef<HTMLElement>(null),
    host = useRef<HTMLDivElement>(null),
    mapRef = useRef<maplibregl.Map | null>(null),
    [is3d, set3d] = useState(true),
    [isFullscreen, setFullscreen] = useState(false);
  const facilities =
    plan?.facilities.filter((f) => f.longitude != null && f.latitude != null) ||
    [];
  // ts-expect-error MapLibre는 런타임 2D/3D 레이어 전환의 판별 유니온을 추론하지 못한다.
  useEffect(() => {
    if (!host.current) return;
    const markers: maplibregl.Marker[] = [];
    const style: any = {
      version: 8,
      sources: {
        vworld: {
          type: "raster",
          tiles: [`${API}/api/vworld/tiles/{z}/{y}/{x}.png`],
          tileSize: 256,
        },
        openmaptiles: {
          type: "vector",
          url: "https://demotiles.maplibre.org/tiles/tiles.json",
        },
      },
      layers: [{ id: "vworld", type: "raster", source: "vworld" }],
    };
    const m = new maplibregl.Map({
      container: host.current,
      style,
      center,
      zoom: 15.5,
      pitch: is3d ? 55 : 0,
      bearing: is3d ? -15 : 0,
    });
    mapRef.current = m;
    if (boundary)
      m.fitBounds(
        [
          [boundary.bounds[0], boundary.bounds[1]],
          [boundary.bounds[2], boundary.bounds[3]],
        ],
        { padding: 35, duration: 0 },
      );
    m.addControl(new maplibregl.NavigationControl(), "top-right");
    m.on("load", () => {
      if (boundary) {
        m.addSource("district-boundary", {
          type: "geojson",
          data: {
            type: "Feature",
            properties: { name: boundary.districtName },
            geometry: districtOutline(boundary),
          } as any,
        });
        m.addLayer({
          id: "district-boundary",
          source: "district-boundary",
          type: "line",
          paint: {
            "line-color": "#2563eb",
            "line-width": 3,
            "line-opacity": 0.9,
          },
        });
      }
      m.addLayer({
        id: "existing-buildings",
        source: "openmaptiles",
        "source-layer": "building",
        type: "fill-extrusion",
        paint: {
          "fill-extrusion-color": "#c7c4bb",
          "fill-extrusion-height": ["coalesce", ["get", "render_height"], 12],
          "fill-extrusion-opacity": 0.72,
        },
      });
      if (!plan) return;
      const features = facilities.map((f) => {
        const [, , color] = facilityVisual(f);
        return {
          type: "Feature",
          properties: { name: f.name, color },
          geometry: { type: "Point", coordinates: [f.longitude!, f.latitude!] },
        };
      });
      m.addSource("facility-areas", {
        type: "geojson",
        data: { type: "FeatureCollection", features } as any,
      });
      m.addLayer({
        id: "facility-area-fill",
        source: "facility-areas",
        type: "circle",
        paint: {
          "circle-color": ["get", "color"],
          "circle-radius": [
            "interpolate",
            ["linear"],
            ["zoom"],
            10,
            18,
            14,
            44,
            17,
            90,
          ],
          "circle-opacity": 0.2,
          "circle-stroke-color": ["get", "color"],
          "circle-stroke-width": 2,
          "circle-stroke-opacity": 0.7,
        },
      });
      m.addLayer({
        id: "facility-area-center",
        source: "facility-areas",
        type: "circle",
        paint: {
          "circle-color": ["get", "color"],
          "circle-radius": 7,
          "circle-stroke-color": "#fff",
          "circle-stroke-width": 3,
        },
      });
      facilities.forEach((f, i) => {
        const [label, icon, color] = facilityVisual(f);
        const el = document.createElement("button");
        el.className = "facility-marker area-marker";
        el.style.setProperty("--facility-color", color);
        el.innerHTML = `<strong>${i + 1}</strong><span>${escapeHtml(icon)} ${escapeHtml(f.name)}</span>`;
        const popup = new maplibregl.Popup({ offset: 28 }).setHTML(
          `<b>${escapeHtml(icon)} ${escapeHtml(f.name)}</b><small>${escapeHtml(label)} 우선 검토 권역</small><p>${escapeHtml(f.reason)}</p>`,
        );
        markers.push(
          new maplibregl.Marker({ element: el, anchor: "center" })
            .setLngLat([f.longitude!, f.latitude!])
            .setPopup(popup)
            .addTo(m),
        );
      });
      if (plan.roads.length) {
        m.addSource("ai-roads", {
          type: "geojson",
          data: {
            type: "FeatureCollection",
            features: plan.roads.map((r) => ({
              type: "Feature",
              properties: {
                name: r.name,
                improvement: r.improvementType,
                feasibility: r.feasibility,
                evidence: r.evidence,
                reason: r.reason,
                landImpact: r.landImpact,
                studies: r.requiredStudies.join(", "),
              },
              geometry: { type: "LineString", coordinates: r.coordinates },
            })),
          } as any,
        });
        m.addLayer({
          id: "ai-roads",
          source: "ai-roads",
          type: "line",
          paint: { "line-color": "#ff6b4a", "line-width": 7 },
        });
        m.on("click", "ai-roads", (e) => {
          const p = e.features?.[0]?.properties;
          if (!p) return;
          new maplibregl.Popup()
            .setLngLat(e.lngLat)
            .setHTML(
              `<b>${escapeHtml(p.name)}</b><small>${escapeHtml(p.improvement)} · 현실성 ${escapeHtml(p.feasibility)}</small><p>${escapeHtml(p.reason)}</p><p>근거: ${escapeHtml(p.evidence)}</p><p>토지·건물 영향: ${escapeHtml(p.landImpact)}</p><p>추가 조사: ${escapeHtml(p.studies)}</p>`,
            )
            .addTo(m);
        });
        m.on(
          "mouseenter",
          "ai-roads",
          () => (m.getCanvas().style.cursor = "pointer"),
        );
        m.on("mouseleave", "ai-roads", () => (m.getCanvas().style.cursor = ""));
      }
      if (plan.zones.length) {
        m.addSource("ai-zones", {
          type: "geojson",
          data: {
            type: "FeatureCollection",
            features: plan.zones.map((z) => ({
              type: "Feature",
              properties: { name: z.name },
              geometry: { type: "Polygon", coordinates: [z.coordinates] },
            })),
          } as any,
        });
        if (is3d) {
          m.addLayer({
            id: "ai-zones",
            source: "ai-zones",
            type: "fill-extrusion",
            paint: {
              "fill-extrusion-color": "#36a58a",
              "fill-extrusion-height": 8,
              "fill-extrusion-opacity": 0.45,
            },
          });
        } else {
          m.addLayer({
            id: "ai-zones",
            source: "ai-zones",
            type: "fill",
            paint: {
              "fill-color": "#36a58a",
              "fill-opacity": 0.35,
            },
          });
        }
      }
    });
    return () => {
      markers.forEach((x) => x.remove());
      mapRef.current = null;
      m.remove();
    };
  }, [center, boundary, plan, is3d]);
  useEffect(() => {
    const map = mapRef.current;
    if (!map || !plan) return;
    const labels = plan.roads
      .filter((x) => x.coordinates.length >= 2)
      .map((road) => {
        const point = road.coordinates[Math.floor(road.coordinates.length / 2)],
          el = document.createElement("span");
        el.className = "road-line-label";
        el.textContent = road.name;
        return new maplibregl.Marker({ element: el, anchor: "bottom" })
          .setLngLat([point[0], point[1]])
          .addTo(map);
      });
    return () => labels.forEach((x) => x.remove());
  }, [plan, is3d]);
  useEffect(() => {
    const onChange = () => {
      const active = document.fullscreenElement === shell.current;
      setFullscreen(active);
      setTimeout(() => mapRef.current?.resize(), 80);
    };
    document.addEventListener("fullscreenchange", onChange);
    return () => document.removeEventListener("fullscreenchange", onChange);
  }, []);
  const toggleFullscreen = async () => {
    if (!shell.current) return;
    try {
      if (document.fullscreenElement) await document.exitFullscreen();
      else await shell.current.requestFullscreen();
    } catch {
      setErrorMessage(
        "전체 화면을 열 수 없습니다. 브라우저 권한을 확인해 주세요.",
      );
    }
  };
  const [errorMessage, setErrorMessage] = useState("");
  return (
    <article
      ref={shell}
      className={`live-map ${isFullscreen ? "map-fullscreen" : ""}`}
    >
      <header>
        <small>분석 범위 · {boundary?.districtName || "선택 구"} 전체</small>
        <b>{title}</b>
        <button className="map-mode-button" onClick={() => set3d((v) => !v)}>
          {is3d ? "2D" : "3D"}
        </button>
      </header>
      <div ref={host} />
      <aside className="map-layer-legend">
        <b>지도 표시</b>
        {boundary && (
          <span>
            <i className="boundary-swatch" />
            파란선 · {boundary.districtName} 전체 경계
          </span>
        )}
        <span>
          <i className="building-swatch" />
          회색 · 기존 건물
        </span>
        {plan && (
          <>
            <span>
              <i className="road-swatch" />
              주황선 · 실제 혼잡 도로와 개선 대상
            </span>
            <span>
              <i className="zone-swatch" />
              초록면 · 우선 개선구역
            </span>
          </>
        )}
      </aside>
      {plan && (
        <aside className="facility-legend">
          <b>
            {facilities.length
              ? `필요 시설 검토 권역 · ${facilities.length}개`
              : "현재 배치가 적정하여 추가 시설 없음"}
          </b>
          {facilities.map((f, i) => {
            const [label, icon, color] = facilityVisual(f);
            return (
              <span
                key={f.id}
                style={{ "--facility-color": color } as CSSProperties}
              >
                <strong>{i + 1}</strong>
                {icon} {f.name}
                <small>{label}</small>
              </span>
            );
          })}
        </aside>
      )}
      <button
        className="map-fullscreen-button"
        onClick={toggleFullscreen}
        aria-label={
          isFullscreen ? "전체 화면 닫기" : "지도를 전체 화면으로 보기"
        }
        title={isFullscreen ? "전체 화면 닫기" : "전체 화면으로 보기"}
      >
        <span>{isFullscreen ? "×" : "⛶"}</span>
        <b>{isFullscreen ? "닫기" : "전체 화면"}</b>
      </button>
      {errorMessage && <p className="map-fullscreen-error">{errorMessage}</p>}
    </article>
  );
}
