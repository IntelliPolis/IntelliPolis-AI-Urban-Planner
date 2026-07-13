import { useEffect,useState } from "react";
const messages=["도시 데이터를 검증하고 있습니다.","교통·환경·경제·생활 에이전트가 분석 중입니다.","도시계획 대안을 구성하고 있습니다.","예상 점수와 지도 데이터를 정리하고 있습니다."];
export default function AnalysisLoading(){const [progress,setProgress]=useState(12);useEffect(()=>{const id=setInterval(()=>setProgress(v=>Math.min(95,v+4)),500);return()=>clearInterval(id)},[]);return <div className="loading" role="status"><strong>{messages[Math.min(3,Math.floor(progress/25))]}</strong><progress max="100" value={progress}/><span>{progress}%</span></div>}
