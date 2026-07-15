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

Requires a Java 25 toolchain (Gradle will provision one automatically if
`org.gradle.java.installations.auto-download` is not disabled).

## License

MIT — see [LICENSE](LICENSE).
