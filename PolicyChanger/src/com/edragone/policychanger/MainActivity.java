package com.edragone.policychanger;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks;
import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.drive.Drive;
import com.google.android.gms.drive.DriveApi.DriveIdResult;
import com.google.android.gms.drive.DriveContents;
import com.google.android.gms.drive.DriveFile;
import com.google.android.gms.drive.DriveId;
import com.google.android.gms.drive.DriveApi.DriveContentsResult;
//import com.google.android.gms.drive.DrivePreferencesApi.FileUploadPreferencesResult;

import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningServiceInfo;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.app.AlertDialog.Builder;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender;
import android.content.IntentSender.SendIntentException;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

public class MainActivity extends Activity implements ConnectionCallbacks, OnConnectionFailedListener
{
	private static final String TAG = "PolicyChanger";
	//	private static final boolean DEBUGGING = false;
	CheckBox isLogging;
	CheckBox dlmStats;
	String policy;
	Spinner spinner;
	TextView display;
	ToggleButton driveButton;
	CheckBox screenshotCheckBox;

	int currentPolicy;
	int selectedPolicy;
	String [] policies;

	int pdProgress;
	String pdMessage;
	String uniqueID;

	//	RelativeLayout ul;
	RelativeLayout ml;

	SharedPreferences prefs;

	GoogleApiClient mGoogleApiClient;
	WakeLock wakeLock;

	private static final int REQUEST_CODE_RESOLUTION = 3;
	private static final int ACCESS_GRANTED = 2;
	//	private DriveId mFOLDER_ID;
	//	private DriveId robustFolderID;
	//	private static File mFile; 
	private static final String LOGS_FOLDER_ID = "0B5m56xhm_hJLVTZIUVpYUG55SE0"; // Robust log shared folder
	private static final String POLICIES_FILE_ID = "0B3DQcb4QSHNMM0pYN3FVcjFUc0E";
	//	private static final String ROBUST_FOLDER = "0B8Oo94Out4zdc1ZXRDFNa3dxM3c";

	//	 private GoogleApiClient mGoogleApiClient;

