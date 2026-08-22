package com.vikaspokala.daybyday.data.remote

import com.vikaspokala.daybyday.BuildConfig
import com.vikaspokala.daybyday.data.remote.api.AuthApi
import com.vikaspokala.daybyday.data.remote.api.PlannerApi
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

object NetworkClient {

    val json: Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
    }

    val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .build()
    }

    fun createRetrofit(
        baseUrl: String = BuildConfig.BASE_URL,
        client: OkHttpClient = okHttpClient
    ): Retrofit {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
    }

    fun createAuthApi(
        baseUrl: String = BuildConfig.BASE_URL,
        client: OkHttpClient = okHttpClient
    ): AuthApi {
        return createRetrofit(baseUrl, client).create(AuthApi::class.java)
    }

    val authApi: AuthApi by lazy {
        createAuthApi()
    }

    fun createPlannerApi(
        baseUrl: String = BuildConfig.BASE_URL,
        client: OkHttpClient = okHttpClient
    ): PlannerApi {
        return createRetrofit(baseUrl, client).create(PlannerApi::class.java)
    }

    val plannerApi: PlannerApi by lazy {
        createPlannerApi()
    }
}
