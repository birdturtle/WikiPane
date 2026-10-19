Add **WikiPane 1.0.0-beta.5**, a native OSRS Wiki reader for RuneLite. Complete server-parsed articles are normalized into source-backed semantic content and reconstructed for the narrow Swing sidebar. Search, internal links, page history, requirements, compact tables, images and Wiki map previews share the same pipeline.

Optional integration routes existing quest-guide and supported skill Wiki actions into the reader. RuneLite's minimap Wiki widgets are untouched. Informational entity menus support the article or Quick Inspect. Users can highlight and pin reference text. Supported character requirement checks stay local.

### Standard build and dependency removal

`build=standard`, Java 11, **no additional production dependencies**. Both the jsoup binary and the source-subset build have been removed, including generated sources and dependency-verification changes. This PR now changes only the plugin marker.

The injected RuneLite OkHttp client fetches server-rendered Wiki HTML; a small in-memory markup reader retains the structures needed by the existing semantic normalizer. It does not fetch resources, execute code or expand wikitext/templates. Gson is injected. No API exception or scan suppression is requested.

### Privacy and review boundaries

Requests are limited to the Wiki and its map server. Selected lookups include entity identity/location as needed by Special:Lookup. Skills, quest states, account identifiers, inventory snapshots and credentials are not uploaded. Optional integrations default disabled with server warnings. No telemetry, backend, input injection or gameplay automation.

The minimap replacement implementation has been deleted: no widget hiding/replacement, repositioning, listener takeover or DPS forwarding remains. Please review quest/skill Wiki-action routing, static reference overlays and user-selected map markers. Other plugins' settings remain untouched. Restricted combat-strategy selections cannot be pinned.

### Verification

Submitted source: `fac2bfa8b0877e91fd51c2fbdf0d41a1c1de7957`.

- Beta-5 Java 11 build: 256 tests pass, no failures/errors/skips.
- All 14 semantic reports match beta 4; six map fixtures pass.
- Packaged-code and negative-fixture audits pass, including the new rejection of Widget mutators and client target-selection setters.
- The owner confirmed the beta-5 in-client test.
- [Hosted standard-build CI passed](https://github.com/runelite/plugin-hub/actions/runs/39199247403) on the submitted beta-5 commit. The refreshed reviewbot comment has no API/dependency warnings; approval is a separate maintainer decision.

The owner previously tested reading, routing, pinning, character checks, skill guides and maps in RuneLite, and has now confirmed the beta-5 widget-removal build. Broader multi-user/platform/lifecycle testing remains incomplete. Automated checks do not establish Hub approval.

Limits: no website JavaScript/calculators, automatic underground map switching or persistent pins; unsupported requirements remain unknown. Source/browser fallbacks remain available.

Generated-by: Codex
Generated-by: Claude Code
