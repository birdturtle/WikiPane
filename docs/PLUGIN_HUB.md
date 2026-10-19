# Plugin Hub submission

Active review: [runelite/plugin-hub#18901](https://github.com/runelite/plugin-hub/pull/18901). Passing a build is not approval.

## Standard build

WikiPane uses `build=standard`, Java 11 and RuneLite-provided dependencies only. There is no jsoup binary, vendored source, generated parser or dependency-verification change. The Hub PR should change only `plugins/wikipane`.

The Wiki API returns server-rendered HTML. WikiPane reads that in memory through `com.wikipane.html`, retaining structures and attributes needed by the existing normalizer. No local template engine, browser CSS/JavaScript execution or remote code loading is involved. The complete article still feeds the semantic document and native reader; this change does not substitute an article summary or raw website view.

HTTP uses injected OkHttp; Gson is injected. Parsing and decoding run off the client/EDT threads. Source input is bounded by size, nesting and node count. Local packaged-API checks inspect every class without dependency exemptions.

## Review boundaries

- Searches and selected entity lookup identity/location go to the Wiki; character-state comparisons remain local. No telemetry or external backend.
- Optional integrations are disabled by default with third-party-server warnings. No other plugin settings are changed.
- Client-only menus and explicit map requests do not automate gameplay. Core Wiki widgets are retained/restored with DPS callback forwarding.
- Monster information is static; restricted combat-strategy selections cannot be pinned.
- No website JavaScript/calculators, automatic underground map switching or persistent pins. Unsupported requirements remain unknown.

## Verification

Run `JAVA_HOME=/usr/lib/jvm/java-11-openjdk ./gradlew clean build semanticReport mapCoverageReport betaBundle` for local Java 11 tests, composition reports, packaging and API checks. The official standard-build packager must also pass on the submitted source marker.

Current local results: 267 passing tests, 14 composition reports identical to the previous reader, six map fixtures, packaged API audit and live Wiki/entity/image checks passing.

The existing reader, routing, overlays and maps were user-tested before dependency removal. The new beta JAR requires an in-client regression pass; compilation is not gameplay verification.

`docs/submission/wikipane` records the exact source marker. Advance it on the existing PR after source changes; do not open duplicate submissions.

[Official standard build and submission guidance](https://github.com/runelite/plugin-hub#creating-new-plugins).
