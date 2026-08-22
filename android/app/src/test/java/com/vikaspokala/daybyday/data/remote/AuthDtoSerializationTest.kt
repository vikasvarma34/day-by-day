package com.vikaspokala.daybyday.data.remote

import com.vikaspokala.daybyday.data.remote.dto.AuthUserDto
import com.vikaspokala.daybyday.data.remote.dto.LoginRequestDto
import com.vikaspokala.daybyday.data.remote.dto.LoginResponseDto
import com.vikaspokala.daybyday.data.remote.dto.MeResponseDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AuthDtoSerializationTest {

    private val json = NetworkClient.json

    @Test
    fun serializeLoginRequest_producesExactJsonShape() {
        val request = LoginRequestDto(
            email = "dev.user@example.com",
            password = "DevPassword12345!"
        )
        val serialized = json.encodeToString(LoginRequestDto.serializer(), request)

        val expected = """{"email":"dev.user@example.com","password":"DevPassword12345!"}"""
        assertEquals(expected, serialized)
    }

    @Test
    fun deserializeLoginResponse_withNonNullNickname_matchesContract() {
        val jsonString = """
            {
                "token": "sess_abc123xyz",
                "expiresAt": "2026-09-21T03:24:44.000Z",
                "user": {
                    "id": "11111111-2222-3333-4444-555555555555",
                    "email": "dev.user@example.com",
                    "firstName": "Vikas",
                    "lastName": "Varma",
                    "nickname": "Vicky"
                }
            }
        """.trimIndent()

        val response = json.decodeFromString(LoginResponseDto.serializer(), jsonString)

        assertEquals("sess_abc123xyz", response.token)
        assertEquals("2026-09-21T03:24:44.000Z", response.expiresAt)
        assertEquals(
            AuthUserDto(
                id = "11111111-2222-3333-4444-555555555555",
                email = "dev.user@example.com",
                firstName = "Vikas",
                lastName = "Varma",
                nickname = "Vicky"
            ),
            response.user
        )
    }

    @Test
    fun deserializeLoginResponse_withNullNickname_matchesContract() {
        val jsonString = """
            {
                "token": "sess_abc123xyz",
                "expiresAt": "2026-09-21T03:24:44.000Z",
                "user": {
                    "id": "11111111-2222-3333-4444-555555555555",
                    "email": "dev.user@example.com",
                    "firstName": "Vikas",
                    "lastName": "Varma",
                    "nickname": null
                }
            }
        """.trimIndent()

        val response = json.decodeFromString(LoginResponseDto.serializer(), jsonString)

        assertNull(response.user.nickname)
        assertEquals("Vikas", response.user.firstName)
    }

    @Test
    fun deserializeMeResponse_matchesContract() {
        val jsonString = """
            {
                "user": {
                    "id": "11111111-2222-3333-4444-555555555555",
                    "email": "dev.user@example.com",
                    "firstName": "Vikas",
                    "lastName": "Varma",
                    "nickname": null
                }
            }
        """.trimIndent()

        val response = json.decodeFromString(MeResponseDto.serializer(), jsonString)

        assertEquals("11111111-2222-3333-4444-555555555555", response.user.id)
        assertEquals("dev.user@example.com", response.user.email)
        assertEquals("Vikas", response.user.firstName)
        assertEquals("Varma", response.user.lastName)
        assertNull(response.user.nickname)
    }

    @Test
    fun deserializeMeResponse_ignoresUnknownFieldsGracefully() {
        val jsonString = """
            {
                "user": {
                    "id": "11111111-2222-3333-4444-555555555555",
                    "email": "dev.user@example.com",
                    "firstName": "Vikas",
                    "lastName": "Varma",
                    "nickname": null,
                    "unknownFutureField": 12345
                },
                "serverTime": "2026-08-22T08:00:00.000Z"
            }
        """.trimIndent()

        val response = json.decodeFromString(MeResponseDto.serializer(), jsonString)

        assertEquals("11111111-2222-3333-4444-555555555555", response.user.id)
        assertEquals("Vikas", response.user.firstName)
    }
}
