# Saibon

A Fabric mod for Hypixel SkyBlock — information/QoL only, zero telemetry, and
a hard commitment to never automating gameplay.

See [`docs/planning/PLAN.md`](docs/planning/PLAN.md) for the full project plan,
architecture, and staged roadmap (not tracked in git — kept locally).

## Status

Stage 1 (core skeleton): mod bootstrap, event bus, config persistence, command
registry, and an empty settings GUI shell.

## Building

```
./gradlew build
```

Requires a JDK 25 to run Gradle itself (Minecraft 26.2 ships unobfuscated, so
no mappings download is needed, but Loom requires the Gradle daemon to run on
Java 25, not just a toolchain).

## Releasing

Push a tag matching `v*.*.*` (e.g. `v0.1.0`) and CI builds the jar and
publishes it to the repo's Releases page with auto-generated notes:

```
git tag v0.1.0
git push origin v0.1.0
```

## License

MIT — see [LICENSE](LICENSE).
