package com.jeep.plugin.capacitor.capacitorvideoplayer;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import androidx.media3.cast.CastPlayer;
import androidx.media3.cast.SessionAvailabilityListener;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.Player;
import androidx.media3.ui.PlayerView;
import androidx.mediarouter.app.MediaRouteButton;
import androidx.mediarouter.media.MediaControlIntent;
import androidx.mediarouter.media.MediaRouteSelector;
import androidx.mediarouter.media.MediaRouter;
import com.google.android.gms.cast.framework.CastButtonFactory;
import com.google.android.gms.cast.framework.CastContext;
import com.google.android.gms.cast.framework.CastState;
import com.google.android.gms.cast.framework.CastStateListener;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.jeep.plugin.capacitor.capacitorvideoplayer.Notifications.NotificationCenter;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Cast backend (Chromecast remote playback).
 *
 * <p>This is the ONLY class in the plugin that references {@code media3-cast} and {@code
 * play-services-cast-framework}. It is instantiated via reflection from {@link
 * FullscreenExoPlayerFragment} only after {@code Class.forName("androidx.media3.cast.CastPlayer")}
 * succeeds, so apps that exclude the cast libs (via {@code capacitorVideoPlayerCast=false}) never
 * load (and thus never verify) this class.
 */
public class CastHelper implements CastSupport {

  private static final String TAG = CastHelper.class.getName();

  private final FullscreenExoPlayerFragment fragment;
  private CastContext castContext;
  private CastPlayer castPlayer;
  private MediaItem mediaItem;
  private MediaRouter mRouter;
  private final MediaRouter.Callback mCallback = new EmptyCallback();
  private MediaRouteSelector mSelector;
  private CastStateListener castStateListener = null;
  private boolean isCastSession = false;

  public CastHelper(FullscreenExoPlayerFragment fragment) {
    this.fragment = fragment;
  }

  @Override
  public void initialize() {
    Context context = fragment.getPluginContext();
    MediaRouteButton mediaRouteButton = fragment.getMediaRouteButton();
    if (context == null || mediaRouteButton == null) {
      Log.w(TAG, "initialize: context or route button null, skipping cast init");
      return;
    }
    Executor executor = Executors.newSingleThreadExecutor();
    Task<CastContext> task;
    try {
      task = CastContext.getSharedInstance(context, executor);
    } catch (Exception e) {
      Log.w(TAG, "CastContext.getSharedInstance failed (cast libs missing?)", e);
      hideButton();
      return;
    }

    task.addOnCompleteListener(
        new OnCompleteListener<CastContext>() {
          @Override
          public void onComplete(Task<CastContext> task) {
            if (!fragment.isCastUiAlive()) {
              return;
            }
            if (task.isSuccessful()) {
              try {
                initOnSuccess(task.getResult());
              } catch (NoClassDefFoundError | Exception e) {
                Log.w(TAG, "Cast init failed, hiding cast button", e);
                hideButton();
              }
            } else {
              Exception e = task.getException();
              if (e != null) {
                e.printStackTrace();
              } else {
                Log.e(TAG, "initializeCastService: CastContext task failed with no exception");
              }
              hideButton();
            }
          }
        });
  }

