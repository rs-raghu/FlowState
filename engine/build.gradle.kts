plugins { kotlin("jvm"); kotlin("plugin.serialization") }
kotlin { jvmToolchain(23) }
dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    testImplementation(kotlin("test"))
    testImplementation("junit:junit:4.13.2")
}
