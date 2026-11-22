plugins {
    id("java-library")
    alias(libs.plugins.jetbrains.kotlin.jvm)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}
dependencies {
    implementation(project(":core"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)   // deprecated-warning ок: плагин сам знает версию форка
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.kotlinx.coroutines.swing)
}
compose.desktop {
    application {
        mainClass = "com.c0dex.desktop.MainKt"
        nativeDistributions {
            targetFormats(
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Exe
            )
            packageName = "PassVault"
            packageVersion = "5.5.5"
            description = "PassVault — офлайн-менеджер паролей (формат PMV3)"
            vendor = "c0dex"
            copyright = "© 2026 c0dex"
            windows {
                // iconFile.set(file("icons/passvault.ico"))  // раскомментируй, когда положишь .ico
                upgradeUuid = "b7f3a2c1-4d5e-4f6a-9b8c-1d2e3f4a5b6c"  // НЕ менять между версиями
                menuGroup = "PassVault"
                shortcut = true
                perUserInstall = true   // без админ-прав, в папку пользователя
            }
        }
    }
}