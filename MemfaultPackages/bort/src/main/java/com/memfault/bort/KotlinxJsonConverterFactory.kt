package com.memfault.bort

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.ExperimentalSerializationApi
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Converter

@OptIn(ExperimentalSerializationApi::class)
fun kotlinxJsonConverterFactory(): Converter.Factory =
    BortJson.asConverterFactory("application/json".toMediaType())
