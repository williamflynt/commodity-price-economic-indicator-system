package edu.colorado

import kotlinx.serialization.Serializable

@Serializable
data class AnalysisResult(
    val id: String,
    val startDate: String,
    val endDate: String,
    val category1: String,
    val category2: String,
    val medianCategory1: Float,
    val slopeCategory1: Float,
    val medianCategory2: Float,
    val slopeCategory2: Float,
    val regressionSlope: Float,
    val regressionIntercept: Float,
)

@Serializable
data class CollectedObservation(
    val id: Int,
    val category: String,
    val date: String,
    val value: Float?
)
