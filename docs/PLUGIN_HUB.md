# Plugin Hub submission — current parser-only build

Active review: [runelite/plugin-hub#18901](https://github.com/runelite/plugin-hub/pull/18901). Opening a PR or passing its build does not constitute acceptance.

## Beta 3: conforming the packaged artifact

The beta-2 artifact passed compilation/packaging, but the Hub security scan correctly found prohibited APIs in its bundled jsoup binary. The initial request for an exception was the wrong resolution and is withdrawn.

Beta 3 compiles a private in-memory subset from the pinned `org.jsoup:jsoup:1.23.2:sources@jar` build input. HTTP transports, authentication, file loading, connection/form-submission state and reflective optional-loader code are absent. The deprecated-selector diagnostic uses SLF4J debug. No scan exception, suppressed finding or dependency-package exemption is requested.

The original tokenizer/tree builder, DOM, entities and CSS selectors are retained. [Source selection, exact patches, source hash and limitations](../third-party/jsoup-parser/README.md) are reviewable. The subset is relocated to `com.wikipane.vendor.jsoup` to avoid collisions. Code generation/compilation occurs only during the build; no runtime code download/loading is introduced. The unrestricted jsoup binary is neither a compile nor a runtime dependency.

`verifyPackagedApis` scans every class in the finished developer JAR, including the parser. The same inspection runs on the official Hub archive task. `verifyApiAudit` proves rejection of each originally flagged API with valid classfile fixtures outside WikiPane's namespace. The normal fixtures and behavioral tests use the restricted parser, not a different test dependency.

## Source dependency verification

Only `jsoup-1.23.2-sources.jar` is needed. Its independently Maven-Central-checked SHA-256 is:

`f0ed21735857df93c3e461d1aecf89d28c3eda552f2e043d68959d341376aa17`

The build itself checks that hash before preparing sources. The Hub patch also registers it for Gradle dependency verification. An independent `thirdPartySources` configuration avoids upgrading the existing binary jsoup dependency used by other plugins during `verifyAll`. The previous binary/POM/Netty-BOM additions are removed from the proposed patch; Netty is not needed or shipped.

BSD-2-Clause code licensing, original RuneLite adaptation notices, full jsoup MIT license, Wiki contributor/source/revision attribution and separate Jagex imagery terms remain available. The license and parser adaptation notice are embedded in developer and Hub artifacts.

## Other review boundaries

- WikiPane HTTP uses RuneLite's injected OkHttp client, with bounded requests, restricted HTTPS destinations, timeouts and cancellation. Gson is injected. There is no backend or telemetry.
- Selected entity lookups disclose identity/location when required by Special:Lookup. Skills, quest states, account identifiers, inventory snapshots and credentials are not sent. Optional lookup integrations start disabled with the third-party-server warning.
- Client-only informational menus do not send game actions. The Wiki widget is retained/restored without mutating another plugin's configuration; the core DPS callback is forwarded. This widget/menu boundary is explicitly called out for review.
- Inspections show static monster data, and restricted combat-strategy selections cannot be pinned. No encounter prediction, input injection, automatic walking or gameplay automation.
- Native map markers are explicit user requests based on verified source points. Preview regions/routes are not inferred exact game locations. Existing owned markers are replaced only after validation; unrelated plugins' markers are left alone.
- Public-beta limits remain: no website JavaScript/calculators, no automatic underground map-area switching, session-only pins and unknown unsupported requirements. Broader gameplay/platform/lifecycle acceptance remains incomplete and is disclosed.

## Evidence and updating the PR

The parser-only clean build passes 261 tests with no failures/errors/skips. All 14 semantic reports are byte-for-byte identical to the unrestricted beta-2 reports. The artifact API guard and all three negative fixtures pass. Fresh official packaging and the Hub security rescan must target the new source marker before claiming the scan findings are cleared.

`docs/submission/wikipane` and the PR's `plugins/wikipane` identify the exact submitted source commit. The combined patch and PR body are retained under `docs/submission/`. Continue fixes in the existing PR rather than opening duplicates; advance the marker whenever runtime/build inputs change.

## Official references

- [Plugin Hub submission and dependency verification](https://github.com/runelite/plugin-hub)
- [Verification template](https://github.com/runelite/plugin-hub/tree/master/package/verification-template)
- [Official packaging tooling](https://github.com/runelite/plugin-hub-tooling)
- [Plugin Hub security/rule review](https://github.com/runelite/runelite/wiki/Plugin-Hub-Review)
- [Rejected or rolled-back features](https://github.com/runelite/runelite/wiki/Rejected-or-Rolled-Back-Features)
