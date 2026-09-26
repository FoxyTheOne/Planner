plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Чистый Kotlin без Android: модели и бизнес-правила, быстро тестируются на JVM
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    api(libs.kotlinx.coroutines.core)
    api(libs.javax.inject)
    testImplementation(libs.junit)
}
