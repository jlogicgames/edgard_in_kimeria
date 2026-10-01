val gdxVersion = project.property("gdxVersion") as String
val appName = project.property("appName") as String

plugins {
    application
    id("org.beryx.runtime") version "2.0.1"
}

dependencies {
    implementation(project(":core"))
    implementation("com.badlogicgames.gdx:gdx-backend-lwjgl3:$gdxVersion")
    implementation("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-desktop")
    implementation("com.badlogicgames.gdx-controllers:gdx-controllers-desktop:2.2.4")
}

// A separate source set (and classpath) for the one-off font-baking tool
// below, kept off the app's own dependencies -- and so out of the shipped
// jar: the GWT/web build can't run FreeTypeFontGenerator at all, so fonts
// are pre-baked to bitmap files once on desktop here and shipped as plain
// assets for every platform, including this one. gdx-freetype itself isn't
// a runtime dependency of the game anymore, see Assets.font().
sourceSets {
    create("fontBaker") {
        java.srcDir("src/fontBaker/java")
    }
}

val fontBakerImplementation: Configuration = configurations.getByName("fontBakerImplementation")

dependencies {
    fontBakerImplementation(project(":core"))
    fontBakerImplementation("com.badlogicgames.gdx:gdx-backend-headless:$gdxVersion")
    fontBakerImplementation("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-desktop")
    fontBakerImplementation("com.badlogicgames.gdx:gdx-freetype:$gdxVersion")
    fontBakerImplementation("com.badlogicgames.gdx:gdx-freetype-platform:$gdxVersion:natives-desktop")
    fontBakerImplementation("com.badlogicgames.gdx:gdx-tools:$gdxVersion") {
        exclude(group = "com.badlogicgames.gdx", module = "gdx-backend-lwjgl")
    }
}

tasks.register<JavaExec>("bakeFonts") {
    description = "Regenerates assets/fonts/generated/*.fnt+.png from the .ttf sources. Run after changing a font, its sizes, or the baked charset."
    classpath = sourceSets["fontBaker"].runtimeClasspath
    mainClass.set("com.jlogicsoftware.kimeria.tools.FontBaker")
    workingDir = rootProject.file("assets")
}

// libGDX 1.14.2's gdx-backend-lwjgl3 bundles LWJGL 3.3.3, which fails to
// enumerate monitors (glfwGetPrimaryMonitor() returns NULL) on very new
// macOS releases. Force a newer LWJGL that has the GLFW/Cocoa fix.
val lwjglVersion = "3.4.3"
configurations.all {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.lwjgl") {
            useVersion(lwjglVersion)
        }
    }
}

// libGDX's SharedLibraryLoader calls System.load, a restricted method: JDK 24+
// warns on it and a future JDK will block it unless native access is enabled.
// The classpath is the unnamed module, hence ALL-UNNAMED.
val nativeAccessArgs = listOf("--enable-native-access=ALL-UNNAMED")

// LWJGL's default memory backend on JDK 25+ still uses sun.misc.Unsafe, which
// JDK 25 warns about (terminally deprecated). LWJGL 3.4 ships an FFM-based
// backend for JDK 25+ instead; it is only in the multi-release part of the
// jar, so pick it only when the JVM that runs the game (and, for jpackage,
// supplies the bundled JRE) is that new. Not applied to the fat jar, whose
// flattened layout drops the multi-release classes.
val lwjglMemoryArgs =
    if (JavaVersion.current().isCompatibleWith(JavaVersion.VERSION_25))
        listOf("-Dorg.lwjgl.system.memoryBackend=org.lwjgl.system.MemoryBackendFFM")
    else listOf()

application {
    mainClass.set("com.jlogicsoftware.kimeria.lwjgl3.Lwjgl3Launcher")
}

sourceSets {
    main {
        resources.srcDirs("../assets")
    }
}

tasks.jar {
    archiveBaseName.set(appName)
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest {
        attributes["Main-Class"] = "com.jlogicsoftware.kimeria.lwjgl3.Lwjgl3Launcher"
        // `java -jar` has no command line to put --enable-native-access on;
        // JDK 24+ honours this manifest attribute instead (older JDKs ignore it).
        attributes["Enable-Native-Access"] = "ALL-UNNAMED"
    }
    dependsOn(configurations.runtimeClasspath)
    from({ configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) } })
}

// Native installers/app-images via jpackage (bundles its own JRE, so no
// local Java install is needed to play): `.exe`/`.msi` on Windows,
// `.app`/`.dmg` on macOS, `.deb`/`.rpm`/app-image on Linux. jpackage can
// only build for the OS it runs on, so CI runs this per-OS (see
// .github/workflows/deploy-desktop.yml) rather than cross-building.
runtime {
    options.addAll(listOf("--strip-debug", "--compress", "2", "--no-header-files", "--no-man-pages"))

    launcher {
        noConsole = true
        val isMac = org.gradle.internal.os.OperatingSystem.current().isMacOsX
        jvmArgs = nativeAccessArgs + lwjglMemoryArgs + if (isMac) listOf("-XstartOnFirstThread") else listOf()
    }

    jpackage {
        imageName = appName
        installerName = appName
        // jpackage's macOS bundler rejects a leading 0 (rejects "0.0.1", the
        // project's library version) -- app-version needs its own value.
        appVersion = "1.0.0"

        val os = org.gradle.internal.os.OperatingSystem.current()
        if (os.isWindows) {
            installerOptions.addAll(listOf("--win-menu", "--win-shortcut"))
        } else if (os.isLinux) {
            installerOptions.addAll(listOf("--linux-shortcut", "--linux-package-name", appName))
        } else if (os.isMacOsX) {
            // macOS wants this at 16 characters or under.
            installerOptions.add("--mac-package-name")
            installerOptions.add("Kimeria")
        }
    }
}

tasks.named<JavaExec>("run") {
    jvmArgs(nativeAccessArgs + lwjglMemoryArgs)
    // macOS needs -XstartOnFirstThread for LWJGL3/GLFW to create a window.
    if (System.getProperty("os.name").lowercase().contains("mac")) {
        jvmArgs("-XstartOnFirstThread")

        // On some very new macOS builds, LWJGL's bundled GLFW native fails
        // to enumerate monitors (glfwGetPrimaryMonitor() returns NULL,
        // crashing window creation with an NPE in Checks.check). If a
        // system GLFW is installed (e.g. `brew install glfw`), prefer it --
        // see README "Troubleshooting" for details.
        val systemGlfw = listOf("/opt/homebrew/lib/libglfw.dylib", "/usr/local/lib/libglfw.dylib")
            .map(::File).firstOrNull { it.exists() }
        if (systemGlfw != null) {
            jvmArgs("-Dorg.lwjgl.glfw.libname=${systemGlfw.absolutePath}")
        }
    }
    standardInput = System.`in`
    workingDir = rootProject.file("assets")
}
