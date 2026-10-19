# Plugin Hub readiness and submission

Reviewed against upstream Plugin Hub and its v5 packaging tooling on 2026-10-18. A local technical check is not RuneLite approval. The friend beta is a separate developer JAR; Plugin Hub builds from the source commit, not that JAR.

## Packaging

- Java 11 release target; official example-plugin Gradle layout. `build=gradle` is necessary because the only added production dependency is jsoup 1.23.2.
- RuneLite/client, OkHttp and Gson are provided by RuneLite. No direct production dependencies on client transitive libraries are declared.
- BSD-2-Clause code license, preserved RuneLite adaptation notice and bundled jsoup MIT license. Wiki contributions/fixtures retain CC BY-NC-SA attribution and source revision references; Jagex imagery has separate terms.
- Root icon is a real PNG within Hub limits. No runtime code loading, native binaries or executable files are shipped.
- Local `verifyDeveloperPluginJar` checks shaded bytecode, metadata, licensing and accidental client/test packaging. `verifyHubMetadata` checks icon bounds and the official 120-column Gradle display-width rule. CI runs the Java 11 tests and coverage reports.

## Dependency verification — upstream change required

At review, upstream verification-template listed jsoup 1.13.1, not 1.23.2. Do not downgrade a working parser solely to avoid review. The prepared patch adds `org.jsoup:jsoup:1.23.2` under `thirdParty` with reason `wikipane`, and the following independently Maven-Central-checked SHA-256 values. Gradle also resolves the Netty BOM imported by jsoup's POM for its test dependencies; the patch verifies that POM, without introducing Netty binaries:

| Artifact | SHA-256 |
| --- | --- |
| jsoup-1.23.2.jar | e9d8c856856680427f096d156f02a414289985c930998fcb519ce5be39b42003 |
| jsoup-1.23.2.pom | 3ff28beafd484e9c345e0749d697d2aa31f29ace54b5d4da4e8375e6d8fe1802 |
| netty-bom-4.2.17.Final.pom | 0951aa35bdcbf6dbc87af75a67e9af43c01687ed571f77c2e9195417c3afe969 |

The Hub-specific archive task uses stored ZIP entries to avoid the v5 packager copying stale compressed sizes when re-encoding across Java/zlib versions. This changes compression only, not runtime content; the friend JAR remains compressed.

jsoup parses already-downloaded HTML in memory. Its fetching APIs are not used. It has no production transitive dependencies. Maintainers must independently approve the dependency and hashes; the local patch is not an allowlist exemption on the public Hub.

## Runtime policy review

- The injected OkHttp client is derived with bounded timeouts and redirects disabled. Allowed content hosts are `oldschool.runescape.wiki` and the restricted tile paths on `maps.runescape.wiki`. Browser launches require a deliberate user action and HTTPS URL. Downloads and image dimensions are bounded.
- No player names/account IDs/credentials, inventory snapshots, skill levels or quest states are exported. Selected entity requests carry type, ID, name and location where necessary for official lookup resolution; this is disclosed. Optional third-party lookup features start disabled and have the required warning.
- Menus use local informational callbacks; no added menu action sends game actions to the server. Existing quest/skill Wiki events are consumed only for supported explicit Wiki actions. No synthetic input, automatic walking, combat prediction or scene scans.
- The minimap replacement temporarily retains/restores the core Wiki widget and forwards its DPS callback. This is the most sensitive compatibility boundary: ask maintainers to review it explicitly. It uses supported widgets/events, not reflection or a LinkBrowser patch.
- Inspections project static monster information. Combat strategy pinning is restricted; the ordinary reader can show source articles. These boundaries still require maintainer assessment.
- Map flags are explicit user selections from verified source points. Wiki shapes/routes are static preview content, not live gameplay guidance. Existing markers are replaced only after coverage validation; other plugins' markers remain owned by those plugins. Underground area auto-switching is unsupported.
- Swing mutations, game-state reads and worker operations retain their threading boundaries and cleanup/generation guards. Repeated lifecycle behavior still needs beta regression testing.

## Prepared submission (do not submit an untested commit)

