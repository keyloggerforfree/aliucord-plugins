import com.aliucord.gradle.AliucordExtension
import com.android.build.gradle.BaseExtension

buildscript {
    repositories {
        // mavenLocal()
        google()
        mavenCentral()
        maven("https://jitpack.io")
        maven("https://maven.aliucord.com/snapshots")
    }
    dependencies {
        classpath("com.android.tools.build:gradle:7.4.2")
        classpath("com.aliucord:gradle:bbcd8a8")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.5.32")
    }
}

allprojects {
    repositories {
        // mavenLocal()
        google()
        mavenCentral()
        maven("https://maven.aliucord.com/snapshots")
    }
}

fun Project.aliucord(configuration: AliucordExtension.() -> Unit) = extensions.getByName<AliucordExtension>("aliucord").configuration()

fun Project.android(configuration: BaseExtension.() -> Unit) = extensions.getByName<BaseExtension>("android").configuration()

subprojects {
    apply(plugin = "com.android.library")
    apply(plugin = "com.aliucord.gradle")
    apply(plugin = "kotlin-android")

    // Lets `clean make` run in one invocation without clean deleting outputs mid-build
    tasks.configureEach { if (name != "clean") mustRunAfter("clean") }

    aliucord {
        author("keyloggerforfree", 259342907914321930L)
        updateUrl.set("https://git.chinesespyware.net/keyloggerforfree/aliucord-plugins/raw/branch/builds/updater.json")
        buildUrl.set("https://git.chinesespyware.net/keyloggerforfree/aliucord-plugins/raw/branch/builds/%s.zip")
    }

    android {
        compileSdkVersion(34)

        defaultConfig {
            minSdk = 24
            targetSdk = 34
        }

        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_11
            targetCompatibility = JavaVersion.VERSION_11
        }

        tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
            kotlinOptions {
                jvmTarget = "11"
                freeCompilerArgs = freeCompilerArgs +
                    "-Xno-call-assertions" +
                    "-Xno-param-assertions" +
                    "-Xno-receiver-assertions"
            }
        }
    }

    dependencies {
        val discord by configurations
        val compileOnly by configurations

        discord("com.discord:discord:126021")
        compileOnly("com.aliucord:Aliucord:2.1.1")
    }
}

task<Delete>("clean") {
    delete(rootProject.buildDir)
}

// ./gradlew publishBuilds [-PdryRun] [-PallowDirty]
// Gradle builds everything first; publish.sh only creates and pushes the commit on the builds branch.
tasks.register<Exec>("publishBuilds") {
    group = "aliucord"
    description = "Builds all plugins and pushes the zips and updater.json to the builds branch"
    // Clean first: make doesn't notice changes to version/author/description, so the zip's manifest could be stale
    dependsOn(subprojects.map { "${it.path}:clean" }, subprojects.map { "${it.path}:make" }, "generateUpdaterJson")

    val args = mutableListOf(rootProject.file("publish.sh").absolutePath, "--no-build")
    if (project.hasProperty("dryRun")) args += "--dry-run"
    if (project.hasProperty("allowDirty")) args += "--allow-dirty"
    commandLine(args)
}
