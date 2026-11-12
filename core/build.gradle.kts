plugins { kotlin("jvm") }
kotlin { jvmToolchain(17) }
dependencies {
    implementation("com.google.code.gson:gson:2.10.1")
    testImplementation("junit:junit:4.13.2")
}
tasks.test { systemProperty("catalogPath", rootProject.file("app/src/main/assets/scenarios.json").absolutePath) }
