    package de.uol.neuropsy.recorda.util;

    import android.os.AsyncTask;

    import de.uol.neuropsy.recorda.MainActivity;
    import edu.ucsd.sccn.LSL;

    public class ResolveStreamsTask extends AsyncTask<MainActivity, Integer, LSL.StreamInfo[]> {
        MainActivity parent;
        protected LSL.StreamInfo[] doInBackground(MainActivity... main){
            parent=main[0];
            // runs off the UI thread, so we can afford to wait for slow responders (e.g. hotspot clients)
            return LSL.resolve_streams(3.0);
        }

        @Override
        protected void onPostExecute(LSL.StreamInfo[] results) {
    parent.onStreamRefresh(results);
        }
    }
