package michal.playgoandroidbridge;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.telephony.SmsManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Date;
import java.util.Set;
import java.util.UUID;

public class PlayGoActivity extends AppCompatActivity {


    BluetoothAdapter mBluetoothAdapter;

    final int REQUEST_ENABLE_BT = 232;
    final UUID PLAYGO_BLUETOOTH_UUID = UUID.fromString("8e7d70e5-5668-41c7-8978-a72a1e43616b");
    TextView textMain = null;
    int msgCounter = 0;
    private static int lastState = TelephonyManager.CALL_STATE_IDLE;
    private static Date callStartTime;
    private static boolean isIncoming;
    private static String savedNumber;  //because the passed incoming is only valid in ringing

    private static ConnectedThread conThread  = null;
    private static String msgToSend = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_play_go);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        textMain = (TextView) findViewById(R.id.mainText);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Connect", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });

        openBluetoothConnectionServer();


    }


    public void updateText(final String text){
        if(textMain != null) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    textMain.setText(text);
                }
            });
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_play_go, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            //open settings page
            Intent i = new Intent(this, SettingsActivity.class);
            startActivity(i);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public static boolean sendBluetoothMsg(String msg)
    {
        if(conThread != null)
            if(conThread.isAlive())
            {
                conThread.write(msg.getBytes());

                return true;
            }

        return false;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        if(requestCode == REQUEST_ENABLE_BT){
            if(resultCode == RESULT_OK) {
                //start the listenting thread
                AcceptThread acceptThread = new AcceptThread();
                acceptThread.start();
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void sendSms(String msg) {
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        String ph = sharedPref.getString(SettingsActivity.KEY_PREF_PHONE_NUM, "");
        SmsManager smsManager = SmsManager.getDefault();
        smsManager.sendTextMessage(ph, null,msg, null, null);
    }

    private void openBluetoothConnectionServer()    {

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null) {

            Toast.makeText(getApplicationContext(), "No Bluetooth adapter on this device, application will not work",
                    Toast.LENGTH_LONG).show();
            return;
        }

        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }
        else //bluetooth allready enabled - just start listening
        {
            AcceptThread acceptThread = new AcceptThread();
            acceptThread.start();
        }


//        //query paired devices
//        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
//        // If there are paired devices
//        if (pairedDevices.size() > 0) {
//            // Loop through paired devices
//            for (BluetoothDevice device : pairedDevices) {
//                // Add the name and address to an array adapter to show in a ListView
//                //mArrayAdapter.add(device.getName() + "\n" + device.getAddress());
//            }
//        }
    }


    private class AcceptThread extends Thread {
        private final BluetoothServerSocket mmServerSocket;

        public AcceptThread() {
            // Use a temporary object that is later assigned to mmServerSocket,
            // because mmServerSocket is final
            BluetoothServerSocket tmp = null;
            try {
                // MY_UUID is the app's UUID string, also used by the client code
                tmp = mBluetoothAdapter.listenUsingRfcommWithServiceRecord("PlayGoBluetooth", PLAYGO_BLUETOOTH_UUID);
            } catch (IOException e) { }
            mmServerSocket = tmp;
        }

        public void run() {
            BluetoothSocket socket = null;
            // Keep listening until exception occurs or a socket is returned
            while (true) {
                try {
                    socket = mmServerSocket.accept();
                } catch (IOException e) {
                    Log.w("PlayGoConnection", "server socket timedout, msg: " + e.getMessage());
                    break;
                }
                // If a connection was accepted
                if (socket != null) {
                    // Do work to manage the connection (in a separate thread)

                    updateText("Connected");
                    manageConnectedSocket(socket);
                    try {
                        mmServerSocket.close();
                    } catch (IOException e) {
                        Log.w("PlayGoConnection", "server socket closed exception " + e.getMessage());
                        e.printStackTrace();
                    }
                    break;
                }
            }
        }

        

        /** Will cancel the listening socket, and cause the thread to finish */
        public void cancel() {
            try {
                mmServerSocket.close();
            } catch (IOException e) {
                Log.w("PlayGoConnection", "server socket closed exception " + e.getMessage());
                e.printStackTrace();
            }
        }


        public void manageConnectedSocket(BluetoothSocket socket){

            conThread = new ConnectedThread(socket);
            conThread.start();
        }


    }


    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        public ConnectedThread(BluetoothSocket socket) {
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // Get the input and output streams, using temp objects because
            // member streams are final
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) { }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {
            byte[] buffer = new byte[1024];  // buffer store for the stream
            int bytes; // bytes returned from read()

            // Keep listening to the InputStream until an exception occurs
            while (true) {
                String msg = "";
                try {
                    // Read from the InputStream
                    bytes = mmInStream.read(buffer);
                    // Send the obtained bytes to the UI activity
                    ///handle incoming message
                    if(bytes > 0) {
                        msg = new String(buffer, 0, bytes, "UTF-8");
                        Log.i("PlayGoConnection", "received buffer " + msg);

                        if(msg.startsWith("PlayGo SMS")) {
                            sendSms(msg);
                        }
                        updateText(msg + "\r\n" + "received " + msgCounter++);

//                        runOnUiThread(new Runnable() {
//                            @Override
//                            public void run() {
//                                Toast.makeText(getApplicationContext(), "Got message: " + msg, Toast.LENGTH_LONG);
//
//                            }
//                        });
                    }
                    //mHandler.obtainMessage(MESSAGE_READ, bytes, -1, buffer)
                    //        .sendToTarget();

                    //String sHello = "hello there.";
                    //mmOutStream.write(sHello.getBytes());
                    //mmOutStream.flush();
                 } catch (IOException e) {
                    Log.w("PlayGoConnection", "Failed to display buffer " + e.getMessage());
                    e.printStackTrace();
                    break;
                }
            }
        }

        /* Call this from the main activity to send data to the remote device */
        public void write(byte[] bytes) {
            try {
                mmOutStream.write(bytes);
                mmOutStream.flush();
            } catch (IOException e) {
                Log.w("PlayGoConnection", "Failed to write msg " + e.getMessage());
            }
        }

        /* Call this from the main activity to shutdown the connection */
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) { }
        }
    }




}
