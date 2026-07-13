import { Link } from "react-router-dom";
export default function AppHeader(){return <header className="header"><Link to="/" className="brand"><span className="brand-mark"><i/><i/><i/></span><span>INTELLI<em>POLIS</em></span></Link><nav><Link to="/">도시 분석</Link><a href="/#process">분석 과정</a></nav><Link to="/" className="header-cta">새 분석 시작 ↗</Link></header>}
