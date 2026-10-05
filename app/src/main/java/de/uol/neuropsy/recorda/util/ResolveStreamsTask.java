    package de.uol.neuropsy.recorda.util;

import android.util.Log;

import de.uol.neuropsy.recorda.MainActivity;
import edu.ucsd.sccn.LSL;

public class ResolveStreamsTask {

    private static final String TAG = "ResolveStreamsTask";

    public void execute(final MainActivity activity) {
        new Thread(() -> {
            LSL.StreamInfo[] resolved;
            try {
                resolved = LSL.resolve_streams();
                Log.i(TAG, "Resolved " + resolved.length + " LSL streams");
            } catch (Throwable t) {
                Log.e(TAG, "LSL stream resolution failed", t);
                resolved = new LSL.StreamInfo[0];
            }
            final LSL.StreamInfo[] results = resolved;
            activity.runOnUiThread(() -> activity.onStreamRefresh(results));
        }).start();
    }
}
