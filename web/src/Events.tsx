import React from 'react';
import {Badge, Box, HStack, Spacer, Text} from "@chakra-ui/react";
import {SseConnection, SseEvent} from "./Sse.ts";

export const getColorScheme = (eventType: string | null) => {
    if (eventType === null) {
        return "gray";
    }
    switch (eventType) {
        case "ERROR":
            return "red";
        case "HEARTBEAT":
            return "pink";
        case "ANALYZE":
            return "blue";
        case "COLLECT":
            return "yellow";
        case "ANALYZE-START":
            return "blue";
        case "COLLECT-START":
            return "yellow";
        case "ANALYZE-DONE":
            return "blue";
        case "COLLECT-DONE":
            return "yellow";
        case "HELO":
            return "green";
        default:
            return "gray";
    }
}

const EventRow: React.FC<{ event: SseEvent }> = ({event}) => {
    return (
        <HStack paddingRight={2}>
            <Badge colorScheme={getColorScheme(event.type)} textAlign={"center"}>{event.type}</Badge>
            <Spacer gap={2}/>
            <Text size={'sm'}>{event.id}</Text>
        </HStack>
    );
}

export const Events: React.FC<SseConnection> = ({sseStatus, events, error}) => {
    return (
        <Box height="100%" overflowX="hidden">
            <p>EVENTS - {sseStatus}</p>
            <Box marginLeft={2}>
                {error && <span>Uh oh. {error}</span>}
                {!events.length && <span>No events yet.</span>}
                {events.reverse().map((event, index) => (
                    <EventRow event={event} key={index}/>
                ))}
            </Box>
        </Box>
    );
};
