package com.vikaspokala.daybyday.data.repository

import com.vikaspokala.daybyday.data.local.auth.SessionTokenStore
import com.vikaspokala.daybyday.data.remote.NetworkClient
import com.vikaspokala.daybyday.data.remote.api.AuthApi
import com.vikaspokala.daybyday.data.remote.dto.AuthUserDto
import com.vikaspokala.daybyday.data.remote.dto.LoginRequestDto

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
}
