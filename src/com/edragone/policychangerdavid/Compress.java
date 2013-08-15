package com.edragone.policychangerdavid;

import android.util.Log; 

import java.io.BufferedInputStream; 
import java.io.BufferedOutputStream; 
import java.io.FileInputStream; 
import java.io.FileOutputStream; 
import java.util.zip.ZipEntry; 
import java.util.zip.ZipOutputStream;

public class Compress { 
	private static final int BUFFER = 2048; 

	private String[] _files; 
	private String _zipFile; 
	private String error;

	public Compress(String[] files, String zipFile) { 
		_files = files; 
		_zipFile = zipFile; 
	} 

	public boolean zip() { 
		try  { 
			BufferedInputStream origin = null; 
			FileOutputStream dest = new FileOutputStream(_zipFile); 

			ZipOutputStream out = new ZipOutputStream(new BufferedOutputStream(dest)); 

			byte data[] = new byte[BUFFER]; 

			for(int i=0; i < _files.length; i++) 
			{
				if(!_files[i].contains("GCPolicy"))
				{
					//  _files[i] = _files[i].replace(".", "").replace("txt", "")+".txt";
					Log.v("Compress", "Adding: " + _files[i]); 
					FileInputStream fi = new FileInputStream(_files[i]); 
					origin = new BufferedInputStream(fi, BUFFER); 
					ZipEntry entry = new ZipEntry(_files[i].substring(_files[i].lastIndexOf("/") + 1)); 
					out.putNextEntry(entry); 
					int count; 
					while ((count = origin.read(data, 0, BUFFER)) != -1) { 
						out.write(data, 0, count); 
					} 
					origin.close();
				}
			} 

			out.close(); 
		} catch(Exception e) { 
			error = e.toString();
			printError();
			Log.e("PolicyChanger",e.toString()); 
			return false;
		} 
		return true;
	} 
	
	public String printError()
	{
		return error;
	}

} 
