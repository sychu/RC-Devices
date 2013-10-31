package pl.apcode.rcdevices;

import android.util.Log;

//Just a wrapper for now but in future it will feed LogActivity
public class EventLogger {
	
	public static void e(String tag, String msg) {
		e(msg, null);
	}
	
	public static void e(String tag, String msg, Exception ex) {
		Log.e(tag, msg);
		if(ex != null)
			Log.d(tag, ex.getMessage());
	}
	
	public static void i(String tag, String msg) {
		Log.i(tag, msg);
	}
	
	public static void w(String tag, String msg) {
		Log.w(tag, msg);
	}
	
	public static void d(String tag, String msg) {
		Log.d(tag, msg);
	}
	
}
