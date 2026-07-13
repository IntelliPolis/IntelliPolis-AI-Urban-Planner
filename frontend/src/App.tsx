"use client";

import { useState } from "react";

const plans = [
  { code: "BALANCED", label: "균형형", score: 84, tone: "교통·환경·생활의 조화" },
  { code: "ECO_FOCUSED", label: "환경 중심형", score: 88, tone: "녹지와 보행 경험 강화" },
  { code: "COST_EFFECTIVE", label: "예산 효율형", score: 81, tone: "기존 인프라를 현명하게 활용" },
];

const scores = [
  ["교통", 70], ["환경", 65], ["경제", 80], ["생활", 72],
];

export default function Home() {
  const [menuOpen, setMenuOpen] = useState(false);
  const [selectedPlan, setSelectedPlan] = useState(0);
  const [view3d, setView3d] = useState(true);

  const scrollTo = (id: string) => {
    document.getElementById(id)?.scrollIntoView({ behavior: "smooth" });
    setMenuOpen(false);
  };

  return (
    <main>
      <header className="nav-shell">
        <button className="brand" onClick={() => scrollTo("home")} aria-label="홈으로 이동">
          <span className="brand-mark"><i /><i /><i /></span>
          <span>INTELLI<span>POLIS</span></span>
        </button>
        <button className="menu-button" onClick={() => setMenuOpen(!menuOpen)} aria-label="메뉴 열기">☰</button>
        <nav className={menuOpen ? "open" : ""}>
          <button onClick={() => scrollTo("about")}>플랫폼 소개</button>
          <button onClick={() => scrollTo("process")}>분석 과정</button>
          <button onClick={() => scrollTo("dashboard")}>대시보드</button>
          <button onClick={() => scrollTo("plans")}>계획안 비교</button>
        </nav>
        <button className="nav-cta" onClick={() => scrollTo("dashboard")}>도시 분석 시작</button>
      </header>

      <section className="hero" id="home">
        <div className="hero-copy">
          <div className="eyebrow"><span /> AI URBAN DECISION SUPPORT</div>
          <h1>도시의 가능성을<br /><em>더 현명하게 설계합니다.</em></h1>
          <p>교통·환경·경제·생활 데이터를 하나의 시선으로 분석하고,<br className="desktop" /> AI와 함께 현실적인 도시계획 대안을 비교하세요.</p>
          <div className="hero-actions">
            <button className="primary" onClick={() => scrollTo("dashboard")}>새 도시 분석 시작 <span>↗</span></button>
            <button className="text-link" onClick={() => scrollTo("about")}>서비스 살펴보기 <span>→</span></button>
          </div>
          <div className="trust-row">
            <div><strong>4</strong><span>전문 분석 관점</span></div>
            <div><strong>3</strong><span>도시계획 대안</span></div>
            <div><strong>100%</strong><span>설명 가능한 비교</span></div>
          </div>
        </div>
        <div className="hero-visual" aria-label="미래 스마트시티 조감도">
          <div className="image-glow" />
          <img
            src="/intellipolis-hero.png"
            alt="흰색과 금색으로 설계된 미래 스마트시티"
          />
          <div className="float-card card-one"><span className="pulse" /><b>도시 종합 점수</b><strong>72</strong><small>현재 분석 기준</small></div>
          <div className="float-card card-two"><span>AI INSIGHT</span><b>녹지 접근성 개선</b><small>우선 검토가 필요합니다</small></div>
          <div className="gold-line line-a" /><div className="gold-line line-b" />
        </div>
        <div className="scroll-cue"><span />SCROLL TO EXPLORE</div>
      </section>

      <section className="intro section" id="about">
        <div className="section-kicker">WHY INTELLIPOLIS</div>
        <div className="section-heading">
          <h2>AI의 제안과 데이터의 근거를<br />하나의 화면에서.</h2>
          <p>IntelliPolis는 행정 결정을 대신하지 않습니다. 다양한 관점의 대안을 투명하게 비교하고 더 나은 선택을 검토하도록 돕습니다.</p>
        </div>
        <div className="feature-grid">
          {[
            ["01", "다각도 도시 분석", "교통, 환경, 경제, 생활 편의 데이터를 분야별 관점으로 해석합니다."],
            ["02", "검증 가능한 점수", "객관적인 수치는 Java 규칙 엔진이 계산하고 AI는 그 의미를 설명합니다."],
            ["03", "지도 기반 계획", "시설, 도로, 개발 구역을 지도에서 직관적으로 확인하고 비교합니다."],
          ].map(([n, title, desc]) => <article className="feature-card" key={n}><span>{n}</span><div className="feature-icon">{n === "01" ? "⌁" : n === "02" ? "◫" : "⌖"}</div><h3>{title}</h3><p>{desc}</p><i /></article>)}
        </div>
      </section>

      <section className="process section" id="process">
        <div className="process-copy">
          <div className="section-kicker">HOW IT WORKS</div>
          <h2>복잡한 도시 데이터를<br />명확한 계획으로.</h2>
          <p>입력부터 대안 비교까지, 모든 분석 과정이 자연스럽게 연결됩니다.</p>
        </div>
        <div className="steps">
          {["도시 데이터 입력", "분야별 AI 분석", "계획안 3개 생성", "지도에서 비교"].map((step, i) => <div className="step" key={step}><span>0{i + 1}</span><b>{step}</b>{i < 3 && <i>→</i>}</div>)}
        </div>
      </section>

      <section className="dashboard-section" id="dashboard">
        <div className="dashboard-title">
          <div><div className="section-kicker">LIVE DASHBOARD PREVIEW</div><h2>가상 해안구 도시 분석</h2></div>
          <div className="status"><span /> 샘플 데이터 연결됨</div>
        </div>
        <div className="dashboard-grid">
          <aside className="score-panel panel">
            <p className="panel-label">CURRENT CITY SCORE</p>
            <div className="overall-score"><strong>72</strong><span>/ 100</span></div>
            <small>현재 도시 종합 점수</small>
            <div className="score-list">
              {scores.map(([label, score]) => <div key={label}><span>{label}</span><i><b style={{ width: `${score}%` }} /></i><strong>{score}</strong></div>)}
            </div>
            <button onClick={() => scrollTo("plans")}>세부 분석 결과 보기 <span>→</span></button>
          </aside>
          <div className="map-panel panel">
            <div className="map-top"><div><span>INTELLIPOLIS MAP</span><b>가상 해안구 · 부산광역시</b></div><button onClick={() => setView3d(!view3d)}><span className={!view3d ? "active" : ""}>2D</span><span className={view3d ? "active" : ""}>3D</span></button></div>
            <div className={`map-art ${view3d ? "is-3d" : ""}`}>
              <div className="water" /><div className="road road-a" /><div className="road road-b" />
              {[1,2,3,4,5,6,7,8,9].map(n => <i className={`building b${n}`} key={n} />)}
              <span className="pin park">♧<small>신규 녹지</small></span><span className="pin transit">⌁<small>환승 거점</small></span><span className="zone">개발 검토 구역</span>
            </div>
            <div className="map-legend"><span><i className="green" />공원</span><span><i className="gold" />교통</span><span><i className="blue" />개발구역</span></div>
          </div>
          <aside className="insight-panel panel">
            <p className="panel-label">AI AGENT INSIGHTS</p>
            {[
              ["교통", "중앙로 혼잡 분산을 위한 대중교통 연결 강화가 필요합니다."],
              ["환경", "서부 생활권의 공원 접근성이 상대적으로 낮습니다."],
              ["생활", "고령 인구를 고려한 의료 접근성 개선을 권장합니다."],
            ].map(([tag, text], i) => <article key={tag}><span className={`agent a${i}`}>{tag}</span><p>{text}</p><small>AI 분석 결과 · 참고용</small></article>)}
          </aside>
        </div>
      </section>

      <section className="plans section" id="plans">
        <div className="section-kicker">COMPARE THE FUTURE</div>
        <div className="section-heading"><h2>세 가지 가능성,<br />더 나은 하나의 선택.</h2><p>각 계획안을 선택해 예상 종합 점수와 핵심 방향을 비교해보세요.</p></div>
        <div className="plan-tabs">
          {plans.map((plan, i) => <button className={selectedPlan === i ? "active" : ""} onClick={() => setSelectedPlan(i)} key={plan.code}><span>0{i+1}</span><b>{plan.label}</b><small>{plan.code}</small></button>)}
        </div>
        <div className="plan-result">
          <div><span>EXPECTED OVERALL SCORE</span><strong>{plans[selectedPlan].score}<small>/100</small></strong></div>
          <div><h3>{plans[selectedPlan].label} 도시계획안</h3><p>{plans[selectedPlan].tone}</p><ul><li>지역 여건과 우선 목표를 함께 반영</li><li>예산 범위 안에서 단계적 개선 제안</li><li>계획 시설과 개발 후보를 지도에 표시</li></ul></div>
          <button>이 계획안 자세히 보기 <span>↗</span></button>
        </div>
      </section>

      <section className="closing">
        <div><span>DESIGNING SMARTER CITIES WITH AI</span><h2>도시의 다음 장면을<br />함께 설계해보세요.</h2><button onClick={() => scrollTo("home")}>프로젝트 시작하기 <span>↗</span></button></div>
      </section>
      <footer><button className="brand" onClick={() => scrollTo("home")}><span className="brand-mark"><i /><i /><i /></span><span>INTELLI<span>POLIS</span></span></button><p>AI 기반 도시계획 의사결정 지원 플랫폼</p><small>© 2026 Team IntelliPolis. For simulation and decision support only.</small></footer>
    </main>
  );
}
