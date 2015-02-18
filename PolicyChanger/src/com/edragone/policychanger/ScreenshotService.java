package com.edragone.policychanger;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.TaskStackBuilder;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Environment;
import android.os.IBinder;
import android.os.PowerManager;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

public class ScreenshotService extends Service {

	private final static String TAG = "ScreenshotService";
	private final static int ID = 71;
	private static BitmapFactory.Options options;
	private static Bitmap reusedBitmap;

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	@Override
	public void onCreate() {
		NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(getApplicationContext());
		mBuilder.setSmallIcon(R.drawable.ic_launcher_3);
		mBuilder.setContentTitle("ScreenshotService");
		mBuilder.setContentText("Taking screenshots every 30 seconds");
		mBuilder.setOngoing(true);

		Intent resultIntent = new Intent(this, MainActivity.class);
		TaskStackBuilder stackBuilder = TaskStackBuilder.create(this);
		stackBuilder.addParentStack(MainActivity.class);

		// Adds the Intent that starts the Activity to the top of the stack
		stackBuilder.addNextIntent(resultIntent);
		PendingIntent resultPendingIntent = stackBuilder.getPendingIntent(0,PendingIntent.FLAG_UPDATE_CURRENT);
		mBuilder.setContentIntent(resultPendingIntent);

		NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		Notification noti = mBuilder.build();
		mNotificationManager.notify(ID, noti);

		startForeground(ID, noti);
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
//		new startScreenshots().execute("");
		takeScreenshot();
		return START_STICKY;
	}

	//	@Override
	//	protected void onHandleIntent(Intent intent) {
	//
	//		
	//	}

	private void takeScreenshot()
	{
//		SharedPreferences prefs = getSharedPreferences("com.edragone.policychanger", Context.MODE_PRIVATE);
//		boolean isScreenshotChecked = prefs.getBoolean("screenshotCheckBox", true);
		PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
		boolean isScreenOn = pm.isScreenOn();

		Process sh;
		OutputStream  os;
		SimpleDateFormat sdf;
		String cdt;
		File screenCap;
		File ssDir;
		File f;
		FileOutputStream fo;

		ByteArrayOutputStream bytes;
		options = new BitmapFactory.Options();
		//			while (isScreenshotChecked) {
		isScreenOn = pm.isScreenOn();	
		//				isScreenshotChecked = prefs.getBoolean("screenshotCheckBox", true);

		if (isScreenOn){
			Log.d(TAG,"handle screenshot intent");

			try {
				sh = Runtime.getRuntime().exec("su", null,null);

				os = sh.getOutputStream();
				os.write(("/system/bin/screencap -p " + "/sdcard/tmp.png").getBytes("ASCII"));
				os.flush();

				os.close();
				sh.waitFor();

				sdf = new SimpleDateFormat("yyyyMMdd_HHmmss",Locale.US);
				cdt = sdf.format(new Date());

				//then read img.png as bitmap 
				screenCap = new File(Environment.getExternalStorageDirectory()+ File.separator +"tmp.png");
				//					    options.inJustDecodeBounds = true;
				//					    options.inMutable = true;
				//			            BitmapFactory.decodeFile(screenCap.getAbsolutePath(), options);

				// we will create empty bitmap by using the option
				//			            reusedBitmap = Bitmap.createBitmap(options.outWidth, options.outHeight, Bitmap.Config.ARGB_8888); 

				// set the option to allocate memory for the bitmap
				//			            options.inJustDecodeBounds = false;
				options.inSampleSize = 2;
				//			            options.inBitmap = reusedBitmap;

				reusedBitmap = BitmapFactory.decodeFile(screenCap.getAbsolutePath(), options);

				ssDir = new File(Environment.getExternalStorageDirectory()+ File.separator+"robust_screenshots");
				if(!ssDir.exists()) ssDir.mkdir();

				//compress and save to SD
				bytes = new ByteArrayOutputStream();
				reusedBitmap.compress(Bitmap.CompressFormat.JPEG, 15, bytes);

				f = new File(ssDir.getPath()+"/screenshot_"+cdt+".jpg");
				//			Log.d(TAG,"path: "+f.getPath());
				f.createNewFile();
				fo = new FileOutputStream(f);
				fo.write(bytes.toByteArray());
				fo.close();

				sh = null;
				os = null;
				sdf = null;
				cdt = null;
				screenCap = null;
				ssDir = null;
				f = null;
				fo = null;

				Runtime.getRuntime().gc();

			} catch (Exception e) {
				Log.e(TAG,"Exception",e);
			}
		}

	}


	@Override
	public void onDestroy()
	{
		NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		mNotificationManager.cancel(ID);
	}
}
