plugins {
    kotlin("jvm") version "1.9.21"
}

group = "com.urosjarc"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.urosjarc:db-messiah:0.0.2")
    implementation("com.urosjarc:db-messiah-extra:0.0.2")
    implementation("org.apache.logging.log4j:log4j-slf4j2-impl:2.20.0")
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    runtimeOnly("org.postgresql:postgresql:42.7.1")}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(19)
}
