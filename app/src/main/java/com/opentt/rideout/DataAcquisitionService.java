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

import android.app.Service;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.location.Location;
import android.os.*;
import android.os.Process;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

import com.opentt.rideout.RideDataContract.RideData;
import com.opentt.rideout.RideDataContract.RideSummary;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.TimeUnit;

public class DataAcquisitionService extends Service implements GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener, LocationListener, Handler.Callback {

    public DataAcquisitionService() {
    }

    /* Log TAG */
    private static final String TAG = "DataAcquisitionService";

    protected static final String ACTION_START_ACQUISITION = "start";
    protected static final String ACTION_STOP_ACQUISITION = "stop";

    /* Booleans for Location services and hardware sensors. Configurable in settings */
    private static Boolean mRequestingHardwareSensors;

    /** Database variables */

    /* Database helper */
    private static RideDataDbHelper mDbHelper;

    /* Database insert variables */
    private static SQLiteDatabase db;
    private static int rideID;
    private static double[] acceleration = new double[3]; // acceleration in x(0), y(1), z(2);
    private static double leanangle = 0.0;

    /* Today's Date */
    private static DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
    private static String mTimeStamp;

    /** Location Variables */

    // Use Location Services?
    protected Boolean mRequestingLocationUpdates;

    /* The desired interval for location updates. Inexact. Updates may be more or less frequent.*/
    public static long UPDATE_INTERVAL_IN_MILLISECONDS = 100;

    /* The fastest rate for active location updates. Exact. Updates will never be more frequent
     * than this value.*/
    public static long FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS;

    /* Provides the entry point to Google Play services. */
    protected static GoogleApiClient mGoogleApiClient;

    /* Stores parameters for requests to the FusedLocationProviderApi.*/
    protected LocationRequest mLocationRequest;

    /* Represents a geographical location.*/
    protected static Location mOriginalLocation;
    protected static Location mCurrentLocation;

    /** Background Service Variables */
    private Looper _looper;
    private Handler _handler;

    private static final String HANDLER_THREAD_NAME = "dataAcquisition";

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate(){
        super.onCreate();

        _handler = getNewHandler();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId){

        Log.i(TAG, "Received start id " + startId + ": " + intent);

       _handler.sendMessage(_handler.obtainMessage(0,intent));
        // We want this service to continue running until it is explicitly
        // stopped, so return sticky.
        return START_STICKY;
    }

    @Override
    public boolean handleMessage(Message msg){
        Intent intent = (Intent) msg.obj;

        String action = intent.getAction();
        Log.d(TAG,"Thread " + Thread.currentThread().getName() + " received action: " + action);

        if ( action.equals(ACTION_START_ACQUISITION) ) {

            // Check to see if user preferences have changed
            getUserPreferences();

            /* Since the background thread must been "restarted", must rebuild the GoogleApiClient:
            -> The GoogleApiClient.builder requires a _handler
            -> The FusedLocationApi location requester requires a _looper
            -> On restart, both _looper and _handler belong to a dead thread.
            */

            // Build & Connect the GoogleAPIClient
            if (mRequestingLocationUpdates) {
                buildGoogleApiClient();
                mGoogleApiClient.connect();
            }

            try {
                mDbHelper = new RideDataDbHelper(DataAcquisitionService.this);
                // Initialise database
                db = mDbHelper.getReadableDatabase();
                // Get the RideID for this ride
                rideID = getNewRideId();
                // Close the readable database to avoid memory leaks
                db.close();
                // Get a writable database for data insertion
                db = mDbHelper.getWritableDatabase();

            } catch (Exception ex) {
                ex.printStackTrace();
            }

            Handler h = new Handler(DataAcquisitionService.this.getMainLooper());
            h.post(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(DataAcquisitionService.this, "Starting Data Acquisition",
                            Toast.LENGTH_SHORT).show();
                }
            });

