import java.io.IOException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket

val gdxVersion: String by project
val appName: String by project

plugins {
    id("com.github.xpenatan.gdx-teavm") version "1.6.2"
}

dependencies {
    implementation(project(":core"))
    implementation("com.badlogicgames.gdx:gdx:$gdxVersion")
    implementation("com.github.xpenatan.gdx-teavm:gdx-controllers-web:1.6.2")
}

val faviconDir = layout.projectDirectory.dir("src/main/webapp")

// TeaVM's dev server defaults to port 8080 and fails outright (with a confusing HTTP 404
// from whatever else owns the port) when that is taken. Take the first free port instead.
fun isPortInUse(port: Int): Boolean {
    // A connect check catches listeners bound to one loopback address only (e.g. a dev
    // server on 127.0.0.1), which a wildcard bind test alone can miss on macOS.
    for (host in listOf("127.0.0.1", "::1")) {
        try {
            Socket().use { it.connect(InetSocketAddress(host, port), 200) }
            return true
        } catch (_: IOException) {
            // nothing listening on this address
        }
    }
    return try {
        ServerSocket(port).close()
        false
    } catch (_: IOException) {
        true
    }
}

fun firstFreePort(from: Int): Int =
    (from until from + 100).firstOrNull { !isPortInUse(it) }
        ?: throw GradleException("No free port for the dev server in $from..${from + 99}")

// Pick once per build: the plugin reads the port from several tasks, and once the dev
// server is up it holds the port, so re-probing would hand the next reader a different one.
val devServerPort: Int by lazy { firstFreePort(8080) }

gdxTeaVM {
    assets(rootProject.file("assets"))

    webDefaults {
        mainClass = "com.jlogicsoftware.kimeria.web.WebLauncher"
        htmlTitle = appName
        serverPort = providers.provider { devServerPort }
    }

    // Unnamed target: `./gradlew :web:gdx_teavm_web_js_run` for local dev,
    // with the TeaVM dev server rebuilding and reloading on change.
    js {
        devServer {
            enabled = true
            autoReload = true
            // Serve favicon.ico from the site root (the plugin's generated
            // index.html has no <link rel="icon">, so browsers request it).
            staticDirs.from(faviconDir)
        }
    }

    // Named "release" target: `./gradlew :web:gdx_teavm_web_js_release_build`
    // produces the static, minified output the GitHub Pages workflow deploys.
    js("release") {
        obfuscated = true
    }
}

// The release build's generated index.html has no <link rel="icon">, so browsers
// request /favicon.ico from the root; copy it next to index.html.
tasks.matching { it.name == "gdx_teavm_web_js_release_build" }.configureEach {
    doLast {
        copy {
            from(faviconDir)
            into(layout.buildDirectory.dir("dist/js/release/webapp"))
        }
    }
}
