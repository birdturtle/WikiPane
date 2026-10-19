Add **WikiPane 1.0.0-beta.3**, an initial public beta of a native OSRS Wiki reader for RuneLite. Complete server-parsed articles are normalized into source-backed semantic content and rebuilt for the narrow Swing sidebar. Search, internal links, page history, requirements, compact tables, full-size images and Wiki map previews use the same pipeline.

Optional integration routes existing minimap, quest-guide and supported skill Wiki actions into the reader. Informational entity menus can open the full article or temporary Quick Inspect. Users can highlight and pin reference text; supported skill/quest/quest-point comparisons stay local.

### Dependencies and privacy

`build=gradle` compiles a **private in-memory subset of jsoup 1.23.2 from its pinned source artifact**. The unrestricted binary is not shipped. Networking remains RuneLite's injected OkHttp client; Gson is injected. The only additional code artifact is the source JAR, verified by SHA-256 in the build and the Hub verification metadata. Gradle also resolves the verified jsoup POM/imported BOM metadata; no Netty or unrestricted jsoup binary is shipped. A separate source-verification configuration avoids upgrading existing plugins' binary dependencies. Licenses, attribution and the exact adaptation are included in the repository and both artifact types.

Requests are limited to the OSRS Wiki and its map-tile server. Selected entity lookups include identity/location as required by Special:Lookup; character levels, quest states, account identifiers, inventory snapshots and credentials are not uploaded. Optional integration features start disabled with third-party-server warnings. There is no telemetry, external backend, input injection or gameplay automation.

### Actual fix for the automated findings

The previous request for an exception is withdrawn. The artifact is now constrained instead:

- HTTP transports, URL/file loading, authentication/session helpers and form-submission APIs are omitted from the source subset.
- The facade accepts already-loaded strings. Document connection state/accessors and form submission/data methods are removed, while forms remain readable DOM content.
- Regex uses the JDK path, without reflective re2j/module probing. W3C/JAXP integration is absent; unsupported XPath reports an explicit error rather than loading another provider.
- The deprecated-selector diagnostic uses SLF4J debug rather than `System.err`.
- Parser code is privately relocated. There is no unrestricted binary dependency or runtime code loading.
- Both developer and Hub artifacts undergo an **all-classes constant-pool API audit**, including the parser. No dependency namespace is exempted. Negative classfile fixtures validate detection of all three originally reported categories.

[Exact source selection, reviewable patches and source artifact hash](https://github.com/birdturtle/WikiPane/blob/main/third-party/jsoup-parser/README.md). This fixes the distribution rather than requesting permission for the prohibited APIs.

### Verification and review areas

Source: `2f6b7b62594b99ab11ddc62deeef8f461d3ca371`.

- Java 11 / RuneLite 1.13.2 build; **261 tests passing**, zero failures/errors/skips.
- 14 valid article composition audits; six complete map-data fixtures with geometry/pixel checks.
- Official v5 local PR-mode packager and API/archive checks passed on this commit with this dependency patch.
- The source-only verification-template `verifyAll` check passed with the verified POM/BOM build metadata.
- All 14 semantic report blocks match beta 2 byte-for-byte; additional parser behavior tests cover malformed tables, entities, links, lists, forms and regex selectors.

The owner has manually tested the main reading, routing, pinning, character checks, skill guides, maps and latest dropdown/zoom refinements in RuneLite. The parser-only beta-3 JAR has also been provided to the owner for an in-client regression test. Broader multi-user/platform/lifecycle testing remains incomplete and is disclosed as part of the public beta. Automated tests and local packaging are not claims of Plugin Hub approval.

Please review the opt-in core Wiki widget retention/restoration and DPS callback forwarding, existing quest/skill Wiki-action routing, static reference overlays, and explicit user-selected map markers. Monster inspection uses static source information; restricted combat-strategy selections cannot be pinned. Other plugins' settings are not mutated and unrelated browser launches are not intercepted.

Known limits: no website JavaScript/calculators; no automatic underground map-area switching; session-only pins; unsupported requirements remain unknown. Source/browser fallbacks remain available.

Generated-by: Codex
Generated-by: Claude Code