	@Override
	protected void onCreate(Bundle savedInstanceState) 
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		PowerManager mgr = (PowerManager) getSystemService(Context.POWER_SERVICE);
		wakeLock = mgr.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "DeleteOperationWakeLock");

		mGoogleApiClient = new GoogleApiClient.Builder(this)
		.addApi(Drive.API)
		.addScope(Drive.SCOPE_FILE)
		.addConnectionCallbacks(this)
		.addOnConnectionFailedListener(this)
		.build();
		mGoogleApiClient.connect();

		prefs = getSharedPreferences(
				"com.edragone.policychanger", Context.MODE_PRIVATE);

		//		ul = (RelativeLayout) findViewById(R.id.uniquelayout);
		ml = (RelativeLayout) findViewById(R.id.mainlayout);
		//		ul.setVisibility(View.INVISIBLE);
		display = (TextView) findViewById(R.id.textView1);
		isLogging = (CheckBox) findViewById(R.id.checkBox1);
		dlmStats = (CheckBox) findViewById(R.id.dlmStats);
		spinner = (Spinner) findViewById(R.id.spinner1); 
		driveButton = (ToggleButton) findViewById(R.id.toggleButton1);
		screenshotCheckBox = (CheckBox) findViewById(R.id.screenshot);  

		//Enable Landscape for tablets
		boolean tabletSize = getResources().getBoolean(R.bool.isTablet);
		if (tabletSize) 
			setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);

		/* Read policies from local file */
		File robustPolicies = new File(Environment.getExternalStorageDirectory().toString()+"/robust/RobustPolicies.txt");
		policies = null;
		if(!robustPolicies.exists()) {
			try {
				robustPolicies.createNewFile();
				String [] temp = {"Baseline","MI2","MI2S","MI2A","MI2AE", "MI2AI", "MI1AI", 
						"MI2AD", "MI1D", "MMI2AD", "MMI1AD", "RT1", "RT2", "RT4", "MI4-spleen",
						"MI1-spleen", "MI2-spleen"};
				policies = temp;
				PrintWriter pw = new PrintWriter(robustPolicies);
				for(String p : temp)
					pw.println(p);
				pw.close();
			} catch (IOException e) {
				Log.e(TAG,"Error creating file",e);
			}
		} else {
			try {
				policies  = readLines(robustPolicies.getPath());
			} catch (IOException e) {
				Log.e(TAG,"Error reading file",e);
			}
		}

		if(policies != null)
			addItemsOnSpinner(policies);
		uniqueID = prefs.getString("deviceID", "Default_ID"); //Default for now.

		spinner.setOnItemSelectedListener(new OnItemSelectedListener() {
			@Override
			public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
				selectedPolicy = spinner.getSelectedItemPosition() + 1;
				if(!isLogging.isChecked())
					selectedPolicy *= -1;
				prefs.edit().putInt("policy", selectedPolicy).commit();
				//				Toast.makeText(getApplicationContext(), "policy: "+selectedPolicy,Toast.LENGTH_SHORT).show();
			}

			@Override
			public void onNothingSelected(AdapterView<?> parentView) {
				// Do nothing. Method implementation needed.
			}
		});   
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) 
	{
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		int itemId = item.getItemId();
		switch(itemId) {
		case R.id.action_settings:
			openSettings();
			return true;
		case R.id.About:
			showVersion();
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}

	//ToggleButton not enabled yet
	public void changeDriveConnection(View v)
	{
		if(driveButton.isChecked() && mGoogleApiClient.isConnected())
		{
			mGoogleApiClient.disconnect();
			Toast.makeText(getBaseContext(), "Disconnected from Drive", Toast.LENGTH_SHORT).show();
		}
		else
		{
			mGoogleApiClient.reconnect();
		}
	}

	public void showVersion()
	{
		Context context = getApplicationContext();
		try {
			String versionName = context.getPackageManager()
					.getPackageInfo(context.getPackageName(), 0).versionName;
			Toast.makeText(context, "Version "+versionName, Toast.LENGTH_LONG).show();
		} catch (NameNotFoundException e) {
			Log.e(TAG,"Failed to get packageName()",e);
		}
	}

	public String[] readLines(String filename) throws IOException {
		FileReader fileReader = new FileReader(filename);
		BufferedReader bufferedReader = new BufferedReader(fileReader);
		List<String> lines = new ArrayList<String>();
		String line = null;
		while ((line = bufferedReader.readLine()) != null) {
			lines.add(line);
		}
		bufferedReader.close();
		return lines.toArray(new String[lines.size()]);
	}

	public void addItemsOnSpinner(String[] s) 
	{
		ArrayAdapter<String> dataAdapter = new ArrayAdapter<String>(this,android.R.layout.simple_spinner_item, s);
		dataAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		spinner.setAdapter(dataAdapter);
	}

	public void openSettings()
	{
		//		ml.setVisibility(View.INVISIBLE);
		//		ul.setVisibility(View.VISIBLE);
		Intent myIntent = new Intent(MainActivity.this, SettingsActivity.class);
		//		myIntent.putExtra("key", value); //Optional parameters
		MainActivity.this.startActivity(myIntent);
	}

	public void mvStatusLogs()
	{
		String cmds [] = {"cp -r /data/data/*/*.status /sdcard/robust/", 
		"rm -r /data/data/*/*.status"};
		RunAsRoot(cmds);
	}

	public void logChangesToPolicy()
	{
		String s = "@policyChanged{\"deviceID\":"+uniqueID+",\"policy\":"+policy+",\"wcTime-ms\":"
				+System.currentTimeMillis()+"}\n";
		File policyChanges = new File(Environment.getExternalStorageDirectory().toString()+"/robust/PolicyChanges.txt");
		if (!policyChanges.exists())
		{
			try {
				policyChanges.createNewFile();
				FileOutputStream fOut = new FileOutputStream(policyChanges);
				OutputStreamWriter myOutWriter = new OutputStreamWriter(fOut);
				myOutWriter.append(s);
				myOutWriter.close();
				fOut.close();
			} catch (Exception e) {
				Log.e(TAG, "Error writting to PolicyChanges.txt", e);
			}	
		}
	}

	public void save(View v)
	{
		int p = spinner.getSelectedItemPosition() + 1;
		int iDlmStats = 0;
		if(!isLogging.isChecked())
			p *= -1;
		policy = Integer.toString(p);
		if(dlmStats.isChecked())
			iDlmStats = 1;
		try {
			File myFile = new File(Environment.getExternalStorageDirectory().toString()+"/robust/GCPolicy.txt");
			myFile.createNewFile();
			FileOutputStream fOut = new FileOutputStream(myFile);
			OutputStreamWriter myOutWriter = new OutputStreamWriter(fOut);

			myOutWriter.append(policy+"\n"+uniqueID+"\n"+iDlmStats+"\n");

			myOutWriter.close();
			fOut.close();
		} catch (IOException e) {
			Log.e(TAG, "Failed to write to GCPolicy.txt", e);
		}
		Toast.makeText(getApplicationContext(), "Settings Saved",Toast.LENGTH_SHORT).show();
		logChangesToPolicy();

		/* Alert user of reboot */
		AlertDialog.Builder builder = new Builder(MainActivity.this);
		builder.setMessage("System needs to reboot for changes to take effect.");
		builder.setTitle("Confirmation Dialog");
		
		 builder.setNeutralButton("Soft Reboot", new DialogInterface.OnClickListener() {
		      public void onClick(DialogInterface dialog, int id) {
		    	  softReboot();
		      }	
		 });

		builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
				// do something after confirm
				Toast.makeText(MainActivity.this, "System Rebooting", Toast.LENGTH_SHORT).show();
				reboot();
			}
		});	

		builder.setNegativeButton("CANCEL", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
				dialog.cancel();
			}
		});	

		builder.create().show();	

	}

	//Screenshot check box
	public void screenshotChanged(View v)
	{
		boolean isChecked = screenshotCheckBox.isChecked();

		Context context = getApplicationContext();
		Intent myIntent = new Intent(context, ScreenshotService.class);
		PendingIntent pendingIntent = PendingIntent.getService(context,  0, myIntent, 0);

		AlarmManager alarmManager = (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
		Calendar calendar = Calendar.getInstance();
		calendar.setTimeInMillis(System.currentTimeMillis());
		long frequency= 30 * 1000; // in ms 

		if (isChecked) {
			Log.d(TAG, "Starting screenshot service");
			alarmManager.setRepeating(AlarmManager.RTC, calendar.getTimeInMillis(), frequency, pendingIntent);
			//			startService(myIntent);
		} else {
			Log.d(TAG, "Stopping screenshot service");
			alarmManager.cancel(pendingIntent);
			stopService(myIntent);
		}

		prefs.edit().putBoolean("screenshotCheckBox", isChecked).commit();
	}

	//Logging check box
	public void loggingChanged(View v)
	{
		selectedPolicy *= -1;	
		Toast.makeText(getApplicationContext(), "policy: "+selectedPolicy, Toast.LENGTH_SHORT).show();
	}

	/*
	 * Saves logcat and demsg to sdcard.
	 */
	public void dumpLogs(View v)
	{
		//		String path = Environment.getExternalStorageDirectory().toString()+"/robust";
		SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmm",Locale.US);
		String cdt = sdf.format(new Date());

		//Prepare the commands
		String[] cmds ={"logcat -d -f /sdcard/robust/logcat"+cdt+".txt","dmesg > /sdcard/robust/dmesg"+cdt+".txt"};

		if(RunAsRoot(cmds))
			Toast.makeText(this.getApplicationContext(), "Write successful. Logs at /sdcard/robust/", Toast.LENGTH_SHORT).show();
		else
			Toast.makeText(this.getApplicationContext(), "Log write error", Toast.LENGTH_SHORT).show();
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
			Toast.makeText(this.getApplicationContext(), "Command Unsuccessful "+e.toString(), Toast.LENGTH_SHORT).show();
		}
		return success;
	}

	/*
	 * Sends reboot command to phone.
	 */
	public void reboot()
	{
		try {
			Runtime.getRuntime().exec(new String[] { "su", "-c", "reboot" });
			//			proc.waitFor();
		} catch (Exception ex) {
			Log.e(TAG, "Could not reboot", ex);
		}
	}
	
	public void softReboot()
	{
		String rebootCmd[] = {"killall zygote"};
		RunAsRoot(rebootCmd);
	}

	/*
	 * Checks policy status.
	 * Display current policy to user.
	 */
	public void getStatus()
	{
		String res = null;
		String tokens[] = new String[3];
		File sdcard = Environment.getExternalStorageDirectory();

		int iPolicy;
		//Check if robust directory exists. If not, create one.
		File directory = new File(sdcard,"/robust");
		if(!directory.exists())
			directory.mkdir();

		//Get the text file
		File file = new File(sdcard,"/robust/GCPolicy.txt");

		if(!file.exists()) {
			showMessage("File does not exists, Creating file with baseline");
			policy = "1";
			openSettings();
			Log.d(TAG,"openSettings() 1");
			save(null);
			iPolicy = Integer.parseInt(policy);
		} else {
			//Read text from file
			StringBuilder stringBuilder = new StringBuilder();
			try {
				BufferedReader br = new BufferedReader(new FileReader(file));
				String line;

				while ((line = br.readLine()) != null) {
					stringBuilder.append(line+":");
				}
				br.close();
			} catch (Exception e) {
				Toast.makeText(getApplicationContext(), e.toString(), Toast.LENGTH_SHORT).show();
			}

			res = stringBuilder.toString();
			if (res != null) {
				//			res = res.replace(" ", "").replace(",", "");
				tokens = res.split(":");
			}

			if(tokens.length <= 1) {
				openSettings();
				Log.d(TAG,"openSettings() 2");
			}
			else if(tokens.length < 3)
			{
				String[] tmp = {tokens[0],tokens[1],"-1"};
				tokens = tmp;
			}
			else {
				uniqueID = tokens[1];
				prefs.edit().putString("deviceID", uniqueID).commit();
				Log.d(TAG, "Policy: "+tokens[0] + "\n" +tokens[1] + "\n" + "dlmStats:"+tokens[2]);
			}

			try 
			{
				iPolicy = Integer.parseInt(tokens[0]);
				int dlmInt = Integer.parseInt(tokens[2]);
				if(dlmInt == 1)
					dlmStats.setChecked(true);
				else
					dlmStats.setChecked(false);
				prefs.edit().putInt("dlmStats", dlmInt).commit();

			} catch(Exception e) {
				Log.e(TAG, "Error Parsing Integer. Setting iPolicy to 1", e);
				iPolicy = 1;
				spinner.setSelection(Math.abs(iPolicy)-1);
				if(iPolicy > 0)
					isLogging.setChecked(true);
				else
					isLogging.setChecked(false);
				save(null);
			}
		}

		//Check that policy is in list
		if(iPolicy > policies.length)
			iPolicy = 1;

		//			Log.d(TAG,"setting selection");
		spinner.setSelection(Math.abs(iPolicy)-1);
		//			Log.e(TAG, "Unknown Policy", e);

		String[] shouldLog = {"Enabled", "Disabled"};
		if(iPolicy > 0)
			isLogging.setChecked(true);
		else
			isLogging.setChecked(false);
		display.setText("Current Policy: "+spinner.getSelectedItem().toString()+
				"\nLogging is: "+(isLogging.isChecked()?shouldLog[0]:shouldLog[1])+
				"\nDevice ID: "+uniqueID);
		currentPolicy = iPolicy;
		//		Log.d(TAG,"after setting selection");
		screenshotCheckBox.setChecked(prefs.getBoolean("screenshotCheckBox", false));
	}

	/*
	 * Sends command to change system to rw
	 */
	public void changePermission(View v)
	{
		try {
			Process proc = Runtime.getRuntime().exec(new String[]{"su", "-c", "chmod -R 777 /sys/devices/system/cpu/"});
			proc.waitFor();
		} catch (Exception e) {
			Toast.makeText(getApplicationContext(), "Permission Denied "+e.toString(),Toast.LENGTH_SHORT).show();
		}
		Toast.makeText(getApplicationContext(), "Changed filesystem permissions",Toast.LENGTH_SHORT).show();
	}

	/*
	 * Clears all logs under /sdcard/robust
	 */
	public void clearLog(View v)
	{
		/* Get count of logs */
		String path = Environment.getExternalStorageDirectory().toString()+"/robust";
		//		Log.d("Files", "Path: " + path);
		File f = new File(path);        
		File file[] = f.listFiles();
		v.setEnabled(false);
		/* Alert user of action */
		AlertDialog.Builder builder = new Builder(MainActivity.this);
		builder.setMessage("Are you sure you want to clear all " + (file.length-1) +" logs?"+
				" (including logcat and dmesg)");
		builder.setTitle("Delete Logs");

		builder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
				dialog.cancel();
				new deleteOperation().execute("");
			}
		});	

		builder.setNegativeButton("No", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
				dialog.cancel();
			}
		});	

		builder.create().show();
		v.setEnabled(true);
	}

	/*
	 * Backups all logs under /sdcard/robust
	 */
	public void backup(View v)
	{
		if(!isMyServiceRunning()) {
			showMessage("Backup service started");
			Intent intent = new Intent(this,TimedBackupService.class);
			this.startService(intent);
		}
		else
			showMessage("Backup service already running");
	}
	private boolean isMyServiceRunning() {
		ActivityManager manager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
		for (RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
			//	    	Log.i(TAG,service.service.getClassName());
			if ("com.edragone.policychanger.TimedBackupService".equals(service.service.getClassName())) {
				return true;
			}
		}
		return false;
	}

	private void showMessage(String message)
	{
		Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
	}

	private class deleteOperation extends AsyncTask<String, String, String> {

		ProgressDialog pd;
		WakeLock wakeLock;

		@Override
		protected void onPreExecute() 
		{	
			pd = new ProgressDialog(MainActivity.this);
			pd.setTitle("Deleting...");
			pd.setMessage("Please wait.");
			pd.setCancelable(false);
			pd.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
			pd.setIndeterminate(false);
			pd.show();

			PowerManager mgr = (PowerManager) getSystemService(Context.POWER_SERVICE);
			wakeLock = mgr.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "CompressWakeLock");
			wakeLock.acquire();

			super.onPreExecute();
		}

		@Override
		protected String doInBackground(String... params) 
		{	
			// Delete activity status logs.
			String cmds[] = {"rm -r /data/data/*/*.status"};
			RunAsRoot(cmds);

			String path = Environment.getExternalStorageDirectory().toString()+"/robust";
			//				Log.d("Files", "Path: " + path);
			File f = new File(path);        
			File file[] = f.listFiles();
			pd.setMax(file.length);

			for (int i=0; i < file.length; i++) {
				if(!file[i].getName().contains("GCPolicy") && !file[i].getName().contains("RobustPolicies")) {
					file[i].delete();
					publishProgress(file[i].getName().toString());

					//try {Thread.sleep(5000);} catch (InterruptedException e) {} //For testing

				}
				pd.setProgress(i+1);	
			}


			return "All logs deleted!";
		}

		@Override
		protected void onProgressUpdate(String... values) 
		{
			pd.setMessage(values[0]);
		}

		@Override
		protected void onPostExecute(String result) 
		{
			if (pd!=null) {
				//				pBar.setVisibility(View.INVISIBLE);
				pd.dismiss();
			}
			showMessage(result);
			wakeLock.release();
			super.onPostExecute(result);
		}
	}


	/*
	 * (non-Javadoc)
	 * @see android.app.Activity#onBackPressed()
	 * Override to alert user of unsaved changes to policy.
	 */
	@Override
	public void onBackPressed()
	{
		if(currentPolicy != selectedPolicy){
			/* Alert user of reboot */
			AlertDialog.Builder builder = new Builder(MainActivity.this);
			builder.setMessage("System needs to reboot for changes to take effect.");
			builder.setTitle("Changes Not Saved");

			builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int which) {
					// do something after confirm
					Toast.makeText(MainActivity.this, "System Rebooting", Toast.LENGTH_SHORT).show();
					reboot();
				}
			});	

			builder.setNegativeButton("CANCEL", new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int which) {
					dialog.dismiss();
					MainActivity.this.finish();
				}
			});	

			builder.create().show();	
		}		
		else
			super.onBackPressed();
	}

	@Override
	public void onResume()
	{
		super.onResume();
		getStatus();
		//		if(!mGoogleApiClient.isConnected())
		//			mGoogleApiClient.reconnect();
	}

	@Override
	public void onPause()
	{
		super.onPause();
	}

	@Override
	public void onStop()
	{
		if(mGoogleApiClient.isConnected())
			mGoogleApiClient.disconnect();
		if(wakeLock.isHeld())
			wakeLock.release();
		super.onStop();
	}

	@Override
	public void onConnectionFailed(ConnectionResult connectionResult) {
		if (connectionResult.hasResolution()) {
			try {
				connectionResult.startResolutionForResult(this, REQUEST_CODE_RESOLUTION);
			} catch (IntentSender.SendIntentException e) {
				Log.e(TAG,"Drive API connection failed",e);
			}
		} else {
			GooglePlayServicesUtil.getErrorDialog(connectionResult.getErrorCode(), this, 0).show();
		}

	}

	@Override
	protected void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
		switch (requestCode) {
		//	        ...
		case REQUEST_CODE_RESOLUTION:
			if (resultCode == RESULT_OK) {
				mGoogleApiClient.reconnect();
			}
			break;

		case ACCESS_GRANTED:
			Drive.DriveApi.fetchDriveId(getGoogleApiClient() , POLICIES_FILE_ID)
			.setResultCallback(fileIdCallback);
			break;
		}
	}


	@Override
	public void onConnected(Bundle connectionHint) {		
		driveButton.setChecked(true);
		Log.d(TAG, "Drive API client connected.");

		/*
		 * Get proper access to the correct folders and files from user.
		 */
		Drive.DriveApi.fetchDriveId(getGoogleApiClient() , LOGS_FOLDER_ID)
		.setResultCallback(LogsIdCallback);
	}

	@Override
	public void onConnectionSuspended(int arg0) {
		driveButton.setChecked(false);

	}

	final private ResultCallback<DriveIdResult> LogsIdCallback = new ResultCallback<DriveIdResult>() {
		@Override
		public void onResult(DriveIdResult result) {

			if (!result.getStatus().isSuccess()) {
				Log.i(TAG,"Cannot find LOGS_FOLDER_ID. Are you authorized to view this file?");
				//	                return;
				if(mGoogleApiClient.isConnected()) 
				{
					IntentSender intentSender = Drive.DriveApi
							.newOpenFileActivityBuilder()
							.setMimeType(new String[] { })
							.setActivityTitle("Select file in Logs Folder")
							//				            .setActivityStartFolder()
							.build(mGoogleApiClient);
					try {
						startIntentSenderForResult(
								intentSender, ACCESS_GRANTED, null, 0, 0, 0);
					} catch (SendIntentException e) {
						Log.w(TAG, "Unable to send intent", e);
					}
				}
				return;
			}
			//			Drive.DriveApi.fetchDriveId(getGoogleApiClient() , ROBUST_FOLDER)
			//			.setResultCallback(RobustIdCallback);
			Drive.DriveApi.fetchDriveId(getGoogleApiClient() , POLICIES_FILE_ID)
			.setResultCallback(fileIdCallback);
		}
	};

	final private ResultCallback<DriveIdResult> fileIdCallback = new ResultCallback<DriveIdResult>() {
		@Override
		public void onResult(DriveIdResult result) {

			if (!result.getStatus().isSuccess()) {
				Log.i(TAG,"Cannot find POLICIES_FILE_ID. Are you authorized to view this file?");
				//	                return;
				if(mGoogleApiClient.isConnected()) 
				{
					IntentSender intentSender = Drive.DriveApi
							.newOpenFileActivityBuilder()
							.setMimeType(new String[] { })
							.setActivityTitle("Select GCPolicies File")
							.build(mGoogleApiClient);
					try {
						startIntentSenderForResult(
								intentSender, ACCESS_GRANTED, null, 0, 0, 0);
					} catch (SendIntentException e) {
						Log.w(TAG, "Unable to send intent", e);
					}
				}
				return;
			}
			/*
			 * Get Robust policies from Drive
			 */
			new RetrieveDriveFileContentsAsyncTask(
					MainActivity.this).execute(result.getDriveId());
		}
	};

	/*
	 * 
	 */
	final private class RetrieveDriveFileContentsAsyncTask
	extends ApiClientAsyncTask<DriveId, Boolean, String> {

		public RetrieveDriveFileContentsAsyncTask(Context context) {
			super(context);
		}

		@Override
		protected String doInBackgroundConnected(DriveId... params) {
			String contents = null;
			DriveFile file = Drive.DriveApi.getFile(getGoogleApiClient(), params[0]);
			DriveContentsResult driveContentsResult =
					file.open(getGoogleApiClient() , DriveFile.MODE_READ_ONLY, null).await();
			if (!driveContentsResult.getStatus().isSuccess()) {
				Log.e(TAG,"File open not successfull");
				return null;
			}
			DriveContents driveContents = driveContentsResult.getDriveContents();
			BufferedReader reader = new BufferedReader(
					new InputStreamReader(driveContents.getInputStream()));
			StringBuilder builder = new StringBuilder();
			String line;
			try {
				while ((line = reader.readLine()) != null) {
					builder.append(line+"\n");
				}
				contents = builder.toString();
			} catch (IOException e) {
				Log.e(TAG, "IOException while reading from the stream", e);
			}
			driveContents.discard(getGoogleApiClient());
			return contents;
		}

		@Override
		protected void onPostExecute(String result) {
			super.onPostExecute(result);
			if (result == null) {
				Log.d(TAG, "Error while reading from the file");
				return;
			}

			//Update policies on spinner
			String[] newPoliciesList = result.split("\n");
			int position = spinner.getSelectedItemPosition();
			addItemsOnSpinner(newPoliciesList);
			//Set back to current policy
			if (position < spinner.getCount())
				spinner.setSelection(position);
			Log.d(TAG,"GCPolicies Updated");
			updateLocalPolicyFile(newPoliciesList);
			getStatus();
		}
	}

	public void updateLocalPolicyFile(String[] newPoliciesList) {
		File robustPolicies = new File(Environment.getExternalStorageDirectory().toString()+"/robust/RobustPolicies.txt");
		try {
			PrintWriter pw = new PrintWriter(robustPolicies);
			for(String line : newPoliciesList)
				pw.println(line);
			pw.close();
		} catch (IOException e) {
			Log.e(TAG,"Error creating file",e);
		}
	}

	/**
	 * Getter for the {@code GoogleApiClient}.
	 */
	public GoogleApiClient getGoogleApiClient() {
		return mGoogleApiClient;
	}

}