/**
 * Copyright 2015 Edmund Higham. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.opentt.rideout;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.google.android.gms.maps.model.Marker;


public class PlaybackActivity extends Activity
        implements PlaybackOverviewFragment.OnMarkerWindowClickListener {

    /** Log Tag */
    private static final String TAG = "PlaybackActivity";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_playback);
        if (savedInstanceState == null) {
            getFragmentManager().beginTransaction()
                    .add(R.id.container, new PlaybackOverviewFragment())
                    .commit();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_playback, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * Called by PlaybackOverviewFragment
     * Launches new fragment containing polyline for that marker
     */
    @Override
    public void onMarkerWindowClick(final Marker marker){
        Toast.makeText(PlaybackActivity.this,"Received " + marker.getId(), Toast.LENGTH_SHORT ).show();

        String mkrTtl = marker.getTitle();

        int RideID = Integer.valueOf(mkrTtl.substring(5,mkrTtl.length()));

        Log.i(TAG,"Sending RideID " + RideID + " to OverlayActivity");

        Intent intent = new Intent(this, OverlayActivity.class);
        intent.putExtra("RideID",RideID);

        startActivity(intent);

    }
}
