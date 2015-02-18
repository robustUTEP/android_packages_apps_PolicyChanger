package com.edragone.policychanger;

import java.io.DataOutputStream;
import java.text.SimpleDateFormat;
import java.util.Calendar;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

public class OnBootReceiver extends BroadcastReceiver {

	private static final String TAG = "OnBootReceiver";
	SharedPreferences prefs;

	@Override
	public void onReceive(final Context context, Intent intent) {    	

		prefs = context.getSharedPreferences(
				"com.edragone.policychanger", Context.MODE_PRIVATE);
		boolean changePermissionOnBoot = prefs.getBoolean("permissionOnBoot", false);
		if (changePermissionOnBoot)
		{
			changePermission();
			Log.d(TAG, "ON BOOT change permission");
		}
		boolean timedBoot = prefs.getBoolean("timedBackupCheckBox", false);
		if (timedBoot) 
		{
			int time = prefs.getInt("TimedInterval", 0) + 1;
			setAlarmManager(context,time);
		}
		boolean isChecked = prefs.getBoolean("screenshotCheckBox", false);
		if(isChecked)
		{
			screenshotChecked(context);
			Log.d(TAG, "start screenshots");
		}
		boolean isCPUCores1 = prefs.getBoolean("cpuCoresCheckBox", false);
		if(isCPUCores1)
		{
			cpuCores(isCPUCores1);
		}
	}

	/*
	 * Sends command to change system to rw
	 */
	public void changePermission()
	{
		Log.d(TAG,"change permissions");
		String [] cmds = {"chmod -R 777 /sys/devices/system/cpu/",
		"echo -1 > /proc/sys/kernel/perf_event_paranoid "};
		RunAsRoot(cmds);
	}

	/*
	 * Set alarm manager to backup logs every hour.
	 */
	@SuppressLint("SimpleDateFormat")
	public void setAlarmManager(Context context, int time)
	{
		long timeIntervalMillis = (time*60) * 60 * 1000; //time is in hours, converting to millis
		
		Intent intent = new Intent(context,TimedBackupService.class);
		intent.putExtra("fromAlarm", true);
		PendingIntent pendingIntent = PendingIntent.getService(context, 0, 
				intent, PendingIntent.FLAG_UPDATE_CURRENT);
		Calendar calendar = Calendar.getInstance();
//		calendar.setTimeInMillis(System.currentTimeMillis());
		calendar.add(Calendar.MINUTE, 1); //Start after 1 minute.
		
		/* Get last set backup time; if not available use current time. */
		long lastBackupTime = prefs.getLong("backupAlarmTime", calendar.getTimeInMillis());
		AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
		alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, lastBackupTime, timeIntervalMillis, pendingIntent);
		
		calendar.setTimeInMillis(lastBackupTime);
		SimpleDateFormat sdf = new SimpleDateFormat("MM/dd HH:mm");
		String timeToDisplay = sdf.format(calendar.getTime());
		String text = "Next backup:\n\t"+timeToDisplay;
		prefs.edit().putString("timeToDisplay", text).commit();
		Log.d(TAG,"Setting Alarm: "+text);
	}

	public void screenshotChecked(Context context)
	{
		Intent myIntent = new Intent(context, ScreenshotService.class);
		PendingIntent pendingIntent = PendingIntent.getService(context,  0, myIntent, 0);

		AlarmManager alarmManager = (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
		Calendar calendar = Calendar.getInstance();
		calendar.setTimeInMillis(System.currentTimeMillis());
		calendar.add(Calendar.MINUTE, 1); //Start after 1 minute
		long frequency= 30 * 1000; // in ms 

		Log.d(TAG, "CheckBox checked, setting alarm");
		alarmManager.setRepeating(AlarmManager.RTC, calendar.getTimeInMillis(), frequency, pendingIntent);
		//Start once after 1 min device boot
		//		alarmManager.set(AlarmManager.RTC, calendar.getTimeInMillis(), pendingIntent);
		//		context.startService(myIntent);

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
		}
		return success;
	}

	public void cpuCores(boolean isCPUCores1)
	{
		Log.d(TAG,"1 CPU Core "+isCPUCores1);
		if(isCPUCores1) {
			try {
				String [] s = {"echo 0 > /sys/devices/system/cpu/cpu1/online", 
						"chmod 444 /sys/devices/system/cpu/cpu1/online"};
				RunAsRoot(s);
			} catch (Exception e) {
				Log.e(TAG,"permission denied",e);
			}

		}
	}
}