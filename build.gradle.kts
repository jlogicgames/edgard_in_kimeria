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
    dependsOn(":web:verifyWebReleaseArtifact")
    dependsOn("validateSpriteArt")
    dependsOn("validateAllocations")
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
    dependsOn("validateAllocations")
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

val validateNamedParameters = tasks.register<Exec>("validateNamedParameters") {
    group = "verification"
    description = "Rejects unnamed tuning and geometry literals in changed production code."
    workingDir = rootDir
    commandLine(if (System.getProperty("os.name").startsWith("Windows")) "python" else "python3",
        "tools/check_named_parameters.py", "--base", providers.gradleProperty("namedParameterBase").getOrElse("auto"))
}
project(":core").tasks.named("check") { dependsOn(validateNamedParameters) }
project(":lwjgl3").tasks.matching { it.name == "jar" }.configureEach { dependsOn(validateNamedParameters) }
project(":web").tasks.matching { it.name == "gdx_teavm_web_js_release_build" }.configureEach { dependsOn(validateNamedParameters) }

val validateAllocations = tasks.register<Exec>("validateAllocations") {
    group = "verification"
    description = "Rejects allocation in per-frame methods (update*, render*, draw*) on changed production lines."
    workingDir = rootDir
    commandLine(if (System.getProperty("os.name").startsWith("Windows")) "python" else "python3",
        "tools/check_allocations.py", "--base", providers.gradleProperty("allocationBase").getOrElse("auto"))
}
project(":core").tasks.named("check") { dependsOn(validateAllocations) }
project(":lwjgl3").tasks.matching { it.name == "jar" }.configureEach { dependsOn(validateAllocations) }
project(":web").tasks.matching { it.name == "gdx_teavm_web_js_release_build" }.configureEach { dependsOn(validateAllocations) }

val testTextStyleGate = tasks.register<Exec>("testTextStyleGate") {
    group = "verification"
    description = "Runs the unit tests of tools/check_text_style.py, so a broken gate fails the build."
    workingDir = rootDir
    commandLine(if (System.getProperty("os.name").startsWith("Windows")) "python" else "python3",
        "-m", "unittest", "discover", "-s", "tools", "-p", "test_check_text_style.py")
}
val validateTextStyle = tasks.register<Exec>("validateTextStyle") {
    group = "verification"
    description = "Checks player-facing strings against docs/TEXT_STYLE.md (glossary, tale register, hint budget, unused keys)."
    dependsOn(testTextStyleGate)
    workingDir = rootDir
    commandLine(if (System.getProperty("os.name").startsWith("Windows")) "python" else "python3", "tools/check_text_style.py")
}
project(":core").tasks.named("check") { dependsOn(validateTextStyle) }
project(":lwjgl3").tasks.matching { it.name == "jar" }.configureEach { dependsOn(validateTextStyle) }
project(":web").tasks.matching { it.name == "gdx_teavm_web_js_release_build" }.configureEach { dependsOn(validateTextStyle) }

val testContrastGate = tasks.register<Exec>("testContrastGate") {
    group = "verification"
    description = "Runs the unit tests of tools/check_contrast.py, so a broken gate fails the build."
    workingDir = rootDir
    commandLine(if (System.getProperty("os.name").startsWith("Windows")) "python" else "python3",
        "-m", "unittest", "discover", "-s", "tools", "-p", "test_check_contrast.py")
}
val validateContrast = tasks.register<Exec>("validateContrast") {
    group = "verification"
    description = "Checks every placed sprite against the D76 contrast tiers (active loud, decor quiet)."
    dependsOn(testContrastGate)
    workingDir = rootDir
    commandLine(if (System.getProperty("os.name").startsWith("Windows")) "python" else "python3", "tools/check_contrast.py")
}
project(":core").tasks.named("check") { dependsOn(validateContrast) }
project(":lwjgl3").tasks.matching { it.name == "jar" }.configureEach { dependsOn(validateContrast) }
project(":web").tasks.matching { it.name == "gdx_teavm_web_js_release_build" }.configureEach { dependsOn(validateContrast) }

