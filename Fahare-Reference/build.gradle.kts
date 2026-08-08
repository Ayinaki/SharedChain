plugins {
    java
    id("xyz.jpenilla.run-paper") version "3.0.2"
//    id("com.gradleup.shadow") version "9.3.1"
}

group = "dev.qixils"
version = "1.3.2-SNAPSHOT"

val mcVersion = "1.21.11"
val targetJavaVersion = 21

repositories {
    mavenCentral()
    maven {
        name = "papermc"
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }
    maven {
        name = "sonatype"
        url = uri("https://oss.sonatype.org/content/groups/public/")
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:$mcVersion-R0.1-SNAPSHOT")
}

java {
    val javaVersion = JavaVersion.toVersion(targetJavaVersion)
    sourceCompatibility = javaVersion
    targetCompatibility = javaVersion

    if (JavaVersion.current() < javaVersion) {
        toolchain.languageVersion.set(JavaLanguageVersion.of(targetJavaVersion))
    }
}

tasks.withType<JavaCompile>().configureEach {
    if (targetJavaVersion >= 10 || JavaVersion.current().isJava10Compatible) {
        options.release.set(targetJavaVersion)
    }
}

tasks {
    runServer {
        minecraftVersion(mcVersion)
    }

    /*
    build {
        dependsOn(shadowJar)
    }
     */
}

tasks.processResources {
    val props = mapOf("version" to version)
    inputs.properties(props)
    filteringCharset = "UTF-8"

    filesMatching("plugin.yml") {
        expand(props)
    }
}

/*
tasks.shadowJar {
    minimize()

    dependencies {
        exclude(dependency("net.kyori:.*"))
    }
}
 */