  private void initOnSuccess(CastContext result) {
    Context context = fragment.getPluginContext();
    MediaRouteButton mediaRouteButton = fragment.getMediaRouteButton();
    if (context == null || mediaRouteButton == null) {
      return;
    }
    castContext = result;
    castPlayer = new CastPlayer(castContext);
    mRouter = MediaRouter.getInstance(context);
    mSelector =
        new MediaRouteSelector.Builder()
            .addControlCategories(
                Arrays.asList(
                    MediaControlIntent.CATEGORY_LIVE_AUDIO, MediaControlIntent.CATEGORY_LIVE_VIDEO))
            .build();

    fragment.tintMediaRouteButton(mediaRouteButton);
    if (castContext != null && castContext.getCastState() != CastState.NO_DEVICES_AVAILABLE) {
      mediaRouteButton.setVisibility(View.VISIBLE);
    }

    castStateListener =
        state -> {
          MediaRouteButton btn = fragment.getMediaRouteButton();
          if (btn == null) {
            return;
          }
          if (state == CastState.NO_DEVICES_AVAILABLE) {
            btn.setVisibility(View.GONE);
          } else {
            if (btn.getVisibility() == View.GONE) {
              btn.setVisibility(View.VISIBLE);
            }
          }
        };
    CastButtonFactory.setUpMediaRouteButton(context, mediaRouteButton);

    String title = fragment.title != null ? fragment.title : "";
    String smallTitle = fragment.smallTitle != null ? fragment.smallTitle : "";
    String artwork = fragment.artwork;
    MediaMetadata movieMetadata;
    if (artwork != null && !artwork.trim().isEmpty()) {
      MediaMetadata.Builder metadataBuilder =
          new MediaMetadata.Builder()
              .setTitle(title)
              .setSubtitle(smallTitle)
              .setMediaType(MediaMetadata.MEDIA_TYPE_MOVIE);
      try {
        metadataBuilder.setArtworkUri(Uri.parse(artwork));
      } catch (Exception e) {
        Log.e(TAG, "Invalid artwork uri: " + artwork, e);
      }
      movieMetadata = metadataBuilder.build();
      new SetCastImage().execute();
    } else {
      movieMetadata =
          new MediaMetadata.Builder().setTitle(title).setSubtitle(smallTitle).build();
    }
    String videoPath = fragment.videoPath != null ? fragment.videoPath : "";
    mediaItem =
        new MediaItem.Builder()
            .setUri(videoPath)
            .setMimeType(MimeTypes.VIDEO_UNKNOWN)
            .setMediaMetadata(movieMetadata)
            .build();

    castPlayer.setSessionAvailabilityListener(
        new SessionAvailabilityListener() {
          @Override
          public void onCastSessionAvailable() {
            isCastSession = true;
            androidx.media3.exoplayer.ExoPlayer local = fragment.getLocalPlayer();
            final Long videoPosition = local != null ? local.getCurrentPosition() : 0L;
            ImageButton pipBtn = fragment.getPipButton();
            ImageButton resizeBtn = fragment.getResizeButton();
            ImageView castImage = fragment.getCastImageView();
            PlayerView styledView = fragment.getStyledPlayerView();
            if (pipBtn != null) {
              pipBtn.setVisibility(View.GONE);
            }
            if (resizeBtn != null) {
              resizeBtn.setVisibility(View.GONE);
            }
            if (local != null) {
              local.setPlayWhenReady(false);
            }
            if (castImage != null) {
              castImage.setVisibility(View.VISIBLE);
            }
            if (castPlayer != null) {
              castPlayer.setMediaItem(mediaItem, videoPosition);
            }
            if (styledView != null) {
              styledView.setPlayer(castPlayer);
              styledView.setControllerShowTimeoutMs(0);
              styledView.setControllerHideOnTouch(false);
              // We perform a click because for some weird reason, the layout is black until the
              // user clicks on it
              styledView.performClick();
            }
          }

          @Override
          public void onCastSessionUnavailable() {
            isCastSession = false;
            final Long videoPosition = castPlayer != null ? castPlayer.getCurrentPosition() : 0L;
            androidx.media3.exoplayer.ExoPlayer local = fragment.getLocalPlayer();
            ImageButton pipBtn = fragment.getPipButton();
            ImageButton resizeBtn = fragment.getResizeButton();
            ImageView castImage = fragment.getCastImageView();
            PlayerView styledView = fragment.getStyledPlayerView();
            Boolean pipEnabled = fragment.pipEnabled;
            if (pipBtn != null && Boolean.TRUE.equals(pipEnabled)) {
              pipBtn.setVisibility(View.VISIBLE);
            }
            if (resizeBtn != null) {
              resizeBtn.setVisibility(View.VISIBLE);
            }
            if (castImage != null) {
              castImage.setVisibility(View.GONE);
            }
            if (styledView != null) {
              styledView.setPlayer(local);
            }
            if (local != null) {
              local.setPlayWhenReady(true);
              local.seekTo(videoPosition);
            }
            if (styledView != null) {
              styledView.setControllerShowTimeoutMs(3000);
              styledView.setControllerHideOnTouch(true);
            }
          }
        });

    castPlayer.addListener(
        new Player.Listener() {
          @Override
          public void onPlayerStateChanged(boolean playWhenReady, int state) {
            androidx.media3.exoplayer.ExoPlayer local = fragment.getLocalPlayer();
            Map<String, Object> info =
                new HashMap<String, Object>() {
                  {
                    put("fromPlayerId", fragment.playerId);
                    put(
                        "currentTime",
                        String.valueOf(local != null ? local.getCurrentPosition() / 1000 : 0));
                  }
                };
            switch (state) {
              case CastPlayer.STATE_READY:
                if (castPlayer != null && castPlayer.isPlaying()) {
                  NotificationCenter.defaultCenter().postNotification("playerItemPlay", info);
                } else {
                  NotificationCenter.defaultCenter().postNotification("playerItemPause", info);
                }
                break;
              default:
                break;
            }
          }
        });

    castContext.addCastStateListener(castStateListener);
    if (mRouter != null && mSelector != null && mCallback != null) {
      mRouter.addCallback(mSelector, mCallback, MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY);
    }
  }

