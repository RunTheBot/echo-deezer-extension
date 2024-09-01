import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    id("java-library")
    id("org.jetbrains.kotlin.jvm")
    id("com.gradleup.shadow") version "8.3.0"
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    val libVersion: String by project
    compileOnly("com.github.brahmkshatriya:echo:$libVersion")

    implementation("com.squareup.okhttp3:okhttp:5.0.0-alpha.14")
    implementation("com.squareup.okhttp3:okhttp-coroutines:5.0.0-alpha.14")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
}


tasks {
    val shadowJar by getting(ShadowJar::class) {
        archiveBaseName.set("deezer")
    }
}

