package com.edragone.policychanger;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;

import android.content.Context;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;

public class Common {

	private static String TAG = "Common";

	String policy;
	String uniqueID;
	String dlmStats;

	public Common(String _policy, String _uniqueID, String _dlmStats)
	{
		policy = _policy;
		uniqueID = _uniqueID;
		dlmStats = _dlmStats;

	}

	public void save(final Context context)
	{
		try {

			//Read text from file
			StringBuilder text = new StringBuilder();
			String res = "";
			String tokens[] = new String[3];
			File sdcard = Environment.getExternalStorageDirectory();
			File file = new File(sdcard,"/robust/GCPolicy.txt");
			try {
				BufferedReader br = new BufferedReader(new FileReader(file));
				String line;
				while ((line = br.readLine()) != null) {
					text.append(line+":");
				}
				br.close();
			} catch (Exception e) {
				Toast.makeText(context, e.toString(), Toast.LENGTH_SHORT).show();
			}
			res = text.toString();
			if (res != null) {
				tokens = res.split(":");
			}
			policy = tokens[0];
			PrintWriter pw = new PrintWriter(file);
			pw.write(policy+"\n"+uniqueID+"\n"+dlmStats+"\n");
			pw.close();
		} catch (IOException e) {
			Log.e(TAG, "Failed to write to GCPolicy.txt", e);
		}
		Toast.makeText(context, "Settings Saved",Toast.LENGTH_SHORT).show();
	}

}
