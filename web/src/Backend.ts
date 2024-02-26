import axios, {AxiosInstance} from 'axios';
import {useEffect, useState} from "react";


const BASE_API_URL = (import.meta.env.VITE_API_URL || 'http://localhost:8888') as string;

export type AnalysisPayload = {
    id: string;
    startDate: string;
    endDate: string;
    category1: string;
    category2: string;
    medianCategory1: number;
    slopeCategory1: number;
    medianCategory2: number;
    slopeCategory2: number;
    regressionSlope: number;
    regressionIntercept: number;
}

export type AnalysisData = {
    id: string;
    startDate: string;
    endDate: string;
    category: string;
    payload: AnalysisPayload;
}

export type AnalysisPreview = {
    id: string;
    startDate: string;
    endDate: string;
    category: string;
}


class ApiClient {
    private axiosInstance: AxiosInstance;

    constructor(baseURL: string) {
        this.axiosInstance = axios.create({baseURL});
    }

    getAnalysisPreviews = async (): Promise<AnalysisPreview[]> => {
        const response = await this.axiosInstance.get('/analysis');
        return response.data;
    }

    getAnalysisDataById = async (id: string): Promise<AnalysisData> => {
        const response = await this.axiosInstance.get(`/analysis/${id}`);
        return response.data;
    }

    requestAnalysis = async (startDate: string, endDate: string, category: string): Promise<{ id: string }> => {
        const response = await this.axiosInstance.get(`/analysis/${category}/${startDate}/${endDate}`);
        return response.data;
    }
}

export const backendClient = new ApiClient(BASE_API_URL);

export const useAnalysisPreviews = () => {
    const [analysisPreviews, setAnalysisPreviews] = useState<AnalysisPreview[] | null>(null);
    const [error, setError] = useState<null | string>(null);

    useEffect(() => {
        const fetchPreviews = () => {
            backendClient.getAnalysisPreviews()
                .then((data) => {
                    data.sort((a, b) => a.startDate.localeCompare(b.startDate))
                    setAnalysisPreviews(data)
                })
                .catch((error) => setError(error.message));
        }

        fetchPreviews(); // Fetch immediately on component mount

        const intervalId = setInterval(fetchPreviews, 5000);

        return () => clearInterval(intervalId);
    }, []);

    return {analysisPreviews, error};
}
