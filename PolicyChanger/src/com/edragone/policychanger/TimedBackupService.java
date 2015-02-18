package com.edragone.policychanger;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutionException;

import com.google.android.gms.drive.Drive;
import com.google.android.gms.drive.DriveApi.DriveContentsResult;
import com.google.android.gms.drive.DriveContents;
import com.google.android.gms.drive.DriveFolder;
import com.google.android.gms.drive.DriveId;
import com.google.android.gms.drive.DriveResource;
import com.google.android.gms.drive.Metadata;
import com.google.android.gms.drive.MetadataChangeSet;

import android.annotation.SuppressLint;
import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Environment;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.Toast;

public class TimedBackupService extends IntentService 
//implements ConnectionCallbacks, OnConnectionFailedListener
{

	private static final String TAG = "TimedBackupService";
	private final static int ID = 81;
	private final static int FILE_NOTI_ID = 91;
	String uniqueID;
	String policy;
	
	WakeLock wakeLock;

	private static boolean isRunning;
	
//	GoogleApiClient mGoogleApiClient;
	private DriveId mFOLDER_ID;
	private static File mFile; 
	private static final String EXISTING_FOLDER_ID = "0B5m56xhm_hJLVTZIUVpYUG55SE0"; //Logs robust shared folder

	public TimedBackupService() {
		super("TimedBackupService");
	}

	@Override
	public void onCreate()
	{
		super.onCreate();
		isRunning = false;
	}
	
	@SuppressLint("SimpleDateFormat")
	@Override
	protected void onHandleIntent(Intent arg0) 
	{
		if(!isRunning) {
			isRunning = true;
			SharedPreferences prefs = getSharedPreferences("com.edragone.policychanger", Context.MODE_PRIVATE);
			
			PowerManager mgr = (PowerManager) getSystemService(Context.POWER_SERVICE);
			wakeLock = mgr.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "CompressWakeLock");
			wakeLock.acquire();
			
			boolean isScheduledBackup = arg0.getBooleanExtra("fromAlarm", false);
			if (isScheduledBackup) {
				/* Update next backup time in case of boot */
				int time = prefs.getInt("TimedInterval", 0) + 1;
				Calendar calendar = Calendar.getInstance();
//				calendar.setTimeInMillis(System.currentTimeMillis());
				calendar.add(Calendar.HOUR, time);
				
				SimpleDateFormat sdf = new SimpleDateFormat("MM/dd HH:mm");
				String timeToDisplay = sdf.format(calendar.getTime());
				String text = "Next backup:\n\t"+timeToDisplay;
				
				prefs.edit().putLong("backupAlarmTime", calendar.getTimeInMillis()).commit();
				prefs.edit().putString("timeToDisplay", text).commit();
				Log.d(TAG,"updated next backupAlarmTime: "+text);
			}

			NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(getApplicationContext());
			mBuilder.setSmallIcon(R.drawable.ic_launcher_3);
			mBuilder.setContentTitle("BackupService");
			mBuilder.setContentText("Compressing logs");
			mBuilder.setOngoing(true);

			NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
			Notification noti = mBuilder.build();
			mNotificationManager.notify(ID, noti);

			startForeground(ID, noti);

			getStatus();

			onPreExecute();
			String message = doInBackground("");
			onPostExecute(message);
		} else {
			Toast.makeText(getApplicationContext(), "Backup already running", Toast.LENGTH_SHORT).show();
		}
	}

	public void mvStatusLogs()
	{
		String cmds [] = {"cp -r /data/data/*/*.status /sdcard/robust/", 
		"rm -r /data/data/*/*.status"};
		RunAsRoot(cmds);
	}

	/*
	 * Accepts an array of commands to run as root
	 * @param: String array
	 * @return: true if successful
	 */
	public boolean RunAsRoot(String[] cmds){
		boolean success = true;
		Process p;
		try {
			p = Runtime.getRuntime().exec("su");
			DataOutputStream os = new DataOutputStream(p.getOutputStream());            
			for (String tmpCmd : cmds) {
				os.writeBytes(tmpCmd+"\n");
			}           
			os.writeBytes("exit\n");  
			os.flush();
			os.close();
			p.waitFor();
		} catch (Exception e) {
			success = false;
			Log.e(TAG, "Root command failed", e);
		}
		//		Log.d(TAG, "RunAsRoot()");
		return success;
	}

	/*
	 * Checks policy status.
	 * Display current policy to user.
	 */
	public void getStatus()
	{
		/*
		 *		1	baseline
		 *		2	MI2
		 *		3	MI2S
		 *		4	MI2A
		 * 		5	MI2AE
		 * 		6 	MI2AI
		 */

		//		String myFile = Environment.getExternalStorageDirectory().toString()+"/robust/GCPolicy.txt";
		//		Log.d(TAG,"File path: "+myFile);
		String res = null;
		String tokens[] = new String[2];
		File sdcard = Environment.getExternalStorageDirectory();

		//		int iPolicy;

		//Get the text file
		File file = new File(sdcard,"/robust/GCPolicy.txt");

		if(!file.exists()) {
			policy = "7";
			uniqueID = "undefined";
			//			save(null);
			//			iPolicy = Integer.parseInt(policy);
		}

		else {
			//Read text from file
			StringBuilder text = new StringBuilder();
			try {
				BufferedReader br = new BufferedReader(new FileReader(file));
				String line;

				while ((line = br.readLine()) != null) {
					text.append(line+":");
				}
				br.close();
			} catch (Exception e) {
				Log.e(TAG, "I/O Exception", e);
			}

			res = text.toString();
			if (res != null) {
				//			res = res.replace(" ", "").replace(",", "");
				tokens = res.split(":");
				//				tokens[0] = tokens[0].charAt(0)+"";
			}

			if(tokens[1] != null)
				uniqueID = tokens[1];
			else
				uniqueID = "undefined";
		}
	}

	/*
	 * Saves logcat and demsg to sdcard.
	 */
	public void dumpLogs()
	{
		//		String path = Environment.getExternalStorageDirectory().toString()+"/robust";
		SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmm",Locale.US);
		String cdt = sdf.format(new Date());

		//Prepare the commands
		String[] cmds ={"logcat -d -f /sdcard/robust/logcat"+cdt+".txt","dmesg > /sdcard/robust/dmesg"+cdt+".txt"};

		RunAsRoot(cmds);
	}

