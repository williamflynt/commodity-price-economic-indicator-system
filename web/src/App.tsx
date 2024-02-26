import React from "react";
import './App.css'
import {Layout} from "./Layout.tsx";
import {Events} from "./Events.tsx";
import {AnalysisPane} from "./AnalysisPane.tsx";
import {SseEvent, useSseConnection} from "./Sse.ts";
import {AnalysisList} from "./AnalysisList.tsx";
import {AnalysisData, backendClient, useAnalysisPreviews} from "./Backend.ts";


/**
 * The main application component.
 * @constructor
 */
export const App = () => {
    const [analysisData, setAnalysisData] = React.useState<AnalysisData | null>(null)
    // When any new analysis comes in, fetch and retrieve it. This is dumb - we should wait for a specific one.
    const onAnalysisDone = React.useCallback(async (eventData: SseEvent) => {
        if (eventData.type === "ANALYZE-DONE" && eventData.id !== null) {
            setAnalysisData(await backendClient.getAnalysisDataById(eventData.id))
        }
    }, [setAnalysisData])
    const sseConnection = useSseConnection(onAnalysisDone)
    const {analysisPreviews} = useAnalysisPreviews()

    return (
        <Layout
            leftTop={<AnalysisPane data={analysisData}/>}

            leftBottom={<AnalysisList items={analysisPreviews} setFunc={setAnalysisData}/>}

            right={<Events
                events={sseConnection.events}
                error={sseConnection.error}
                eventSource={sseConnection.eventSource}
                sseStatus={sseConnection.sseStatus}
            />}
        />
    )
}
