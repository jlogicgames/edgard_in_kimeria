allprojects {
    group = "com.jlogicsoftware.kimeria"
    version = "0.0.1"

    repositories {
        mavenCentral()
        maven("https://s01.oss.sonatype.org")
    }
}

subprojects {
    apply(plugin = "java-library")

    configure<JavaPluginExtension> {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
    }
}

// Alias so `./gradlew run` (from the repo root, no module path to remember)
// launches the desktop build, same as `./gradlew :lwjgl3:run`.
tasks.register("run") {
    dependsOn(":lwjgl3:run")
}

// Development only: the desktop game with hot reload / hot restart on source and
// asset changes, see README. Never part of a release build.
tasks.register("runDev") {
    dependsOn(":lwjgl3:runDev")
}

// Same idea for the web build's plugin-generated task names, which are long
// because they encode module + platform + JS-vs-Wasm + variant.
tasks.register("runWeb") {
    description = "Starts the web build's dev server with auto-reload, same as ./gradlew :web:gdx_teavm_web_js_run."
    dependsOn(":web:gdx_teavm_web_js_run")
}

tasks.register("buildWeb") {
    description = "Builds the static, minified web output, same as ./gradlew :web:gdx_teavm_web_js_release_build."
    dependsOn(":web:gdx_teavm_web_js_release_build")
    dependsOn("validateSpriteArt")
}

tasks.register("validateLocalization") {
    group = "verification"
    description = "Validates all translation catalogs and shipped fonts."
    dependsOn(":lwjgl3:validateLocalization")
}

// Same convention for the desktop fat jar, so no build target needs a
// module path memorized.
tasks.register("buildDesktop") {
    description = "Builds a runnable desktop fat jar at lwjgl3/build/libs/, same as ./gradlew :lwjgl3:jar."
    dependsOn(":lwjgl3:jar")
    dependsOn("validateSpriteArt")
}

val validateSpriteArt = tasks.register<Exec>("validateSpriteArt") {
    group = "verification"
    description = "Rejects procedural artwork used instead of authored game sprites."
    workingDir = rootDir
    commandLine(if (System.getProperty("os.name").startsWith("Windows")) "python" else "python3", "tools/check_sprite_art.py")
}
project(":core").tasks.named("check") { dependsOn(validateSpriteArt) }
project(":lwjgl3").tasks.matching { it.name == "jar" }.configureEach { dependsOn(validateSpriteArt) }
project(":web").tasks.matching { it.name == "gdx_teavm_web_js_release_build" }.configureEach { dependsOn(validateSpriteArt) }

// Native installer/app-image (bundles its own JRE -- nothing else to
// install to play): .exe/.msi on Windows, .app/.dmg on macOS,
// .deb/.rpm/app-image on Linux, at lwjgl3/build/jpackage/. jpackage can only
// target the OS it runs on, so this produces one platform's installer per
// invocation -- see .github/workflows/deploy-desktop.yml for all three.
tasks.register("packageDesktop") {
    description = "Builds a native installer for this OS (bundled JRE, no local Java needed), same as ./gradlew :lwjgl3:jpackage."
    dependsOn(":lwjgl3:jpackage")
}
