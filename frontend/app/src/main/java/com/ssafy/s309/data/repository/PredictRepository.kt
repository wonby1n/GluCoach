package com.ssafy.s309.data.repository

import com.ssafy.s309.data.api.PredictApi
import com.ssafy.s309.data.model.CompareExplainRequest
import com.ssafy.s309.data.model.CompareExplainResponse
import com.ssafy.s309.data.model.GlucoseCompareRequest
import com.ssafy.s309.data.model.GlucoseCompareResponse
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PredictRepository
    @Inject
    constructor(
        private val predictApi: PredictApi,
    ) {
        suspend fun compareGlucose(request: GlucoseCompareRequest): Result<GlucoseCompareResponse> =
            runCatching {
                predictApi.compareGlucose(request)
            }

        suspend fun explainCompare(request: CompareExplainRequest): Result<CompareExplainResponse> =
            runCatching {
                predictApi.explainCompare(request)
            }
    }
