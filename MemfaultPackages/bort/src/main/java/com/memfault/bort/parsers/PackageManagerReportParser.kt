package com.memfault.bort.parsers

import com.memfault.bort.FileUploadPayload
import java.io.InputStream

data class Package(
    val id: String,
    val userId: Int? = null,
    val codePath: String? = null,
    val versionCode: Long? = null,
    val versionName: String? = null,
) {
    fun toUploaderPackage(): FileUploadPayload.Package? {
        userId ?: return null
        codePath ?: return null
        versionCode ?: return null
        versionName ?: return null
        return FileUploadPayload.Package(
            id = id,
            versionCode = versionCode,
            versionName = versionName,
            userId = userId,
            codePath = codePath,
        )
    }
}

data class PackageManagerReport(val packages: List<Package> = emptyList())

class PackageManagerReportParser(val inputStream: InputStream) {
    private var section: String? = null
    private var packages: MutableList<Package> = mutableListOf()
    private var currentPackage: Package? = null

    fun parse(): PackageManagerReport {
        for (line in inputStream.bufferedReader().lineSequence()) {
            if (tryParseSectionHeader(line)) continue
            when (section) {
                SECTION_PACKAGES -> {
                    if (tryParsePackageHeader(line)) continue
                    if (currentPackage?.userId == null && tryParsePackageUserId(line)) continue
                    if (currentPackage?.codePath == null && tryParsePackageCodePath(line)) continue
                    if (currentPackage?.versionCode == null && tryParsePackageVersionCode(line)) continue
                    if (currentPackage?.versionName == null && tryParsePackageVersionName(line)) continue
                }
            }
        }
        finishCurrentPackage()
        return PackageManagerReport(packages)
    }

    private fun tryParseSectionHeader(line: String): Boolean =
        SECTION_HEADER_REGEX.withMatch(line) { (s) ->
            finishCurrentPackage()
            section = s
        }

    private fun tryParsePackageHeader(line: String): Boolean =
        PACKAGE_HEADER_REGEX.withMatch(line) { (id) ->
            finishCurrentPackage()
            currentPackage = Package(id = id)
        }

    private fun tryParsePackageUserId(line: String): Boolean =
        PACKAGE_USER_ID_REGEX.withMatch(line) { (userId) ->
            currentPackage = currentPackage?.copy(userId = userId.toInt())
        }

    private fun tryParsePackageCodePath(line: String): Boolean =
        PACKAGE_CODE_PATH_REGEX.withMatch(line) { (codePath) ->
            currentPackage = currentPackage?.copy(codePath = codePath)
        }

    private fun tryParsePackageVersionCode(line: String): Boolean =
        PACKAGE_VERSION_CODE_REGEX.withMatch(line) { (versionCode) ->
            currentPackage = currentPackage?.copy(versionCode = versionCode.toLong())
        }

    private fun tryParsePackageVersionName(line: String): Boolean =
        PACKAGE_VERSION_NAME_REGEX.withMatch(line) { (versionName) ->
            currentPackage = currentPackage?.copy(versionName = versionName)
        }

    private fun finishCurrentPackage() {
        currentPackage?.let {
            packages.add(it)
            currentPackage = null
        }
    }

    private fun Regex.withMatch(input: String, block: (MatchResult.Destructured) -> Unit): Boolean {
        val match = this.matchEntire(input) ?: return false
        block(match.destructured)
        return true
    }
}

private val SECTION_HEADER_REGEX = Regex("""^([A-Za-z0-9()][A-Za-z0-9 ()-]+):$""")
private val SECTION_PACKAGES = "Packages"
private val PACKAGE_HEADER_REGEX = Regex("""^ {2}Package \[([a-zA-Z0-9_.]+)] \([^)]+\):$""")
private val PACKAGE_USER_ID_REGEX = Regex("""^ {4}userId=([0-9]+)$""")
private val PACKAGE_CODE_PATH_REGEX = Regex("""^ {4}codePath=(.+)$""")
private val PACKAGE_VERSION_CODE_REGEX = Regex("""^ {4}versionCode=([0-9]+) minSdk=[0-9]+ targetSdk=[0-9]+$""")
private val PACKAGE_VERSION_NAME_REGEX = Regex("""^ {4}versionName=(.+)$""")
