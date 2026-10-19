# Plugin Hub readiness and submission

Reviewed against upstream Plugin Hub and its v5 packaging tooling on 2026-10-18. A local technical check is not RuneLite approval. The friend beta is a separate developer JAR; Plugin Hub builds from the source commit, not that JAR.

## Packaging

- Java 11 release target; official example-plugin Gradle layout. `build=gradle` is necessary because the only added production dependency is jsoup 1.23.2.
- RuneLite/client, OkHttp and Gson are provided by RuneLite. No direct production dependencies on client transitive libraries are declared.
- BSD-2-Clause code license, preserved RuneLite adaptation notice and bundled jsoup MIT license. Wiki contributions/fixtures retain CC BY-NC-SA attribution and source revision references; Jagex imagery has separate terms.
- Root icon is a real PNG within Hub limits. No runtime code loading, native binaries or executable files are shipped.
- Local `verifyDeveloperPluginJar` checks shaded bytecode, metadata, licensing and accidental client/test packaging. `verifyHubMetadata` checks icon bounds and the official 120-column Gradle display-width rule. CI runs the Java 11 tests and coverage reports.

## Dependency verification — upstream change required

At review, upstream verification-template listed jsoup 1.13.1, not 1.23.2. Do not downgrade a working parser solely to avoid review. The prepared patch adds `org.jsoup:jsoup:1.23.2` under `thirdParty` with reason `wikipane`, and these independently Maven-Central-checked SHA-256 values:

| Artifact | SHA-256 |
| --- | --- |
| jsoup-1.23.2.jar | e9d8c856856680427f096d156f02a414289985c930998fcb519ce5be39b42003 |
| jsoup-1.23.2.pom | 3ff28beafd484e9c345e0749d697d2aa31f29ace54b5d4da4e8375e6d8fe1802 |

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

A source marker, dependency diff and PR body are prepared locally during release work. Creating an upstream PR is a separate final action after the user reviews the concrete submission.

## Official references

- [Plugin Hub creation and submission](https://github.com/runelite/plugin-hub#submitting-a-plugin)
- [Dependency verification template](https://github.com/runelite/plugin-hub/tree/master/package/verification-template)
- [Official example build](https://github.com/runelite/example-plugin/blob/master/build.gradle)
- [Rejected or rolled-back features](https://github.com/runelite/runelite/wiki/Rejected-or-Rolled-Back-Features)
- [Official packager and disallowed APIs](https://github.com/runelite/plugin-hub-tooling)
