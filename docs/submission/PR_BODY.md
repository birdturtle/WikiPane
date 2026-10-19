Add WikiPane, a native OSRS Wiki reader with reconstructed article content, page history, source links, full-size images and Wiki map previews. Optional existing Wiki-action routing and contextual menus use the same ID-aware lookup pipeline. Users can pin selected reference text; supported character requirement comparisons stay local.

The plugin uses Java 11 and `build=gradle` for jsoup 1.23.2, used solely for in-memory HTML parsing. The dependency patch includes its JAR/POM and imported Netty BOM metadata SHA-256 values independently checked against Maven Central. Netty binaries are not included. MIT, BSD and Wiki attribution are documented in the source and beta bundle.

Please review the opt-in core Wiki widget retention/restoration and DPS callback forwarding, informational quest/skill menu interception, static reference overlays, and explicit native map flags. There is no gameplay automation, external backend, telemetry, credentials or uploaded character-state data.

Verification for source `636e60a67be8b7b92de2856b8288fc286979a9f7`: Java 11/RuneLite 1.13.2 build; 254 offline tests passing; 14 valid semantic composition audits; six complete map fixtures with geometry/pixel checks; developer distribution checks. Official v5 local PR-mode packager passed with this dependency patch, including API and archive checks. The source marker pins that tested commit. [Clean GitHub Java 11 verification](https://github.com/birdturtle/WikiPane/actions/runs/39189832826) passed; beta evidence still needs review before opening this PR. The project owner has tested the major interactions in gameplay. Broader friend-beta lifecycle/platform testing remains a gate before submitting; this draft does not claim that testing is complete or that the Hub has approved the plugin.

Known limits: no website JavaScript/calculators, no automatic underground map-area switching, session-only pins, unsupported character requirements remain unknown. Source/browser fallbacks stay explicit.

Generated-by: Codex
Generated-by: Claude Code
