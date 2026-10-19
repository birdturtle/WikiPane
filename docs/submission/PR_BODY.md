Add **WikiPane 1.0.0-beta.4**, a native OSRS Wiki reader for RuneLite. Complete server-parsed articles are normalized into source-backed semantic content and reconstructed for the narrow Swing sidebar. Search, internal links, page history, requirements, compact tables, images and Wiki map previews share the same pipeline.

Optional integration routes existing minimap, quest-guide and supported skill Wiki actions into the reader. Informational entity menus support the article or Quick Inspect. Users can highlight and pin reference text. Supported character requirement checks stay local.

### Standard build and dependency removal

`build=standard`, Java 11, **no additional production dependencies**. Both the jsoup binary and the source-subset build have been removed, including generated sources and dependency-verification changes. This PR now changes only the plugin marker.

The injected RuneLite OkHttp client fetches server-rendered Wiki HTML; a small in-memory markup reader retains the structures needed by the existing semantic normalizer. It does not fetch resources, execute code or expand wikitext/templates. Gson is injected. No API exception or scan suppression is requested.

### Privacy and review boundaries

Requests are limited to the Wiki and its map server. Selected lookups include entity identity/location as needed by Special:Lookup. Skills, quest states, account identifiers, inventory snapshots and credentials are not uploaded. Optional integrations default disabled with server warnings. No telemetry, backend, input injection or gameplay automation.

Please review core Wiki widget retention/restoration and DPS callback forwarding, quest/skill Wiki-action routing, static reference overlays and user-selected map markers. Other plugins' settings remain untouched. Restricted combat-strategy selections cannot be pinned.

### Verification

- Java 11 / RuneLite 1.13.2 build: 267 tests pass, no failures/errors/skips.
- All 14 semantic composition reports match the previous reader byte-for-byte; six complete map fixtures pass.
- Live Wiki articles/search, canonical item lookup, seed inspection, NPC variant and image decode pass.
- Packaged API audit and negative API fixtures pass.
- [Official hosted standard-build packaging passed](https://github.com/runelite/plugin-hub/actions/runs/39195494680) on source `472857ab8cf75913356b279c4857e553743e2b3c`.
- The refreshed reviewbot reports the new plugin/source link without API, dependency or non-plugin-change warnings. This is not maintainer approval.

The local official packager compiled the standard template but encountered an archive-recompression error on this Arch environment. The unmodified hosted packager subsequently passed; no project build hook or dependency exception is used.

The owner previously tested reading, routing, pinning, character checks, skill guides and maps in RuneLite. The dependency-free beta requires a fresh in-client regression pass. Broader multi-user/platform/lifecycle testing remains incomplete. Automated checks do not establish Hub approval.

Limits: no website JavaScript/calculators, automatic underground map switching or persistent pins; unsupported requirements remain unknown. Source/browser fallbacks remain available.

Generated-by: Codex
Generated-by: Claude Code
