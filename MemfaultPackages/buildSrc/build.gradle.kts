plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
}

dependencies {
    // FIXME: should be using the dependencies dict, but it depends on buildSrc, maybe deps can be moved here later.
    implementation("com.fasterxml.jackson.core:jackson-databind:2.12.1")
    implementation("com.flipkart.zjsonpatch:zjsonpatch:0.4.11")
}
