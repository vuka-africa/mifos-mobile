import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    js(IR) {
        moduleName = "cmp-web"
        browser {
            commonWebpackConfig {
                outputFileName = "cmp-web.js"
            }
        }
        binaries.executable()
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        moduleName = "cmp-wasm"
        browser {
            commonWebpackConfig {
                outputFileName = "cmp-wasm.js"
            }
        }
        binaries.executable()
    }

    applyDefaultHierarchyTemplate()

    sourceSets {
        val jsWasmMain by creating {
            dependsOn(commonMain.get())
            dependencies {
                implementation(projects.cmpShared)
                implementation(projects.core.common)
                implementation(projects.core.data)
                implementation(projects.core.model)
                implementation(projects.core.datastore)

                implementation(compose.runtime)
                implementation(compose.ui)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.components.resources)

                implementation(libs.multiplatform.settings)
                implementation(libs.multiplatform.settings.serialization)
                implementation(libs.multiplatform.settings.coroutines)

                implementation(libs.koin.core)
            }
        }

        jsMain.get().dependsOn(jsWasmMain)
        wasmJsMain.get().dependsOn(jsWasmMain)

        // JS-specific dependencies (npm packages)
        jsMain.get().dependencies {
            // OIDC client library for Zitadel authentication
            // Handles popup-based login, token refresh, and PKCE
            implementation(npm("oidc-client-ts", "3.0.1"))
        }
    }
}

tasks.register("jsBrowserRun") {
    dependsOn("jsBrowserDevelopmentRun")
}

compose.resources {
    publicResClass = true
    generateResClass = always
}