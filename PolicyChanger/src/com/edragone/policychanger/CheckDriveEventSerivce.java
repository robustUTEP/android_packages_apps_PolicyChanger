package com.edragone.policychanger;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.google.android.gms.drive.events.ChangeEvent;
import com.google.android.gms.drive.events.CompletionEvent;
import com.google.android.gms.drive.events.DriveEventService;

public class CheckDriveEventSerivce extends DriveEventService {

	private final static String TAG = "CheckDriveEventService";
	private final static int ID = 91;
	
	
	@Override
	public void onChange(ChangeEvent event) {
		String m = "File upload completed\nCheck Drive for file with "+event.getDriveId();
		Log.d(TAG, m);
		
		// Application-specific handling of event.
		NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(getApplicationContext());
		mBuilder.setSmallIcon(R.drawable.ic_launcher_3);
		mBuilder.setContentTitle("Drive Event");
		mBuilder.setOngoing(false);
		mBuilder.setStyle(new NotificationCompat.BigTextStyle().bigText(m));
		mBuilder.setContentText(m);
		NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		Notification noti = mBuilder.build();
		mNotificationManager.notify(ID, noti);
	}
	
	@Override
	public void onCompletion(CompletionEvent event) {
		Log.d(TAG, "Action completed with status: " + event.getStatus());
		NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(getApplicationContext());
		mBuilder.setSmallIcon(R.drawable.ic_launcher_3);
		mBuilder.setContentTitle("DriveEventService");

		// handle completion event here.
		if (event.getStatus() == CompletionEvent.STATUS_SUCCESS) {
			// Commit completed successfully.
			mBuilder.setContentText("Commit completed successfully");
			NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
			Notification noti = mBuilder.build();
			mNotificationManager.notify(ID, noti);
			// ...
		}

		if (event.getStatus() == CompletionEvent.STATUS_FAILURE) {
			// Modified contents and metadata failed to be applied to the server.
			// They can be retrieved from the CompletionEvent to try to be applied later.
			//            InputStream modifiedInputStream = event.getModifiedContentsInputStream();
			//            MetadataChangeSet modifiedMetadata = event.getModifiedMetadataChangeSet();
			mBuilder.setContentText("Commit failed");
			NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
			Notification noti = mBuilder.build();
			mNotificationManager.notify(ID, noti);


			// ...
		}

		event.dismiss();
	}

}
