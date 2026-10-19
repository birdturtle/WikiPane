# Verification and gameplay acceptance

Automated tests cover source normalization, semantic coverage, exact entity routing, recursive three-state requirements, navigation, selection pins, bounded images/maps, lifecycle guards and Swing layout contracts. Saved complete Wiki API responses make these checks independent of live Wiki availability.

```sh
JAVA_HOME=/usr/lib/jvm/java-11-openjdk ./gradlew clean build semanticReport mapCoverageReport betaBundle
```

`check` includes distributable contents/Java-11 bytecode checks, version consistency, icon bounds and the Hub Gradle line-width check. `betaBundle` writes a versioned JAR/guide/checksum directory under `build/beta/`.

Optional network check: `./gradlew liveCheck`. Offline fixtures are regression evidence, not proof of full-Wiki coverage. Isolated Swing previews validate layout without logging into or interacting with RuneScape. Map previews intentionally fetch Wiki tiles; ordinary `preview` uses bundled fixtures/images.

## Manual acceptance

Use the [friend beta checklist](BETA_TESTING.md), plus focused checks in [player context](PLAYER_CONTEXT.md), [pinning](PINNING.md), [quest interfaces](QUEST_INTERFACES.md), [skill interfaces](SKILL_INTERFACES.md), and [map coverage](MAP_COVERAGE_AUDIT.md).

Before public submission, record tester OS, RuneLite build, UI layout/scale, version, pass/fail, and issue links. Test fixed/resizable layouts, a narrow sidebar, core Wiki enabled/disabled, Menu Entry Swapper, and repeated lifecycle transitions. Exercise offline/timeouts/rate-limit recovery without leaving stale overlays or losing the previous article.

The developer has reported successful gameplay testing of the main reading, routing, pinning, skills and map paths. That is separate from a systematic multi-user/multi-platform acceptance matrix. A successful build or mocked client test does not establish real game behavior. No automated gameplay input or capture is used.
