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

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Environment;
import android.util.Log;

import com.opentt.rideout.RideDataContract.RideData;
import com.opentt.rideout.RideDataContract.RideSummary;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;

public class RideDataDbHelper extends SQLiteOpenHelper{
    private static final String TAG = "RideDataDbHelper";

    public static final String DATABASE_NAME = "RideData.db";
    public static final int DATABASE_VERSION = 1;

    private static final String TEXT_TYPE = " TEXT";
    private static final String COMMA_SEP = ",";
    private static final String SQL_CREATE_DATA_TABLE =
            "CREATE TABLE IF NOT EXISTS " + RideDataContract.RideData.TABLE_NAME + " (" +
                    RideData._ID    + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    RideData.RIDE_ID        + TEXT_TYPE + COMMA_SEP +
                    RideData.TIME_STAMP     + TEXT_TYPE + COMMA_SEP +
                    RideData.LATITUDE       + TEXT_TYPE + COMMA_SEP +
                    RideData.LONGITUDE      + TEXT_TYPE + COMMA_SEP +
                    RideData.ALTITUDE       + TEXT_TYPE + COMMA_SEP +
                    RideData.SPEED          + TEXT_TYPE + COMMA_SEP +
                    RideData.BEARING        + TEXT_TYPE + COMMA_SEP +
                    RideData.ACCELERATION_X + TEXT_TYPE + COMMA_SEP +
                    RideData.ACCELERATION_Y + TEXT_TYPE + COMMA_SEP +
                    RideData.ACCELERATION_Z + TEXT_TYPE + COMMA_SEP +
                    RideData.LEAN_ANGLE     + TEXT_TYPE + " )";

    private static final String SQL_CREATE_SUMMARY_TABLE =
            "CREATE TABLE IF NOT EXISTS "  + RideSummary.TABLE_NAME + " (" +
                    RideSummary._ID        + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    RideSummary.RIDE_ID            + TEXT_TYPE + COMMA_SEP +
                    RideSummary.LATITUDE           + TEXT_TYPE + COMMA_SEP +
                    RideSummary.LONGITUDE          + TEXT_TYPE + COMMA_SEP +
                    RideSummary.TIME_STAMP         + TEXT_TYPE + COMMA_SEP +
                    RideSummary.DURATION           + TEXT_TYPE + COMMA_SEP +
                    RideSummary.DISTANCE_TRAVELLED + TEXT_TYPE + COMMA_SEP +
                    RideSummary.MAX_SPEED          + TEXT_TYPE + COMMA_SEP +
                    RideSummary.MAX_LEAN_ANGLE     + TEXT_TYPE + " )";


    private static final String SQL_DELETE_DATA_TABLE =
            "DROP TABLE IF EXISTS " + RideData.TABLE_NAME;

    private static final String SQL_DELETE_SUMMARY_TABLE =
            "DROP TABLE IF EXISTS " + RideSummary.TABLE_NAME;

    public RideDataDbHelper(Context context){
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    public void onCreate(SQLiteDatabase db){
        db.execSQL(SQL_CREATE_DATA_TABLE);
        db.execSQL(SQL_CREATE_SUMMARY_TABLE);
        Log.i(TAG, "Database Path = " + db.getPath());
    }

    public void clearDb(SQLiteDatabase db){
        db.execSQL(SQL_DELETE_DATA_TABLE);
        db.execSQL(SQL_DELETE_SUMMARY_TABLE);
        onCreate(db);
    }

    /**
     * Called when the database needs to be upgraded. The implementation
     * should use this method to drop tables, add tables, or do anything else it
     * needs to upgrade to the new schema version.
     *
     * <p>
     * The SQLite ALTER TABLE documentation can be found
     * <a href="http://sqlite.org/lang_altertable.html">here</a>. If you add new columns
     * you can use ALTER TABLE to insert them into a live table. If you rename or remove columns
     * you can use ALTER TABLE to rename the old table, then create the new table and then
     * populate the new table with the contents of the old table.
     * </p><p>
     * This method executes within a transaction.  If an exception is thrown, all changes
     * will automatically be rolled back.
     * </p>
     *
     * @param db The database.
     * @param oldVersion The old database version.
     * @param newVersion The new database version.
     */
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion){
        // TODO: Change method to store old entries in new database
        db.execSQL(SQL_DELETE_DATA_TABLE);
        db.execSQL(SQL_DELETE_SUMMARY_TABLE);
        onCreate(db);
    }

    /**
     * Called when the database needs to be downgraded. This is strictly similar to
     * {@link #onUpgrade} method, but is called whenever current version is newer than requested one.
     *
     * <p>
     * This method executes within a transaction.  If an exception is thrown, all changes
     * will automatically be rolled back.
     * </p>
     *
     * @param db The database.
     * @param oldVersion The old database version.
     * @param newVersion The new database version.
     */
    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {

        if (newVersion > oldVersion) {
            Log.e(TAG, "Cannot downgrade database to a version greater than its current " +
                  "version: V" + db.getVersion() + " -> " + newVersion + " : " + getDatabaseName());
        } else {
            onUpgrade(db, oldVersion, newVersion);
        }
    }

