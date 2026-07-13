import type { CityAnalysisRequest, CityAnalysisResponse, CityPlan, PlanType } from "../types/city";
const BASE=import.meta.env.VITE_API_BASE_URL||"http://localhost:8080";
async function request<T>(path:string,init?:RequestInit):Promise<T>{try{const r=await fetch(`${BASE}${path}`,{...init,headers:{"Content-Type":"application/json",...init?.headers}});if(!r.ok){const body=await r.json().catch(()=>({})) as {message?:string};throw new Error(body.message||`요청에 실패했습니다. (${r.status})`)}return await r.json() as T}catch(e){if(e instanceof TypeError)throw new Error("백엔드 서버에 연결할 수 없습니다. Spring Boot 서버가 실행 중인지 확인해주세요.");throw e}}
export const getHealth=()=>request<{status:string;service:string}>("/api/health");
export const getSampleCity=()=>request<CityAnalysisRequest>("/api/cities/sample");
export const createCityAnalysis=(body:CityAnalysisRequest)=>request<CityAnalysisResponse>("/api/city-analyses",{method:"POST",body:JSON.stringify(body)});
export const getCityAnalysis=(id:string)=>request<CityAnalysisResponse>(`/api/city-analyses/${id}`);
export const evaluatePlan=(id:string,type:PlanType,plan:CityPlan)=>request<CityAnalysisResponse>(`/api/city-analyses/${id}/plans/${type}/evaluate`,{method:"POST",body:JSON.stringify(plan)});
export const explainPlan=(id:string,type:PlanType)=>request<{explanation:string}>(`/api/city-analyses/${id}/plans/${type}/explain`,{method:"POST"});
