# Repo context — owned by this repo

Seeded once by the `repo-templates` sync; afterwards this file is owned by this
repo. Future syncs ignore it. Fill it in so audits ground findings in real
users and explicit scope instead of generic assumptions.

## Project summary

`@picsa/capacitor-video-player`: custom native Capacitor plugin (web, Android
Java, iOS Swift) that plays video, forked from the abandoned
`harmonwood/capacitor-video-player`. Actively modernised with AI-assisted
development; recent releases fixed frequent Android null/lifecycle crashes.
Baseline audit exists at `AUDIT.md` (November 2026) — new audits reconcile
against it.

## User archetypes

- App dev integrating video into offline-first rural field apps (e.g. PICSA agricultural extension); broken means crash/ANR or killed host app on low-end Android, especially around PiP and backgrounding.
- End user watching training video on old or low-bandwidth devices; broken means stalls, lost playback position, battery drain, or unplayable formats.
- Maintainer triaging AI-assisted PRs; broken means unclear bridge contracts, unreproducible native crashes, and CI that never compiles native code.

## Tech focus areas

- Android fragment/player lifecycle and teardown (static player removal, PiP `finishAndRemoveTask`, back-press handling on API 36+).
- PiP architecture trade-offs (fragment overlay vs dedicated `PlayerActivity`) and background playback compliance (`MediaSessionService`).
- Bridge typing: resolved-not-rejected errors, string-vs-number mismatches, optional-only options.
- iOS parity: SPM support, unregistered methods, stale Swift version.
- CI that compiles and tests native code on every PR.

## Scope overrides

- Emphasise: Android lifecycle/teardown and PiP architecture; bridge API typing; iOS SPM parity.
- Skip: none.
- Replace: none (use the skill's P0–P2 severity).

## Second-opinion preference

- Ask each time (no standing reviewer agreement yet).

## Audit file

- `AUDIT.md` at repo root (November 2026 baseline already committed; reconcile, never duplicate).

## Out of scope

- `dist/`, `build/`, lockfiles, vendored code (mention only if the pipeline is broken).
- Upstream `harmonwood/capacitor-video-player` history except where migration notes affect users.

## Known pain / history

- Fork of an abandoned upstream; all work targets this repo only.
- v8.0.x releases patched recurring NPE/lifecycle crashes with guards; root causes (static player, fragment overlay, global event bus) tracked in `AUDIT.md` §2.
- `url: "internal"` picker removal and v9 API cleanup are accepted breaking-change directions.
