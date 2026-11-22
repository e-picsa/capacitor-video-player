# Consumer ProGuard/R8 rules for @picsa/capacitor-video-player.
# Packaged into the AAR via `consumerProguardFiles` so minified apps keep the
# reflection targets used for optional Cast + adaptive streaming.

# CastHelper is instantiated via Class.forName from FullscreenExoPlayerFragment,
# so R8 cannot see the reference. Keep it (and its interface) whole.
-keep class com.jeep.plugin.capacitor.capacitorvideoplayer.CastHelper { *; }
-keep class com.jeep.plugin.capacitor.capacitorvideoplayer.CastSupport { *; }

# Adaptive factories are instantiated via Class.forName in
# FullscreenExoPlayerFragment.createAdaptiveMediaSource. Keep their public
# constructor + factory method so minified release builds keep working.
-keep class androidx.media3.exoplayer.dash.DashMediaSource$Factory {
    public <init>(...);
    public *** createMediaSource(...);
}
-keep class androidx.media3.exoplayer.hls.HlsMediaSource$Factory {
    public <init>(...);
    public *** createMediaSource(...);
}
-keep class androidx.media3.exoplayer.smoothstreaming.SsMediaSource$Factory {
    public <init>(...);
    public *** createMediaSource(...);
}

# These are all optional deps (see capacitorVideoPlayerCast flag / Gradle
# excludes). Silence missing-class warnings when a consumer opts out.
-dontwarn androidx.media3.exoplayer.dash.**
-dontwarn androidx.media3.exoplayer.hls.**
-dontwarn androidx.media3.exoplayer.smoothstreaming.**
-dontwarn androidx.media3.cast.**
-dontwarn com.google.android.gms.cast.**
-dontwarn com.google.android.gms.tasks.**
