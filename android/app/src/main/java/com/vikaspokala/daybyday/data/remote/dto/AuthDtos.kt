package com.vikaspokala.daybyday.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class AuthUserDto(
    val id: String,
    val email: String,
    val firstName: String,
    val lastName: String,
    val nickname: String? = null
)

@Serializable
data class LoginRequestDto(
    val email: String,
    val password: String
)

@Serializable
data class LoginResponseDto(
    val token: String,
    val expiresAt: String,
    val user: AuthUserDto
)

@Serializable
data class MeResponseDto(
    val user: AuthUserDto
)
