import React, { useState } from 'react';

// 프로젝트 요구사항에 정의된 타입 스냅샷 (필요시 types/city.ts 등과 연동하거나 유지)
interface AnalysisRequest {
    city: string;
    district?: string;
}

export default function App() {
    // --- 1. 기존 핵심 상태 및 UI 관리 State ---
    const [cityInput, setCityInput] = useState('Busan');
    const [districtInput, setDistrictInput] = useState('');
    const [analysisResult, setAnalysisResult] = useState<any>(null);
    const [error, setError] = useState<string | null>(null);

    // --- 2. [추가] 실시간 진행률 연동용 State ---
    const [isAnalyzing, setIsAnalyzing] = useState(false);
    const [progress, setProgress] = useState(0);
    const [progressMessage, setProgressMessage] = useState('');

    // --- 3. 핵심 로직: 실제 API resolve 시점에 연동되는 분석 함수 ---
    const handleAnalyze = async () => {
        // 초기화 및 시작 세팅 (0%)
        setIsAnalyzing(true);
        setProgress(0);
        setProgressMessage('도시 기본 데이터(Summary)를 조회하고 있습니다...');
        setError(null);
        setAnalysisResult(null);

        const requestData: AnalysisRequest = {
            city: cityInput,
            district: districtInput || undefined
        };

        try {
            // [1단계] urban-data/summary 조회 (도시 기본 데이터)
            const urbanResponse = await fetch('/api/urban-data/summary', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(requestData),
            });
            if (!urbanResponse.ok) throw new Error('도시 기본 데이터 조회에 실패했습니다.');
            const urbanData = await urbanResponse.json();

            // 1단계 완료 -> 25% 업할당 및 다음 메시지 전환
            setProgress(25);
            setProgressMessage('교통 정보(Traffic Summary) 분석을 시작합니다...');

            // [2단계] traffic/summary 조회 (교통 정보)
            const trafficResponse = await fetch('/api/traffic/summary', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(requestData),
            });
            if (!trafficResponse.ok) throw new Error('교통 정보 분석에 실패했습니다.');
            const trafficData = await trafficResponse.json();

            // 2단계 완료 -> 50% 업할당 및 다음 메시지 전환
            setProgress(50);
            setProgressMessage('공간 및 입지 후보지(Spatial Candidates) 분석을 진행 중입니다...');

            // [3단계] spatial/candidates 조회 (공간·후보지 분석)
            const spatialResponse = await fetch('/api/spatial/candidates', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(requestData),
            });
            if (!spatialResponse.ok) throw new Error('공간 후보지 분석에 실패했습니다.');
            const spatialData = await spatialResponse.json();

            // 3단계 완료 -> 75% 업할당 및 생성 요청
            setProgress(75);
            setProgressMessage('AI 도시 계획안(City Analyses)을 대조 및 생성하고 있습니다...');

            // [4단계] city-analyses POST (AI 계획안 생성)
            // 네트워크 대기 시간을 고려하여 요청 직전에 95%~99% 선진입 유도 (실제 완료 전 100% 방지 규칙 준수)
            setProgress(95);

            const aiResponse = await fetch('/api/city-analyses', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    ...requestData,
                    urbanSummary: urbanData,
                    trafficSummary: trafficData,
                    spatialCandidates: spatialData
                }),
            });
            if (!aiResponse.ok) throw new Error('AI 계획안 최종 생성에 실패했습니다.');
            const finalResult = await aiResponse.json();

            // 모든 단계 최종 성공 -> 100% 도달
            setProgress(100);
            setProgressMessage('모든 분석이 성공적으로 완료되었습니다!');
            setAnalysisResult(finalResult);

            // 사용자 체감을 위해 100% 완료 상태를 잠시 보여준 뒤 모달 오버레이를 닫음
            setTimeout(() => {
                setIsAnalyzing(false);
            }, 600);

        } catch (err: any) {
            // 실패 시: 각 단계 catch에서 progress를 멈추고 에러 메시지 표시
            console.error('분석 에러 발생:', err);
            setError(err.message || '도시 분석 중 문제가 발생했습니다. 다시 시도해 주세요.');
            setProgress(0);
            setIsAnalyzing(false);
        }
    };

    return (
        <div className="live-app" style={{ padding: '20px', fontFamily: 'sans-serif' }}>
            <header className="nav-shell" style={{ marginBottom: '30px', borderBottom: '1px solid #ccc', paddingBottom: '10px' }}>
                <h2>IntelliPolis AI Urban Planner</h2>
            </header>

            {/* 입력 폼 파트 */}
            <div className="metric-row" style={{ display: 'flex', gap: '15px', marginBottom: '20px', alignItems: 'center' }}>
                <label>
                    <strong>도시 선택: </strong>
                    <input
                        type="text"
                        value={cityInput}
                        onChange={(e) => setCityInput(e.target.value)}
                        disabled={isAnalyzing}
                        style={{ padding: '5px', borderRadius: '4px', border: '1px solid #aaa' }}
                    />
                </label>
                <label>
                    <strong>세부 구/군 (선택): </strong>
                    <input
                        type="text"
                        value={districtInput}
                        placeholder="예: 해운대구"
                        onChange={(e) => setDistrictInput(e.target.value)}
                        disabled={isAnalyzing}
                        style={{ padding: '5px', borderRadius: '4px', border: '1px solid #aaa' }}
                    />
                </label>
                <button
                    onClick={handleAnalyze}
                    disabled={isAnalyzing}
                    style={{
                        padding: '6px 15px', backgroundColor: '#007bff', color: '#fff',
                        border: 'none', borderRadius: '4px', cursor: 'pointer', fontWeight: 'bold'
                    }}
                >
                    {isAnalyzing ? '분석 진행 중' : '분석 시작'}
                </button>
            </div>

            {/* 에러 발생 시 기존 error state 재활용 레이아웃 */}
            {error && (
                <div className="warning-panel" style={{
                    backgroundColor: '#fff3cd', color: '#856404', padding: '15px',
                    borderRadius: '4px', border: '1px solid #ffeeba', marginBottom: '20px'
                }}>
                    <strong>⚠️ 에러 안내:</strong> {error}
                </div>
            )}

            {/* --- 실시간 진행률 팝업 오버레이 UI --- */}
            {isAnalyzing && (
                <div className="progress-overlay" style={{
                    position: 'fixed', top: 0, left: 0, width: '100vw', height: '100vh',
                    backgroundColor: 'rgba(0, 0, 0, 0.6)', display: 'flex', flexDirection: 'column',
                    justifyContent: 'center', alignItems: 'center', zIndex: 9999, color: '#fff'
                }}>
                    <div style={{ backgroundColor: '#fff', padding: '30px', borderRadius: '8px', color: '#333', width: '350px', textAlign: 'center' }}>
                        <h3 style={{ marginTop: 0, marginBottom: '20px' }}>도시 분석 시뮬레이션</h3>

                        {/* 프로그레스 바 외형 트랙 */}
                        <div className="progress-container" style={{
                            width: '100%', backgroundColor: '#e9ecef', borderRadius: '4px', overflow: 'hidden', height: '16px'
                        }}>
                            {/* 프로그레스 바 내부 채우기 (CSS Transition 적용으로 25%씩 부드럽게 전진) */}
                            <div className="progress-bar" style={{
                                width: `${progress}%`, height: '100%', backgroundColor: '#28a745',
                                transition: 'width 0.5s ease-in-out'
                            }} />
                        </div>

                        <div style={{ marginTop: '15px', fontSize: '18px', fontWeight: 'bold', color: '#28a745' }}>
                            {progress}% 완료
                        </div>
                        <div style={{ marginTop: '8px', fontSize: '14px', color: '#666', minHeight: '40px', lineHeight: '1.4' }}>
                            {progressMessage}
                        </div>
                    </div>
                </div>
            )}

            {/* 결과 화면 출력 렌더링 파트 */}
            {analysisResult && (
                <div className="analysis-result-panel" style={{ marginTop: '20px', padding: '20px', border: '1px solid #28a745', borderRadius: '6px' }}>
                    <h3 style={{ color: '#28a745', marginTop: 0 }}>📊 AI 분석 및 계획안 생성 완료</h3>
                    <pre style={{ backgroundColor: '#f8f9fa', padding: '15px', borderRadius: '4px', overflowX: 'auto' }}>
            {JSON.stringify(analysisResult, null, 2)}
          </pre>
                </div>
            )}
        </div>
    );
}