package com.edragone.policychanger;

import java.io.DataOutputStream;
import java.text.SimpleDateFormat;
import java.util.Calendar;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.app.AlertDialog.Builder;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.NumberPicker;
import android.widget.TextView;
import android.widget.Toast;

public class SettingsActivity extends Activity {

	private static final String TAG = "Settings";

	SharedPreferences prefs;
	CheckBox timedBackupCheckBox;
	EditText deviceID;
	NumberPicker np;
	CheckBox permissionOnBoot;
	CheckBox cpuCores;

	int savedInterval;

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_settings);

		prefs = getSharedPreferences("com.edragone.policychanger", Context.MODE_PRIVATE);

		np = (NumberPicker) findViewById(R.id.numberPicker1);		
		deviceID = (EditText) findViewById(R.id.editText1);
		timedBackupCheckBox = (CheckBox) findViewById(R.id.timedBackupCheckBox);
		permissionOnBoot = (CheckBox) findViewById(R.id.permissionOnBoot);
		cpuCores = (CheckBox) findViewById(R.id.cpuCoresCheckBox);

		deviceID.setText(prefs.getString("deviceID", ""));
		timedBackupCheckBox.setChecked(prefs.getBoolean("timedBackupCheckBox", false));
		permissionOnBoot.setChecked(prefs.getBoolean("permissionOnBoot", true));
		cpuCores.setChecked(prefs.getBoolean("cpuCoresCheckBox", false));

		// Set properties for NumberPicker
		savedInterval = prefs.getInt("TimedInterval", 23);
		String nums[] = getIntervalNumbers(72); //72 allows for 3-day interval backups;
		np.setMaxValue(nums.length-1);
		np.setMinValue(0);
		np.setWrapSelectorWheel(true);
		np.setDisplayedValues(nums);
		np.setValue(savedInterval);
		
		String text = prefs.getString("timeToDisplay", "Next backup:\n\tNot set");
		TextView display = (TextView) findViewById(R.id.timeDisplay);
		display.setText(text);		
	}

	/*
	 * Sets intervals of 1 hour.
	 * @param: Integer range of numbers for NumberPicker.
	 * @return: String array of available intervals to be displayed.
	 */
	private String[] getIntervalNumbers(int range)
	{
		String sNums[] = new String[range];
		sNums[0] = "1";
		for(int i=1; i<range; i++) 
			sNums[i] = Integer.toString(i+1);
		return sNums;
	}

	/*
	 * Sends command to change /sys to rw
	 */
	public void changePermission(View v)
	{
		String [] cmds = {"chmod -R 777 /sys/devices/system/cpu/",
		"echo -1 > /proc/sys/kernel/perf_event_paranoid "};
		RunAsRoot(cmds);
		Toast.makeText(getApplicationContext(), "Changed filesystem permissions",Toast.LENGTH_SHORT).show();
	}

	/*
	 * @param: String array of commands to run as root
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

	public void saveSettings(View v)
	{
		Editor e = prefs.edit();
		e.putBoolean("timedBackupCheckBox", timedBackupCheckBox.isChecked());
		e.putInt("TimedInterval", np.getValue()).commit();
		e.putBoolean("permissionOnBoot", permissionOnBoot.isChecked());

		if(!deviceID.getText().toString().isEmpty()) 
		{
			Common c = new Common(String.valueOf(prefs.getInt("policy", 7)),
					deviceID.getText().toString(),String.valueOf(prefs.getInt("dlmStats", -1)));
			c.save(getApplicationContext());
			e.putString("deviceID", deviceID.getText().toString());
			e.commit();
			enableTimedBackup();
			super.onBackPressed();
		}
		else
			Toast.makeText(getApplicationContext(), "Needs Device ID", Toast.LENGTH_SHORT).show();
	}

	private void enableTimedBackup()
	{	
		Context context = getApplicationContext();
		Intent intent = new Intent(context,TimedBackupService.class);
		intent.putExtra("fromAlarm", true);
		boolean alarmUp = (PendingIntent.getService(context, 0, 
				intent, PendingIntent.FLAG_NO_CREATE) != null);

		if(!timedBackupCheckBox.isChecked()) 
		{
			if(alarmUp)
			{
				PendingIntent pendingIntent = PendingIntent.getService(context, 0, 
						intent, PendingIntent.FLAG_UPDATE_CURRENT);
				AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
				alarmManager.cancel(pendingIntent);
				pendingIntent.cancel();
				prefs.edit().putString("timeToDisplay", "Next backup:\n\tNot set").commit();
				Log.d(TAG,"Alarm canceled");
			}
		} else {
			if (!alarmUp) 
			{
				setAlarmManager(context, intent, np.getValue() + 1);
				Log.d(TAG,"Setting alarm manager");
			} else {	
				//set new time interval if needed
				if(savedInterval != np.getValue()) 
				{
					setAlarmManager(context, intent, np.getValue()+1);
					Log.d(TAG,"Updated alarm interval to "+(np.getValue()+1)+"hrs");
				} else
					Log.d(TAG,"Alarm already active");
			}
		}
	}

	@SuppressLint("SimpleDateFormat")
	public void setAlarmManager(Context context, Intent intent, int time)
	{	
		long timeIntervalMillis = (time*60) * 60 * 1000; //time is in hours, convert to millis
		PendingIntent pendingIntent = PendingIntent.getService(context, 0, 
				intent, PendingIntent.FLAG_UPDATE_CURRENT);
		Calendar calendar = Calendar.getInstance();
		long timeNow = calendar.getTimeInMillis();
		AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
		alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, timeNow, timeIntervalMillis, pendingIntent);
		
		SimpleDateFormat sdf = new SimpleDateFormat("MM/dd HH:mm");
		calendar.add(Calendar.HOUR, time);
		String timeToDisplay = sdf.format(calendar.getTime());
		String text = "Next backup:\n\t"+timeToDisplay;
		TextView display = (TextView) findViewById(R.id.timeDisplay);
		display.setText(text);
		calendar.setTimeInMillis(timeNow);
		calendar.add(Calendar.HOUR, time);
		prefs.edit().putString("timeToDisplay", text).commit();
		prefs.edit().putLong("backupAlarmTime", calendar.getTimeInMillis()).commit();
		Log.d(TAG,"Time to schedule: "+timeToDisplay);
	}

	public void cpuCores(View v)
	{
		prefs.edit().putBoolean("cpuCoresCheckBox", ((CheckBox) v).isChecked()).apply();
		if(((CheckBox) v).isChecked()) {
			try {
				String [] s = {"echo 0 > /sys/devices/system/cpu/cpu1/online", 
				"chmod 444 /sys/devices/system/cpu/cpu1/online"};
				RunAsRoot(s);
			} catch (Exception e) {
				Toast.makeText(getApplicationContext(), "Permission Denied "+e.toString(),Toast.LENGTH_SHORT).show();
			}
			Toast.makeText(getApplicationContext(), "1 Core Active",Toast.LENGTH_SHORT).show();
		} else {

			/* Alert user of reboot */
			AlertDialog.Builder builder = new Builder(SettingsActivity.this);
			builder.setMessage("System needs to reboot for changes to take effect.");
			builder.setTitle("Enable All Cores");

			builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int which) {
					// do something after confirm
					Toast.makeText(SettingsActivity.this, "System Rebooting", Toast.LENGTH_SHORT).show();
					reboot();
				}
			});	

			builder.create().show();
		}
	}

	public void reboot()
	{
		try {
			Runtime.getRuntime().exec(new String[] { "su", "-c", "reboot" });
			//			proc.waitFor();
		} catch (Exception ex) {
			Log.e(TAG, "Could not reboot", ex);
		}
	}

	@Override
	public void onBackPressed()
	{
		saveSettings(null);
	}

}