val testParallaxGate = tasks.register<Exec>("testParallaxGate") {
    group = "verification"
    description = "Runs the unit tests of tools/parallax_game.py and tools/parallax_mock.py (game maps and sketches), so a broken gate fails the build."
    workingDir = rootDir.resolve("tools")
    commandLine(if (System.getProperty("os.name").startsWith("Windows")) "python" else "python3",
        "-m", "unittest", "test_parallax_game")
}
val validateParallax = tasks.register<Exec>("validateParallax") {
    group = "verification"
    description = "Checks the foreground of every forest room that has one against the map's objects: rule B, cover time, the middle of long rooms (docs/ART_DIRECTION.md)."
    dependsOn(testParallaxGate)
    workingDir = rootDir.resolve("tools")
    commandLine(if (System.getProperty("os.name").startsWith("Windows")) "python" else "python3", "parallax_mock.py", "--check", "--game")
}
project(":core").tasks.named("check") { dependsOn(validateParallax) }
project(":lwjgl3").tasks.matching { it.name == "jar" }.configureEach { dependsOn(validateParallax) }
project(":web").tasks.matching { it.name == "gdx_teavm_web_js_release_build" }.configureEach { dependsOn(validateParallax) }

val testWindSpotsGate = tasks.register<Exec>("testWindSpotsGate") {
    group = "verification"
    description = "Runs the unit tests of tools/wind_spots.py, so a broken gate fails the build."
    workingDir = rootDir.resolve("tools")
    commandLine(if (System.getProperty("os.name").startsWith("Windows")) "python" else "python3",
        "-m", "unittest", "test_wind_spots")
}
val validateWindSpots = tasks.register<Exec>("validateWindSpots") {
    group = "verification"
    description = "Fails on a stale assets/wind/forest-spots.txt or a wind glint colour too far from its leaf (docs/ART_DIRECTION.md, \"Wind in the crowns\")."
    dependsOn(testWindSpotsGate)
    workingDir = rootDir
    commandLine(if (System.getProperty("os.name").startsWith("Windows")) "python" else "python3", "tools/wind_spots.py", "--check")
}
project(":core").tasks.named("check") { dependsOn(validateWindSpots) }
project(":lwjgl3").tasks.matching { it.name == "jar" }.configureEach { dependsOn(validateWindSpots) }
project(":web").tasks.matching { it.name == "gdx_teavm_web_js_release_build" }.configureEach { dependsOn(validateWindSpots) }

// Style-system gates for docs and skills (D75, docs/STYLE_SYSTEM.md). They judge documentation and agent skills, which
// ship nothing in the game, so they run in :core:check and not in the release builds.
val testStyleSystemGates = tasks.register<Exec>("testStyleSystemGates") {
    group = "verification"
    description = "Runs the unit tests of the doc-gap gate, the skills gate, the style hook dispatcher and the shared diff base."
    workingDir = rootDir.resolve("tools")
    commandLine(if (System.getProperty("os.name").startsWith("Windows")) "python" else "python3",
        "-m", "unittest", "test_check_doc_gaps", "test_check_skills", "test_check_style_hook", "test_git_base")
}
val validateDocGaps = tasks.register<Exec>("validateDocGaps") {
    group = "verification"
    description = "A gap written into docs/*.md on the branch's changed lines must link an issue (AGENTS.md)."
    dependsOn(testStyleSystemGates)
    workingDir = rootDir
    // "auto": the merge-base with origin/main when that ref exists (the whole branch plus the working tree), else HEAD.
    // A CI job needs `actions/checkout` with `fetch-depth: 0` so that origin/main and the merge-base exist.
    commandLine(if (System.getProperty("os.name").startsWith("Windows")) "python" else "python3",
        "tools/check_doc_gaps.py", "--base", providers.gradleProperty("docGapBase").getOrElse("auto"))
}
val validateSkills = tasks.register<Exec>("validateSkills") {
    group = "verification"
    description = "Shared skills, their index and the Claude pointers agree (.agents/skills/README.md)."
    dependsOn(testStyleSystemGates)
    workingDir = rootDir
    commandLine(if (System.getProperty("os.name").startsWith("Windows")) "python" else "python3", "tools/check_skills.py")
}
project(":core").tasks.named("check") { dependsOn(validateDocGaps, validateSkills) }

// Native installer/app-image (bundles its own JRE -- nothing else to
// install to play): .exe/.msi on Windows, .app/.dmg on macOS,
// .deb/.rpm/app-image on Linux, at lwjgl3/build/jpackage/. jpackage can only
// target the OS it runs on, so this produces one platform's installer per
// invocation -- see .github/workflows/deploy-desktop.yml for all three.
tasks.register("packageDesktop") {
    description = "Builds a native installer for this OS (bundled JRE, no local Java needed), same as ./gradlew :lwjgl3:jpackage."
    dependsOn(":lwjgl3:jpackage")
}