//	private class LongOperation extends AsyncTask<String, String, String> {

//		@Override
		protected void onPreExecute() 
		{	
			
			dumpLogs();
			//			Log.d(TAG,"dumpLogs() done");
			mvStatusLogs();
			//			Log.d(TAG, "mvStatusLogs() done");
//			super.onPreExecute();
		}

//		@Override
		protected String doInBackground(String... params) 
		{
			//			Log.d(TAG,"inside doInBackground()");
			String message = params[0];
			SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmm",Locale.US);
			String cdt = sdf.format(new Date());

			File compressedDirectory = new File(Environment.getExternalStorageDirectory().toString()
					+"/robust_compressed");
			if(!compressedDirectory.exists())
				compressedDirectory.mkdir();
			File backupFile = new File(Environment.getExternalStorageDirectory().toString()
					+"/robust_compressed/robust_"+uniqueID+"_"+cdt+".zip");
			mFile = backupFile;
			if (!backupFile.exists())
			{
				String path = Environment.getExternalStorageDirectory().toString()+"/robust";
				File md5sum = new File(Environment.getExternalStorageDirectory().toString()
						+"/robust_compressed/robust_"+uniqueID+"_"+cdt+".md5");
				//				Log.d("Files", "Path: " + path);
				File f = new File(path);        
				File file[] = f.listFiles();
				String [] files = new String[file.length];
				//				Log.d("Files", "Size: "+ file.length);

				/* Get all file names in directory */
				for (int i=0; i < file.length; i++)
				{
					//					Log.d("Files", "FileName:" + file[i].getName());
					files[i] = path+"/"+file[i].getName();
				}

				Compress compress = new Compress(files,backupFile.getAbsolutePath());

				/* Compress all files in directory*/
				if(compress.zip())
				{
					/* Compute MD5 sum and write to sdcard */
					try {
						String hashFile = MD5.asHex(MD5.getHash(backupFile));
						PrintWriter pw = new PrintWriter(md5sum);
						pw.println(hashFile);
						pw.close();
					} catch (IOException e) {
						Log.e(TAG,"Computing md5sum",e);
					}
					for (int i=0; i < file.length; i++)
					{
						if(!file[i].getName().contains("GCPolicy") 
								&& !file[i].getName().contains("RobustPolicies")) 	// Don't delete
						{
							file[i].delete();
						}
					}
					message = "Directory /robust Backed Up\nSaved as "+backupFile.getName();
				} else {
					message =  "Error Compressing Logs " + compress.printError();
					Log.d(TAG,message);
					backupFile.delete();
				}
			} else {
				message = "Backup already created!";
			}

			return message;
		}

