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
    // RoomCardsTest (D72) reads the room cards: a card edit alone must rerun the tests.
    inputs.file("../docs/ROOM_CARDS.md").withPathSensitivity(PathSensitivity.RELATIVE)
}
