import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

dependencies {
    implementation(projects.shared)

    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutines.swing)
    
    implementation(libs.decompose.extensions.compose)
    implementation(libs.components.resources)
    implementation(libs.ui)

    implementation(libs.ui.tooling.preview)
}

compose.desktop {
    application {
        mainClass = "org.bibletranslationtools.glossary.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "org.bibletranslationtools.glossary"
            packageVersion = libs.versions.glossary.name.get()

            macOS {
                iconFile.set(project.file("icons/spotlight.icns"))
            }
            windows {
                iconFile.set(project.file("icons/spotlight.ico"))
            }
            linux {
                iconFile.set(project.file("icons/spotlight.png"))
                // FileKit requires this to be set
                modules("jdk.security.auth")
            }
        }
    }
}
