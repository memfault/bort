plugins {
    `kotlin-dsl`
}

repositories {
    jcenter()
}

dependencies {
    // FIXME: should be using the dependencies dict, but it depends on buildSrc, maybe deps can be moved here later.
    implementation("org.json:json:20201115")
}
