# Repository Audit — November 2026

Scope: `@picsa/capacitor-video-player` v8.0.3 (Capacitor 8). This covers the tech stack, risks that affect users, and code structure. Every finding below comes from reading the source. Line references point at the current `main`.

---

## TL;DR

| Area | Verdict |
| --- | --- |
| Release / supply-chain tooling | ✅ Good for a small plugin (release-please, npm provenance, pkg.pr.new previews, conventional PR titles) |
| Android dependency currency | ✅ Media3 1.9, AGP 8.13, SDK 36, Java 21 |
| Android robustness | ⚠️ Recent fixes deal with symptoms. The root causes (a `static` player, the fragment overlay design, the global event bus) are still there |
| Android platform compliance (2026) | ❌ Back handling breaks on Android 16 with targetSdk 36. Background playback has no foreground service. `READ_MEDIA_VIDEO` runs into Play policy |
| iOS | ❌ No Swift Package Manager support (SPM is the Capacitor 8 default). 3 API methods aren't exposed. Marked untested in `example-app/TODO.md` |
| CI / verification | ❌ CI never compiles or tests native code. This matters more than anything else when most changes are AI-written |
| API design | ⚠️ Errors come back as resolved `{result:false}`, everything in the types is optional, and Android sends some fields as the wrong type |
| Code structure | ⚠️ Two god classes (1.7k + 1.3k lines). About 60% of the plugin class is copy-paste. A custom event bus is used where a plain callback would do |

---

## 1. What's good

- **Release pipeline.** release-please → `npm publish --provenance` with `id-token: write` is the 2026 best practice. `pkg-pr-new` previews make it cheap to test a PR inside the real app. A semantic PR title check keeps the changelog clean.
- **Dependencies are current.** The ExoPlayer2 → Media3 migration is done, and Media3, AGP, compile/target SDK 36 and Java 21 are all up to date.
- **Security hardening in [FilesUtils.java](android/src/main/java/com/jeep/plugin/capacitor/capacitorvideoplayer/Utilities/FilesUtils.java).** Capacitor file URLs and `application://` URLs are canonicalised and kept inside the app's own directories, with separator-safe prefix checks. That's a thoughtful fix that most plugins don't have.
- **Regression tests exist.** [FullscreenExoPlayerFragmentTest.java](example-app/android/app/src/androidTest/java/com/getcapacitor/myapp/FullscreenExoPlayerFragmentTest.java) covers the lifecycle-NPE cases. That's the right instinct (see §4 for the gap).
- **Process hygiene.** `AGENTS.md` guardrails, issue/PR templates, a README that is honest about fork status, and a working example app.
- **Fragment lifecycle awareness has improved.** Orphan cleanup in `load()`, null-guarded `onResume`/`onStart`, and a guarded PiP aspect ratio all remove real crash paths.

---

## 2. Stack & platform concerns that affect users (ordered by impact)

