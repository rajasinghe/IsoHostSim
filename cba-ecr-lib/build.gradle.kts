// CBA ECR class library (TCPJAVA_CBA_ClassLibrary), vendored as source from the
// company's Java ECR library. See README.md for origin and PATCHES.md for the
// Linux-only changes. Protocol behaviour is unchanged.
plugins {
    `java-library`
}

dependencies {
    api(libs.serial.comms)                     // com.fazecast:jSerialComm 2.9.1, shared with the app (PaxPort exposes SerialPort)
    api("org.json:json:20220320")              // facade returns org.json.JSONObject

    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(17)
    options.encoding = "UTF-8"
}

tasks.test {
    useJUnitPlatform()
}
