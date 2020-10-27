package com.memfault.bort.shared

interface SdkVersionInfo {
    val appVersionName: String
    val appVersionCode: Int
    val upstreamVersionName: String
    val upstreamVersionCode: Int
    val upstreamGitSha: String
    val currentGitSha: String
}

object BuildConfigSdkVersionInfo : SdkVersionInfo {
    override val appVersionName = BuildConfig.APP_VERSION_NAME
    override val appVersionCode = BuildConfig.APP_VERSION_CODE
    override val upstreamVersionName = BuildConfig.UPSTREAM_VERSION_NAME
    override val upstreamVersionCode = BuildConfig.UPSTREAM_VERSION_CODE
    override val upstreamGitSha = BuildConfig.UPSTREAM_GIT_SHA
    override val currentGitSha = BuildConfig.CURRENT_GIT_SHA
}
