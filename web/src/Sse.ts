import {useCallback, useLayoutEffect, useState} from "react";

export type SseEvent = {
    type: string | null;
    id: string | null;
    payload: string | null; // JSON
}

const readyState = (readyState: number) => {
    return readyState == 0 ? "CONNECTING" : readyState == 1 ? "OPEN" : "CLOSED";
}


export type SseConnection = {
    events: LoggedEvent[];
    error: null | string;
    sseStatus: null | string;
    eventSource: null | EventSource;
}

export type LoggedEvent = SseEvent & { timestamp: number };

export const useSseConnection = (onMessage?: (data: SseEvent) => void): SseConnection => {
    const [events, setEvents] = useState<LoggedEvent[]>([]);
    const [error, setError] = useState<null | string>(null);
    const [sseStatus, setSseStatus] = useState<null | string>(null);
    const [eventSource, setEventSource] = useState<null | EventSource>(null);

    const connectToServer = useCallback(() => {
        const eventSource = new EventSource('/sse');
        setSseStatus(readyState(eventSource.readyState));

        eventSource.onopen = () => {
            setError(null);
            setSseStatus(readyState(eventSource.readyState));
        }

        eventSource.onmessage = (event) => {
            setError(null);
            const data: SseEvent = JSON.parse(event.data);
            if (data.type != 'HEARTBEAT') {
                setEvents((prevEvents: LoggedEvent[]) => [...prevEvents, {...data, timestamp: Math.floor(Date.now() / 1000)}]);
            }
            if (onMessage) {
                onMessage(data);
            }
        };

        eventSource.onerror = () => {
            eventSource.close();
            setError('Not connected to the server.');
            setSseStatus("RECONNECTING");
            setTimeout(connectToServer, 1500); // Reconnect efforts.
        }

        setEventSource(eventSource);

        // Dispose of the event source when the component is unmounted.
        return () => {
            console.info("closing the event source")
            eventSource.close();
            setSseStatus(readyState(eventSource.readyState));
        };
    }, [])

    useLayoutEffect(() => connectToServer(), []);

    return {events, error, sseStatus, eventSource};
}