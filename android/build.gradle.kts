plugins {
    id("com.android.library")
}

android {
    namespace = "com.schwanitz.tagix"
    compileSdk = 34

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    api("org.slf4j:slf4j-api:2.0.18")
}

android.sourceSets {
    getByName("main") {
        java.srcDirs("../../src/main/java")
    }
    getByName("test") {
        java.srcDirs("../../src/test/java")
    }
}
