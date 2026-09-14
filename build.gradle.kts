// AGP 9 自带 Kotlin 支持（默认 KGP 2.2.10），这里显式抬到 2.4.10，
// 否则读不了 miuix 用 2.4.x 编出来的 metadata。
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.10")
    }
}

plugins {
    id("com.android.application") version "9.3.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.10" apply false
}
