package com.jeep.plugin.capacitor.capacitorvideoplayer.Utilities;

import android.util.Log;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import com.getcapacitor.Bridge;

public class FragmentUtils {

    private static final String TAG = "FragmentUtils";
    private Bridge bridge;

    public FragmentUtils(Bridge bridge) {
        this.bridge = bridge;
    }

    public void loadFragment(Fragment vpFragment, int frameLayoutId) {
        if (vpFragment == null || bridge == null || bridge.getActivity() == null) {
            return;
        }
        try {
            // create a FragmentManager
            FragmentManager fm = bridge.getActivity().getSupportFragmentManager();
            // create a FragmentTransaction to begin the transaction and replace the Fragment
            FragmentTransaction fragmentTransaction = fm.beginTransaction();
            // replace the FrameLayout with new Fragment
            fragmentTransaction.replace(frameLayoutId, vpFragment);
            fragmentTransaction.commitAllowingStateLoss(); // save the changes
        } catch (Exception e) {
            Log.e(TAG, "Error loading fragment", e);
        }
    }

    public void removeFragment(/*VideoPlayerFragmentFullscreenExoPlayer*/Fragment vpFragment) {
        if (vpFragment == null || bridge == null || bridge.getActivity() == null) {
            return;
        }
        try {
            FragmentManager fm = bridge.getActivity().getSupportFragmentManager();
            FragmentTransaction fragmentTransaction = fm.beginTransaction();
            fragmentTransaction.remove(vpFragment);
            fragmentTransaction.commitAllowingStateLoss();
        } catch (Exception e) {
            Log.e(TAG, "Error removing fragment", e);
        }
    }
}
