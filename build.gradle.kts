
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.gradle.util.GFileUtils
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val detektVersion = "1.0.0-RC14"
val kotlinTestVersion = "3.3.1"
val junit5Version = "5.4.0"
val jacksonVersion = "2.9.7"

plugins {
    val kotlinVersion = "1.3.21"
    val detektVersion = "1.0.0-RC14"

    id("org.jetbrains.kotlin.jvm") version kotlinVersion
    id("io.gitlab.arturbosch.detekt") version detektVersion
    id("com.github.johnrengelman.shadow") version "5.0.0"
    id("de.sebastianboegl.shadow.transformer.log4j") version "2.1.1"
}

group = "com.github.zxkane"
version = "1.0.0-SNAPSHOT"

tasks.withType<KotlinCompile> {
    kotlinOptions {
        jvmTarget = "1.8"
        freeCompilerArgs = listOf("-Xjsr305=strict")
    }
}

val test by tasks.getting(Test::class) {
    useJUnitPlatform()
    testLogging {
        // set options for log level LIFECYCLE
        events = setOf(
                TestLogEvent.FAILED,
                TestLogEvent.PASSED,
                TestLogEvent.SKIPPED,
                TestLogEvent.STANDARD_OUT
        )
        exceptionFormat = TestExceptionFormat.FULL
        showExceptions = true
        showCauses = true
        showStackTraces = true

    }
}

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("com.amazonaws:aws-lambda-java-core:1.2.0")
    implementation("com.amazonaws:aws-lambda-java-events:2.2.6"){
        exclude("com.amazonaws:aws-java-sdk-s3")
        exclude("com.amazonaws:aws-java-sdk-kms")
    }
    implementation("com.amazonaws:aws-lambda-java-log4j2:1.1.0")

    implementation("com.fasterxml.jackson.core:jackson-databind:${jacksonVersion}")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:${jacksonVersion}")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:${jacksonVersion}"){
        exclude("org.jetbrains.kotlin")
    }

    implementation("commons-codec:commons-codec:1.11")
    compile(files("lib/lippi-oapi-encrpt.jar"))

    implementation("com.amazonaws:aws-java-sdk-dynamodb:1.11.519")

    testImplementation("org.junit.jupiter:junit-jupiter-api:${junit5Version}")
    testRuntime("org.junit.jupiter:junit-jupiter-engine:${junit5Version}")
    testCompile("io.kotlintest:kotlintest-runner-junit5:${kotlinTestVersion}"){
        exclude("org.jetbrains.kotlin")
    }
    testCompile("org.mockito:mockito-core:2.23.+")
    testImplementation("com.nhaarman.mockitokotlin2:mockito-kotlin:2.1.0")

    detektPlugins("io.gitlab.arturbosch.detekt:detekt-formatting:${detektVersion}")
}

tasks.wrapper {
    gradleVersion = "5.2.1"
    distributionType = Wrapper.DistributionType.ALL
    doLast {
        /*
         * Copy the properties file into the detekt-gradle-plugin project.
         * This allows IDEs like IntelliJ to import the detekt-gradle-plugin as a standalone project.
         */
        val gradlePluginWrapperDir = File(gradle.includedBuild("detekt-gradle-plugin").projectDir, "/gradle/wrapper")
        GFileUtils.mkdirs(gradlePluginWrapperDir)
        copy {
            from(propertiesFile)
            into(gradlePluginWrapperDir)
        }
    }
}

val userHome = System.getProperty("user.home")!!

detekt {
    debug = true
    toolVersion = detektVersion
    filters = ".*/resources/.*,.*/build/.*"

    reports {
        xml.enabled = true
        html.enabled = true
    }

    idea {
        path = "$userHome/.idea"
        codeStyleScheme = "$userHome/.idea/idea-code-style.xml"
        inspectionsProfile = "$userHome/.idea/inspect.xml"
        report = "project.projectDir/reports"
        mask = "*.kt"
    }
}

tasks.withType<ShadowJar> {
    transform(com.github.jengelman.gradle.plugins.shadow.transformers.Log4j2PluginsCacheFileTransformer::class.java)
}

tasks.getByName("build") {
    finalizedBy("shadowJar")
}
