package com.getcapacitor.myapp;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.jeep.plugin.capacitor.capacitorvideoplayer.FullscreenExoPlayerFragment;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class FullscreenExoPlayerFragmentTest {

  @Test
  public void onResumeWithoutPlayerViewDoesNotCrash() {
    InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
      FullscreenExoPlayerFragment fragment = new FullscreenExoPlayerFragment();
      fragment.onResume();
    });
  }
}
