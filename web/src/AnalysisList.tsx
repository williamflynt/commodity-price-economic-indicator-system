import React, {useCallback} from "react";
import {Box, HStack, Link, Spacer, StackItem, Text, VStack} from "@chakra-ui/react";
import {AnalysisPayload, AnalysisPreview, backendClient} from "./Backend.ts";

const AnalysisPreviewItem: React.FC<{ item: AnalysisPreview, onClick: () => void }> = ({item, onClick}) => {
    return (
        <HStack gap={1}>
            <Link color='teal.500' onClick={onClick}>{item.id}</Link>
            <Spacer padding={6}/>
            <Text>{item.startDate}</Text>
            <Text>-</Text>
            <Text>{item.endDate}</Text>
        </HStack>
    )
}

export const AnalysisList: React.FC<{
    items: AnalysisPreview[] | null,
    setFunc: (data: AnalysisPayload | null) => void
}> = ({items, setFunc}) => {
    if (items === null || !items.length) {
        return (
            <Box>
                <p>PAST ANALYSES</p>
                No analyses yet.
            </Box>
        )
    }

    const handleClick = useCallback(async (item: AnalysisPreview) => {
        const data = await backendClient.getAnalysisDataById(item.id)
        setFunc(data)
    }, [setFunc])

    return (
        <>
            <p>PAST ANALYSES</p>
            <VStack spacing={2}>
                {items.map((item, index) => (
                    <StackItem key={index}>
                        <AnalysisPreviewItem item={item} onClick={() => handleClick(item)}/>
                    </StackItem>
                ))}
            </VStack>
        </>
    )
}
