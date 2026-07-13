import type { AnalysisDomain, PlanType, Severity } from "../types/city";
export const population=(v:number)=>`${v.toLocaleString("ko-KR")}명`;
export const money=(v:number|null|undefined)=>v==null?"정보 없음":`${v.toLocaleString("ko-KR")}원`;
export const score=(v:number|null|undefined)=>v==null?"정보 없음":`${v}점`;
export const date=(v:string)=>new Intl.DateTimeFormat("ko-KR",{dateStyle:"medium",timeStyle:"short"}).format(new Date(v));
export const domainLabel:Record<AnalysisDomain,string>={TRAFFIC:"교통",ENVIRONMENT:"환경",ECONOMY:"경제",LIVING:"생활"};
export const severityLabel:Record<Severity,string>={LOW:"낮음",MEDIUM:"보통",HIGH:"높음"};
export const planLabel:Record<PlanType,string>={BALANCED:"균형형",ECO_FOCUSED:"환경 중심형",COST_EFFECTIVE:"예산 효율형"};
