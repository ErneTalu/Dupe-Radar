plugins {
    alias(libs.plugins.fabric.loom)
}

base {
    archivesName = properties["archives_base_name"] as String
    version = libs.versions.mod.version.get()
    group = properties["maven_group"] as String
}

repositories {
    // AUTISM Client is consumed from your local Maven repo. In the AUTISM project run:
    //     ./gradlew publishToMavenLocal
    mavenLocal()
    maven("https://maven.fabricmc.net/") { name = "Fabric" }
    mavenCentral()
    maven("https://jitpack.io") { name = "JitPack" }
}

dependencies {
    // Mirrors the AUTISM Client build: official Mojang mappings are implicit for 26.1.2, so there is no
    // explicit `mappings(...)` line and dependencies use plain `implementation`.
    minecraft(libs.minecraft)
    implementation(libs.fabric.loader)
    implementation(libs.fabric.api)

    // The AUTISM Client API (published to mavenLocal from the AUTISM project).
    implementation(libs.autism)
    implementation("com.github.DupeDB:java-api:1.0.3")
}

// Turns an exact Minecraft version (e.g. "26.1.2") into a patch-compatible range ("~26.1") so the addon
// keeps loading across patch releases instead of pinning one exact build.
fun toMinecraftCompat(version: String): String {
    val m = Regex("""^(\d+)\.(\d+)(?:\.(\d+))?$""").matchEntire(version)
        ?: return version
    val (year, drop, _) = m.destructured
    return "~$year.$drop"
}

tasks {
    processResources {
        val propertyMap = mapOf(
            "version" to project.version,
            "mc_version" to libs.versions.minecraft.get(),
            "mc_compat" to toMinecraftCompat(libs.versions.minecraft.get())
        )
        inputs.properties(propertyMap)
        filteringCharset = "UTF-8"
        filesMatching("fabric.mod.json") {
            expand(propertyMap)
        }
    }

    java {
        sourceCompatibility = JavaVersion.VERSION_25
        targetCompatibility = JavaVersion.VERSION_25
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(25))
        }
    }

    withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.release.set(25)
    }
}

tasks.register<Copy>("deployToClient") {
    dependsOn(tasks.build)
    from(layout.buildDirectory.dir("libs")) {
        include("*.jar")
        exclude("*-sources.jar")
    }
    into("../Autism-Client-main/run/mods")
}