1. Finish the [beta checklist](BETA_TESTING.md), recording unresolved issues and platform coverage. Fix regressions and rebuild if RuneLite changes.
2. Select the exact tested, public WikiPane source commit (full 40-character SHA). Prepare `plugins/wikipane` in a fork of [runelite/plugin-hub](https://github.com/runelite/plugin-hub):

   ```properties
   repository=https://github.com/birdturtle/WikiPane.git
   commit=FULL_TESTED_SOURCE_COMMIT
   ```

3. Include the dependency-verification patch under `package/verification-template/` in the same submission branch. Keep jsoup review evidence and rationale in the PR description.
4. Review the prepared PR body in `docs/submission/PR_BODY.md`; update exact build/test evidence and user testing. Ask for widget/menu/map review. Do not call local checks Plugin Hub acceptance.
5. Open one PR, inspect the Hub build/check feedback, and keep fixes in that PR by advancing the source commit marker. Wait for maintainer review. Source changes do not update Hub users until its marker is advanced and accepted.

The public-beta submission is open as [runelite/plugin-hub#18901](https://github.com/runelite/plugin-hub/pull/18901). The source marker, dependency diff and submitted PR body are retained in `docs/submission/`. Updates should continue in that same PR.

## Official references

- [Plugin Hub creation and submission](https://github.com/runelite/plugin-hub#submitting-a-plugin)
- [Dependency verification template](https://github.com/runelite/plugin-hub/tree/master/package/verification-template)
- [Official example build](https://github.com/runelite/example-plugin/blob/master/build.gradle)
- [Rejected or rolled-back features](https://github.com/runelite/runelite/wiki/Rejected-or-Rolled-Back-Features)
- [Official packager and disallowed APIs](https://github.com/runelite/plugin-hub-tooling)

## Recorded local rehearsal

Source commit: `636e60a67be8b7b92de2856b8288fc286979a9f7`, Java 11 / RuneLite 1.13.2.

- `build semanticReport mapCoverageReport betaBundle`: success, 254 tests, no failures/errors/skips; 14 article composition audits valid and six complete map-data fixtures audited.
- Official v5 packager in `PACKAGE_IS_PR=true` local mode: success, including API/disallowed-symbol checks, dependency verification and final archive rewrite. No upload configuration or credentials were supplied.
- Hub baseline: `759be27a1871164c51fcacc8c65ad487eddfaff0`; tooling source inspected: `034eaffd5bce7bb678d35287b547874f3dba6b3b`.
- The local upstream rehearsal includes the proposed jsoup/BOM verification patch. A real upstream build needs that patch accepted or included in its PR; the unmodified upstream verification list is insufficient.
- The clean GitHub runner identified a clipped RFD requirement introduction. The reader now reserves the final glyph bounds at the actual width. The indented requirement container now measures before reserving its height. The final [clean GitHub Java 11 run](https://github.com/birdturtle/WikiPane/actions/runs/39189832826) passed, including the prior failure.

`docs/submission/wikipane` pins this exact technically tested source. `docs/submission/plugin-hub.patch` combines its marker and dependency verification changes, ready to review/apply in a fresh Hub fork. These artifacts are retained alongside the active PR linked above. Before submitting, update the marker if further runtime code changes and rerun the official check.

## Public beta 2 submission checkpoint

Version `1.0.0-beta.2`, source `113241b012ddd925b2af739dfe5d9a3822b65e0b`, is the current submission candidate. It includes the player-confirmed dropdown toggle, retained map-chooser state, focal-point image zoom, truthful map wording and window-aware popup sizing.

The Java 11 build passed with 259 tests and no failures/errors/skips. The official v5 packager passed against this exact source and the refreshed upstream baseline `b458965`; the verification-template `verifyAll` check also passed. The owner authorized initial public-beta submission after confirming the latest changes in gameplay. Wider friend/platform/lifecycle coverage remains incomplete and is disclosed rather than claimed complete.

The marker, combined patch and PR description under `docs/submission/` now target this checkpoint. Earlier recorded rehearsals above remain historical evidence. The active upstream submission is [runelite/plugin-hub#18901](https://github.com/runelite/plugin-hub/pull/18901). The official Hub CI and maintainers determine acceptance; opening the PR does not make the plugin installable yet.
