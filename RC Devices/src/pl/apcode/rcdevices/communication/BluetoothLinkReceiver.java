package pl.apcode.rcdevices.communication;

import pl.apcode.rcdevices.EventLogger;
import pl.apcode.rcdevices.communication.BluetoothLinkServiceHandler.MsgType;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.net.SocketException;

import android.app.Application;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;

public class BluetoothLinkReceiver extends Thread {
	    public static final String tag = BluetoothLinkReceiver.class.getSimpleName();
        
	    private final Handler h;
        private final BluetoothSocket socket;
        private final Context ctx;
        private byte[] buffer = new byte[256];  // buffer store for the stream
        int cmdPos = 0;
        
        private InputStream inStream;
        
        public BluetoothLinkReceiver(BluetoothSocket socket, Handler serviceHandler, Context ctx) {
            super(tag);
        	this.socket = socket;
        	this.ctx = ctx;
            h=serviceHandler;
        }
      

        private boolean isEndOfCommand(byte ch) {
        	if(ch == '\n' || ch == ';')
        		return true;
        	else
        		return false;
        }
        
        private void sendCommand() {
        	if(cmdPos > 0) {
        		String cmd = new String(buffer, 0, cmdPos);
        		EventLogger.d(tag, "Command from Arduino: " + cmd);
        		
		        Intent intent = new Intent(BluetoothLinkService.RECEIVED_CMD);
		        
		        intent.putExtra("ReceiverData", cmd);
		        ctx.sendBroadcast(intent); 
        	}
        }
        
        private void readCommands() throws IOException {
            while(true) {
	            int readVal =  inStream.read(); 
	            if(readVal < 0) {
	            	EventLogger.e(tag, "Exiting Receiver. End of stream.");
	            } else if(readVal > 0) {
	            	byte ch = (byte)readVal;
	            	if(isEndOfCommand(ch)) {
	            		sendCommand();
	            		cmdPos = 0;
	            	} else {
	            		buffer[cmdPos] = ch;
	            		cmdPos++;
	            		if(cmdPos >= buffer.length)
	            			cmdPos = 0;
	            	}
	            }
            }
        }
        
        
        public void run() {    
            try {
            	inStream = socket.getInputStream();
            	EventLogger.i(tag, "Receiver Started");
            }
             catch(IOException ex)
             {
            	 EventLogger.e(tag, "Exiting Receiver. Unable to get input stream.", ex);
            	 h.obtainMessage(MsgType.CONNECT).sendToTarget();
            	 return;
             }
            
            
            try {
            	readCommands();	
            }
            catch (IOException e) {
           	    EventLogger.e(tag, "Exiting Receiver. Error during reading input stream.", e);
           	    h.obtainMessage(MsgType.CONNECT).sendToTarget();
           	    return;
            }
            
        }
      
	
}
