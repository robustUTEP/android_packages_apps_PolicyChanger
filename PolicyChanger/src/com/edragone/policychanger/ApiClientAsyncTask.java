package com.edragone.policychanger;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks;
import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener;
import com.google.android.gms.drive.Drive;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;

import java.util.concurrent.CountDownLatch;

/**
 * An AsyncTask that maintains a connected client.
 */
public abstract class ApiClientAsyncTask<Params, Progress, Result>
        extends AsyncTask<Params, Progress, Result> {

    private GoogleApiClient mClient;
    private final static String TAG = "AsyncTask";

    public ApiClientAsyncTask(Context context) {
        GoogleApiClient.Builder builder = new GoogleApiClient.Builder(context)
                .addApi(Drive.API)
                .addScope(Drive.SCOPE_FILE);
        mClient = builder.build();
    }

    @Override
    protected final Result doInBackground(Params... params) {
        Log.d(TAG, "in background");
        final CountDownLatch latch = new CountDownLatch(1);
        mClient.registerConnectionCallbacks(new ConnectionCallbacks() {
            @Override
            public void onConnectionSuspended(int cause) {
            	Log.d(TAG,"connection suspended cause:" + 
            			((cause==1)?"SERVICE_DISCONNECTED":"NETWORK_LOST"));
            }

            @Override
            public void onConnected(Bundle arg0) {
            	Log.d(TAG,"mClient connected");
                latch.countDown();
            }
        });
        mClient.registerConnectionFailedListener(new OnConnectionFailedListener() {
            @Override
            public void onConnectionFailed(ConnectionResult arg0) {
            	Log.e(TAG, "mClient connection failed");
                latch.countDown();
            }
        });
        mClient.connect();
        try {
            latch.await();
        } catch (InterruptedException e) {
        	Log.e(TAG,"latch.await()",e);
            return null;
        }
        if (!mClient.isConnected()) {
        	Log.e(TAG,"mClient not connected");
            return null;
        }
        try {
            return doInBackgroundConnected(params);
        } finally {
        	Log.d(TAG,"mClient disconnect");
            mClient.disconnect();
        }
    }

    /**
     * Override this method to perform a computation on a background thread, while the client is
     * connected.
     */
    protected abstract Result doInBackgroundConnected(Params... params);

    /**
     * Gets the GoogleApliClient owned by this async task.
     */
    protected GoogleApiClient getGoogleApiClient() {
        return mClient;
    }
}