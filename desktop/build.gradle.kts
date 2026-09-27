import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)      // compiler de Compose
    alias(libs.plugins.compose.desktop)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":core"))

    implementation(compose.desktop.currentOs)
    implementation("org.jetbrains.compose.material3:material3:1.7.3")
    implementation(libs.kotlinx.coroutines.swing)

    // El AppImage aarch64 se empaqueta en un runner x86_64: currentOs solo
    // incluye los nativos del host (libskiko-linux-x64.so), así que el jar
    // llegaba a ARM64 sin su libskiko-linux-arm64.so y fallaba con
    // "Cannot find libskiko-linux-arm64.so.sha256" al crear la ventana.
    // Se añade explícitamente el runtime skiko arm64 (misma versión que
    // resuelve Compose Desktop 1.7.3).
    implementation("org.jetbrains.skiko:skiko-awt-runtime-linux-arm64:0.8.18")

    testImplementation("junit:junit:4.13.2")
}

compose.desktop {
    application {
        mainClass = "es.davidrg.rommsync.desktop.MainKt"

        // ProGuard rompe con las reglas por defecto sobre el uber-jar; el jar
        // va embebido en el AppImage así que el minificado no aporta nada.
        buildTypes.release.proguard {
            isEnabled = false
        }

        nativeDistributions {
            packageName = "romm-sync"
            description = "Sincroniza ROMs y saves con tu servidor RomM"
            vendor = "davidrg"
            copyright = "© 2026 David RG"

            targetFormats(TargetFormat.AppImage, TargetFormat.Deb, TargetFormat.Rpm)

            modules("java.naming", "java.sql", "jdk.crypto.ec")

            // x86_64 y aarch64: ambos en la misma distribución
            linux {
                iconFile = rootProject.file("desktop/packaging/romm-sync.png")
            }
        }
    }
}
