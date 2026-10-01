import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kmp)
    alias(libs.plugins.kmpLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.sqlDelight)
}

kotlin {
    android {
        namespace = "org.bibletranslationtools.glossary.shared"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }

        androidResources {
            enable = true
        }

        withHostTest {
            isIncludeAndroidResources = true
        }
    }

    jvm()

    sourceSets {
        commonMain {
            dependencies {
                implementation(libs.runtime)
                implementation(libs.foundation)
                implementation(libs.material3)
                implementation(libs.material.icons.extended)
                implementation(libs.ui)
                implementation(libs.components.resources)
                implementation(libs.ui.tooling.preview)
                implementation(libs.androidx.lifecycle.runtimeCompose)
                implementation(libs.kotlinx.datetime)
                implementation(libs.kotlinx.io)
                implementation(libs.kotlinx.serialization.json)

                implementation(libs.koin.compose)

                api(libs.decompose.decompose)
                implementation(libs.decompose.extensions.compose)

                implementation(libs.sqldelight.runtime)
                implementation(libs.sqldelight.coroutines)
                implementation(libs.compose.remember.setting)

                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.client.serialization)
                implementation(libs.ktor.serialization.kotlinx.json)
                implementation(libs.ktor.client.logging)

                implementation(libs.filekit.dialogs.core)
                implementation(libs.filekit.dialogs.compose)

                implementation(libs.kotlin.multiplatform.diff)

                implementation(libs.usfmtools.jvm)
                implementation(libs.kotlin.resource.container)
                implementation(libs.jackson.databind)
                implementation(libs.jackson.kotlin)
                implementation(libs.jackson.yaml)
            }
        }
        
        androidMain {
            dependencies {
                implementation(libs.ui.tooling.preview)
                implementation(libs.androidx.activity.compose)

                implementation(libs.koin.android)
                implementation(libs.koin.androidx.compose)

                implementation(libs.sqldelight.android)
                implementation(libs.ktor.client.android)
                implementation(libs.requery.sqlite)
            }
        }

        jvmMain {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(libs.kotlinx.coroutines.swing)

                implementation(libs.sqldelight.jvm)
                implementation(libs.ktor.client.cio)
            }
        }
        
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.mockk)
        }
    }
}

sqldelight {
    databases {
        create("GlossaryDatabase") {
            packageName.set("org.bibletranslationtools.glossary")
            dialect("app.cash.sqldelight:sqlite-3-38-dialect:2.2.1")
        }
    }
}

dependencies {
    androidRuntimeClasspath(libs.ui.tooling)
}

// The glossary backup license lives with the API (api/src/assets/LICENSE.md, bundled
// by wrangler). Copy it into generated compose resources at build time
val glossaryLicenseResources = layout.buildDirectory.dir("generated/glossaryLicense/composeResources")
val copyGlossaryLicense = tasks.register<Copy>("copyGlossaryLicense") {
    description = "Copy license asset"
    from(rootProject.layout.projectDirectory.file("api/src/assets/LICENSE.md")) {
        into("files/glossary")
    }
    into(glossaryLicenseResources)
}

compose.resources {
    publicResClass = true

    // Platform source sets, as commonMain keeps src/commonMain/composeResources
    val licenseDir = copyGlossaryLicense.map { glossaryLicenseResources.get() }
    customDirectory(sourceSetName = "androidMain", directoryProvider = licenseDir)
    customDirectory(sourceSetName = "jvmMain", directoryProvider = licenseDir)
}
