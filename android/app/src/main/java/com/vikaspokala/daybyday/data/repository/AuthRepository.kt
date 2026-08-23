package com.vikaspokala.daybyday.data.repository

import com.vikaspokala.daybyday.data.local.auth.SessionTokenStore
import com.vikaspokala.daybyday.data.remote.NetworkClient
import com.vikaspokala.daybyday.data.remote.api.AuthApi
import com.vikaspokala.daybyday.data.remote.dto.AuthUserDto
import com.vikaspokala.daybyday.data.remote.dto.LoginRequestDto

import com.vikaspokala.daybyday.data.remote.dto.ChangePasswordRequestDto
import com.vikaspokala.daybyday.data.remote.dto.UpdateProfileRequestDto

open class AuthRepository(
    private val sessionTokenStore: SessionTokenStore? = null,
    private val authApi: AuthApi = NetworkClient.authApi
) {

    open suspend fun getStoredToken(): String? {
        return sessionTokenStore?.readToken()
    }

    open suspend fun clearSession() {
        sessionTokenStore?.clearToken()
    }

    open suspend fun getCacheOwner(): String? {
        return sessionTokenStore?.readCacheOwner()
    }

    open suspend fun saveCacheOwner(userId: String) {
        sessionTokenStore?.saveCacheOwner(userId)
    }

    open suspend fun clearCacheOwner() {
        sessionTokenStore?.clearCacheOwner()
    }

    open suspend fun login(email: String, password: String): String {
        val response = authApi.login(
            LoginRequestDto(
                email = email.trim(),
                password = password
            )
        )
        sessionTokenStore?.saveToken(response.token)
        return response.token
    }

    open suspend fun verifySession(token: String): AuthUserDto {
        val response = authApi.getMe("Bearer $token")
        return response.user
    }

    open suspend fun updateProfile(request: UpdateProfileRequestDto): AuthUserDto {
        val token = getStoredToken()
            ?: throw IllegalStateException("Missing authentication session token")
        val response = authApi.updateProfile("Bearer $token", request)
        return response.user
    }

    open suspend fun changePassword(request: ChangePasswordRequestDto) {
        val token = getStoredToken()
            ?: throw IllegalStateException("Missing authentication session token")
        authApi.changePassword("Bearer $token", request)
    }

    open suspend fun logout(token: String? = null) {
        val bearerToken = token ?: getStoredToken() ?: return
        authApi.logout("Bearer $bearerToken")
    }
}
