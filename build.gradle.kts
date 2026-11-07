import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.0.0"
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.0"
    id("org.jetbrains.compose") version "1.6.11"
}

group = "com.silver.financas"
version = "1.0.0"

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation("org.json:json:20240303")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.1")
}

kotlin {
    jvmToolchain(17)
}

compose.desktop {
    application {
        mainClass = "com.silver.financas.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Exe, TargetFormat.Msi)
            packageName = "SilverFinancas"
            packageVersion = "1.0.0"
            description = "Controle financeiro pessoal"
            vendor = "Silver"
            modules("java.naming", "java.sql", "jdk.unsupported")
            windows {
                iconFile.set(project.file("icon.ico"))
                menuGroup = "SilverFinancas"
                shortcut = true
                dirChooser = true
                perUserInstall = true
                upgradeUuid = "6b1f0c2e-6a55-4f0e-9d0a-5b7c3f1e9a21"
            }
        }
    }
}
