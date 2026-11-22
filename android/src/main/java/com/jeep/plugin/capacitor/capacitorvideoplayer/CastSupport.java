package com.jeep.plugin.capacitor.capacitorvideoplayer;

import androidx.media3.common.Player;

/**
 * Cast integration (Chromecast remote playback).
 *
 * <p>Implemented by {@link CastHelper}, which is the <b>only</b> class in this plugin that directly
 * references {@code androidx.media3:media3-cast} and
 * {@code com.google.android.gms:play-services-cast-framework}. It is instantiated via reflection from
 * {@link FullscreenExoPlayerFragment} only when:
 *
 * <ul>
 *   <li>{@code chromecast == true}, and
 *   <li>{@code Class.forName("androidx.media3.cast.CastPlayer")} succeeds (i.e. the cast libraries
 *       are present on the runtime classpath).
 * </ul>
 *
 * <p>This keeps the main fragment free of class-verification references to cast classes, so apps
 * built with {@code capacitorVideoPlayerCast=false} do not hit {@link NoClassDefFoundError} at
 * class-load time. When cast libs are absent the fragment simply hides the cast button and plays
 * locally.
 */
public interface CastSupport {
  /** Async init: resolves CastContext, builds CastPlayer, wires route button + session listeners. */
  void initialize();

  /** True while a cast session is active (remote playback). */
  boolean isCastSession();

  /** True when a cast device is available for launching (used to skip local resume in onStart). */
  boolean isCastSessionAvailable();

  /** Exposes the remote player as a plain media3 {@link Player} (no cast types leak to caller). */
  Player getCastPlayer();

  /** Release the remote player. Safe to call when never initialized. */
  void release();

  /** Mirrors old onPause cleanup: remove CastStateListener. */
  void onPauseCleanup();

  /** Mirrors old onDestroy cleanup: remove MediaRouter callback. */
  void onDestroyCleanup();
}
