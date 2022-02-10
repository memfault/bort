package com.memfault.bort.http

import com.memfault.bort.settings.ProjectKey
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import retrofit2.Invocation

internal const val PROJECT_KEY_HEADER = "Memfault-Project-Key"

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class ProjectKeyAuthenticated

/**
 * OkHttp interceptor that adds the "Memfault-Project-Key" authentication header to all
 * requests coming from calls of Retrofit methods marked with the @ProjectKeyAuth annotation.
 */
@ContributesMultibinding(SingletonComponent::class)
class ProjectKeyInjectingInterceptor @Inject constructor(val getProjectKey: ProjectKey) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        return chain.proceed(transformRequest(chain.request()))
    }

    fun transformRequest(originalRequest: Request): Request {
        val annotation = originalRequest.tag(Invocation::class.java)?.method()?.getAnnotation(
            ProjectKeyAuthenticated::class.java
        )
        return when (annotation) {
            null -> originalRequest
            else -> originalRequest.newBuilder().header(
                PROJECT_KEY_HEADER,
                getProjectKey()
            ).build()
        }
    }
}
