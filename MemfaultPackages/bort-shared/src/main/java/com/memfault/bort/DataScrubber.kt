package com.memfault.bort

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.kocakosm.jblake2.Blake2b
import java.lang.StringBuilder

fun interface LineScrubbingCleaners {
    operator fun invoke(): List<LineScrubbingCleaner>
}

interface LineScrubbingCleaner {
    fun clean(line: String): String
}

class DataScrubber(
    private val cleaners: LineScrubbingCleaners,
    private val hash: (line: String) -> String = ::blake2b,
) {
    operator fun invoke(line: String): String = cleaners().fold(line) { data, matcher ->
        matcher.clean(data)
    }

    fun scrubEntirely(line: String, dataKind: String = "SCRUBBED"): String =
        "***$dataKind-${hash(line)}***"
}

fun Sequence<String>.scrubbedWith(scrubber: DataScrubber): Sequence<String> = map { scrubber(it) }

private const val BLAKE2B_DIGEST_SIZE = 4
fun blake2b(plainText: String): String {
    val digest = Blake2b(BLAKE2B_DIGEST_SIZE).apply {
        val input = plainText.toByteArray()
        update(input, 0, input.size)
    }.digest()
    return digest.joinToString("") { "%02x".format(it) }
}

@Serializable
sealed class DataScrubbingRule

@Serializable
@SerialName("android_app_id")
data class AndroidAppIdScrubbingRule(
    @SerialName("app_id_pattern")
    val appIdPattern: String,
) : DataScrubbingRule()

private const val EMAIL_REGEX_GROUP = 1

@Serializable
@SerialName("text_email")
object EmailScrubbingRule :
    DataScrubbingRule(),
    LineScrubbingCleaner by RegexLineCleaner(
        "([a-z0-9!#$%&'*+=?^_`{|}~-]+" + // start with this character
            "(?:\\.[a-z0-9!#$%&'*+=?^_`{|}~-]+)*" + // valid next characters
            "@" +
            "(?:" +
            "[a-z0-9]" + // subdomain starts like this
            "(?:[a-z0-9-]*[a-z0-9])?" + // might have this
            "\\." + // .
            ")*" + // repeat as necessary
            "(?:" +
            "[a-z0-9]" + // 2nd level domain starts like this
            "[a-z0-9-]*[a-z0-9]" + // must have this (at least 2 chars)
            "\\." + // .
            ")" +
            "[a-z]{2,})", // TLD: 2+ letters only for now
        listOf("EMAIL" to EMAIL_REGEX_GROUP),
    )

private const val USERNAME_REGEX_GROUP = 2
private const val PASSWORD_REGEX_GROUP = 4

@Serializable
@SerialName("text_credential")
object CredentialScrubbingRule :
    DataScrubbingRule(),
    LineScrubbingCleaner by RegexLineCleaner(
        "(username|login|u:)\\s*:?\\s*" + // username might have : and whitespace
            "([\\w\\-\\.@+]*)" + // capture the username for replacement
            "\\s+" + // some whitespace between
            "(password|pw|p:)\\s*:?\\s*" + // password might have : and whitespace
            "(.*)" + // password can be anything until EOL
            ".*",
        listOf(
            "USERNAME" to USERNAME_REGEX_GROUP,
            "PASSWORD" to PASSWORD_REGEX_GROUP,
        ),
    )

/**
 * Regex-based cleaner, takes a regex and a list of group names that will be cleaned
 * by replacing their content with {{NAME}}
 */
class RegexLineCleaner(
    private val regex: String,
    private val groups: List<Pair<String, Int>>,
) : LineScrubbingCleaner {
    private val matcher: Regex by lazy { regex.toRegex() }
    override fun clean(line: String): String = matcher.find(line)?.let {
        val transformed = StringBuilder(line)
        groups.fold(0) { adjustedPos, (groupName, groupIdx) ->
            val replacement = "{{$groupName}}"
            it.groups[groupIdx]?.let { match ->
                transformed.replace(
                    match.range.first + adjustedPos,
                    match.range.last + adjustedPos + 1,
                    replacement,
                )
                adjustedPos - (match.range.last - match.range.first + 1) + replacement.length
            } ?: adjustedPos
        }
        transformed.toString()
    } ?: line
}

@Serializable
object UnknownScrubbingRule : DataScrubbingRule()
