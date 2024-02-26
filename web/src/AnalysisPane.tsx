import React, {useState} from "react";
import {
    Box,
    Button,
    FormControl,
    FormLabel,
    HStack,
    Input,
    Select,
    Stack,
    Text,
    useToast,
    VStack
} from "@chakra-ui/react";
import {AnalysisData, backendClient} from "./Backend.ts";
import {AnalysisChart} from "./AnalysisChart.tsx";

const AnalysisRequestForm: React.FC = () => {
    const [startDate, setStartDate] = useState("");
    const [endDate, setEndDate] = useState("");
    const [category, setCategory] = useState("DCOILBRENTEU");
    const toast = useToast();

    const today = new Date().toISOString().split('T')[0]; // Format today's date as YYYY-MM-DD.

    const handleSubmit = async () => {
        // TODO: Update required fields, validation, send a nice notice if we are missing something, ...
        if (startDate && endDate && category) {
            try {
                const response = await backendClient.requestAnalysis(startDate, endDate, category);
                toast({
                    title: "Analysis requested",
                    status: "success",
                    duration: 2500,
                    isClosable: true,
                });
                setStartDate("");
                setEndDate("");
            } catch (error) {
                toast({
                    title: "An error occurred.",
                    description: "Unable to complete analysis request",
                    status: "error",
                    duration: 5000,
                    isClosable: true,
                });
            }
        }
    };

    const readySubmit = startDate && endDate && category

    return (
        <VStack spacing={"12px"} align={"left"} padding={"6px"}>
            <HStack spacing={"12px"}>
                <FormControl isRequired>
                    <FormLabel>Start Date</FormLabel>
                    <Input
                        value={startDate}
                        onChange={(e) => setStartDate(e.target.value)}
                        max={endDate || today}
                        placeholder="YYYY-MM-DD"
                        size="md"
                        type="date"
                    />
                </FormControl>
                <FormControl isRequired>
                    <FormLabel>End Date</FormLabel>
                    <Input
                        value={endDate}
                        onChange={(e) => setEndDate(e.target.value)}
                        min={startDate}
                        max={today}
                        placeholder="YYYY-MM-DD"
                        size="md"
                        type="date"
                    />
                </FormControl>
                <FormControl>
                    <FormLabel>Commodity Category</FormLabel>
                    <Select value={category} onChange={(e) => setCategory(e.target.value)}>
                        <option value="DCOILBRENTEU">Brent Crude EU</option>
                    </Select>
                </FormControl>
            </HStack>
            <HStack>
                <Button colorScheme={"pink"} onClick={handleSubmit} isDisabled={!readySubmit}>
                    Run Analysis
                </Button>
            </HStack>
        </VStack>
    );
};

const AnalysisDisplay: React.FC<{ data: AnalysisData | null }> = ({data}) => {
    if (!data) {
        return <Box>Waiting for analysis data...</Box>
    }

    return (
        <Box>
            <h3>Analysis ID: <Text as={'samp'}>{data.id}</Text></h3>
            <HStack padding={4}>
                <AnalysisChart data={data}/>
                <pre>{JSON.stringify(data, null, 2)}</pre>
            </HStack>
        </Box>
    )
}

export const AnalysisPane: React.FC<{ data: AnalysisData | null }> = ({data}) => {
    return (
        <>
            <p>ANALYSIS</p>
            <Stack spacing={"24px"}>
                <AnalysisRequestForm/>
                <AnalysisDisplay data={data}/>
            </Stack>
        </>
    )
}
