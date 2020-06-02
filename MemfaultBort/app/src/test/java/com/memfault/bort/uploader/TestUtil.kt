package com.memfault.bort.uploader

import com.memfault.bort.ComponentsBuilder
import okhttp3.mockwebserver.MockWebServer
import retrofit2.Retrofit
import java.io.File

const val UPLOAD_URL = "https://test.com/abc"
const val AUTH_TOKEN = "auth_token"
const val UPLOAD_RESPONSE =
    """
        {
            "data": {
                "upload_url": "$UPLOAD_URL",
                "token": "$AUTH_TOKEN"
            }
        }
    """

const val SECRET_KEY = "secretKey"

internal fun createUploader(server: MockWebServer) =
    PreparedUploader(
        createService(server),
        apiKey = SECRET_KEY
    )

fun createService(server: MockWebServer): PreparedUploadService =
    Retrofit.Builder()
        .baseUrl(server.url("/"))
        .addConverterFactory(ComponentsBuilder().converterFactory)
        .build()
        .create(PreparedUploadService::class.java)

fun loadTestFileFromResources() = File(
    PreparedUploaderTest::class.java.getResource("/test.txt")!!.path
)
