package com.onsite.onsitefaulttracker_v2.connectivity;

import android.content.Context;
import android.os.Handler;
import android.util.Log;
import android.widget.Toast;

import com.onsite.onsitefaulttracker_v2.model.notifcation_events.BLEStartRecordingEvent;
import com.onsite.onsitefaulttracker_v2.model.notifcation_events.BLEStopRecordingEvent;
import com.onsite.onsitefaulttracker_v2.model.notifcation_events.UsbConnectedNotification;
import com.onsite.onsitefaulttracker_v2.model.notifcation_events.UsbDisconnectedNotification;
import com.onsite.onsitefaulttracker_v2.util.BusNotificationUtil;
import com.onsite.onsitefaulttracker_v2.util.ThreadUtil;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;

/**
 * TcpConnection class handles socket communication with a client.
 *
 * Used for communicating via USB once a Tcp connection has been
 * established via the Android adb tool
 */
public class TcpConnection implements Runnable {

    // Tag name for this class
    private static final String TAG = TcpConnection.class.getSimpleName();

    // Shared instance of TCP connection
    private static TcpConnection sharedInstance;

    public static final int TIMEOUT=100;
    private String connectionStatus=null;
    private Handler mHandler;
    private ServerSocket server=null;
    private Context context;
    private Socket client=null;
    private String line="";
    BufferedReader socketIn;
    PrintWriter socketOut;
    private boolean mStarted;
    private boolean mRecording;
    private Thread mTcpThread;
    private Thread mReadThread;

    /**
     * Returns the shared instance of TcpConnection
     */
    public static TcpConnection getSharedInstance() {
        if (sharedInstance != null) {
            return sharedInstance;
        } else {
            throw new RuntimeException("TcpConnection must be initialized");
        }
    }

    /**
     * Initializes the tcp connection
     *
     * @param context
     */
    public static void initialize(Context context) {
        sharedInstance = new TcpConnection(context);
    }

    private TcpConnection(Context c) {
        // TODO Auto-generated constructor stub
        Log.i(TAG, "(TCP) TcpConnection creator");
        context=c;
        mStarted = false;
        mRecording = false;
        mHandler=new Handler();
    }

    /**
     * Start the tcp connection
     */
    public void startTcpConnection() {
        if (mStarted || mTcpThread != null) {
            return;
        }

        mStarted = true;
        mTcpThread = new Thread(this);
        mTcpThread.setPriority(Thread.MAX_PRIORITY);
        mTcpThread.start();
        Log.i(TAG, "Started Tcp Thread");
    }

    /**
     * Stop the tcp connection
     */
    public void stopTcpConnection() {
        if (!mStarted) {
            return;
        }
        mStarted = false;
        if (mTcpThread != null) {
            try {
                mTcpThread.join();
            } catch (InterruptedException ex) {

            }
        }
        mTcpThread = null;
    }

    /**
     * Update whether the app is recording or not
     *
     * @param recording
     */
    public void setRecording(boolean recording) {
        mRecording = recording;
        sendRecordingStatus();
    }

    /**
     * Send the recording status
     */
    private void sendRecordingStatus() {
        ThreadUtil.executeOnNewThread(new Runnable() {
            @Override
            public void run() {
                if (socketOut != null) {
                    socketOut.println(mRecording ? "RECORDING\n" : "STOPPED\n");
                    socketOut.flush();
                }
            }
        });
    }

    /**
     * Returns true if connected
     *
     * @return
     */
    public boolean isConnected() {
        return socketOut != null;
    }

    /**
     * Opens a port and listens for a socket connections
     */
    @Override
    public void run() {
        // TODO Auto-generated method stub
        // initialize server socket
        Log.i(TAG, "(TCP) TcpConnection run");
        try {
            Log.i(TAG, "(TCP) create server socket");
            server = new ServerSocket(38300);
            Log.i(TAG, "(TCP) added server socket on 38300");
            server.setSoTimeout(TIMEOUT*1000);
        } catch (IOException e1) {
            Log.i(TAG, "(TCP) server create exception");
            // TODO Auto-generated catch block
            e1.printStackTrace();
        }

        //attempt to accept a connection
        try{
            Log.i(TAG, "(TCP) will call server.accept");
            client = server.accept();
            Log.i(TAG, "(TCP) did accept");

            socketOut = new PrintWriter(client.getOutputStream(), true);
            socketOut.println("CONNECTED\n");
            socketOut.flush();

            BusNotificationUtil.sharedInstance().postNotification(new UsbConnectedNotification());

            mReadThread = new Thread(readFromClient);
            mReadThread.setPriority(Thread.MAX_PRIORITY);
            mReadThread.start();
            Log.e(TAG, "Sent");
        }
        catch (SocketTimeoutException e) {
            // print out TIMEOUT
            connectionStatus="Connection has timed out! Please try again";
            mHandler.post(showConnectionStatus);
            try {
                server.close();
            } catch (IOException e1) {
                // TODO Auto-generated catch block
                e1.printStackTrace();
            }


        }
        catch (IOException e) {
            Log.e(TAG, ""+e);
        }

        if (client!=null) {
            try{
                // print out success
                connectionStatus="Connection successful!";
                Log.e(TAG, connectionStatus);
                mHandler.post(showConnectionStatus);
            }
            catch(Exception e)
            {
                e.printStackTrace();
            }
        }
    }

    /**
     * To run in the background,  reads in comming data
     * from the client
     */
    private Runnable readFromClient = new Runnable() {

        @Override
        public void run() {
            // TODO Auto-generated method stub
            try {
                Log.e(TAG, "Reading from server");
                socketIn=new BufferedReader(new InputStreamReader(client.getInputStream()));
                while ((line = socketIn.readLine()) != null) {
                    Log.d("ServerActivity", line);
                    //Do something with line
                    if(line.contains("START")) {
                        BusNotificationUtil.sharedInstance().postNotification(new BLEStartRecordingEvent());
                    } else if (line.contains("STOP")) {
                        BusNotificationUtil.sharedInstance().postNotification(new BLEStopRecordingEvent());
                    } else if (line.contains("STATUS")) {
                        sendRecordingStatus();
                    }
                }
                socketIn.close();
                closeAll();
                Log.e(TAG, "OUT OF WHILE");
            }
            catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    };

    /**
     * Close the socket connection if open and restart listening for a connection
     */
    public void closeAll() {
        // TODO Auto-generated method stub
        try {
            BusNotificationUtil.sharedInstance().postNotification(new UsbDisconnectedNotification());
            Log.e(TAG, "Closing All");
            socketOut.close();
            socketOut = null;
            client.close();
            server.close();

            stopTcpConnection();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        restartTcpConnection();
    }

    /**
     * Restarts listening for a socket connection
     * after a one second wait
     */
    private void restartTcpConnection() {
        ThreadUtil.executeOnMainThreadDelayed(new Runnable() {
            @Override
            public void run() {
                startTcpConnection();
            }
        }, 1000);
    }

    /**
     * Show the connection status in a Toast and send
     * the current status through the open socket if a
     * connection is established
     */
    private Runnable showConnectionStatus = new Runnable() {
        public void run() {
            try
            {
                Toast.makeText(context, connectionStatus, Toast.LENGTH_SHORT).show();
                sendRecordingStatus();
            }
            catch(Exception e)
            {
                e.printStackTrace();
            }
        }
    };
}