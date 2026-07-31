plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.vanniktechPublish)
    alias(libs.plugins.dokka)
}

kotlin {
    applyDefaultHierarchyTemplate()

    androidLibrary {
        namespace = "xyz.ksharma.darpan.roborazzi"
        compileSdk = libs.versions.android.compile.sdk.get().toInt()
        minSdk = libs.versions.android.min.sdk.get().toInt()
    }

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(libs.versions.java.get()))
        }
    }

    sourceSets {
        androidMain.dependencies {
            api(projects.darpanAnnotations)

            implementation(libs.compose.runtime)
            implementation(libs.compose.ui)
            implementation(libs.compose.foundation)

            // Every one of these is `api`, not `implementation`: consumers write test classes
            // that extend BaseSnapshotTest, so the JUnit runner, the Robolectric annotations and
            // the Roborazzi device qualifiers all appear in their own source. Hiding them behind
            // `implementation` would mean every consumer redeclaring the same five dependencies.
            api(libs.roborazzi)
            api(libs.roborazzi.compose)
            api(libs.roborazzi.junit)
            api(libs.preview.scanner.android)
            api(libs.test.robolectric)
            api(libs.test.junit)
            api(libs.test.composeUiTestJunit4)
            // Without ui-test-manifest, ComponentActivity fails to resolve under Robolectric.
            api(libs.test.composeUiTestManifest)
            // The androidx (not org.jetbrains.compose) tooling artifact - the scanner reads
            // androidx.compose.ui.tooling.preview.Preview out of the bytecode.
            api(libs.androidx.ui.tooling)
        }
    }
}
