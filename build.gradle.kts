plugins {
    id("java-library")
    id("xyz.jpenilla.run-paper") version "3.0.2"
}

group = "me.ayinaki.sharedchain"
// Version defaults to gradle.properties; override with -Pversion= (used by the release workflow)

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    // Paper 26.2 (bump the build number as Paper publishes newer stable builds)
    compileOnly("io.papermc.paper:paper-api:26.2.build.111-stable")

    testImplementation("io.papermc.paper:paper-api:26.2.build.111-stable")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

tasks {
    withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release.set(25)
        options.compilerArgs.add("-Xlint:deprecation")
    }

    test {
        useJUnitPlatform()
    }

    runServer {
        minecraftVersion("26.2")
        jvmArgs("-Xms2G", "-Xmx2G")
    }

    processResources {
        val props = mapOf("version" to version)
        inputs.properties(props)
        filteringCharset = "UTF-8"
        filesMatching("plugin.yml") {
            expand(props)
        }
    }
}
