# Release Notes

Style guide for entries in this file (read this before adding a new one):

- One `##` heading per version: `## vX.Y.Z - YYYY-MM-DD`, newest at the top.
- Group changes under `### Added`, `### Changed`, `### Fixed`, `### Policy`
  (only include the subheadings that apply to that release).
- Each bullet is one line, states the user-visible or contributor-visible
  effect first, then the reason if it's not obvious.
- To cut a release: add a new entry here for the version being released, bump
  `mod_version` in `gradle.properties` to match, commit, tag `vX.Y.Z`, and
  push the tag. The `Release` GitHub Actions workflow reads this file's
  section for that tag and publishes it as the GitHub release body, plus
  builds and attaches the jar automatically.

---

## v0.1.1 - 2026-07-14

### Changed
- Widened the supported Minecraft version range from a fixed `~26.2` to
  `>=26.1 <26.3`, so Saibon now loads on 26.1.x clients (e.g. 26.1.2) in
  addition to 26.2. 26.1+ all share the same unobfuscated API surface, so no
  code changes were needed beyond the `fabric.mod.json` dependency range.

### Policy
- Replaced the "single version at a time, ported forward" policy with a
  documented supported-range policy: floor stays at 26.1 (start of the
  unobfuscated era), ceiling is capped to versions actually verified and
  bumped forward only after a new drop is confirmed working. See `PLAN.md`.

## v0.1.0 - 2026-07-14

### Added
- Initial Stage 1 core skeleton: mod bootstrap/entrypoints, internal event
  bus, JSON config persistence engine under `config/saibon/`, and the
  `/saibon` (`/sb`) command opening an empty categorized settings GUI shell.
- Gradle/Fabric Loom project scaffold targeting Minecraft 26.2, Fabric Loader
  0.19.x, Fabric API, Fabric Language Kotlin, Java 25 toolchain.
- CI build workflow and tag-triggered release workflow.
