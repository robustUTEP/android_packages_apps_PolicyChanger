package com.edragone.policychangerdavid;


import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import android.os.Bundle;
import android.os.Environment;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.SharedPreferences;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.CheckBox;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;
import android.widget.RadioGroup.OnCheckedChangeListener;

public class MainActivity extends Activity implements OnCheckedChangeListener 
{
	CheckBox isLogging;
	String policy;


	@Override
	protected void onCreate(Bundle savedInstanceState) 
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		SharedPreferences preferences = getPreferences(MODE_PRIVATE);
		policy = preferences.getString("Policy", "3");
		//logging = preferences.getString("Logging", "1");

		isLogging = (CheckBox) findViewById(R.id.checkBox1);
		RadioGroup radgrp = (RadioGroup) findViewById(R.id.rad);
		radgrp.setOnCheckedChangeListener(this);

		getStatus();
	}


	@Override
	public boolean onCreateOptionsMenu(Menu menu) 
	{
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}


	@Override
	public void onCheckedChanged(RadioGroup group, int checkedId) 
	{
		switch(checkedId)
		{
		case R.id.radioButton1:
			policy = "1";
			break;
		case R.id.radioButton2:
			policy = "2";
			break;
		case R.id.radioButton3:
			policy = "4";
			break;
		case R.id.radioButton4:
			policy = "3";
			break;
		}
		Toast.makeText(getApplicationContext(),policy,Toast.LENGTH_SHORT).show();

	}
	public void shouldLog(View v)
	{
		if(!isLogging.isChecked())
			policy = "-"+policy;
		else
			policy = policy.replace("-", "");
		Toast.makeText(getApplicationContext(), policy,Toast.LENGTH_SHORT).show();

	}

	public void save(View v)
	{
		try {
			File myFile = new File(Environment.getExternalStorageDirectory().toString()+"/robust/GCPolicy.txt");
			myFile.createNewFile();
			FileOutputStream fOut = new FileOutputStream(myFile);
			OutputStreamWriter myOutWriter = new OutputStreamWriter(fOut);

			myOutWriter.append(policy+" ,\n");

			myOutWriter.close();
			fOut.close();
		} catch (IOException e) {
			Log.e("PolicyChanger", "IO Exception", e);
		}
		savePreferences();
		Toast.makeText(getApplicationContext(), "Settings Saved",Toast.LENGTH_SHORT).show();

		reboot();
	}

	public void reboot()
	{
		try {
			Process proc = Runtime.getRuntime().exec(new String[] { "su", "-c", "reboot" });
			proc.waitFor();
		} catch (Exception ex) {
			Log.e("PolicyChanger", "Could not reboot", ex);
		}
	}

	public void getStatus()
	{
		int checkedId = Integer.parseInt(policy);
		switch(checkedId)
		{
		case 1:
		case -1:
			RadioButton rb1 = (RadioButton) findViewById(R.id.radioButton1);
			rb1.setChecked(true);
			break;
		case 2:
		case -2:
			RadioButton rb2 = (RadioButton) findViewById(R.id.radioButton2);
			rb2.setChecked(true);
			break;
		case 3:
		case -3:
			RadioButton rb3 = (RadioButton) findViewById(R.id.radioButton4);
			rb3.setChecked(true);
			break;
		case 4:
		case -4:
			RadioButton rb4 = (RadioButton) findViewById(R.id.radioButton3);
			rb4.setChecked(true);
			break;
		}
		int isLogEnabled = Integer.parseInt(policy);
		if(isLogEnabled > 0){
			isLogging.setChecked(true);
		}
	       
	}

	public void changePermission(View v)
	{
		try {
			Process proc = Runtime.getRuntime().exec(new String[]{"su", "-c", "chmod -R 777 /sys/devices/system/cpu/"});
			proc.waitFor();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			Toast.makeText(getApplicationContext(), "Permission Denied "+e.toString(),Toast.LENGTH_SHORT).show();
		}
		Toast.makeText(getApplicationContext(), "Changed filesystem permissions",Toast.LENGTH_SHORT).show();
	}

	@SuppressLint("SdCardPath")
	public void backup(View v)
	{
		SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmm",Locale.US);
		String cdt = sdf.format(new Date());

		File bak = new File(Environment.getExternalStorageDirectory().toString()+"/robust"+cdt+".zip");
		if (!bak.exists())
		{
			String path = Environment.getExternalStorageDirectory().toString()+"/robust";
			Log.d("Files", "Path: " + path);
			File f = new File(path);        
			File file[] = f.listFiles();
			String [] files = new String[file.length];
			Log.d("Files", "Size: "+ file.length);
			for (int i=0; i < file.length; i++)
			{
				Log.d("Files", "FileName:" + file[i].getName());
				files[i] = path+"/"+file[i].getName();
			}

			Compress compress = new Compress(files,bak.getAbsolutePath());

			if(compress.zip())
			{
				for (int i=0; i < file.length; i++)
				{
					if(!file[i].getName().contains("GCPolicy"))
						file[i].delete();
				}
				Toast.makeText(getApplicationContext(), "Log Backed Up",Toast.LENGTH_SHORT).show();
			}
			else
			{
				Toast.makeText(getApplicationContext(), "Error "+compress.printError(),Toast.LENGTH_SHORT).show();
				bak.delete();
			}
		}
		else
		{
			Toast.makeText(getApplicationContext(), "Backup already created!",Toast.LENGTH_SHORT).show();
		}
	}

	public void savePreferences()
	{
		SharedPreferences preferences = getPreferences(MODE_PRIVATE);
		SharedPreferences.Editor editor = preferences.edit();
		editor.putString("Policy", policy);
		//editor.putString("Logging", logging);
		editor.commit();
	}

	@Override
	public void onPause()
	{
		super.onPause();
		savePreferences();
	}

	@Override
	public void onResume()
	{
		super.onResume();
		getStatus();
	}

}
