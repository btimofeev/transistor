/**
 * StationFetcher.java
 * Implements helper for getting radio station metadata from local storage or internet
 * The downloader runs as AsyncTask
 *
 * This file is part of
 * TRANSISTOR - Radio App for Android
 *
 * Copyright (c) 2015-16 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package org.y20k.transistor.helpers;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.os.EnvironmentCompat;
import android.util.Log;
import android.widget.Toast;

import org.y20k.transistor.R;
import org.y20k.transistor.core.Collection;
import org.y20k.transistor.core.Station;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;


/**
 * StationFetcher class
 */
public final class StationFetcher extends AsyncTask<Void, Void, Station> {

    /* Define log tag */
    private static final String LOG_TAG = StationFetcher.class.getSimpleName();


    /* Keys */
    private static final String EXTRA_STATION_URI_CURRENT = "STATION_URI_CURRENT";
    private static final String ACTION_COLLECTION_CHANGED = "org.y20k.transistor.action.COLLECTION_CHANGED";
    private static final String PREF_STATION_ID_CURRENT = "prefStationIDCurrent";

    /* Main class variables */
    private final Activity mActivity;
    private Collection mCollection;
    private final File mFolder;
    private final Uri mStationUri;
    private final String mStationUriScheme;
    private URL mStationURL;
    private final boolean mFolderExists;


    /* Constructor */
    public StationFetcher(Uri stationUri, Activity activity) {
        mActivity = activity;
        mStationUri = stationUri;
        mStationUriScheme = stationUri.getScheme();

        // get collection folder from external storage
        mFolder = getCollectionDirectory("Collection");

        // set mFolderExists
        assert mFolder != null;
        mFolderExists = mFolder.exists();

        // load collection
        mCollection = new Collection(mFolder);

        // notify user
        if (stationUri != null && mStationUriScheme != null && mStationUriScheme.startsWith("http")) {
            Toast.makeText(mActivity, mActivity.getString(R.string.toastmessage_add_download_started) + " " + stationUri.toString(), Toast.LENGTH_LONG).show();
        } else if (stationUri != null && mStationUriScheme != null && mStationUriScheme.startsWith("file")) {
            // notify user
            Toast.makeText(mActivity, mActivity.getString(R.string.toastmessage_add_open_file_started) + " " + stationUri.toString(), Toast.LENGTH_LONG).show();
        }

    }


    /* Background thread: download station */
    @Override
    public Station doInBackground(Void... params) {

        if (mFolderExists && mStationUriScheme != null && mStationUriScheme.startsWith("http")  && urlCleanup()) {
            // download and return new station
            return new Station(mFolder, mStationURL);

        } else if (mFolderExists && mStationUriScheme != null && mStationUriScheme.startsWith("file")) {
            // read file and return new station
            return new Station(mFolder, mStationUri);

        } else {
            return null;
        }
    }


    /* Main thread: set station and activate listener */
    @Override
    protected void onPostExecute(Station station) {

        boolean stationAdded = false;


        if (station != null && !station.getStationFetchError() && mFolderExists) {

            // get currently playing station
            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
            String stationUriCurrent = mCollection.getStations().get(settings.getInt(PREF_STATION_ID_CURRENT, -1)).getStreamUri().toString();

            // add station to collection
            stationAdded = mCollection.add(station);

            if (stationAdded) {
                // get position
                int position = mCollection.getStations().indexOf(station);

                // send local broadcast
                Intent i = new Intent();
                i.setAction(ACTION_COLLECTION_CHANGED);
                i.putExtra(EXTRA_STATION_URI_CURRENT, stationUriCurrent);
                LocalBroadcastManager.getInstance(mActivity.getApplication()).sendBroadcast(i);
            }


        }

        if (station == null || station.getStationFetchError() || !mFolderExists || !stationAdded) {

            String errorTitle;
            String errorMessage;
            String errorDetails;

            if (mStationUriScheme != null && mStationUriScheme.startsWith("http")) {
                // construct error message for "http"
                errorTitle = mActivity.getResources().getString(R.string.dialog_error_title_fetch_download);
                errorMessage = mActivity.getResources().getString(R.string.dialog_error_message_fetch_download);
                errorDetails = buildDownloadErrorDetails(station);
            } else if (mStationUriScheme != null && mStationUriScheme.startsWith("file")) {
                // construct error message for "file"
                errorTitle = mActivity.getResources().getString(R.string.dialog_error_title_fetch_read);
                errorMessage = mActivity.getResources().getString(R.string.dialog_error_message_fetch_read);
                errorDetails = buildReadErrorDetails(station);
            } else if (!stationAdded  && mStationUriScheme != null) {
                // construct error message for write error
                errorTitle = mActivity.getResources().getString(R.string.dialog_error_title_fetch_write);
                errorMessage = mActivity.getResources().getString(R.string.dialog_error_message_fetch_write);
                errorDetails = mActivity.getResources().getString(R.string.dialog_error_details_write);
            } else {
                // default values
                errorTitle = mActivity.getResources().getString(R.string.dialog_error_title_default);
                errorMessage = mActivity.getResources().getString(R.string.dialog_error_message_default);
                errorDetails = mActivity.getResources().getString(R.string.dialog_error_details_default);
            }

            // show error dialog
            DialogError dialogError = new DialogError(mActivity, errorTitle, errorMessage, errorDetails);
            dialogError.show();
        }

    }