### 2.1 🔴 Android back button stops working for apps targeting SDK 36 (Android 16+)
[FullscreenExoPlayerFragment.java#L452-L487](android/src/main/java/com/jeep/plugin/capacitor/capacitorvideoplayer/FullscreenExoPlayerFragment.java#L452-L487) catches back with `View.OnKeyListener` and `KEYCODE_BACK`. When the **host app** targets API 36 (the Capacitor 8 template default), Android 16 enables predictive back and **no longer sends `KEYCODE_BACK`**. The back gesture then goes to Capacitor's own handler (webview history or app exit) while the player overlay stays on screen. `KEYCODE_HOME` is never delivered to apps at all.

**Fix:** register an `OnBackPressedCallback` on `requireActivity().getOnBackPressedDispatcher()` with `getViewLifecycleOwner()`. It's small and can ship on its own.

### 2.2 🔴 The `static` ExoPlayer is the root of the NPE/lifecycle crashes
`private static ExoPlayer player;` ([L135](android/src/main/java/com/jeep/plugin/capacitor/capacitorvideoplayer/FullscreenExoPlayerFragment.java#L135)) and `private static ImageView cast_image;` ([L152](android/src/main/java/com/jeep/plugin/capacitor/capacitorvideoplayer/FullscreenExoPlayerFragment.java#L152)) are shared across every fragment instance and outlive the Activity:
- When a fragment is recreated (rotation, process restore, a second `initPlayer`), the new instance and the old one both own the same player. One releases it while the other is still using it, which is exactly the crash class the last four `fix(android)` releases patched around.
- The static `ImageView` keeps a destroyed Activity alive (memory leak).

The recent guards (`isFragmentDestroyed`, `isPlayerOperationSafe`, main-thread re-posting, try/catch around everything) treat symptoms of this one decision. **Making the player an instance field owned by one lifecycle** removes a whole category of bugs and lets much of that defensive code be deleted.

### 2.3 🔴 Closing PiP kills the whole host app (and PiP architecture trade-offs)
PiP is currently entered on the **host Capacitor Activity** (`BridgeActivity`), meaning the entire Activity—WebView and all—shrinks into the floating PiP window with `FullscreenExoPlayerFragment` covering it. Furthermore, in [`onStop`](android/src/main/java/com/jeep/plugin/capacitor/capacitorvideoplayer/FullscreenExoPlayerFragment.java#L762-L776), being in PiP mode calls `activity.finishAndRemoveTask()`. When a user swipes away the PiP window, the app's main activity and its task go with it, losing any unsaved app state.

To support **browsing the app while a video plays in PiP**, **launching a new video while already in PiP** (replacing or reusing the active player), and **streaming playback events/progress back to the Capacitor JS layer while in PiP**, there are two architectural options:

#### Option A: Dedicated `PlayerActivity` (`singleTask` / `supportsPictureInPicture="true"`) + Event Bridge
Fullscreen playback runs in its own `PlayerActivity` rather than a Fragment overlaid on `BridgeActivity`.
- **How it works:**
  - When the user enters PiP, only `PlayerActivity` shrinks into the floating PiP window. `BridgeActivity` (the Capacitor WebView) remains full-screen underneath so the user can keep using the app.
  - Because `PlayerActivity` is declared with `android:launchMode="singleTask"` (or `singleTop`), calling `initPlayer()` from JS while `PlayerActivity` is already in PiP delivers `onNewIntent(Intent)` to the existing `PlayerActivity` instance, which simply swaps the `MediaItem` on the existing `ExoPlayer` (or resets it) and optionally expands back to fullscreen.
  - A lightweight shared `PlayerEventBridge` (singleton or bound `MediaSessionService`) forwards playback state/progress events (`Play`, `Pause`, `Ended`, `Exit`, time updates) from `PlayerActivity` back to `CapacitorVideoPlayerPlugin` -> JS `notifyListeners(...)`.
- **Pros:**
  - **Only way to allow in-app browsing during PiP.** On Android, PiP operates at the `Activity` level. If the player is a Fragment inside `BridgeActivity`, the entire `BridgeActivity` goes into the tiny PiP window—the user *cannot* use the Capacitor app while watching in PiP unless the player is in a separate `PlayerActivity`.
  - Cleanly isolates orientation locking, fullscreen window insets, and back-button handling from the host WebView.
  - Dismissing the PiP window only finishes `PlayerActivity`; `BridgeActivity` is untouched.
- **Cons:**
  - Launching `PlayerActivity` pauses `BridgeActivity` while fullscreen, which fires Capacitor `appStateChange` (`isActive: false`) in JS. (Once `PlayerActivity` enters PiP and the user taps back into the app, `BridgeActivity` resumes).
  - Requires a small static/singleton callback bridge between the plugin and `PlayerActivity` (though this is much simpler and safer than the current `NotificationCenter`).

#### Option B: Keep `FullscreenExoPlayerFragment` in `BridgeActivity` (Fix lifecycle & remove `finishAndRemoveTask()`)
Keep the single-Activity Fragment overlay approach, remove `activity.finishAndRemoveTask()`, pass config via `Fragment.setArguments(Bundle)`, and allow `initPlayer()` to replace the active media item or swap the Fragment cleanly.
- **Pros:**
  - Zero cross-Activity communication needed; the Plugin holds a direct reference to the Fragment and its `PlayerEventListener`.
  - Never pauses `BridgeActivity` when opening the fullscreen player.
  - Smaller refactor from the current codebase.
- **Cons:**
  - **Users cannot interact with the Capacitor app while in PiP.** Because `BridgeActivity` itself is the Activity in PiP mode, shrinking to PiP sends the whole app to the home screen / over other apps. Tapping the PiP window restores the fullscreen video, not the Capacitor WebView underneath.

> [!IMPORTANT]
> **Recommendation:**
> - **Immediate fix (Phase 2):** Delete `activity.finishAndRemoveTask()` in `FullscreenExoPlayerFragment.onStop()`, and update `CapacitorVideoPlayerPlugin.initPlayer()` so that if an existing player fragment is already active (including in PiP), it cleanly stops/removes the old fragment (or loads the new URI into the existing player) instead of failing with `"FrameLayout for ExoPlayer already exists"` ([Plugin L1209](android/src/main/java/com/jeep/plugin/capacitor/capacitorvideoplayer/CapacitorVideoPlayerPlugin.java#L1209)).
> - **Target architecture (Phase 3):** If your users need to **navigate the Capacitor app while a video continues in PiP** (which your requirements—"whilst in PiP mode the user tries to launch a different video" and "track video playback progress events in the app whilst in PiP"—directly imply), **you must use Option A (Dedicated `PlayerActivity`)**. On Android, a single Activity cannot be both in PiP mode and full-screen interactive at the same time.

### 2.4 🟠 Background mode doesn't work as intended, and isn't compliant
- [`isApplicationSentToBackground`](android/src/main/java/com/jeep/plugin/capacitor/capacitorvideoplayer/FullscreenExoPlayerFragment.java#L1550-L1562) returns `true` whenever the app's own process is in `getRunningAppProcesses()`, and it always is. So with `bkmodeEnabled` (default `true`), `onPause` **never pauses**. That includes when a phone call or the notification shade takes focus.
- Real background playback on Android 14+ needs a `MediaSessionService` with `foregroundServiceType="mediaPlayback"` and a media notification. Without one, the OS kills playback unpredictably and the user gets no lock-screen or notification controls. Today there is a `MediaSession` but no service.

**Fix:** fix/remove `isApplicationSentToBackground` and default `bkmodeEnabled` to `false` immediately; later use Media3 `MediaSessionService` if true background audio playback is needed, or remove background mode and document PiP as the supported multitasking mode.

### 2.5 🟠 Remove the `url: "internal"` Video Picker and `READ_MEDIA_VIDEO` permissions
The `url: "internal"` path requests `READ_MEDIA_VIDEO` (and `READ_EXTERNAL_STORAGE`) and opens a custom MediaStore grid picker ([PickerVideo/](android/src/main/java/com/jeep/plugin/capacitor/capacitorvideoplayer/PickerVideo/)).
- Google Play's photo/video permissions policy restricts `READ_MEDIA_VIDEO` to apps whose core purpose requires broad media library access. Simply declaring these permissions in the plugin can trigger Play Console policy declarations or rejections for consumer apps—even if they never use `url: "internal"`.
- The permission prompt is also triggered by a fragile substring check: `url.contains("DCIM") || url.contains("Documents")` ([Plugin L247](android/src/main/java/com/jeep/plugin/capacitor/capacitorvideoplayer/CapacitorVideoPlayerPlugin.java#L247)), and `loadVideos()` queries MediaStore synchronously on the main thread (ANR risk).

**Decision:** Drop support for `url: "internal"` and remove the built-in video picker altogether (while keeping full support for local app files and remote HTTP/HLS/DASH streaming).
- **What gets deleted:** The entire `PickerVideo/` package (4 classes), `fragment_picker_video.xml`, `row_video.xml`, the `VideoPicker/` classes on iOS, the `READ_MEDIA_VIDEO` / `READ_EXTERNAL_STORAGE` `@Permission` annotations in `CapacitorVideoPlayerPlugin`, and the `com.squareup.picasso:picasso`, `recyclerview`, and `cardview` dependencies.
- **Keep local file & URI playback working:** Update `FilesUtils.getFilePath()` so that any `content://` or `file://` URI passed from `@capacitor/filesystem` or an external picker plugin is passed through to Media3 `DefaultDataSource` (while keeping the canonical path traversal checks for `_capacitor_file_` and `application://` paths).
- **Migration note:** Document the removal of `url: "internal"` clearly in `CHANGELOG.md` and `README.md` as a breaking change for users migrating from `harmonwood/capacitor-video-player`.

### 2.6 🟠 iOS: no Swift Package Manager support, and missing methods
- There is no `Package.swift`, and the plugin registers via the legacy `CAP_PLUGIN` macro in [CapacitorVideoPlayerPlugin.m](ios/Plugin/CapacitorVideoPlayerPlugin.m) instead of `CAPBridgedPlugin`. **New Capacitor 8 iOS projects use SPM by default**, so the plugin can't be installed in them without falling back to CocoaPods.
- The `.m` file doesn't register `showController`, `isControllerIsFullyVisible` or `exitPlayer`, and Swift doesn't implement them either. They are in `definitions.ts` but will reject with "not implemented" on iOS.
- `s.swift_version = '5.1'` in the podspec is very out of date. iOS state is spread across globals (`isPIPModeAvailable`, `isInPIPMode`, `isInBackgroundMode`). `example-app/TODO.md` still says "Setup and test iOS".

### 2.7 🟡 Smaller user-facing issues
- **Debug toasts & deprecated Media3 listener:** "Device is a TV" ([L413](android/src/main/java/com/jeep/plugin/capacitor/capacitorvideoplayer/FullscreenExoPlayerFragment.java#L413)) shows to end users. The player listener overrides the deprecated `onPlayerStateChanged(boolean playWhenReady, int state)` ([L314](android/src/main/java/com/jeep/plugin/capacitor/capacitorvideoplayer/FullscreenExoPlayerFragment.java#L314)) instead of `onPlaybackStateChanged` + `onPlayWhenReadyChanged` + `onPlayerError`, and shows "Video Url not found" on *any* `STATE_IDLE` ([L327-L331](android/src/main/java/com/jeep/plugin/capacitor/capacitorvideoplayer/FullscreenExoPlayerFragment.java#L327-L331)), so real playback errors are never reported to JS with a cause. All toasts are hard-coded English.
- **Credentials can leak into logcat:** `Log.v(TAG, "headers: " + headers)` ([Plugin L241](android/src/main/java/com/jeep/plugin/capacitor/capacitorvideoplayer/CapacitorVideoPlayerPlugin.java#L241)) logs request headers, which often carry `Authorization` tokens.
- **`content://` URIs** that aren't `content://media` return `null` from `FilesUtils.getFilePath`. Files picked via SAF or `@capacitor/filesystem` content URIs fail with "file not found".
- **Fullscreen and orientation:** `FLAG_FULLSCREEN` and `SYSTEM_UI_FLAG_*` are deprecated. Use `WindowInsetsControllerCompat`, especially with Android 15+ edge-to-edge enforcement. On Android 16, `setRequestedOrientation` is **ignored on large screens (≥600dp)**, so `displayMode: landscape|portrait` silently does nothing on tablets and foldables.
- **APK size and devices without Google services:** `media3-cast` (and so Play Services Cast), DASH, SmoothStreaming and Picasso are always bundled, and `chromecast` defaults to `true`. If your users are on low-end devices or bandwidth-limited installs, a Gradle flag to leave cast out would be a meaningful saving.
- **TV D-pad fast-forward never works.** `mDuration` is declared but never assigned, so `position < mDuration - videoStep` ([L1249](android/src/main/java/com/jeep/plugin/capacitor/capacitorvideoplayer/FullscreenExoPlayerFragment.java#L1249)) is always false.
- **Playback position is lost on recreation.** `onSaveInstanceState` saves `PLAYBACK_TIME` and it's read back into `mCurrentPosition` ([L441](android/src/main/java/com/jeep/plugin/capacitor/capacitorvideoplayer/FullscreenExoPlayerFragment.java#L441)), but that value is never used, so the video restarts from 0.
- **Threads and deprecated APIs:** `AsyncTask` (deprecated) is used for cast artwork. Each fragment creates a `newSingleThreadExecutor()` that is never shut down. `artwork != ""` compares by reference ([L534](android/src/main/java/com/jeep/plugin/capacitor/capacitorvideoplayer/FullscreenExoPlayerFragment.java#L534), [L1608](android/src/main/java/com/jeep/plugin/capacitor/capacitorvideoplayer/FullscreenExoPlayerFragment.java#L1608)).

---

## 3. API / TypeScript surface

- **Errors are resolved, not rejected.** Every failure is `call.resolve({result:false, message})`. Consumers have to remember to check `result`, and `try/catch` never fires. That doesn't match Capacitor conventions.
- **The types are loose.** All options are optional, including required ones like `playerId`, `url` and `mode`. `mode` and `displayMode` are `string`, not unions. The result type is `value?: any`.
- **Android sends the wrong types.** In play/pause/ready/ended events, Android puts `currentTime` as a **string** (`String.valueOf(...)`), but it's typed `number`, and `playerExit` sends an int. `dismiss` is passed around as the string `"1"` and parsed with `Integer.valueOf`.
- **`playerId` is ceremony.** Native code only supports one fullscreen player, and `playerId` mostly guards against typos. `echo` is template leftover and ships in the public API.
- A good candidate for a **v9 breaking cleanup**: reject on error, a typed result per method, string-literal unions, drop `echo`, and decide whether `playerId` means anything on native.
- `hls.js` is a **required** peer dependency, even for native-only users. Mark it optional via `peerDependenciesMeta`.

---

## 4. Tooling & the AI-assisted workflow

Shipping fast with AI-written changes only works if **the machine checks what the reviewer can't**. Right now:

- CI ([.github/workflows](.github/workflows)) only runs `npm run build` (TS) on PRs. **No Gradle build, no Android lint, no `xcodebuild`, no instrumented tests.** The lifecycle tests you wrote never run automatically. `npm test` is `echo "No test specified"`.
- `lintOptions { abortOnError = false }` (deprecated DSL) means Android Lint findings are never surfaced.
- ESLint 8 is end-of-life (legacy `.eslintignore` and `eslintConfig`), and `npm run lint` includes `swiftlint`, which isn't run anywhere.
- `ios/Pods/` and `android/.idea/` are committed. `docs/` has stale material (`Usage_2.4.7.md`, `Notes_Exoplayer2.18.1.md`), and `homepage` points at the old upstream site.

**Recommended CI (highest ROI item in this report):**
1. `npm ci && npm run lint && npm run build` (ubuntu)
2. `cd example-app && npm run build && cd android && ./gradlew lint assembleDebug` (ubuntu, JDK 21)
3. Android instrumented tests on an emulator (`reactivecircus/android-emulator-runner`) running `FullscreenExoPlayerFragmentTest`, ideally including an API 36 image
4. `xcodebuild` for the plugin (macos runner), plus an SPM build once `Package.swift` exists

With this in place, every agent-written PR gets a real compile-and-lifecycle check before you read it.

---

## 5. Code structure: separation, over- and under-engineering

### Under-engineered (missing structure)
- **The god fragment.** [FullscreenExoPlayerFragment.java](android/src/main/java/com/jeep/plugin/capacitor/capacitorvideoplayer/FullscreenExoPlayerFragment.java) (1,734 lines) handles UI binding, player setup, media-source selection by URL sniffing, subtitles, Cast, PiP, MediaSession, background detection, TV key handling and colour parsing. A natural split would be `PlayerController` (ExoPlayer and media sources), `CastController`, `PipController`, and a thin view layer.
- **A 21-argument config pipeline.** Options are parsed into ~25 mutable fields on the plugin, passed as **21 positional arguments** through `createFullScreenFragment` (twice) and [CapacitorVideoPlayer.java](android/src/main/java/com/jeep/plugin/capacitor/capacitorvideoplayer/CapacitorVideoPlayer.java), then assigned to **public mutable fields** on the fragment. A single `PlayerConfig` value object parsed once and passed via `Bundle`/Intent args would:
  - remove a class of argument-order bugs,
  - survive process death properly, so the "orphaned fragment" cleanup becomes unnecessary,
  - make the config testable on its own.
- **Shared mutable state across calls.** `private final JSObject ret` ([Plugin L84](android/src/main/java/com/jeep/plugin/capacitor/capacitorvideoplayer/CapacitorVideoPlayerPlugin.java#L84)) is reused across `initPlayer` calls, so stale `message` keys leak between calls. `this.call = call` is overwritten by *every* method, and the internal-picker path later resolves whichever call happened to be stored last ([L1109](android/src/main/java/com/jeep/plugin/capacitor/capacitorvideoplayer/CapacitorVideoPlayerPlugin.java#L1109)).
- **Java without nullability annotations.** Kotlin is the norm for new Android code in 2026, and its null-safety would have caught most of the NPEs fixed in 8.0.1–8.0.3. A full rewrite isn't worth it on its own, but write *new* classes in Kotlin, or at minimum add `@NonNull/@Nullable`, and convert the fragment as part of the restructure above.

### Over-engineered (more machinery than needed)
- **A custom `NotificationCenter` event bus** ([Notifications/](android/src/main/java/com/jeep/plugin/capacitor/capacitorvideoplayer/Notifications/)). It's a port of the iOS pattern to Android: a global singleton with string event names, untyped `Map<String,Object>` payloads, and a `MyRunnable` that stores its payload in a mutable field (not re-entrant). It serves one producer (the fragment) and one consumer (the plugin). Observers are added again on every `initPlayer` and only cleared on end/dismiss, so duplicate events are possible, and the singleton holds onto the plugin. **Replace it with a `PlayerEventListener` interface** that the plugin implements and passes to the fragment/activity.
- **Defensive layering.** `play()`, `pause()` and `releasePlayer()` each check `isFragmentDestroyed`, check the thread and re-post to a main handler, then wrap everything in try/catch. Every caller is already on the main thread (`runOnUiThread` in the plugin). This is the visible residue of repeated AI "add a guard" fixes. Once the player has a single owner (§2.2), most of this can go.
- **The pass-through `CapacitorVideoPlayer` "implementation" class** only copies arguments onto the fragment. Either give it a real role (owning player state, as with the Capacitor template's intent) or remove it.
- **Copy-paste method bodies.** 17 `@PluginMethod`s ([L269-L900](android/src/main/java/com/jeep/plugin/capacitor/capacitorvideoplayer/CapacitorVideoPlayerPlugin.java#L269-L900)) repeat the same ~40-line validate-playerId / runOnUiThread / null-check / resolve pattern. A helper like `withPlayer(call, "play", f -> { f.play(); return true; })` would cut ~600 lines to ~100, and the next fix happens in one place instead of 17.

### Dead code (safe deletions)
- `res/layout/exoplayer_layout_youtube.xml` (361 lines) and `res/layout/bridge_layout_main.xml` aren't referenced anywhere.
- The `androidx.gridlayout` and `androidx.coordinatorlayout` dependencies are unused (coordinator is only used in the dead layout above). `recyclerview` 1.2.0 and `constraintlayout` 2.0.4 are several years old.
- Unused or write-only fields (`isEnded`, `visibilityListener`, `mDuration` and `mCurrentPosition`, which are both bugs, see §2.7), the dead `Build.VERSION_CODES.LOLLIPOP`/`N`/`SDK_INT < 24` branches (minSdk is 24), and commented-out orientation code.

---

## 6. Phased Roadmap

### Phase 1: Automated Safety Net (Highest ROI for AI-assisted cadence)
| # | Change | Effort | Impact |
| --- | --- | --- | --- |
| 1 | **Native CI:** GitHub Actions workflow running TS lint/build, Android `./gradlew lint assembleDebug`, Android instrumented tests (`FullscreenExoPlayerFragmentTest`), and iOS `xcodebuild` on every PR | S | Prevents regressions on all subsequent PRs |

### Phase 2: High-Impact Android Stability & Compliance Wins (Small, surgical PRs)
| # | Change | Effort | Impact |
| --- | --- | --- | --- |
| 2 | **Make `ExoPlayer` and `cast_image` instance fields:** Remove `static` from `player` and `cast_image`, ensure `releasePlayer()` runs cleanly in `onDestroy()`, and strip redundant thread-hopping guards | S–M | Eliminates the root cause of recreation/multi-init NPE crashes and memory leaks |
| 3 | **Android 16 (SDK 36) Back Navigation:** Replace `View.OnKeyListener` (`KEYCODE_BACK`) with `OnBackPressedCallback` | S | Fixes broken back gesture on Android 16+ |
| 4 | **PiP & Background hotfixes:** Remove `activity.finishAndRemoveTask()` in `onStop()`, allow `initPlayer()` to replace an already-active player instead of failing, and fix/remove `isApplicationSentToBackground()` | S | Stops PiP close from killing the host app; fixes player never pausing on focus loss |
| 5 | **Media3 listener & log/toast cleanup:** Migrate to `onPlaybackStateChanged` + `onPlayerError`, remove `"Device is a TV"` toast, stop logging `headers` to logcat, and fix `mDuration` / `mCurrentPosition` | S | Proper error reporting, credential hygiene, working TV fast-forward and state restore |

### Phase 3: Structural Cleanup & PiP Architecture
| # | Change | Effort | Impact |
| --- | --- | --- | --- |
| 6 | **Remove `url: "internal"` video picker & media permissions:** Delete `PickerVideo/` (Android) and `VideoPicker/` (iOS), remove `READ_MEDIA_VIDEO` / `READ_EXTERNAL_STORAGE` permissions and Picasso/RecyclerView/CardView deps, allow standard `content://` URIs in `FilesUtils`, and document the breaking change for upstream migrators | S–M | Eliminates Google Play policy rejections, ANR risk, and dead dependencies |
| 7 | **Deduplicate plugin & replace `NotificationCenter`:** Collapse the 17 copy-pasted `@PluginMethod` wrappers into a shared helper, stop sharing `this.ret` / `this.call` across calls, introduce an immutable `PlayerConfig` (`Bundle`/`Parcelable`), and replace `NotificationCenter` with a typed `PlayerEventListener` + periodic progress callback | M | Cuts ~600 lines of boilerplate; enables reliable playback progress tracking in JS |
| 8 | **Dedicated `PlayerActivity` (`singleTask`, `supportsPictureInPicture`):** Move fullscreen playback from an overlay Fragment to `PlayerActivity` with `onNewIntent` media swapping (for launching a new video while in PiP) and a `PlayerEventBridge` back to the plugin | L | Enables browsing the Capacitor app while watching in PiP, seamless video replacement in PiP, and clean process-death recovery |

### Phase 4: iOS Parity, Optional Features & v9 API Cleanup
| # | Change | Effort | Impact |
| --- | --- | --- | --- |
| 9 | **iOS modernization:** Add `Package.swift` + `CAPBridgedPlugin` for Capacitor 8 SPM support, implement the 3 missing methods (`showController`, `isControllerIsFullyVisible`, `exitPlayer`), and verify in CI | M | Unblocks new Capacitor 8 iOS apps using SPM |
| 10 | **v9 API cleanup & optional background service:** Reject promises on error instead of resolving `{result:false}`, fix Android `currentTime` string-vs-number mismatch, tighten TS option types, drop `echo`, and optionally add a Media3 `MediaSessionService` if background audio is needed | M | Clean, predictable TypeScript DX |
