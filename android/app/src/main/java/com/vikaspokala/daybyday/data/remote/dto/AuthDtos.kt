package com.vikaspokala.daybyday.data.remote.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer

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

@Serializable(with = UpdateProfileRequestDtoSerializer::class)
data class UpdateProfileRequestDto(
    val firstName: String? = null,
    val lastName: String? = null,
    val nickname: String? = null,
    val includeNickname: Boolean = false
) {
    companion object {
        fun forFirstName(firstName: String) = UpdateProfileRequestDto(firstName = firstName)
        fun forLastName(lastName: String) = UpdateProfileRequestDto(lastName = lastName)
        fun forNickname(nickname: String?) = UpdateProfileRequestDto(nickname = nickname, includeNickname = true)
    }
}

@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
object UpdateProfileRequestDtoSerializer : kotlinx.serialization.KSerializer<UpdateProfileRequestDto> {
    override val descriptor: kotlinx.serialization.descriptors.SerialDescriptor =
        kotlinx.serialization.descriptors.buildClassSerialDescriptor("UpdateProfileRequestDto") {
            element("firstName", String.serializer().descriptor, isOptional = true)
            element("lastName", String.serializer().descriptor, isOptional = true)
            element("nickname", String.serializer().descriptor, isOptional = true)
        }

    override fun serialize(encoder: kotlinx.serialization.encoding.Encoder, value: UpdateProfileRequestDto) {
        val jsonEncoder = encoder as? kotlinx.serialization.json.JsonEncoder
        if (jsonEncoder != null) {
            val jsonObject = kotlinx.serialization.json.buildJsonObject {
                value.firstName?.let { put("firstName", kotlinx.serialization.json.JsonPrimitive(it)) }
                value.lastName?.let { put("lastName", kotlinx.serialization.json.JsonPrimitive(it)) }
                if (value.includeNickname) {
                    if (value.nickname != null) {
                        put("nickname", kotlinx.serialization.json.JsonPrimitive(value.nickname))
                    } else {
                        put("nickname", kotlinx.serialization.json.JsonNull)
                    }
                }
            }
            jsonEncoder.encodeJsonElement(jsonObject)
        } else {
            val composite = encoder.beginStructure(descriptor)
            value.firstName?.let { composite.encodeStringElement(descriptor, 0, it) }
            value.lastName?.let { composite.encodeStringElement(descriptor, 1, it) }
            if (value.includeNickname) {
                if (value.nickname != null) {
                    composite.encodeStringElement(descriptor, 2, value.nickname)
                } else {
                    composite.encodeNullableSerializableElement(descriptor, 2, String.serializer(), null)
                }
            }
            composite.endStructure(descriptor)
        }
    }

    override fun deserialize(decoder: kotlinx.serialization.encoding.Decoder): UpdateProfileRequestDto {
        val jsonDecoder = decoder as? kotlinx.serialization.json.JsonDecoder
        if (jsonDecoder != null) {
            val element = jsonDecoder.decodeJsonElement()
            val obj = if (element is kotlinx.serialization.json.JsonObject) element else null
            val firstName = obj?.get("firstName")?.let {
                if (it is kotlinx.serialization.json.JsonPrimitive && !it.isString) null else it.toString().removeSurrounding("\"")
            }
            val lastName = obj?.get("lastName")?.let {
                if (it is kotlinx.serialization.json.JsonPrimitive && !it.isString) null else it.toString().removeSurrounding("\"")
            }
            val hasNickname = obj != null && "nickname" in obj
            val nickname = if (obj != null && "nickname" in obj) {
                val nickElem = obj["nickname"]
                if (nickElem is kotlinx.serialization.json.JsonNull) null
                else if (nickElem is kotlinx.serialization.json.JsonPrimitive) nickElem.content
                else null
            } else null
            return UpdateProfileRequestDto(
                firstName = firstName,
                lastName = lastName,
                nickname = nickname,
                includeNickname = hasNickname
            )
        } else {
            return UpdateProfileRequestDto()
        }
    }
}

@Serializable
data class UpdateProfileResponseDto(
    val user: AuthUserDto
)

@Serializable
data class ChangePasswordRequestDto(
    val currentPassword: String,
    val newPassword: String
)

@Serializable
data class ChangePasswordResponseDto(
    val success: Boolean = true
)
