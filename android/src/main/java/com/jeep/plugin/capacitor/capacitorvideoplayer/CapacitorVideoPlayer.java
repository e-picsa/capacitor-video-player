package com.jeep.plugin.capacitor.capacitorvideoplayer;

import android.content.Context;
import com.getcapacitor.JSObject;
import com.jeep.plugin.capacitor.capacitorvideoplayer.PickerVideo.PickerVideoFragment;

public class CapacitorVideoPlayer {

    private final Context context;

    CapacitorVideoPlayer(Context context) {
        this.context = context;
    }

    public String echo(String value) {
        return value;
    }

    public FullscreenExoPlayerFragment createFullScreenFragment(
        String videoPath,
        Float videoRate,
        Boolean exitOnEnd,
        Boolean loopOnEnd,
        Boolean pipEnabled,
        Boolean bkModeEnabled,
        Boolean showControls,
        String displayMode,
        String subTitle,
        String language,
        JSObject subTitleOptions,
        JSObject headers,
        String title,
        String smallTitle,
        String accentColor,
        Boolean chromecast,
        String artwork,
        Boolean isTV,
        String playerId,
        Boolean isInternal,
        Long videoId
    ) {
        FullscreenExoPlayerFragment fsFragment = new FullscreenExoPlayerFragment();

        fsFragment.videoPath = videoPath != null ? videoPath : "";
        fsFragment.videoRate = videoRate != null ? videoRate : 1.0f;
        fsFragment.exitOnEnd = exitOnEnd != null ? exitOnEnd : true;
        fsFragment.loopOnEnd = loopOnEnd != null ? loopOnEnd : false;
        fsFragment.pipEnabled = pipEnabled != null ? pipEnabled : true;
        fsFragment.bkModeEnabled = bkModeEnabled != null ? bkModeEnabled : true;
        fsFragment.showControls = showControls != null ? showControls : true;
        fsFragment.displayMode = displayMode != null ? displayMode : "all";
        fsFragment.subTitle = subTitle != null ? subTitle : "";
        fsFragment.language = language != null ? language : "";
        fsFragment.subTitleOptions = subTitleOptions != null ? subTitleOptions : new JSObject();
        fsFragment.headers = headers != null ? headers : new JSObject();
        fsFragment.title = title != null ? title : "";
        fsFragment.smallTitle = smallTitle != null ? smallTitle : "";
        fsFragment.accentColor = accentColor != null ? accentColor : "";
        fsFragment.chromecast = chromecast != null ? chromecast : true;
        fsFragment.artwork = artwork != null ? artwork : "";
        fsFragment.isTV = isTV != null ? isTV : false;
        fsFragment.playerId = playerId != null ? playerId : "";
        fsFragment.isInternal = isInternal != null ? isInternal : false;
        fsFragment.videoId = videoId != null ? videoId : 0L;
        return fsFragment;
    }

    public PickerVideoFragment createPickerVideoFragment() {
        return new PickerVideoFragment();
    }
}
