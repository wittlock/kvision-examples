import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpack
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    val kotlinVersion: String by System.getProperties()
    kotlin("plugin.serialization") version kotlinVersion
    kotlin("multiplatform") version kotlinVersion
    kotlin("plugin.allopen") version kotlinVersion
    kotlin("kapt") version kotlinVersion
    val shadowVersion: String by System.getProperties()
    id("com.github.johnrengelman.shadow") version shadowVersion
    val kvisionVersion: String by System.getProperties()
    id("io.kvision") version kvisionVersion
    id("application")
}

version = "1.0.0-SNAPSHOT"
group = "com.example"

repositories {
    mavenCentral()
    mavenLocal()
}

// Versions
val kotlinVersion: String by System.getProperties()
val micronautVersion: String by System.getProperties()
val kvisionVersion: String by System.getProperties()
val coroutinesVersion: String by project
val springSecurityCryptoVersion: String by project
val springDataR2dbcVersion: String by project
val r2dbcPostgresqlVersion: String by project
val r2dbcH2Version: String by project
val reactorKotlinExtensionsVersion: String by project
val reactorAdapterVersion: String by project
val kweryVersion: String by project
val h2DatabaseVersion: String by project

val webDir = file("src/frontendMain/web")
val mainClassNameVal = "com.example.MainKt"

configurations.all {
    resolutionStrategy.eachDependency {
        if (requested.group == "com.h2database" && requested.name == "h2") {
            useVersion(h2DatabaseVersion)
        }
    }
}

application {
    mainClass.set(mainClassNameVal)
}

allOpen {
    annotation("io.micronaut.aop.Around")
}

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
                    proxy = mutableMapOf(
                        "/kv/*" to "http://localhost:8080",
                        "/login" to "http://localhost:8080",
                        "/logout" to "http://localhost:8080",
                        "/kvws/*" to mapOf("target" to "ws://localhost:8080", "ws" to true)
                    ),
                    static = mutableListOf("$buildDir/processedResources/frontend/main")
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
                api("io.kvision:kvision-server-micronaut:$kvisionVersion")
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
                implementation(kotlin("reflect"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactive:$coroutinesVersion")
                implementation(project.dependencies.platform("io.micronaut:micronaut-bom:$micronautVersion"))
                implementation("io.micronaut:micronaut-inject")
                implementation("io.micronaut:micronaut-validation")
                implementation("io.micronaut.kotlin:micronaut-kotlin-runtime")
                implementation("io.micronaut:micronaut-runtime")
                implementation("io.micronaut:micronaut-http-server-netty")
                implementation("io.micronaut:micronaut-session")
                implementation("io.micronaut.security:micronaut-security-session")
                implementation("ch.qos.logback:logback-classic")
                implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
                implementation("io.projectreactor.kotlin:reactor-kotlin-extensions:$reactorKotlinExtensionsVersion")
                implementation("io.projectreactor.addons:reactor-adapter:$reactorAdapterVersion")
                implementation("org.springframework.security:spring-security-crypto:$springSecurityCryptoVersion")
                implementation("org.springframework.data:spring-data-r2dbc:$springDataR2dbcVersion")
                implementation("io.r2dbc:r2dbc-postgresql:$r2dbcPostgresqlVersion")
                implementation("io.r2dbc:r2dbc-h2:$r2dbcH2Version")
                implementation("com.github.andrewoma.kwery:core:$kweryVersion")
            }
        }
        val backendTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(kotlin("test-junit"))
                implementation(project.dependencies.platform("io.micronaut:micronaut-bom:$micronautVersion"))
                implementation("org.junit.jupiter:junit-jupiter-api")
                implementation("io.micronaut.test:micronaut-test-junit5")
                implementation("org.junit.jupiter:junit-jupiter-engine")
            }
        }
        val frontendMain by getting {
            resources.srcDir(webDir)
            dependencies {
                implementation("io.kvision:kvision:$kvisionVersion")
                implementation("io.kvision:kvision-bootstrap:$kvisionVersion")
                implementation("io.kvision:kvision-state:$kvisionVersion")
                implementation("io.kvision:kvision-fontawesome:$kvisionVersion")
                implementation("io.kvision:kvision-i18n:$kvisionVersion")
            }
            kotlin.srcDir("build/generated-src/frontend")
        }
        val frontendTest by getting {
            dependencies {
                implementation(kotlin("test-js"))
                implementation("io.kvision:kvision-testutils:$kvisionVersion")
            }
        }
    }
}

tasks {
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
