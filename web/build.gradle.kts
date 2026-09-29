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

gdxTeaVM {
    assets(rootProject.file("assets"))

    webDefaults {
        mainClass = "com.jlogicsoftware.kimeria.web.WebLauncher"
        htmlTitle = appName
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
