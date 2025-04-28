import org.jetbrains.changelog.Changelog
import org.jetbrains.changelog.markdownToHTML
import org.jetbrains.intellij.platform.gradle.TestFrameworkType

//fun prop(key: String) = project.findProperty(key).toString()

plugins {
    id("java")
    //id("org.jetbrains.intellij") version "1.16.1"
    //id("org.jetbrains.changelog") version "2.2.0"
    alias(libs.plugins.kotlin) // Kotlin support
    alias(libs.plugins.intelliJPlatform) // IntelliJ Platform Gradle Plugin
    alias(libs.plugins.changelog) // Gradle Changelog Plugin
}

//group = prop("pluginGroup")
//version = prop("pluginVersion")
group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

// Set the JVM language level used to build the project.
kotlin {
    // required by IntelliJ Platform '2024.2.3'
    jvmToolchain(21)
}

// Configure project's dependencies
repositories {
   // maven("https://www.jetbrains.com/intellij-repository/releases")
   // maven("https://www.jetbrains.com/intellij-repository/snapshots")
   // maven("https://maven.aliyun.com/repository/public/")
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
  //  intellijPlatform {
  //      intellijIdeaCommunity("2025.1")

  //      bundledPlugin("com.intellij.java")

  //      testFramework(TestFrameworkType.Platform)
  //  }

  //  testImplementation("junit:junit:4.13.2")
    // other dependencies, e.g., 3rd-party libraries

    // IntelliJ Platform Gradle Plugin Dependencies Extension - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html
    intellijPlatform {
        create(providers.gradleProperty("platformType"), providers.gradleProperty("platformVersion"))

        // Plugin Dependencies. Uses `platformBundledPlugins` property from the gradle.properties file for bundled IntelliJ Platform plugins.
        bundledPlugins(providers.gradleProperty("platformBundledPlugins").map { it.split(',') })

        // Plugin Dependencies. Uses `platformPlugins` property from the gradle.properties file for plugin from JetBrains Marketplace.
        plugins(providers.gradleProperty("platformPlugins").map { it.split(',') })

        //  instrumentationTools()
        pluginVerifier()
        zipSigner()
        testFramework(TestFrameworkType.Platform)
    }
}

// Configure Gradle IntelliJ Plugin - read more: https://github.com/JetBrains/gradle-intellij-plugin
//intellij {
//    pluginName.set(providers.gradleProperty("pluginName"))
//    version.set(providers.gradleProperty("platformVersion"))
//    type.set(providers.gradleProperty("platformType"))

    // Plugin Dependencies. Uses `platformPlugins` property from the gradle.properties file.
//    plugins.set(providers.gradleProperty("platformPlugins").split(',').map(String::trim).filter(String::isNotEmpty))
//}
// Configure IntelliJ Platform Gradle Plugin - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-extension.html
intellijPlatform {
    pluginConfiguration {
        version = providers.gradleProperty("pluginVersion")

        // Extract the <!-- Plugin description --> section from README.md and provide for the plugin's manifest
        description = providers.fileContents(layout.projectDirectory.file("README.md")).asText.map {
            val start = "<!-- Plugin description -->"
            val end = "<!-- Plugin description end -->"

            with(it.lines()) {
                if (!containsAll(listOf(start, end))) {
                    throw GradleException("Plugin description section not found in README.md:\n$start ... $end")
                }
                subList(indexOf(start) + 1, indexOf(end)).joinToString("\n").let(::markdownToHTML)
            }
        }

        val changelog = project.changelog // local variable for configuration cache compatibility
        // Get the latest available change notes from the changelog file
        changeNotes = providers.gradleProperty("pluginVersion").map { pluginVersion ->
            with(changelog) {
                renderItem(
                    (getOrNull(pluginVersion) ?: getUnreleased())
                        .withHeader(false)
                        .withEmptySections(false),
                    Changelog.OutputType.HTML,
                )
            }
        }

        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
            untilBuild = providers.gradleProperty("pluginUntilBuild")
        }
    }

    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }

    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
        // The pluginVersion is based on the SemVer (https://semver.org) and supports pre-release labels, like 2.1.7-alpha.3
        // Specify pre-release label to publish the plugin in a custom Release Channel automatically. Read more:
        // https://plugins.jetbrains.com/docs/intellij/deployment.html#specifying-a-release-channel
        channels = providers.gradleProperty("pluginVersion").map { listOf(it.substringAfter('-', "").substringBefore('.').ifEmpty { "default" }) }
    }

    pluginVerification {
        ides {
            recommended()
        }
    }
}

// Configure Gradle Changelog Plugin - read more: https://github.com/JetBrains/gradle-changelog-plugin
changelog {
    version.set(providers.gradleProperty("pluginVersion"))
    keepUnreleasedSection.set(false)
    groups.set(emptyList())
}

java {
    //toolchain {
   //    languageVersion.set(providers.gradleProperty("jdkVersion"))
    //}
}

tasks {
    // Set the JVM compatibility versions
    compileJava {
       // options.release.set(providers.gradleProperty("compatibleJdkVersion"))
    }

    wrapper {
        gradleVersion = providers.gradleProperty("gradleVersion").toString()
    }

 /*   patchPluginXml {
        pluginId.set(providers.gradleProperty("pluginGroup"))
        version.set(providers.gradleProperty("pluginVersion").toString())
        sinceBuild.set(providers.gradleProperty("pluginSinceBuild"))
        untilBuild.set(providers.gradleProperty("pluginUntilBuild"))

        // Extract the <!-- Plugin description --> section from README.md and provide for the plugin's manifest
        pluginDescription.set(
            projectDir.resolve("README.md").readText().lines().run {
                val start = "<!-- Plugin description -->"
                val end = "<!-- Plugin description end -->"

                if (!containsAll(listOf(start, end))) {
                    throw GradleException("Plugin description section not found in README.md:\n$start ... $end")
                }
                subList(indexOf(start) + 1, indexOf(end))
            }.joinToString("\n").run { markdownToHTML(this) }
        )

        // Get the latest available change notes from the changelog file
        changeNotes.set(provider {
            changelog.renderItem(
                changelog
                    .getLatest()
                    .withHeader(true)
                    .withEmptySections(false),
                Changelog.OutputType.HTML
            )
        })
    } */

    publishPlugin {
       // dependsOn("patchChangelog")
        //token.set(System.getenv("intellijPublishToken"))
        // pluginVersion is based on the SemVer (https://semver.org) and supports pre-release labels, like 2.1.7-alpha.3
        // Specify pre-release label to publish the plugin in a custom Release Channel automatically. Read more:
        // https://plugins.jetbrains.com/docs/intellij/deployment.html#specifying-a-release-channel
       // channels.set(listOf(providers.gradleProperty("pluginVersion").split('-').getOrElse(1) { "default" }.split('.').first()))
    }
}
