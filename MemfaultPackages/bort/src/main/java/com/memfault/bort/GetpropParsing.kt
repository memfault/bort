package com.memfault.bort

// [persist.sys.timezone]: [GMT]
// See https://cs.android.com/android/platform/superproject/+/master:system/core/toolbox/getprop.cpp;l=69;drc=76858a06d094dde07f1588a5e74d12f4c5c54de8
private val SYSTEM_PROPERTY_REGEX = Regex("""^\[(\S+)]: \[(.*)]$""")

fun parseGetpropOutput(o: String): Map<String, String> =
    o.split("\n")
        .mapNotNull { line -> SYSTEM_PROPERTY_REGEX.matchEntire(line)?.destructured }
        .associateBy({ (k, _) -> k }, {(_, v) -> v})
