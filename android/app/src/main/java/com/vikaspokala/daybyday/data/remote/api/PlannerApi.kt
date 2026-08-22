package com.vikaspokala.daybyday.data.remote.api

import com.vikaspokala.daybyday.data.remote.dto.CompleteTaskRequestDto
import com.vikaspokala.daybyday.data.remote.dto.CompleteTaskResponseDto
import com.vikaspokala.daybyday.data.remote.dto.CreateTaskRequestDto
import com.vikaspokala.daybyday.data.remote.dto.CreateTaskResponseDto
import com.vikaspokala.daybyday.data.remote.dto.DeleteTaskResponseDto
import com.vikaspokala.daybyday.data.remote.dto.PlannerRefreshResponseDto
import com.vikaspokala.daybyday.data.remote.dto.TaskActionResponseDto
import com.vikaspokala.daybyday.data.remote.dto.UndoTaskRequestDto
import com.vikaspokala.daybyday.data.remote.dto.UpdateTaskContentRequestDto
import com.vikaspokala.daybyday.data.remote.dto.UpdateTaskResponseDto
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path

interface PlannerApi {

    @PATCH("tasks/{taskId}")
    suspend fun updateTaskContent(
        @Header("Authorization") authorization: String,
        @Path("taskId") taskId: String,
        @Body request: UpdateTaskContentRequestDto
    ): UpdateTaskResponseDto

    @DELETE("tasks/{taskId}")
    suspend fun deleteTask(
        @Header("Authorization") authorization: String,
        @Path("taskId") taskId: String
    ): DeleteTaskResponseDto

    @POST("tasks")
    suspend fun createTask(
        @Header("Authorization") authorization: String,
        @Body request: CreateTaskRequestDto
    ): CreateTaskResponseDto

    @GET("planner/refresh")
    suspend fun getRefresh(
        @Header("Authorization") authorization: String
    ): PlannerRefreshResponseDto

    @POST("tasks/{taskId}/complete")
    suspend fun completeTask(
        @Header("Authorization") authorization: String,
        @Path("taskId") taskId: String,
        @Body request: CompleteTaskRequestDto
    ): CompleteTaskResponseDto

    @POST("tasks/{taskId}/undo")
    suspend fun undoTask(
        @Header("Authorization") authorization: String,
        @Path("taskId") taskId: String,
        @Body request: UndoTaskRequestDto
    ): TaskActionResponseDto
}