//		@Override
		protected void onPostExecute(String result) 
		{
			NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(getApplicationContext());
			mBuilder.setSmallIcon(R.drawable.ic_launcher_3);
			mBuilder.setContentTitle("BackupService");
			
			if(result.equals("Backup already created!")) {
				mBuilder.setContentText(result);
				NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
				Notification noti = mBuilder.build();
				mNotificationManager.notify(ID, noti);
			} else if(mFile.exists()) {
//				mGoogleApiClient.connect();
				mBuilder.setContentText("Uploading to Drive");
				mBuilder.setOngoing(true);
				NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
				Notification noti = mBuilder.build();
				mNotificationManager.notify(ID, noti);
				try {
					@SuppressWarnings("unused")
					Metadata md = new CreateFileAsyncTask(TimedBackupService.this)
					 	.execute(mFile.getAbsolutePath()).get();
				} catch (InterruptedException e) {
					Log.e(TAG,"CreateFileAsyncTask",e);
				} catch (ExecutionException e) {
					Log.e(TAG,"CreateFileAsyncTask",e);
				}
			}
			else if(wakeLock.isHeld())
				wakeLock.release();
			//			SharedPreferences prefs = getSharedPreferences("com.edragone.policychanger", Context.MODE_PRIVATE);
			//			prefs.edit().putLong("lastBackupTime", System.currentTimeMillis());
//			super.onPostExecute(result);
		}

//	}

//	@Override
//	public void onConnectionFailed(ConnectionResult connectionResult) {
//
//		Log.e(TAG,"Drive API connection failed error code:" + connectionResult);
//		if(wakeLock.isHeld())
//			wakeLock.release();
//
//	}

//	@Override
//	public void onConnected(Bundle arg0) {
//
//		Log.i(TAG, "Drive API client connected.");
//		Drive.DriveApi.fetchDriveId(mGoogleApiClient, EXISTING_FOLDER_ID)
//		.setResultCallback(idCallback);
//	}

//	@Override
//	public void onConnectionSuspended(int arg0) {
//		Log.e(TAG, "Drive API client disconnected.");
//		if(wakeLock.isHeld())
//			wakeLock.release();
//	}

//	final private ResultCallback<DriveIdResult> idCallback = new ResultCallback<DriveIdResult>() {
//		@Override
//		public void onResult(DriveIdResult result) {
//			if (!result.getStatus().isSuccess()) {
//				Log.e(TAG,"Cannot find DriveId. Are you authorized to view this file?");
//				wakeLock.release();
//				return;
//			}
//			mFOLDER_ID = result.getDriveId();
//			Drive.DriveApi.newContents(mGoogleApiClient)
//			.setResultCallback(contentsResult);
//		}
//	};