    /* checks and cleans url string and sets mStationURL */
    private boolean urlCleanup() {
        // remove whitespaces and create url
        try {
            mStationURL = new URL(mStationUri.toString().trim());
            return true;
        } catch (MalformedURLException e) {
            e.printStackTrace();
            return false;
        }
    }


    /* Builds more detailed download error string */
    private String buildDownloadErrorDetails(Station station) {

        // construct details string
        StringBuilder sb = new StringBuilder("");
        sb.append(mActivity.getResources().getString(R.string.dialog_error_message_fetch_general_external_storage));
        sb.append("\n");
        sb.append(mFolder);
        sb.append("\n\n");
        sb.append(mActivity.getResources().getString(R.string.dialog_error_message_fetch_download_station_url));
        sb.append("\n");
        sb.append(mStationUri);
        if (!mStationUri.getLastPathSegment().contains("m3u") || !mStationUri.getLastPathSegment().contains("pls") ) {
            sb.append("\n\n");
            sb.append(mActivity.getResources().getString(R.string.dialog_error_message_fetch_general_hint_m3u));
        }

        if (station != null && station.getStationFetchError()) {
            String remoteFileContent = station.getPlaylistFileContent();
            if (remoteFileContent != null) {
                sb.append("\n\n");
                sb.append(mActivity.getResources().getString(R.string.dialog_error_message_fetch_general_file_content));
                sb.append("\n");
                sb.append(remoteFileContent);
            } else {
                Log.v(LOG_TAG, "no remoteFileContent");
            }

        }
        return sb.toString();

    }


    /* Builds more detailed read error string */
    private String buildReadErrorDetails(Station station) {

        // construct details string
        StringBuilder sb = new StringBuilder("");
        sb.append(mActivity.getResources().getString(R.string.dialog_error_message_fetch_general_external_storage));
        sb.append("\n");
        sb.append(mFolder);
        sb.append("\n\n");
        sb.append(mActivity.getResources().getString(R.string.dialog_error_message_fetch_read));
        sb.append("\n");
        sb.append(mStationUri);
        if (!mStationUri.getLastPathSegment().contains("m3u") || !mStationUri.getLastPathSegment().contains("pls") ) {
            sb.append("\n\n");
            sb.append(mActivity.getResources().getString(R.string.dialog_error_message_fetch_general_hint_m3u));
        }

        if (station != null && station.getStationFetchError()) {
            String remoteFileContent = station.getPlaylistFileContent();
            if (remoteFileContent != null) {
                sb.append("\n\n");
                sb.append(mActivity.getResources().getString(R.string.dialog_error_message_fetch_general_file_content));
                sb.append("\n");
                sb.append(remoteFileContent);
            } else {
                Log.v(LOG_TAG, "no remoteFileContent");
            }

        }
        return sb.toString();

    }


    /* Return a writeable sub-directory from external storage  */
    private File getCollectionDirectory(String subDirectory) {
        File[] storage = mActivity.getExternalFilesDirs(subDirectory);
        for (File file : storage) {
            String state = EnvironmentCompat.getStorageState(file);
            if (Environment.MEDIA_MOUNTED.equals(state)) {
                Log.i(LOG_TAG, "External storage: " + file.toString());
                return file;
            }
        }
        Toast.makeText(mActivity, mActivity.getString(R.string.toastalert_no_external_storage), Toast.LENGTH_LONG).show();
        Log.e(LOG_TAG, "Unable to access external storage.");
        // finish activity
        mActivity.finish();

        return null;
    }


}