  private void hideButton() {
    try {
      MediaRouteButton btn = fragment.getMediaRouteButton();
      if (btn != null) {
        btn.setVisibility(View.GONE);
      }
    } catch (Exception e) {
      Log.w(TAG, "hideButton failed", e);
    }
  }

  @Override
  public boolean isCastSession() {
    return isCastSession;
  }

  @Override
  public boolean isCastSessionAvailable() {
    try {
      return castPlayer != null && castPlayer.isCastSessionAvailable();
    } catch (NoClassDefFoundError | Exception e) {
      return false;
    }
  }

  @Override
  public Player getCastPlayer() {
    return castPlayer;
  }

  @Override
  public void release() {
    if (castPlayer != null) {
      try {
        castPlayer.release();
      } catch (Exception e) {
        Log.e(TAG, "Error releasing cast player", e);
      } finally {
        castPlayer = null;
      }
    }
  }

  @Override
  public void onPauseCleanup() {
    try {
      if (castContext != null && castStateListener != null) {
        castContext.removeCastStateListener(castStateListener);
      }
    } catch (Exception e) {
      Log.w(TAG, "onPauseCleanup failed", e);
    }
  }

  @Override
  public void onDestroyCleanup() {
    try {
      if (mRouter != null) {
        mRouter.removeCallback(mCallback);
      }
    } catch (Exception e) {
      Log.w(TAG, "onDestroyCleanup failed", e);
    }
  }

  /** Loads artwork into the cast overlay image. Moved verbatim from the fragment. */
  private class SetCastImage extends AsyncTask<Void, Void, Bitmap> {

    protected Bitmap doInBackground(Void... params) {
      final String image = fragment.artwork;
      if (image != null && !image.trim().isEmpty()) {
        try {
          URL url = new URL(image);
          HttpURLConnection connection = (HttpURLConnection) url.openConnection();
          connection.setDoInput(true);
          connection.connect();
          InputStream input = connection.getInputStream();
          Bitmap myBitmap = BitmapFactory.decodeStream(input);
          return myBitmap;
        } catch (IOException | IllegalArgumentException e) {
          e.printStackTrace();
          return null;
        }
      } else {
        return null;
      }
    }

    @Override
    protected void onPostExecute(Bitmap result) {
      ImageView castImage = fragment.getCastImageView();
      if (castImage != null) {
        castImage.setImageBitmap(result);
      }
    }
  }

  private static final class EmptyCallback extends MediaRouter.Callback {}
}
