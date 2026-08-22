package com.vikaspokala.daybyday.data.repository

import com.vikaspokala.daybyday.data.local.auth.SessionTokenStore
import com.vikaspokala.daybyday.data.local.planner.PlannerDatabase
import com.vikaspokala.daybyday.data.local.planner.entity.toEntity
import com.vikaspokala.daybyday.data.remote.NetworkClient
import com.vikaspokala.daybyday.data.remote.api.PlannerApi

open class PlannerRepository(
    private val plannerDatabase: PlannerDatabase,
    private val tokenProvider: suspend () -> String?,
    private val plannerApi: PlannerApi = NetworkClient.plannerApi
) {

    constructor(
        plannerDatabase: PlannerDatabase,
        sessionTokenStore: SessionTokenStore,
        plannerApi: PlannerApi = NetworkClient.plannerApi
    ) : this(
        plannerDatabase = plannerDatabase,
        tokenProvider = { sessionTokenStore.readToken() },
        plannerApi = plannerApi
    )

    open suspend fun refresh(): Result<Unit> {
        val token = tokenProvider()
            ?: return Result.failure(IllegalStateException("Missing authentication session token"))

        return try {
            val response = plannerApi.getRefresh("Bearer $token")
            val taskEntities = response.tasks.map { it.toEntity() }
            val scheduleEntities = response.schedules.map { it.toEntity() }
            val completionEntities = response.completions.map { it.toEntity() }

            plannerDatabase.replaceSnapshot(
                tasks = taskEntities,
                schedules = scheduleEntities,
                completions = completionEntities
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
