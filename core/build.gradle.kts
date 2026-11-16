val gdxVersion = project.property("gdxVersion") as String

dependencies {
    api("com.badlogicgames.gdx:gdx:$gdxVersion")
    api("com.badlogicgames.gdx-controllers:gdx-controllers-core:2.2.4")

    testImplementation("com.badlogicgames.gdx:gdx-backend-headless:$gdxVersion")
    testImplementation("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-desktop")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.2")
}

sourceSets {
    main {
        resources.srcDirs("../assets")
    }
    test {
        resources.srcDirs("../assets")
    }
}

tasks.test {
    useJUnitPlatform()
    // Tests that read a document or a data file outside the source sets (D75): an edit to that file alone must rerun
    // them. RoomCardsTest (D72) and TrapCatalogTest (D71) read both docs; HealerDialogueTest reads the text-style data.
    inputs.file("../docs/ROOM_CARDS.md").withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file("../docs/TRAPS.md").withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file("../tools/text_style.json").withPathSensitivity(PathSensitivity.RELATIVE)
}
