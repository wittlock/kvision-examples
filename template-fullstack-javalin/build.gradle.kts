import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpack
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig

plugins {
    val kotlinVersion: String by System.getProperties()
    kotlin("plugin.serialization") version kotlinVersion
    kotlin("multiplatform") version kotlinVersion
    val kvisionVersion: String by System.getProperties()
    id("io.kvision") version kvisionVersion
}

version = "1.0.0-SNAPSHOT"
group = "com.example"

repositories {
    mavenCentral()
    mavenLocal()
}

// Versions
val kotlinVersion: String by System.getProperties()
val kvisionVersion: String by System.getProperties()
val slf4jVersion: String by project

val webDir = file("src/frontendMain/web")
val mainClassName = "com.example.MainKt"

kotlin {
    jvm("backend") {
        withJava()
        compilations.all {
            java {
                targetCompatibility = JavaVersion.VERSION_17
            }
            kotlinOptions {
                jvmTarget = "17"
                freeCompilerArgs = listOf("-Xjsr305=strict")
            }
        }
    }
    js("frontend") {
        browser {
            commonWebpackConfig {
                outputFileName = "main.bundle.js"
            }
            runTask {
                sourceMaps = false
                devServer = KotlinWebpackConfig.DevServer(
                    open = false,
                    port = 3000,
                    proxy = mutableMapOf(
                        "/kv/*" to "http://localhost:8080",
                        "/kvws/*" to mapOf("target" to "ws://localhost:8080", "ws" to true)
                    ),
                    static = mutableListOf("$buildDir/processedResources/frontend/main")
                )
            }
            testTask {
                useKarma {
                    useChromeHeadless()
                }
            }
        }
        binaries.executable()
    }
    sourceSets {
        val commonMain by getting {
            dependencies {
                api("io.kvision:kvision-server-javalin:$kvisionVersion")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
            }
        }
        val backendMain by getting {
            dependencies {
                implementation(kotlin("reflect"))
                implementation("org.slf4j:slf4j-simple:$slf4jVersion")
            }
        }
        val backendTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(kotlin("test-junit"))
            }
        }
        val frontendMain by getting {
            resources.srcDir(webDir)
            dependencies {
                implementation("io.kvision:kvision:$kvisionVersion")
                implementation("io.kvision:kvision-bootstrap:$kvisionVersion")
                implementation("io.kvision:kvision-i18n:$kvisionVersion")
            }
        }
        val frontendTest by getting {
            dependencies {
                implementation(kotlin("test-js"))
                implementation("io.kvision:kvision-testutils:$kvisionVersion")
            }
        }
    }
}
afterEvaluate {
    tasks {
        create("frontendArchive", Jar::class).apply {
            dependsOn("frontendBrowserProductionWebpack")
            group = "package"
            archiveAppendix.set("frontend")
            val distribution =
                project.tasks.getByName("frontendBrowserProductionWebpack", KotlinWebpack::class).destinationDirectory
            from(distribution) {
                include("*.*")
            }
            from(webDir)
            duplicatesStrategy = DuplicatesStrategy.EXCLUDE
            into("/assets")
            inputs.files(distribution, webDir)
            outputs.file(archiveFile)
            manifest {
                attributes(
                    mapOf(
                        "Implementation-Title" to rootProject.name,
                        "Implementation-Group" to rootProject.group,
                        "Implementation-Version" to rootProject.version,
                        "Timestamp" to System.currentTimeMillis()
                    )
                )
            }
        }
        getByName("backendProcessResources", Copy::class) {
            duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        }
        getByName("backendJar").group = "package"
        create("shadowJar", Jar::class).apply {
            dependsOn("frontendArchive", "backendJar")
            group = "package"
            manifest {
                attributes(
                    mapOf(
                        "Implementation-Title" to rootProject.name,
                        "Implementation-Group" to rootProject.group,
                        "Implementation-Version" to rootProject.version,
                        "Timestamp" to System.currentTimeMillis(),
                        "Main-Class" to mainClassName
                    )
                )
            }
            val dependencies = configurations["backendRuntimeClasspath"].filter { it.name.endsWith(".jar") } +
                    project.tasks["backendJar"].outputs.files +
                    project.tasks["frontendArchive"].outputs.files
            dependencies.forEach {
                if (it.isDirectory) from(it) else from(zipTree(it))
            }
            exclude("META-INF/*.RSA", "META-INF/*.SF", "META-INF/*.DSA")
            inputs.files(dependencies)
            outputs.file(archiveFile)
            duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        }
        getByName("jar", Jar::class).apply {
            dependsOn("shadowJar")
        }
        create("backendRun", JavaExec::class) {
            dependsOn("compileKotlinBackend")
            group = "run"
            mainClass.set(mainClassName)
            classpath =
                configurations["backendRuntimeClasspath"] + project.tasks["compileKotlinBackend"].outputs.files +
                        project.tasks["backendProcessResources"].outputs.files
            workingDir = buildDir
        }
    }
}
