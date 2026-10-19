Add **WikiPane 1.0.0-beta.2**, an initial public beta of a native OSRS Wiki reader for RuneLite. Complete server-parsed articles are normalized into source-backed semantic content and rebuilt for the narrow Swing sidebar. Search, internal links, page history, requirements, compact tables, full-size images and Wiki map previews use the same pipeline.

Optional integration routes existing minimap, quest-guide and supported skill Wiki actions into the reader. Informational entity menus can open the full article or temporary Quick Inspect. Users can highlight and pin reference text; supported skill/quest/quest-point comparisons stay local.

### Dependencies and privacy

`build=gradle` adds **jsoup 1.23.2**, solely for in-memory HTML parsing; networking uses RuneLite's injected OkHttp client and Gson. The dependency-verification changes include the jsoup JAR/POM and the Netty BOM metadata imported by its POM. Hashes were independently checked against Maven Central. No Netty binaries are included. Licenses and Wiki attribution are in the repository.

Requests are limited to the OSRS Wiki and its map-tile server. Selected entity lookups include identity/location as required by Special:Lookup; character levels, quest states, account identifiers, inventory snapshots and credentials are not uploaded. Optional integration features start disabled with third-party-server warnings. There is no telemetry, external backend, input injection or gameplay automation.

### Automated scan findings: dependency review requested

The reported `URL.openConnection`, `System.err` and `HttpClient.newBuilder` references originate in **bundled jsoup 1.23.2**, not WikiPane production classes. Checked the exact JAR using Java 11 `javap`:

- `UrlConnectionExecutor.createConnection`: URLConnection transport.
- Java 11 multi-release `HttpClientExecutor.newClient`: Java HTTP transport.
- `Regex.hasRe2j` and `Evaluator$MatchText` constructor: optional regex/deprecation diagnostics.

WikiPane calls only string-based jsoup parsing/DOM APIs; it does not use jsoup HTTP sessions, URL/file parsing, document connections, form submission, or `:matchText`, and does not ship re2j. Its actual HTTP path remains the injected RuneLite OkHttp client. The library references are still present in the distributed dependency; this update does not claim the scan findings are cleared.

Could a maintainer assess whether the declared dependency can be accepted with those unused transport APIs, or whether a parser-only distribution is required? We have preserved the upstream library rather than removing arbitrary classes and risking parser compatibility. [Call-site evidence and exact artifact hash](https://github.com/birdturtle/WikiPane/blob/main/docs/DEPENDENCY_REVIEW.md).

### Verification and review areas

Source: `113241b012ddd925b2af739dfe5d9a3822b65e0b`.

- Java 11 / RuneLite 1.13.2 build; **259 tests passing**, zero failures/errors/skips.
- 14 valid article composition audits; six complete map-data fixtures with geometry/pixel checks.
- Official v5 local PR-mode packager and API/archive checks passed on this commit with this dependency patch.
- The current verification-template's `verifyAll` task passed with the proposed dependency changes.
- [WikiPane Java 11 CI](https://github.com/birdturtle/WikiPane/actions/runs/39191686320).

The owner has manually tested the main reading, routing, pinning, character checks, skill guides, maps and latest dropdown/zoom refinements in RuneLite. Broader multi-user/platform/lifecycle testing remains incomplete and is disclosed as part of the public beta. Automated tests and local packaging are not claims of Plugin Hub approval.

Please review the opt-in core Wiki widget retention/restoration and DPS callback forwarding, existing quest/skill Wiki-action routing, static reference overlays, and explicit user-selected map markers. Monster inspection uses static source information; restricted combat-strategy selections cannot be pinned. Other plugins' settings are not mutated and unrelated browser launches are not intercepted.

Known limits: no website JavaScript/calculators; no automatic underground map-area switching; session-only pins; unsupported requirements remain unknown. Source/browser fallbacks remain available.

Generated-by: Codex
Generated-by: Claude Code