            Log.i(TAG, "Starting Data Acquisition Service with RideID: " + rideID);

        }else if (action.equals(ACTION_STOP_ACQUISITION) ){
            Log.i(TAG, "Disabling Data Acquisition Service");
            stopSelf();
        }

        return true;
    }

    private Handler getNewHandler(){

        HandlerThread thread = new HandlerThread(HANDLER_THREAD_NAME);
        thread.setPriority(Process.THREAD_PRIORITY_BACKGROUND);
        thread.start();
        _looper = thread.getLooper();

        return new Handler(_looper, this);
    }

    @Override
    public void onDestroy(){
        super.onDestroy();

        if(mGoogleApiClient != null){
            if ( mGoogleApiClient.isConnected() ) {
                stopLocationUpdates();
                mGoogleApiClient.disconnect();
            }
        }

        if ( _looper != null ){
            _looper.quit();
        }

        Toast.makeText(this,"Disabling Data Acquisition",Toast.LENGTH_SHORT).show();

        // Update summary database table and close.
        new UpdateSummaryTable().execute();

    }

    protected static void insertData(){

        // Create a new map of values, where column names are the keys
        ContentValues values = new ContentValues();
        values.put(RideData.RIDE_ID, rideID);
        values.put(RideData.TIME_STAMP, mTimeStamp);
        values.put(RideData.LATITUDE, mCurrentLocation.getLatitude());
        values.put(RideData.LONGITUDE, mCurrentLocation.getLongitude());
        values.put(RideData.ALTITUDE,
                mCurrentLocation.hasAltitude() ? mCurrentLocation.getAltitude() : -1);
        values.put(RideData.SPEED,
                mCurrentLocation.hasSpeed() ? mCurrentLocation.getSpeed() : -1);
        values.put(RideData.BEARING,
                mCurrentLocation.hasBearing() ? mCurrentLocation.getBearing() : -1);
        values.put(RideData.ACCELERATION_X, acceleration[0]);
        values.put(RideData.ACCELERATION_Y, acceleration[1]);
        values.put(RideData.ACCELERATION_Z, acceleration[2]);
        values.put(RideData.LEAN_ANGLE, leanangle);

        // Insert the new row, returning the primary key value of the new row
        long id;
        id = db.insert(RideData.TABLE_NAME, null, values);

        Log.d(TAG,"inserted row number = " + id);

        // Check if insert was successful
        if (id == -1){
            Log.e(TAG,"Could not insert new row into database");
            throw new SQLiteException("Could not insert new row");
        }

    }

    /** Checks database for last rideID.
     * If one exists, the rideID is incremented and returned
     * If not, rideID to 1 */
    private int getNewRideId(){
        db = mDbHelper.getReadableDatabase();
        // Initialise the rideID to zero (will be changed if a ride was found in database)
        rideID = 0;

        if (!mDbHelper.isDataTableEmpty(db)) {
            Log.i(TAG, "Existing database found, Acquiring new rideID");

            // Define a projection that specifies which columns from the database
            // you will actually use after this query
            String[] projection = {
                    RideData._ID,
                    RideData.RIDE_ID
            };

            String sortOrder = RideData.RIDE_ID + " DESC";

            try {
                Cursor cursor = db.query(
                        RideData.TABLE_NAME,        // Table to query
                        projection,                 // The columns to return
                        null,                       // The columns for the WHERE clause
                        null,                       // The values for the WHERE clause
                        null,                       // Don't group rows
                        null,                       // Don't filter by row groups
                        sortOrder                   // The sort order
                );

                if (cursor != null) {
                    cursor.moveToFirst();
                    rideID = cursor.getInt(cursor.getColumnIndexOrThrow(RideData.RIDE_ID));
                    cursor.close();
                }
            } catch (IllegalArgumentException ex) {
                Log.e(TAG, "Could not retrieve previous RideID from database");
                throw ex;
            }
        }
        db.close();
        return ++rideID;
    }

    private void getUserPreferences(){
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        mRequestingLocationUpdates = preferences
                .getBoolean(SettingsActivity.PREF_KEY_USE_LOCATION_SERVICES, true);

        mRequestingHardwareSensors = preferences
                .getBoolean(SettingsActivity.PREF_KEY_USE_LOCATION_SERVICES, true);

        UPDATE_INTERVAL_IN_MILLISECONDS = Long.valueOf(preferences
                .getString(SettingsActivity.PREF_KEY_SAMPLE_FREQUENCY,"5"))*1000;

        FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS = UPDATE_INTERVAL_IN_MILLISECONDS/2;
    }

    /**
     * Builds a GoogleApiClient. Uses the {@code #addApi} method to request the
     * LocationServices API.
     */
    protected synchronized void buildGoogleApiClient() {

        Log.d(TAG,"Building GoogleApiClient on thread " + Thread.currentThread().getName());

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .setHandler(_handler)
                .addApi(LocationServices.API)
                .build();
        createLocationRequest();
    }

    /**
     * Sets up the location request. Android has two location request settings:
     * {@code ACCESS_COARSE_LOCATION} and {@code ACCESS_FINE_LOCATION}. These settings control
     * the accuracy of the current location. This app uses ACCESS_FINE_LOCATION, as defined in
     * the AndroidManifest.xml.
     *
     * When the ACCESS_FINE_LOCATION setting is specified, combined with a fast update
     * interval (5 seconds), the Fused Location Provider API returns location updates that are
     * accurate to within a few feet.
     */
    protected void createLocationRequest() {
        mLocationRequest = new LocationRequest();

        // Sets the desired interval for active location updates. This interval is
        // inexact. You may not receive updates at all if no location sources are available, or
        // you may receive them slower than requested. You may also receive updates faster than
        // requested if other applications are requesting location at a faster interval.
        mLocationRequest.setInterval(UPDATE_INTERVAL_IN_MILLISECONDS);

        // Sets the fastest rate for active location updates. This interval is exact, and your
        // application will never receive updates faster than this value.
        mLocationRequest.setFastestInterval(FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS);

        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
    }

    /**
     * Runs when a GoogleApiClient object successfully connects.
     */
    @Override
    public void onConnected(Bundle connectionHint) {
        Log.i(TAG, "Connected GoogleApiClient");

        // If the initial location was never previously requested, we use
        // FusedLocationApi.getLastLocation() to get it. If it was previously requested, we store
        // its value in the Bundle and check for it in onCreate(). We
        // do not request it again unless the user specifically requests location updates by pressing
        // the Start Updates button.
        //
        // Because we cache the value of the initial location in the Bundle, it means that if the
        // user launches the activity,
        // moves to a new location, and then changes the device orientation, the original location
        // is displayed as the activity is re-created.
        if (mCurrentLocation == null) {
            mCurrentLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
        }

        // If the user presses the Start Updates button before GoogleApiClient connects, we set
        // mRequestingLocationUpdates to true (see startUpdatesButtonHandler()). Here, we check
        // the value of mRequestingLocationUpdates and if it is true, we start location updates.
        if (mRequestingLocationUpdates) {
            startLocationUpdates();
        }
    }

    /**
     * Callback that fires when the location changes.
     */
    @Override
    public void onLocationChanged(Location location) {
        mCurrentLocation = location;
        mTimeStamp = dateFormat.format(new Date());
        Log.d(TAG,"Location Updated @: " + mCurrentLocation.getTime() +
        " by thread " + Thread.currentThread().getName());
        insertData();
    }

    /**
     * Requests location updates from the FusedLocationApi.
     */
    protected void startLocationUpdates() {
        stopLocationUpdates();

        LocationServices.FusedLocationApi.requestLocationUpdates(
                mGoogleApiClient, mLocationRequest, this, _looper);

        mOriginalLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
    }

    /**
     * Removes location updates from the FusedLocationApi.
     */
    protected void stopLocationUpdates() {
        LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this);
    }

    @Override
    public void onConnectionSuspended(int cause) {
        // The connection to Google Play services was lost for some reason. We call connect() to
        // attempt to re-establish the connection.
        Log.i(TAG, "Connection suspended, attempting to reconnect");
        mGoogleApiClient.connect();
    }

    @Override
    public void onConnectionFailed(ConnectionResult result) {
        // Refer to the javadoc for ConnectionResult to see what error codes might be returned in
        // onConnectionFailed.
        Log.i(TAG, "Connection failed: ConnectionResult.getErrorCode() = " + result.getErrorCode());
    }

    private class UpdateSummaryTable extends AsyncTask<Void,Void,Void>{

        @Override
        protected Void doInBackground(Void... params) {

            // Check if database is not null, closed or read only
            if ( db == null ){
                db = mDbHelper.getWritableDatabase();
            } else if ( db.isReadOnly() ){
                db.close();
                db = mDbHelper.getWritableDatabase();
            } else if ( !db.isOpen() ){
                db = mDbHelper.getWritableDatabase();
            }

            // Was there any ride data logged?
            if ( !mDbHelper.isRideEntryEmpty(db,rideID) ) {

                ContentValues values = new ContentValues();
                values.put(RideSummary.RIDE_ID, rideID);

                // Query the database for start Lat and Lon and duration
                String[] projection = {RideData._ID,
                                       RideData.TIME_STAMP,
                                       RideData.LATITUDE,
                                       RideData.LONGITUDE,
                                       RideData.TIME_STAMP};

                String selection = RideData.RIDE_ID + " = ? ";
                String[] selectionEquals = new String[]{Integer.toString(rideID)};

                String sortOrder = RideData._ID + " ASC";

                try{
                    Cursor cursor = db.query(RideData.TABLE_NAME,
                            projection, selection, selectionEquals, null, null, sortOrder);

                    if (cursor != null) {
                        cursor.moveToFirst();

                        double summaryLat = cursor.getDouble(cursor
                                .getColumnIndexOrThrow(RideData.LATITUDE));
                        double summaryLng = cursor.getDouble(cursor
                                .getColumnIndexOrThrow(RideData.LONGITUDE));

                        mTimeStamp = cursor.getString(cursor
                                .getColumnIndexOrThrow(RideData.TIME_STAMP));

                        //  Note, reuse of these variables is for convenience only.
                        mOriginalLocation.setLatitude(summaryLat);
                        mOriginalLocation.setLongitude(summaryLng);

                        values.put(RideSummary.LATITUDE, summaryLat);
                        values.put(RideSummary.LONGITUDE, summaryLng);
                        values.put(RideSummary.TIME_STAMP, mTimeStamp);

                        Date start = new Date(0);

                        try {
                            start = dateFormat.parse(mTimeStamp);
                        } catch (ParseException ex){
                            ex.printStackTrace();
                        }

                        // Move to end of database entries for this rideID
                        cursor.moveToLast();

                        //  Note, reuse of these variables is for convenience only.
                        mCurrentLocation.setLatitude(cursor.getDouble(cursor
                                .getColumnIndexOrThrow(RideData.LATITUDE)));
                        mCurrentLocation.setLongitude(cursor.getDouble(cursor
                                .getColumnIndexOrThrow(RideData.LONGITUDE)));

                        mTimeStamp = cursor.getString(cursor
                                .getColumnIndexOrThrow(RideData.TIME_STAMP));


                        Date finish = new Date(0);
                        try {
                            finish = dateFormat.parse(mTimeStamp);
                        } catch (ParseException ex){
                            ex.printStackTrace();
                        }

                        long timeDiff = finish.getTime() - start.getTime();

                        String duration = String.format("%02d:%02d:%02d", TimeUnit.MILLISECONDS.toHours(timeDiff),
                                TimeUnit.MILLISECONDS.toMinutes(timeDiff) -
                                        TimeUnit.HOURS.toMinutes(TimeUnit.MILLISECONDS.toHours(timeDiff)),
                                TimeUnit.MILLISECONDS.toSeconds(timeDiff) -
                                        TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(timeDiff)));



                        values.put(RideSummary.DURATION, duration);
                        values.put(RideSummary.DISTANCE_TRAVELLED,
                                (double) mOriginalLocation.distanceTo(mCurrentLocation));

                        cursor.close();
                    }

                } catch (IllegalArgumentException ex){
                    throw new IllegalArgumentException("Could not query database");
                }


                values.put(RideSummary.MAX_SPEED, getMaxSpeed());
                values.put(RideSummary.MAX_LEAN_ANGLE, getMaxLeanAngle());

                long id = db.insert(RideSummary.TABLE_NAME, null, values);

                if (id == -1) {
                    Log.e(TAG, "Could not update database table: " + RideSummary.TABLE_NAME);
                }

            } else{
                Log.i(TAG,"No data was found for this ride entry");
            }

            mDbHelper.exportDB(db);
            db.close();

            return null;
        }

        private double getMaxSpeed(){
            double max_speed = 0.0;

            String[] projection = {RideData.SPEED};
            String sortOrder = RideData.SPEED + " DESC";

            Cursor cursor = db.query(RideData.TABLE_NAME,
                    projection, null, null, null, null, sortOrder);

            if (cursor != null) {
                cursor.moveToFirst();
                max_speed = cursor.getDouble(cursor
                        .getColumnIndexOrThrow(RideData.SPEED));
                cursor.close();
            }

            return max_speed;
        }

        private double getMaxLeanAngle(){

            double max_lean_angle = 0.0;

            String[] projection = {RideData.LEAN_ANGLE};
            String sortOrder = RideData.LEAN_ANGLE + " DESC";

            Cursor cursor = db.query(RideData.TABLE_NAME,
                    projection, null, null, null, null, sortOrder);

            if ( cursor != null ){
                cursor.moveToFirst();
                max_lean_angle = cursor.getDouble(cursor
                        .getColumnIndexOrThrow(RideData.LEAN_ANGLE));
                cursor.close();
            }

            return max_lean_angle;
        }
    }
}