//	final private ResultCallback<ContentsResult> contentsResult = new
//			ResultCallback<ContentsResult>() {
//		@Override
//		public void onResult(ContentsResult result) {
//			if (!result.getStatus().isSuccess()) {
//				Log.e(TAG,"Error while trying to create new file contents");
//				wakeLock.release();
//				return;
//			}
//			//            mFile = new File(Environment.getExternalStorageDirectory().getPath(),"/robust_compressed/robust_eddie.zip");
//			//            Log.i(TAG,"mFile: "+mFile.getAbsolutePath());
//			String filename = mFile.getName();
//			try {
//				OutputStream outputStream = result.getContents().getOutputStream();
//				InputStream is = new FileInputStream(mFile);
//				ByteArrayOutputStream baos = new ByteArrayOutputStream();
//				byte[] buffer = new byte[4096];
//				//					int count = 0;
//				while (is.read(buffer) != -1) {
//					//						Log.i(TAG,"count:"+count);
//					//						Log.i(TAG,"baos size:"+baos.size());
//					baos.write(buffer);
//					baos.flush();
//				}
//				//				Log.i(TAG,"baos size:"+baos.size() + "\nFile size:"+(int)mFile.length());
//
//				outputStream.write(baos.toByteArray());
//				outputStream.flush();
//				is.close();
//				baos.close();
//
//			} catch (Exception e) {
//				Log.e(TAG, "Unable to write file contents.",e);
//			}
//
//
//			// Create the initial metadata - MIME type and title.
//			MetadataChangeSet metadataChangeSet = new MetadataChangeSet.Builder()
//			.setTitle(filename)
//			.setMimeType("application/zip")
//			.build();
//
//			DriveFolder folder = Drive.DriveApi.getFolder(mGoogleApiClient,mFOLDER_ID);
//			folder.createFile(mGoogleApiClient, metadataChangeSet, result.getContents())
//			.setResultCallback(fileCallback);
//		}
//	};


