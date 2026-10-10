plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.resurgeai.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.resurgeai.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
}

dependencies {
    implementation(project(":core:designsystem"))
    implementation(project(":core:data"))
    implementation(project(":core:healthconnect"))
    implementation(project(":feature:auth"))
    implementation(project(":feature:dashboard"))
    implementation(project(":feature:profile"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.hilt.viewmodel.compose)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    debugImplementation(libs.compose.ui.tooling)
}

/**
 * Fails if any Material / Material3 artifact ends up in the release app. The design system is built
 * on Compose Unstyled; debug-only tooling (ui-tooling previews) is allowed to pull Material in.
 * Runs as part of `check`.
 */
val checkNoMaterial by tasks.registering {
    group = "verification"
    description = "Fails if Material libraries are on the release runtime classpath."
    val root = configurations.named("releaseRuntimeClasspath").flatMap { it.incoming.resolutionResult.rootComponent }
    doLast {
        val banned = listOf("androidx.compose.material", "org.jetbrains.compose.material", "com.google.android.material")
        val seen = mutableSetOf<String>()
        val offenders = mutableListOf<String>()
        fun walk(component: org.gradle.api.artifacts.result.ResolvedComponentResult) {
            component.dependencies
                .filterIsInstance<org.gradle.api.artifacts.result.ResolvedDependencyResult>()
                .forEach { dep ->
                    val id = dep.selected.moduleVersion ?: return@forEach
                    val coordinates = "${id.group}:${id.name}"
                    if (seen.add(coordinates)) {
                        if (banned.any { id.group.startsWith(it) }) offenders += "$coordinates (via ${component.moduleVersion?.name})"
                        walk(dep.selected)
                    }
                }
        }
        walk(root.get())
        if (offenders.isNotEmpty()) {
            throw GradleException("Material dependencies found in release build:\n" + offenders.joinToString("\n") { " - $it" })
        }
    }
}
tasks.named("check") { dependsOn(checkNoMaterial) }
