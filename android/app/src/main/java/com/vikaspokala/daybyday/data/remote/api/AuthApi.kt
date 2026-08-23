package com.vikaspokala.daybyday.data.remote.api

import com.vikaspokala.daybyday.data.remote.dto.ChangePasswordRequestDto
import com.vikaspokala.daybyday.data.remote.dto.ChangePasswordResponseDto
import com.vikaspokala.daybyday.data.remote.dto.LoginRequestDto
import com.vikaspokala.daybyday.data.remote.dto.LoginResponseDto
import com.vikaspokala.daybyday.data.remote.dto.MeResponseDto
import com.vikaspokala.daybyday.data.remote.dto.UpdateProfileRequestDto
import com.vikaspokala.daybyday.data.remote.dto.UpdateProfileResponseDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST

interface AuthApi {

    @POST("auth/login")
    suspend fun login(
        @Body request: LoginRequestDto
    ): LoginResponseDto

    @GET("auth/me")
    suspend fun getMe(
        @Header("Authorization") authorization: String
    ): MeResponseDto

    @PATCH("auth/profile")
    suspend fun updateProfile(
        @Header("Authorization") authorization: String,
        @Body request: UpdateProfileRequestDto
    ): UpdateProfileResponseDto

    @POST("auth/change-password")
    suspend fun changePassword(
        @Header("Authorization") authorization: String,
        @Body request: ChangePasswordRequestDto
    ): ChangePasswordResponseDto

    @POST("auth/logout")
    suspend fun logout(
        @Header("Authorization") authorization: String
    ): Response<Unit>
}
