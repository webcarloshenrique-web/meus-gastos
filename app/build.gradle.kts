plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// O GitHub informa o número da compilação: vira o número da versão do app.
val runNumber = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
// Versão acompanha o ano: 2026 = 1, 2027 = 2... Ex.: 1.12 (o GitHub calcula em VERSAO)
val versaoNome = System.getenv("VERSAO") ?: "1.$runNumber"
val repo = System.getenv("GITHUB_REPOSITORY") ?: "webcarloshenrique-web/meus-gastos"

android {
    namespace = "br.com.meusgastos"
    compileSdk = 35

    defaultConfig {
        applicationId = "br.com.meusgastos"
        minSdk = 26
        targetSdk = 35
        versionCode = runNumber
        versionName = versaoNome
        buildConfigField("String", "REPO", "\"$repo\"")
    }

    // Chave fixa: permite instalar as atualizações por cima, sem perder dados.
    signingConfigs {
        create("release") {
            storeFile = file("release.keystore")
            storePassword = "meusgastos"
            keyAlias = "meusgastos"
            keyPassword = "meusgastos"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