//	final private ResultCallback<DriveFileResult> fileCallback = new
//			ResultCallback<DriveFileResult>() {
//		@Override
//		public void onResult(DriveFileResult result) {
//			if (!result.getStatus().isSuccess()) {
//				Log.e(TAG,"Error while trying to create the file\n"+result.getStatus().getStatusMessage());
//				wakeLock.release();
//				return;
//			}
//			Log.i(TAG,"Created a file: " + result.getDriveFile().getDriveId());
//			mGoogleApiClient.disconnect();
//			wakeLock.release();
//		}
//	};
	public class CreateFileAsyncTask extends ApiClientAsyncTask<String, Void, Metadata>
	{

	    public CreateFileAsyncTask(Context context)
	    {
	        super(context);
	    }
	    
	    @Override
	    protected Metadata doInBackgroundConnected(String... arg0)
	    {
	        // First we start by creating a new contents, and blocking on the
	        // result by calling await().
	        DriveContentsResult contentsResult = Drive.DriveApi.newDriveContents(getGoogleApiClient()).await();

	        if (!contentsResult.getStatus().isSuccess()) {
	            // We failed, stop the task and return.
	            return null;
	        }

	        //file to save in drive
	        String pathFile = arg0[0];
	        File file = new File(pathFile);

	        // Read the contents and open its output stream for writing, then
	        // write a short message.
	        DriveContents originalContents = contentsResult.getDriveContents();
	        OutputStream os = originalContents.getOutputStream();

	        try
	        {
	            InputStream dbInputStream = new FileInputStream(file);

	            byte[] buffer = new byte[4096];
	            int length;
	            while((length = dbInputStream.read(buffer)) > 0)
	            {
	                os.write(buffer, 0, length);
	            }

	            dbInputStream.close();
	            os.flush();
	            os.close();

	        } catch (IOException e) {
	            Log.e(TAG,"Error reading file",e);
	            return null;
	        }

	        // Create the initial metadata - MIME type and title.
	        MetadataChangeSet metadataChangeSet = new MetadataChangeSet.Builder()
	        .setTitle(file.getName())
	        .setMimeType("application/zip")
	        .build();

	        // Create the file in the log folder, again calling await() to
	        // block until the request finishes.
	        mFOLDER_ID = Drive.DriveApi.fetchDriveId(getGoogleApiClient(), EXISTING_FOLDER_ID).await().getDriveId();
	        DriveFolder logFolder = Drive.DriveApi.getFolder(getGoogleApiClient(),mFOLDER_ID);
	        DriveFolder.DriveFileResult fileResult = logFolder.createFile(
	        getGoogleApiClient(), metadataChangeSet, originalContents).await();

	        if (!fileResult.getStatus().isSuccess()) {
	            // We failed, stop the task and return.
	        	String msg = fileResult.getStatus().getStatusMessage();
	        	Log.e(TAG,msg);
	        	NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);	 			
	 	        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(getApplicationContext());
	 			mBuilder.setSmallIcon(R.drawable.ic_launcher_3);
	 			mBuilder.setContentTitle("BackupService");
	 			mBuilder.setContentText(msg);
	        	Notification noti = mBuilder.build();
				mNotificationManager.notify(1, noti);
	            return null;
	        }

	        // Finally, fetch the metadata for the newly created file, again
	        // calling await to block until the request finishes.
	        DriveResource.MetadataResult metadataResult = fileResult.getDriveFile()
	        .getMetadata(getGoogleApiClient())
	        .await();
	         
	        if (!metadataResult.getStatus().isSuccess()) {
	            // We failed, stop the task and return.
	        	String msg = metadataResult.getStatus().getStatusMessage();
	        	Log.e(TAG,msg);
	            return null;
	        }
	        
	        // Track upload
//	        DriveContentsResult currentDriveContentsResult = fileResult.getDriveFile().open(getGoogleApiClient(),
//	                DriveFile.MODE_WRITE_ONLY, null).await();
//	        ExecutionOptions executionOptions = new ExecutionOptions.Builder()
//	        .setNotifyOnCompletion(true)
//	        .build();
//	        	currentDriveContentsResult.getDriveContents().commit(getGoogleApiClient(), null, executionOptions)
//	        	.await();
	        fileResult.getDriveFile().addChangeSubscription(getGoogleApiClient());
	        
	        // We succeeded, return the newly created metadata.
	        return metadataResult.getMetadata();
	    }

	    @SuppressLint("DefaultLocale")
		@Override
	    protected void onPostExecute(Metadata result)
	    {
	        NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
			mNotificationManager.cancel(ID);
			
	        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(getApplicationContext());
			mBuilder.setSmallIcon(R.drawable.ic_launcher_3);
			mBuilder.setOngoing(false);
			mBuilder.setContentTitle("Backup Complete");
			
	        if (result == null)
	        {
	            // The creation failed somehow, so show a message.
	        	Log.e(TAG,"Error while creating file");
	        	mBuilder.setContentText("Error while uploading to Drive");
	        	Notification noti = mBuilder.build();
				mNotificationManager.notify(FILE_NOTI_ID, noti);
				if(wakeLock.isHeld())
					wakeLock.release();
	            return;
	        }
	        // The creation succeeded, show a message.
	        double d = result.getFileSize()/1024.0/1024.0;
	        String tmp = String.format("%.2f",d);
	        String m = "File to upload: " + result.getTitle()+
	        		"\nSize: "+tmp+"MB";
	        mBuilder.setStyle(new NotificationCompat.BigTextStyle().bigText(m));
	        mBuilder.setContentText(m);
	        Notification noti = mBuilder.build();
			mNotificationManager.notify(FILE_NOTI_ID, noti);
	        Log.i(TAG,m);
	        if(wakeLock.isHeld())
	        	wakeLock.release();
	        
	        super.onPostExecute(result);
	    }
	}
	
	@Override
	public void onDestroy()
	{
//		if(wakeLock.isHeld())
//			wakeLock.release();
//		NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
//		mNotificationManager.cancel(ID);
		Log.d(TAG,"onDestroy() called");
		super.onDestroy();
	}
}
