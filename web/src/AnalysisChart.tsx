import React from 'react';
import {AnalysisPayload} from "./Backend.ts";
import {CartesianGrid, Legend, Line, LineChart, ReferenceLine, Tooltip, XAxis, YAxis} from 'recharts';

interface Props {
    data: AnalysisPayload;
}

export const AnalysisChart: React.FC<Props> = ({data}) => {
    const chartData = [
        { name: 'Start', value: data.regressionIntercept },
        { name: 'End', value: data.regressionIntercept + data.regressionSlope },
    ];

    return (
        <LineChart
            width={500}
            height={300}
            data={chartData}
            margin={{ top: 5, right: 30, left: 20, bottom: 5 }}
        >
            <CartesianGrid strokeDasharray="3 3" />
            <XAxis dataKey="name" />
            <YAxis yAxisId="left" orientation="left" stroke="#8884d8" domain={[0, data.medianCategory2 + 1]} />
            <YAxis yAxisId="right" orientation="right" stroke="#82ca9d" domain={[0, data.medianCategory1 + 10]} />
            <Tooltip />
            <Legend />
            <Line yAxisId="left" type="monotone" dataKey="value" label="regression" stroke="red" activeDot={{ r: 8 }} />
            <ReferenceLine yAxisId="left" y={data.medianCategory2} label={data.category2} stroke="#8884d8" />
            <ReferenceLine yAxisId="right" y={data.medianCategory1} label={data.category1} stroke="#82ca9d" />
        </LineChart>
    );
};