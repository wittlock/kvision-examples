import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootPlugin
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpack
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    val kotlinVersion: String by System.getProperties()
    id("kotlinx-serialization") version kotlinVersion
    kotlin("multiplatform") version kotlinVersion
    kotlin("plugin.allopen") version kotlinVersion
    kotlin("kapt") version kotlinVersion
    val shadowVersion: String by System.getProperties()
    id("com.github.johnrengelman.shadow") version shadowVersion
    val kvisionVersion: String by System.getProperties()
    id("kvision") version kvisionVersion
    id("application")
}

version = "1.0.0-SNAPSHOT"
group = "com.example"

repositories {
    mavenCentral()
    jcenter()
    maven { url = uri("https://dl.bintray.com/kotlin/kotlin-eap") }
    maven { url = uri("https://kotlin.bintray.com/kotlinx") }
    maven { url = uri("https://dl.bintray.com/kotlin/kotlin-js-wrappers") }
    maven { url = uri("https://dl.bintray.com/rjaros/kotlin") }
    maven { url = uri("https://repo.spring.io/milestone") }
    maven { url = uri("https://oss.sonatype.org/content/repositories/snapshots") }
    mavenLocal()
}

// Versions
val kotlinVersion: String by System.getProperties()
val micronautVersion: String by System.getProperties()
val kvisionVersion: String by System.getProperties()
val coroutinesVersion: String by project

val webDir = file("src/frontendMain/web")
val mainClassNameVal = "com.example.MainKt"

application {
    mainClassName = mainClassNameVal
}

allOpen {
    annotation("io.micronaut.aop.Around")
}

kotlin {
    jvm("backend") {
        withJava()
        compilations.all {
            kotlinOptions {
                jvmTarget = "1.8"
                freeCompilerArgs = listOf("-Xjsr305=strict")
                javaParameters = true
            }
        }
    }
    js("frontend") {
        browser {
            runTask {
                outputFileName = "main.bundle.js"
                sourceMaps = false
                devServer = KotlinWebpackConfig.DevServer(
                    open = false,
                    port = 3000,
                    proxy = mapOf(
                        "/kv/*" to "http://localhost:8080",
                        "/kvws/*" to mapOf("target" to "ws://localhost:8080", "ws" to true)
                    ),
                    contentBase = listOf("$buildDir/processedResources/frontend/main")
                )
            }
            webpackTask {
                outputFileName = "main.bundle.js"
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
                api("pl.treksoft:kvision-server-micronaut:$kvisionVersion")
            }
            kotlin.srcDir("build/generated-src/common")
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
            }
        }
        val backendMain by getting {
            dependencies {
                implementation(kotlin("stdlib-jdk8"))
                implementation(kotlin("reflect"))
                implementation(project.dependencies.enforcedPlatform("io.micronaut:micronaut-bom:$micronautVersion"))
                implementation("io.micronaut:micronaut-inject")
                implementation("io.micronaut:micronaut-validation")
                implementation("io.micronaut.kotlin:micronaut-kotlin-runtime")
                implementation("io.micronaut:micronaut-runtime")
                implementation("io.micronaut:micronaut-http-server-netty")
                implementation("ch.qos.logback:logback-classic")
                implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
            }
        }
        val backendTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(kotlin("test-junit"))
                implementation(project.dependencies.enforcedPlatform("io.micronaut:micronaut-bom:$micronautVersion"))
                implementation("org.junit.jupiter:junit-jupiter-api")
                implementation("io.micronaut.test:micronaut-test-junit5")
                implementation("org.junit.jupiter:junit-jupiter-engine")
            }
        }
        val frontendMain by getting {
            resources.srcDir(webDir)
            dependencies {
                implementation("pl.treksoft:kvision:$kvisionVersion")
                implementation("pl.treksoft:kvision-bootstrap:$kvisionVersion")
                implementation("pl.treksoft:kvision-bootstrap-css:$kvisionVersion")
                implementation("pl.treksoft:kvision-datacontainer:$kvisionVersion")
            }
            kotlin.srcDir("build/generated-src/frontend")
        }
        val frontendTest by getting {
            dependencies {
                implementation(kotlin("test-js"))
                implementation("pl.treksoft:kvision-testutils:$kvisionVersion")
            }
        }
    }
}

