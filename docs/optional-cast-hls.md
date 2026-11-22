# Optional Cast + HLS (8.1.0 safe default, 9.0.0 lean default)

Cast (Chromecast) and HLS are now **optional but ON by default** so unaware upgraders see zero
change. Lean consumers (picsa-apps) opt out with one line. Planned 9.0.0 flips Cast to lean-by-default.

## Android

### What changed

- `android/build.gradle`:
  - Cast defaults to `implementation` (same as 8.0.x — zero break).
  - New opt-out flag: `capacitorVideoPlayerCast=false` switches cast to `compileOnly`.
    Set it in `android/gradle.properties`, root `ext`, or `-PcapacitorVideoPlayerCast=false`.
  - `androidx.mediarouter:mediarouter` → explicit `implementation` (small; keeps the
    `MediaRouteButton` in the layout inflatable when cast is absent — button is set to `GONE`).
  - HLS/DASH/SS (`media3-exoplayer-hls/dash/smoothstreaming`) stay `implementation` by default,
    but `buildHttpMediaSource()` now instantiates them via reflection with a progressive fallback,
    so they can be excluded without `NoClassDefFoundError`.
- `FullscreenExoPlayerFragment.java` no longer imports any `media3.cast` / `gms.cast` types.
  All cast work moved to `CastHelper.java` (implements `CastSupport.java`), loaded via
  `Class.forName("androidx.media3.cast.CastPlayer")` only when `chromecast:true` **and** the libs
  are present. When absent: cast button hidden, local ExoPlayer plays, no crash (fixes the old
  `CastPlayer.release()` NPE path that forced picsa-apps to hardcode `chromecast:false`).
- `CastHelper` is the **only** class referencing cast types. Excluding cast libs never
  triggers class-verification failure in the fragment.

### For consumers that DON'T use cast (picsa-apps case) — 1 line

In `android/gradle.properties`:

```properties
capacitorVideoPlayerCast=false
```

Then drop any `play-services-cast-framework` pin / `DefaultCastOptionsProvider` manifest entry —
the transitive dep is gone. `chromecast:false` (or default `true` with libs absent — both hide the
button) plays MP4/progressive locally.

### For consumers that DO use cast — zero action on 8.x

Do nothing. Default `capacitorVideoPlayerCast=true` preserves 8.0.x behavior exactly.
Keep passing `chromecast:true` (default), `title`/`smallTitle`/`artwork` as before.
No JS/TS API change.

Planned 9.0.0 (breaking): default flips to `false`. Cast users will then add:
```gradle
dependencies {
    implementation 'androidx.media3:media3-cast:1.9.1'
    implementation 'com.google.android.gms:play-services-cast-framework:21.5.0'
}
```
or set `capacitorVideoPlayerCast=true`. If forgotten: no crash — button hides, local playback
(logcat `Cast libraries not present`).

### For consumers that DON'T use HLS/DASH/SS on Android

- Default still includes them — no change.
- To also drop them (MP4-only APK), exclude in app `build.gradle`:

```gradle
configurations.all {
    exclude group: 'androidx.media3', module: 'media3-exoplayer-hls'
    exclude group: 'androidx.media3', module: 'media3-exoplayer-dash'
    exclude group: 'androidx.media3', module: 'media3-exoplayer-smoothstreaming'
}
```

  `m3u8`/`mpd`/`ism` URLs then fall back to progressive (will not play adaptively — expected).

## Web

### What changed

- `src/web-utils/videoplayer.ts`: removed static `import Hls from 'hls.js'`. HLS lib is loaded via
  `await import('hls.js')` **only** when `_videoType === 'application/x-mpegURL'` and native HLS
  (`canPlayType('application/vnd.apple.mpegurl')`) is unavailable.
- `package.json`: `hls.js` stays as peer (`^1.4.0`) but is now marked
  `peerDependenciesMeta.hls.js.optional=true`. `devDependencies` keeps it for local HLS testing.
- `rollup.config.mjs`: unchanged (`hls.js` external). ESM output no longer has a static hls import;
  `dist/plugin.js` (IIFE) likewise drops the `Hls` global param and uses dynamic import + `window.Hls`
  fallback for script-tag users.

Result: non-HLS consumers (plain MP4, blob: URLs — picsa-apps web player) never download the ~1.3MB
`hls.js` (~523KB lazy chunk / ~140KB deflated in the old APK audit disappears).

### For consumers that DON'T use HLS

- Nothing to do. MP4/webm/etc. never trigger the dynamic import. You may uninstall `hls.js`
  entirely — npm will not warn (optional peer).

### For consumers that DO use HLS — zero action if already installed

- Keep `hls.js@^1.4.0` (you already have it as a peer). No API change.
- Safari/iOS with native HLS plays without `hls.js` even if uninstalled.
- Chrome/Firefox **without** `hls.js` installed: `.m3u8` init now resolves `false` with console error
  `[VideoPlayer] HLS (.m3u8) requires the optional hls.js peer dependency` instead of a bundle-time
  crash. Install the peer to restore playback.
- Script-tag (`plugin.js`) users: either `npm i hls.js` (bundler resolves dynamic import) or load
  hls.js via CDN before the plugin so `window.Hls` exists.

## Verification

- `npm run build` (tsc + rollup + docgen): passes. `dist/esm/web-utils/videoplayer.js` has no static
  `from 'hls.js'` — only `await import('hls.js')`.
- Android: full `./gradlew assembleRelease` requires JDK 21 (repo sets `sourceCompatibility 21`);
  this environment only has JDK 17 so a full APK build was not possible here. Verified instead:
  fragment has zero direct references to cast/DASH/HLS/SS types (only string literals + reflection),
  `CastHelper` isolates all cast imports. Default `wantCast=true` resolves the same `implementation`
  graph as 8.0.x; `capacitorVideoPlayerCast=false` resolves to `compileOnly`. CI
  (`verify:android`) must build both modes before release.
- No JS API change (`chromecast`, `artwork`, `title` etc. unchanged). Behavior for
  `chromecast:false` and MP4 is identical; `chromecast:true` without libs degrades to local playback
  instead of NPE/crash.
