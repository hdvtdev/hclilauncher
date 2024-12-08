plugins {
    id("java")
}

group = "com.github.hdvtdev"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.google.code.gson:gson:2.11.0")
    implementation(files("libraries/com/github/hdvtdev/schliph/1.0.2/schliph-1.0.2.jar"))
}



java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

