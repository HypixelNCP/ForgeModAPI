# Rewound Forge Mod API

A fork of [Hypixel's ForgeModAPI](https://github.com/HypixelDev/ForgeModAPI) that implements the
[Rewound Mod API](https://github.com/HypixelNCP/modapi) for Forge on 1.8.9. It's for end users; developers should look at
the core library.

## Building

The core library isn't published to a repository yet, so install it to your local Maven repository first, then build this
mod with JDK 8.

```sh
cd ../RewoundModAPI && mvn install
cd ../RewoundForgeModAPI && ./gradlew build
```

The mod is `build/libs/RewoundModAPI-<version>.jar`, and the tweaker for bundling is
`tweaker/build/libs/tweaker-<version>.jar`.

Run `./gradlew setupDecompWorkspace` after importing the project to generate the files your IDE needs.

## Usage

Publish the mod to your local Maven repository with `./gradlew publishToMavenLocal`, then depend on it.

```kotlin
repositories {
    mavenLocal()
}

val version = "1.0.2.0"
dependencies {
    modImplementation("cc.rewound:mod-api-forge:$version")
    // If you use ForgeGradle 2 you might need to use fg.deobf or deobfCompile instead. Consult your MDK for tips on how
    // to depend on an obfuscated dependency
}
```

From here on you can use the [Rewound Mod API](https://github.com/HypixelNCP/modapi) directly.

### Bundling the Rewound Mod API

Either tell your users to put this mod in their mods folder, or bundle a loading tweaker. Bundling takes more setup, but
your mod then carries a copy of the mod API and picks the newest available version at runtime.

You need a shadow plugin, because the tweaker has to be relocated, which `fileTree` based jar copying can't do. Use the
[shadow plugin](https://github.com/johnrengelman/shadow) or a [template](https://github.com/nea89o/Forge1.8.9Template) that
already has it.

```kotlin
dependencies {
    modImplementation("cc.rewound:mod-api-forge:$version") // You should already have this dependency from earlier
    shadowImpl("cc.rewound:mod-api-forge-tweaker:$version") // You need to add this dependency
}
```

Relocate the `cc.rewound.modapi.tweaker` package somewhere unique, such as `my.modid.modapitweaker`, and point the
`TweakClass` manifest entry at it (or load the tweaker using delegation).

```kotlin
tasks.shadowJar {
    configurations = listOf(shadowImpl)
    relocate("cc.rewound.modapi.tweaker", "my.modid.modapitweaker")
}

tasks.withType(org.gradle.jvm.tasks.Jar::class) {
    manifest.attributes.run {
        this["TweakClass"] = "my.modid.modapitweaker.RewoundModAPITweaker"
    }
}
```

The version must have four numeric parts, because the tweaker compares them to pick the newest copy.

## Debugging

Start the game with `-Dcc.rewound.modapi.debug=true` to subscribe to every event and log what the server sends.

[DevAuth](https://github.com/DJtheRedstoner/DevAuth) can log you in with your Minecraft account during development.

## License

MIT, as upstream. See [LICENSE](LICENSE).
