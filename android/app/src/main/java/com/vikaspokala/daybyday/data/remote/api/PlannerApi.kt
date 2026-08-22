package com.vikaspokala.daybyday.data.remote.api

import com.vikaspokala.daybyday.data.remote.dto.PlannerRefreshResponseDto
import retrofit2.http.GET
import retrofit2.http.Header

interface PlannerApi {

    @GET("planner/refresh")
    suspend fun getRefresh(
        @Header("Authorization") authorization: String
    ): PlannerRefreshResponseDto
}
