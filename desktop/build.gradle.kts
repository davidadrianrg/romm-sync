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
