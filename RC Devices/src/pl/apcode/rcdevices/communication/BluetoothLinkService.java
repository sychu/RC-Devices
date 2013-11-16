package pl.apcode.rcdevices.communication;

import java.io.IOException;
import java.io.OutputStream;
import java.io.ObjectOutputStream.PutField;
import java.lang.reflect.Method;
import java.util.UUID;

import pl.apcode.rcdevices.EventLogger;
import pl.apcode.rcdevices.R;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.Process;
import android.preference.PreferenceManager;
import android.util.Log;


public class BluetoothLinkService extends Service {	

	public static final String tag = BluetoothLinkService.class.getSimpleName();
	
	public static final String RECEIVED_CMD = "pl.apcode.rcdevices.communication.RECEIVED_CMD";
	
	private Looper mServiceLooper;
	private BluetoothLinkServiceHandler mServiceHandler;
	private final IBinder mBinder = new LocalBinder();

    /**
     * Class used for the client Binder.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with IPC.
     */
    public class LocalBinder extends Binder {
    	public BluetoothLinkService getService() {
            // Return this instance of LocalService so clients can call public methods
            return BluetoothLinkService.this;
        }
    }

	
	@Override
	public void onCreate() {
		Log.d(tag, "onCreate");
		
		HandlerThread thread = new HandlerThread("BluetoothLinkService",
	            Process.THREAD_PRIORITY_DEFAULT);
	    thread.start();
	    
	    mServiceLooper = thread.getLooper();
	    mServiceHandler = new BluetoothLinkServiceHandler(mServiceLooper, getApplicationContext());
	    
	    
	    startForeground(
	    		BluetoothLinkServiceHandler.NotificationID, 
	    		mServiceHandler.createNotification(
	    				R.drawable.ic_stat_notify_btdisconnected, 
	    				null, 
	    				R.string.btlink_connecting)); 
	    
	    
		super.onCreate();
	}
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		Log.d(tag, "onStartCommand");
		Message msg= mServiceHandler.obtainMessage(BluetoothLinkServiceHandler.MsgType.CONNECT);
		msg.sendToTarget();
		return START_STICKY;
	}
	
	@Override
	public IBinder onBind(Intent arg0) {
		Log.d(tag, "onBind");
		return mBinder;

	}
	
	
	@Override
	public void onDestroy() {
		Log.d(tag, "onDestroy");
		mServiceHandler.quitGently();
		stopForeground(true);
		super.onDestroy();
	}
	
	
	public void sendData(String data) {
		Message msg=mServiceHandler.obtainMessage(BluetoothLinkServiceHandler.MsgType.SEND, data);
		msg.sendToTarget();
	}
	


}