    /**
     * Called when the database has been opened.  The implementation
     * should check {@link SQLiteDatabase#isReadOnly} before updating the
     * database.
     * <p>
     * This method is called after the database connection has been configured
     * and after the database schema has been created, upgraded or downgraded as necessary.
     * If the database connection must be configured in some way before the schema
     * is created, upgraded, or downgraded, do it in {@link #onConfigure} instead.
     * </p>
     *
     * @param db The database.
     */
    public void onOpen(SQLiteDatabase db){
        if (db.isReadOnly()){
            //TODO: Do something
        }
    }

    public boolean isDataTableEmpty(SQLiteDatabase db){
        boolean flag = false;

        Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM " + RideData.TABLE_NAME, null);

        if ( cursor != null && cursor.moveToFirst() ){
             if ( cursor.getInt(0) == 0 ) { // If table is empty, a "0" is placed in row 1, column 0
                 flag = true;
                 Log.i(TAG,"Found zero entry in row 1: Table " + RideData.TABLE_NAME + " is empty");
             }
            cursor.close();
        }

        return flag;
    }

    public boolean isRideEntryEmpty(SQLiteDatabase db, int rideID){

        boolean result = true;

        String query = "SELECT COUNT(*) FROM " + RideData.TABLE_NAME +
                " WHERE " + RideData.RIDE_ID + " = ? ";

        Cursor cursor = db.rawQuery(query,
                new String[]{Integer.toString(rideID)});

        if ((cursor != null) && (cursor.moveToFirst())) {
            if (cursor.getInt(0) != 0) {
                result = false;
            }
            cursor.close();
        }

        return result;
    }

    public void exportDB(SQLiteDatabase db){
        FileChannel source=null;
        FileChannel destination=null;
        String currentDBPath = db.getPath();
        String backupDBPath = "/RideDataBackup";

        Log.i(TAG,"src db at path: " + currentDBPath);

        // Check to see if external storage is writable
        if ( isExternalStorageWritable() ) {

            File currentDB = new File(currentDBPath);

            String root = Environment.
                    getExternalStorageDirectory().getAbsolutePath();

            File backupDbDir = new File(root + backupDBPath);
            if ( !backupDbDir.exists() ) {
                if( !backupDbDir.mkdir() ) Log.e(TAG,"Could not make backup directory");
            }

            File backupDB = new File(backupDbDir,"backup.db");

            if (!backupDB.exists()) {

                try {
                    Log.i(TAG,"Attempting to create new file at: " + backupDB.getPath());
                    backupDB.createNewFile();
                } catch (IOException ex){
                    Log.e(TAG, "Failed to create new file");
                    ex.printStackTrace();
                }
            }

            try {
                source = new FileInputStream(currentDB).getChannel();
                destination = new FileOutputStream(backupDB).getChannel();
                destination.transferFrom(source, 0, source.size());
                source.close();
                destination.close();
                Log.i(TAG, "Database Exported to " + backupDB.getPath());
                //Toast.makeText(this, "DB Exported!", Toast.LENGTH_LONG).show();
            } catch (IOException e) {
                e.printStackTrace();
            }

        } else{
            Log.e(TAG, "External Storage not Writable");
        }
    }

    public static void importDb(){
        FileChannel source = null;
        FileChannel destination = null;
        String importPATH = "/RideDataBackup";
        String destinationPATH = "/data/data/com.opentt.rideout/databases/RideData.db";

        // Check to see if external storage is writable
        if ( isExternalStorageWritable() ) {

            File destinationDB = new File(destinationPATH);

            String root = Environment.
                    getExternalStorageDirectory().getAbsolutePath();

            File importDir = new File(root + importPATH);
            if ( importDir.exists() ) {

                File importDB = new File(importDir, "backup.db");

                if (importDB.exists()) {

                    try {
                        source = new FileInputStream(importDB).getChannel();
                        destination = new FileOutputStream(destinationDB).getChannel();
                        destination.transferFrom(source, 0, source.size());
                        source.close();
                        destination.close();
                        Log.i(TAG, "Backup database imported to " + destinationDB.getPath());
                        //Toast.makeText(this, "DB Exported!", Toast.LENGTH_LONG).show();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                } else {
                    Log.e(TAG, "Backup database does not exist");
                }


            } else {
                Log.e(TAG, "Backup directory does not exist");
            }

        } else{
            Log.e(TAG, "External Storage not readable");
        }

    }

    /* Checks if external storage is available for read and write */
    private static boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        return Environment.MEDIA_MOUNTED.equals(state);
    }

}