fun getNodeJsBinaryExecutable(): String {
    val nodeDir = NodeJsRootPlugin.apply(rootProject).nodeJsSetupTaskProvider.get().destination
    val isWindows = System.getProperty("os.name").toLowerCase().contains("windows")
    val nodeBinDir = if (isWindows) nodeDir else nodeDir.resolve("bin")
    val command = NodeJsRootPlugin.apply(rootProject).nodeCommand
    val finalCommand = if (isWindows && command == "node") "node.exe" else command
    return nodeBinDir.resolve(finalCommand).absolutePath
}

tasks {
    create("generatePotFile", Exec::class) {
        dependsOn("compileKotlinFrontend")
        executable = getNodeJsBinaryExecutable()
        args("${rootProject.buildDir}/js/node_modules/gettext-extract/bin/gettext-extract")
        inputs.files(kotlin.sourceSets["frontendMain"].kotlin.files)
        outputs.file("$projectDir/src/frontendMain/resources/i18n/messages.pot")
    }
    withType<JavaExec> {
        jvmArgs("-XX:TieredStopAtLevel=1", "-Dcom.sun.management.jmxremote")
        if (gradle.startParameter.isContinuous) {
            systemProperties(
                mapOf(
                    "micronaut.io.watch.restart" to "true",
                    "micronaut.io.watch.enabled" to "true",
                    "micronaut.io.watch.paths" to "src/backendMain"
                )
            )
        }
    }
}

afterEvaluate {
    tasks {
        getByName("frontendProcessResources", Copy::class) {
            dependsOn("compileKotlinFrontend")
            exclude("**/*.pot")
            doLast("Convert PO to JSON") {
                destinationDir.walkTopDown().filter {
                    it.isFile && it.extension == "po"
                }.forEach {
                    exec {
                        executable = getNodeJsBinaryExecutable()
                        args(
                            "${rootProject.buildDir}/js/node_modules/gettext.js/bin/po2json",
                            it.absolutePath,
                            "${it.parent}/${it.nameWithoutExtension}.json"
                        )
                        println("Converted ${it.name} to ${it.nameWithoutExtension}.json")
                    }
                    it.delete()
                }
            }
        }
        create("frontendArchive", Jar::class).apply {
            dependsOn("frontendBrowserProductionWebpack")
            group = "package"
            archiveAppendix.set("frontend")
            val distribution =
                project.tasks.getByName("frontendBrowserProductionWebpack", KotlinWebpack::class).destinationDirectory!!
            from(distribution) {
                include("*.*")
            }
            from(webDir)
            duplicatesStrategy = DuplicatesStrategy.EXCLUDE
            into("/public")
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
        getByName("shadowJar", ShadowJar::class) {
            dependsOn("frontendArchive", "backendJar")
            manifest {
                attributes(
                    mapOf(
                        "Implementation-Title" to rootProject.name,
                        "Implementation-Group" to rootProject.group,
                        "Implementation-Version" to rootProject.version,
                        "Timestamp" to System.currentTimeMillis(),
                        "Main-Class" to mainClassNameVal
                    )
                )
            }
            from(project.tasks["frontendArchive"].outputs.files)
            mergeServiceFiles()
        }
        getByName("jar", Jar::class).apply {
            dependsOn("shadowJar")
        }
        create("backendRun", JavaExec::class) {
            dependsOn("run")
        }
        getByName("compileKotlinBackend") {
            dependsOn("compileKotlinMetadata")
        }
        getByName("compileKotlinFrontend") {
            dependsOn("compileKotlinMetadata")
        }
    }
}

kapt {
    arguments {
        arg("micronaut.processing.annotations", "com.example.*")
        arg("micronaut.processing.group", "com.example")
        arg("micronaut.processing.module", "template-fullstack-micronaut")
    }
}

dependencies {
    "kapt"(platform("io.micronaut:micronaut-bom:$micronautVersion"))
    "kapt"("io.micronaut:micronaut-inject-java")
    "kapt"("io.micronaut:micronaut-validation")
    "kaptTest"("io.micronaut:micronaut-bom:$micronautVersion")
    "kaptTest"("io.micronaut:micronaut-inject-java")
}
