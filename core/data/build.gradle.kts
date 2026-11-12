plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

kotlin {
    compilerOptions { jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11 }
}

dependencies {
    implementation(libs.proj4j)
    api(project(":core:model"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.datastore.preferences.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.retrofit.core)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.core)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}
