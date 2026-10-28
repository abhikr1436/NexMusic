pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven { setUrl("https://jitpack.io") }
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven { url = uri("https://jitpack.io") }
        maven("https://jogamp.org/deployment/maven")
        maven(url = "https://raw.githubusercontent.com/bravepipeproject/maven-repo/master/repository")
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "NexMusic"

// The app.
include(":nexApp")

// Streaming, library and playback core it is built on (forked from SimpMusic, GPL-3.0).
val coreDir = File(rootDir, "core")
val serviceDir = File(coreDir, "service")
val mediaDir = File(coreDir, "media")
mapOf(
    ":common" to File(coreDir, "common"),
    ":data" to File(coreDir, "data"),
    ":domain" to File(coreDir, "domain"),
    ":ktorExt" to File(serviceDir, "ktorExt"),
    ":aiService" to File(serviceDir, "aiService"),
    ":autoEqService" to File(serviceDir, "autoEqService"),
    ":lyricsService" to File(serviceDir, "lyricsService"),
    ":kotlinYtmusicScraper" to File(serviceDir, "kotlinYtmusicScraper"),
    ":spotify" to File(serviceDir, "spotify"),
    ":kizzy" to File(serviceDir, "kizzy"),
    ":listenTogether" to File(serviceDir, "listenTogether"),
    ":loginSync" to File(serviceDir, "loginSync"),
    ":media3" to File(mediaDir, "media3"),
    // Only referenced by :data's desktop target; never built into the Android app.
    ":media-jvm" to File(mediaDir, "media-jvm"),
    // No-op stand-ins for Google Cast and Last.fm, keeping the app free of GMS and API secrets.
    ":cast-empty" to File(rootDir, "cast-empty"),
    ":lastfm-empty" to File(rootDir, "lastfm-empty"),
).forEach { (path, dir) ->
    include(path)
    project(path).projectDir = dir
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
