# Plugin Hub submission

Active review: [runelite/plugin-hub#18901](https://github.com/runelite/plugin-hub/pull/18901). Passing a build is not approval.

## Standard build

WikiPane uses `build=standard`, Java 11 and RuneLite-provided dependencies only. There is no jsoup binary, vendored source, generated parser or dependency-verification change. The Hub PR should change only `plugins/wikipane`.

The Wiki API returns server-rendered HTML. WikiPane reads that in memory through `com.wikipane.html`, retaining structures and attributes needed by the existing normalizer. No local template engine, browser CSS/JavaScript execution or remote code loading is involved. The complete article still feeds the semantic document and native reader; this change does not substitute an article summary or raw website view.

HTTP uses injected OkHttp; Gson is injected. Parsing and decoding run off the client/EDT threads. Source input is bounded by size, nesting and node count. Local packaged-API checks inspect every class without dependency exemptions.

## Review boundaries

- Searches and selected entity lookup identity/location go to the Wiki; character-state comparisons remain local. No telemetry or external backend.
- Optional integrations are disabled by default with third-party-server warnings. No other plugin settings are changed.
- Client-only menus and explicit map requests do not automate gameplay. Core Wiki widgets are never modified, hidden, replaced or reconfigured; the minimap integration has been deleted.
- Monster information is static; restricted combat-strategy selections cannot be pinned.
- No website JavaScript/calculators, automatic underground map switching or persistent pins. Unsupported requirements remain unknown.

## Verification

Run `JAVA_HOME=/usr/lib/jvm/java-11-openjdk ./gradlew clean build semanticReport mapCoverageReport betaBundle` for local Java 11 tests, composition reports, packaging and API checks. The official standard-build packager must also pass on the submitted source marker.

Beta 5 removes all minimap widget integration. The local Java 11 build passes 256 tests with no failures/errors/skips; all 14 composition reports match beta 4, six map fixtures and packaged API checks pass. The packaged audit rejects Widget mutation and client target-selection setters. The owner confirmed the beta-5 in-client test. The existing PR points to source `fac2bfa8b0877e91fd51c2fbdf0d41a1c1de7957`.

The owner confirmed the beta-5 in-client test after the minimap takeover was removed. Broader multi-user/platform/lifecycle acceptance remains incomplete; compilation is not gameplay verification.

`docs/submission/wikipane` records the exact source marker. Advance it on the existing PR after source changes; do not open duplicate submissions.

[Official standard build and submission guidance](https://github.com/runelite/plugin-hub#creating-new-plugins).

## Submission evidence

Beta 5 source `fac2bfa8b0877e91fd51c2fbdf0d41a1c1de7957` passed the [hosted standard-build check](https://github.com/runelite/plugin-hub/actions/runs/39199247403). The refreshed reviewbot comment has no API/dependency warnings. The PR still changes only `plugins/wikipane`. Maintainer approval is separate from CI.
