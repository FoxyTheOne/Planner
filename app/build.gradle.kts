plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// Языки приложения — ЕДИНСТВЕННОЕ место, где они перечислены. Отсюда их берут:
// - localeFilters ниже (какие переводы попадут в APK);
// - BuildConfig.LOCALES (код, которому нужны тексты на всех языках, например названия «Без списка»).
// Новый язык: добавить сюда + создать папку values-xx со strings.xml.
val appLocales = listOf("en", "ru", "be")

// Тот же список, записанный как массив Java для BuildConfig: {"en", "ru", "be"}.
// Собираем здесь, а не внутри defaultConfig { }: внутри этого блока Android Studio ждёт только
// настройки defaultConfig и подчёркивает обычные функции списка (joinToString) как «подозрительные»
val appLocalesJavaArray = appLocales.joinToString(prefix = "{", postfix = "}") { "\"$it\"" }

android {
    namespace = "app.planner"
    compileSdk = 36

    defaultConfig {
        // applicationId — «паспорт» приложения для Android и магазинов. После публикации его менять НЕЛЬЗЯ:
        // с другим id это будет другое приложение, и обновления до людей не дойдут.
        // Построен от сайта foxytheone.github.io (наоборот) — так он гарантированно ничей больше.
        // «planner», а не название: название (Крок или другое) можно менять сколько угодно, id — нет.
        //
        // Не путать с namespace выше: namespace — пакет для кода (класс R, BuildConfig),
        // пользователь его не видит, и он может отличаться от applicationId.
        applicationId = "io.github.foxytheone.planner"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Превращается в Kotlin: BuildConfig.LOCALES = arrayOf("en", "ru", "be")
        buildConfigField(
            "String[]",
            "LOCALES",
            appLocalesJavaArray,
        )
    }

    // Google Play и RuStore отличаются только магазинными мелочами (кнопка «Поддержать»)
    flavorDimensions += "store"
    productFlavors {
        create("play") {
            dimension = "store"
            buildConfigField("boolean", "SHOW_DONATE", "false")
        }
        create("rustore") {
            dimension = "store"
            buildConfigField("boolean", "SHOW_DONATE", "true")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    androidResources {
        // Языки первого релиза: английский (по умолчанию), русский, белорусский (официальная орфография)
        localeFilters += appLocales
        // Android 13+: язык можно выбрать отдельно для приложения (Настройки → Приложения → Язык).
        // Список языков для этого меню AGP собирает сам из папок values-*
        generateLocaleConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:data"))
    implementation(project(":core:designsystem"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.reorderable)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.viewmodel.compose)

    testImplementation(libs.junit)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)
}
