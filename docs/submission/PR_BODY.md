Add WikiPane, a native OSRS Wiki reader with reconstructed article content, page history, source links, full-size images and Wiki map previews. Optional existing Wiki-action routing and contextual menus use the same ID-aware lookup pipeline. Users can pin selected reference text; supported character requirement comparisons stay local.

The plugin uses Java 11 and `build=gradle` for jsoup 1.23.2, used solely for in-memory HTML parsing. The dependency patch includes its JAR/POM SHA-256 values independently checked against Maven Central. MIT, BSD and Wiki attribution are documented in the source and beta bundle.

Please review the opt-in core Wiki widget retention/restoration and DPS callback forwarding, informational quest/skill menu interception, static reference overlays, and explicit native map flags. There is no gameplay automation, external backend, telemetry, credentials or uploaded character-state data.

Verification: Java 11 build, offline behavioral tests, semantic source-coverage audits, map geometry/pixel tests and developer distribution checks. The exact source SHA, counts and official local packager outcome are recorded in the submission handoff; recheck them before opening this PR. The project owner has tested the major interactions in gameplay. Broader friend-beta lifecycle/platform testing remains a gate before submitting; this draft does not claim that testing is complete or that the Hub has approved the plugin.

Known limits: no website JavaScript/calculators, no automatic underground map-area switching, session-only pins, unsupported character requirements remain unknown. Source/browser fallbacks stay explicit.

Generated-by: Codex
Generated-by: Claude Code
