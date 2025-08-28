// Root Gradle (Kotlin DSL) — moderni setup bez buildscript/allprojects blokova
plugins {
    id("com.android.application") version "8.7.3" apply false
    kotlin("android") version "1.9.24" apply false
}

// Clean task kompatibilan s Gradle 8
tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}