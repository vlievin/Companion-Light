package comvlievin.github.companionlight;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.Timer;
import java.util.TimerTask;


// inspired by http://www.truiton.com/2014/10/android-foreground-service-example/
// a large portion of code have been taken from the app SimpleControls provided by Redbear Labs


public class CompanionService extends Service implements SensorEventListener {

    Handler handler;

    private Sensor mLuminositySensor;
    private SensorManager mSensorManager;
    private List mLuxValues = new ArrayList();
    private int mLuxAverage = 300;
    private boolean mWorkMode = false;
    private boolean mHasLearnt = false;
    private boolean mSleepMode = false;

    private List mWorkRssis = new ArrayList();
    private List mRelaxRssis = new ArrayList();

    private List mRssiValues = new ArrayList();;
    private int mGain = 100;
    private int mHue = 0;
    private boolean mAuto = true;
    private boolean mStarted = false;

    private BluetoothGattCharacteristic characteristicTx = null;
    private BluetoothLeService mBluetoothLeService;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothDevice mDevice = null;
    private String mDeviceAddress;

    private boolean flag = true;
    private boolean connState = false;
    private boolean scanFlag = false;

    private byte[] data = new byte[3];
    private static final int REQUEST_ENABLE_BT = 1;
    private static final long SCAN_PERIOD = 2000;
    final private static char[] hexArray = {'0', '1', '2', '3', '4', '5', '6',
            '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};

    final String TAG = "#CompServ";

    /**
     * This function compute decides which mode to set and send the good values to the Blend Micro
     */
    private void updateValues() {
        int ColorToSet = 0;

        mSleepMode = false;
        boolean mRelaxing = false;
        // luminosity
        int current_gain = mGain;
        if (mAuto)
        {
            // set the intensity of the light automatically
            mLuxAverage = getAverage(mLuxValues);
            Log.i(TAG, "lum: " + mLuxAverage);
            if (mLuxAverage > Constants.LUX_DAY)
                current_gain = Constants.GAIN_DAY;
            else
                current_gain = Constants.GAIN_NIGHT + (int) ( (float)Constants.GAIN_DAY * mLuxAverage / Constants.LUX_DAY);
        }
        if (!mAuto) {
            // color for the manual mode
            float[] hsl = new float[]{mHue, 1, 1};
            ColorToSet = Color.HSVToColor(hsl);
        }
        else
        {
            // define the mode to apply depending on the RSSI
            int averageRSSI = getAverage(mRssiValues);
            if (mHasLearnt)
            {
                int averageWork = getAverage(mWorkRssis);
                int averageRelax = getAverage(mRelaxRssis);
                int mThreshRelax = (averageWork + averageRelax) / 2;
                if (averageRSSI < mThreshRelax )
                    mWorkMode = true;
                else
                    mWorkMode = false;
            }
            if (averageRSSI > Constants.THRESHOLD_OFF) {
                mSleepMode = true;
            }

            // get the time zone and decrease the Blue value of the light
            TimeZone tz = TimeZone.getTimeZone("GMT+01");
            Calendar c = Calendar.getInstance(tz);
            int minutes = c.get(Calendar.MINUTE);
            int hours = c.get(Calendar.HOUR_OF_DAY);

            Log.i("time" , ""+minutes);
            Log.i("time" , ""+hours);

            int red = 255;
            int blue = 255;
            int green = 255;
            if (hours >= 23 )
                blue = (int)(255.0f * ( 1.0f - (float)minutes / 60.0f ) );
            else if (hours <7 )
                blue = 0;
            else if (hours < 8)
                blue = (int)(255.0f * (float)minutes / 60.0f );

            Log.i("time" , "blue: " + blue);

            ColorToSet = Color.rgb( red , blue, green  );
        }


        // prepare the message to send
        byte[] buf = new byte[]{(byte) 0x00, (byte) 0x00, (byte) 0x00 , (byte) 0x00, (byte) 0x00  };
        buf[0] = (byte) Color.red(ColorToSet);
        buf[1] = (byte) Color.green(ColorToSet);
        buf[2] = (byte) Color.blue(ColorToSet);
        buf[3] = (byte) current_gain;
        if ((mAuto && !mWorkMode) || (!mAuto && mRelaxing))
            buf[4] = (byte) 0x01;
        if (mSleepMode)
            buf[4] = (byte)0x02;

        // write and send the message to the Blend Micro
        characteristicTx.setValue(buf);
        mBluetoothLeService.writeCharacteristic(characteristicTx);
    }

    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName,
                                       IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service)
                    .getService();
            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
            }
            else
            {
                startScan();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }
    };

    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                updateNotification("");
                setButtonDisable();
                scanFlag = false;
                //scanFlag = false;
            } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED
                    .equals(action)) {
                updateNotification("");
                getGattService(mBluetoothLeService.getSupportedGattService());

            } else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
                data = intent.getByteArrayExtra(BluetoothLeService.EXTRA_DATA);
            } else if (BluetoothLeService.ACTION_GATT_RSSI.equals(action)) {
                displayData(intent.getStringExtra(BluetoothLeService.EXTRA_DATA));
            } else if (action.equals(Constants.ACTION.SCAN_ACTION)) {
                startScan();
            }
            else if (action.equals(Constants.ACTION.ON_HUE_CHANGE)) {
                mHue = intent.getIntExtra("hue", 0);
                Log.i(TAG, "hue: " + mHue);
                updateNotification("");
            }
            else if (action.equals(Constants.ACTION.ON_GAIN_CHANGE)) {
                mGain = intent.getIntExtra("gain", 0);
                Log.i(TAG, "gain: " + mGain);
            }

        }
    };


    private void startScan()
    {
        if (scanFlag == false) {
            updateNotification("scanning..");
            scanLeDevice();

            Timer mTimer = new Timer();
            mTimer.schedule(new TimerTask() {

                @Override
                public void run() {
                    if (mDevice != null) {
                        mDeviceAddress = mDevice.getAddress();
                        mBluetoothLeService.connect(mDeviceAddress);
                        scanFlag = true;
                    } else {
                        updateNotification("Couldn't search Ble Shiled device!");
                    }
                }
            }, SCAN_PERIOD);
        }

        if (connState == false) {
            mBluetoothLeService.connect(mDeviceAddress);
        } else {
            mBluetoothLeService.disconnect();
            mBluetoothLeService.close();
            setButtonDisable();
        }
    }

    private int getAverage(List list)
    {
        int average = 0;
        for (int i = 0 ; i < list.size() ; i ++) {
            average = average + (int)list.get(i);
        }
        int length = list.size();
        if (length == 0 )
            length = 1;
        average /= length;
        return average;
    }
    private void displayData(String data) {
        if (data != null) {

            mRssiValues.add(0, Math.abs(Integer.parseInt(data)));
            if(mRssiValues.size() > Constants.RSSI_FILTER_LENGTH)
                mRssiValues.remove(mRssiValues.size() - 1);

            int average = getAverage(mRssiValues);

            updateValues();

            // update RSSI
            Log.i(TAG, "rssi: " + average);
            updateNotification("rssi: " + average);
        }
    }

    private void startReadRssi() {
        new Thread() {
            public void run() {

                while (flag) {
                    mBluetoothLeService.readRssi();
                    try {
                        sleep(500);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }.start();
    }

    private void getGattService(BluetoothGattService gattService) {
        if (gattService == null) {
            return;
        }
        setButtonEnable();
        startReadRssi();

        characteristicTx = gattService
                .getCharacteristic(BluetoothLeService.UUID_BLE_SHIELD_TX);

        BluetoothGattCharacteristic characteristicRx = gattService
                .getCharacteristic(BluetoothLeService.UUID_BLE_SHIELD_RX);
        mBluetoothLeService.setCharacteristicNotification(characteristicRx,
                true);
        mBluetoothLeService.readCharacteristic(characteristicRx);
    }

    private static IntentFilter makeGattUpdateIntentFilter() { // intent filter
        final IntentFilter intentFilter = new IntentFilter();

        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_RSSI);
        intentFilter.addAction(Constants.ACTION.ON_HUE_CHANGE);
        intentFilter.addAction(Constants.ACTION.ON_GAIN_CHANGE);
        intentFilter.addAction(Constants.ACTION.SCAN_ACTION);

        return intentFilter;
    }

    private void scanLeDevice() { // scan BLE devices
        new Thread() {

            @Override
            public void run() {

                mBluetoothAdapter.startLeScan(mLeScanCallback);

                try {
                    Thread.sleep(SCAN_PERIOD);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                mBluetoothAdapter.stopLeScan(mLeScanCallback);
            }
        }.start();
    }

    private BluetoothAdapter.LeScanCallback mLeScanCallback = new BluetoothAdapter.LeScanCallback() {

        @Override
        public void onLeScan(final BluetoothDevice device, final int rssi,
                             final byte[] scanRecord) {

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    byte[] serviceUuidBytes = new byte[16];
                    String serviceUuid = "";
                    for (int i = 32, j = 0; i >= 17; i--, j++) {
                        serviceUuidBytes[j] = scanRecord[i];
                    }

                    serviceUuid = bytesToHex(serviceUuidBytes);
                    if (stringToUuidString(serviceUuid).equals(RBLGattAttributes.BLE_SHIELD_SERVICE.toUpperCase(Locale.ENGLISH))) {
                        /**
                         * the UUID provided by RedBear Lab doesn't match the one from the blend micro.
                         * However, changing the UUUID to match with the one shown in the console doesn't work either...
                         */
                        //mDevice = device;
                    } else {
                        Log.i("-- serv -- ", stringToUuidString(serviceUuid));
                        Log.i("-- attr --- ", RBLGattAttributes.BLE_SHIELD_SERVICE.toUpperCase(Locale.ENGLISH));
                    }
                    /**
                     * I use this simple and dirty workaround. It works, but not 100% of the cases.
                     */
                    mDevice = device;
                }
            });
        }
    };

    private String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        int v;
        for (int j = 0; j < bytes.length; j++) {
            v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    private String stringToUuidString(String uuid) {
        StringBuffer newString = new StringBuffer();
        newString.append(uuid.toUpperCase(Locale.ENGLISH).substring(0, 8));
        newString.append("-");
        newString.append(uuid.toUpperCase(Locale.ENGLISH).substring(8, 12));
        newString.append("-");
        newString.append(uuid.toUpperCase(Locale.ENGLISH).substring(12, 16));
        newString.append("-");
        newString.append(uuid.toUpperCase(Locale.ENGLISH).substring(16, 20));
        newString.append("-");
        newString.append(uuid.toUpperCase(Locale.ENGLISH).substring(20, 32));

        return newString.toString();
    }


    public CompanionService() {
    }

    private static final String LOG_TAG = "ForegroundService";

    private void runOnUiThread(Runnable runnable) {
        handler.post(runnable);
    }

    @Override
    public void onCreate() {
        handler = new Handler();
        super.onCreate();

        final BluetoothManager mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (mBluetoothAdapter == null) {
            updateNotification("BLE not supported");
            return;
        }

        Intent gattServiceIntent = new Intent(CompanionService.this,
                BluetoothLeService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);

        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());

        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mLuminositySensor = mSensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);

        mSensorManager.registerListener(this, mLuminositySensor, SensorManager.SENSOR_DELAY_NORMAL);

        mWorkRssis = getFromPrefs(Constants.WORK_KEY);
        mRelaxRssis = getFromPrefs(Constants.RELAX__KEY);

        mHasLearnt = checkLearning();

    }

    @Override
    public final void onSensorChanged(SensorEvent event) { // called when the value of the luminosity sensor change
         int val = (int)event.values[0];
        mLuxValues.add(0 , val );
        if(mLuxValues.size() > Constants.LUX_FILTER_LENGTH)
            mLuxValues.remove(mLuxValues.size() - 1);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    public void storeIntArray(List array, String key){ // store an array in the SharePreferences
        SharedPreferences.Editor edit= this.getSharedPreferences(TAG, Context.MODE_PRIVATE).edit();
        edit.putInt(key + "Count", array.size());
        int count = 0;
        for (int i = 0 ; i < array.size() ; i++){
            edit.putInt(key + "IntValue_" + i, (int)array.get(i));
        }
        edit.commit();
    }
    public List getFromPrefs(String key){ // restore an array from the SharePreferences
        List ret = new ArrayList();
        SharedPreferences prefs = this.getSharedPreferences(TAG, Context.MODE_PRIVATE);
        int count = prefs.getInt(key+"Count", 0);
        for (int i = 0; i < count; i++)
        {
            ret.add( prefs.getInt(key+"IntValue_"+ i, i) );
        }
        return ret;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) { // receive intents and execute some tasks
        if (intent.getAction().equals(Constants.ACTION.STARTFOREGROUND_ACTION)) {
            if (!mStarted) {
                Log.i(LOG_TAG, "Received Start Foreground Intent ");
                Notification mNotification = getMyActivityNotification("");
                startForeground(Constants.NOTIFICATION_ID.FOREGROUND_SERVICE,
                        mNotification);
                mStarted = true;
            }
        } else if (intent.getAction().equals(Constants.ACTION.WORK_MODE)) {
            Log.i(LOG_TAG, "Clicked work");
            mWorkMode = true;
            int average = getAverage(mRssiValues);
            mWorkRssis.add(average);
            mHasLearnt = checkLearning();
            updateNotification("");
        }
        else if (intent.getAction().equals(Constants.ACTION.RELAX_MODE)) {
            Log.i(LOG_TAG, "Clicked relax");
            mWorkMode = false;
            int average = getAverage(mRssiValues);
            mRelaxRssis.add(average);
            mHasLearnt = checkLearning();
            updateNotification("");

        } else if (intent.getAction().equals(Constants.ACTION.CONNECT_ORDER_FROM_NOTIF)) {
            Log.i(LOG_TAG, "Clicked connect");
            startScan();
        } else if (intent.getAction().equals(Constants.ACTION.AUTO_MODE)) {
            Log.i(LOG_TAG, "Clicked auto");
            mAuto = !mAuto;
        } else if (intent.getAction().equals(
                Constants.ACTION.STOPFOREGROUND_ACTION)) {
            Log.i(LOG_TAG, "Received Stop Foreground Intent");
            stopForeground(true);
            stopSelf();
        } else if (intent.getAction().equals(Constants.ACTION.ON_AUTO_CHANGE)) {
            mAuto = !mAuto;
            Log.i(TAG, "auto: " + mAuto);
        }
        else if (intent.getAction().equals(Constants.ACTION.RESET_ORDER)) {
            Log.i(TAG, "#####RESET#####");
            SharedPreferences.Editor editor = CompanionService.this.getSharedPreferences(TAG, Context.MODE_PRIVATE).edit();
            editor.clear();
            editor.commit();
            mRelaxRssis.clear();
            mWorkRssis.clear();
            mHasLearnt = checkLearning();
            updateNotification("");
        }

        return START_STICKY;
    }

    private Notification getMyActivityNotification(String text){ // create the ongoing notification
        // The PendingIntent to launch our activity if the user selects
        // this notification
        Intent notificationIntent = new Intent(this, HomeScreen.class);
        notificationIntent.putExtra("gain" , mGain);
        notificationIntent.putExtra("hue" , mHue);

        notificationIntent.setAction(Constants.ACTION.MAIN_ACTION);
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0,
                notificationIntent, 0);

        Intent workIntent = new Intent(this, CompanionService.class);
        workIntent.setAction(Constants.ACTION.WORK_MODE);
        PendingIntent ppWorkIntent = PendingIntent.getService(this, 0,
                workIntent, 0);

        Intent relaxIntent = new Intent(this, CompanionService.class);
        workIntent.setAction(Constants.ACTION.RELAX_MODE);
        PendingIntent pprelaxIntent = PendingIntent.getService(this, 0,
                workIntent, 0);

        Intent connectIntent = new Intent(this, CompanionService.class);
        connectIntent.setAction(Constants.ACTION.CONNECT_ORDER_FROM_NOTIF);
        PendingIntent ppConnectIntent = PendingIntent.getService(this, 0,
                connectIntent, 0);

        Intent AutoIntent = new Intent(this, CompanionService.class);
        AutoIntent.setAction(Constants.ACTION.AUTO_MODE);
        PendingIntent ppAutoIntent = PendingIntent.getService(this, 0,
                AutoIntent, 0);

        Bitmap icon = BitmapFactory.decodeResource(getResources(),
                R.mipmap.ic_launcher);

        Notification mNotification;
        if (connState) {
            String Text_auto = "Turn auto on";
            if (mAuto)
                Text_auto = "Turn auto off";
            if ( !mAuto || mHasLearnt)
            {

                mNotification = new NotificationCompat.Builder(this)
                        .setContentTitle("CompanionLight")
                        .setTicker("CompanionLight")
                        .setContentText(text)
                        .setSmallIcon(R.mipmap.ic_launcher)
                        .setLargeIcon(
                                Bitmap.createScaledBitmap(icon, 128, 128, false))
                        .setContentIntent(pendingIntent)
                        .setOngoing(true)
                        .addAction(R.drawable.ic_auto, Text_auto,
                                ppAutoIntent)
                        .build();
            }
            else
            {
                if (mWorkMode)
                {
                    mNotification = new NotificationCompat.Builder(this)
                            .setContentTitle("CompanionLight")
                            .setTicker("CompanionLight")
                            .setContentText(text)
                            .setSmallIcon(R.mipmap.ic_launcher)
                            .setLargeIcon(
                                    Bitmap.createScaledBitmap(icon, 128, 128, false))
                            .setContentIntent(pendingIntent)
                            .setOngoing(true)
                            .addAction(R.drawable.ic_relax,
                                    "relax", pprelaxIntent)
                            .addAction(R.drawable.ic_auto, Text_auto,
                                    ppAutoIntent)
                            .build();
                }
                else
                {
                    mNotification = new NotificationCompat.Builder(this)
                            .setContentTitle("CompanionLight")
                            .setTicker("CompanionLight")
                            .setContentText(text)
                            .setSmallIcon(R.mipmap.ic_launcher)
                            .setLargeIcon(
                                    Bitmap.createScaledBitmap(icon, 128, 128, false))
                            .setContentIntent(pendingIntent)
                            .setOngoing(true)
                            .addAction(R.drawable.ic_working,
                                    "work", ppWorkIntent)
                            .addAction(R.drawable.ic_auto, Text_auto,
                                    ppAutoIntent)
                            .build();
                }
            }
        }
        else
        {
            mNotification = new NotificationCompat.Builder(this)
                    .setContentTitle("CompanionLight")
                    .setTicker("CompanionLight")
                    .setContentText(text)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setLargeIcon(
                            Bitmap.createScaledBitmap(icon, 128, 128, false))
                    .setContentIntent(pendingIntent)
                    .setOngoing(true)
                    .addAction(R.drawable.ic_connect, "Connect",
                            ppConnectIntent)
                    .build();
        }

        return mNotification;
    }

    private void updateNotification(String text ) {
        if (!connState)
            text += " Disconnected";
        if (!mSleepMode)
        {
            if (connState && !mAuto)
                text += " Auto off";
            if (connState && mAuto)
            {
                if (mWorkMode)
                    text += " work";
                else
                    text += " relax";
            }
        }
        else
        text += " Off";

        Notification notification = getMyActivityNotification(text);

        NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        mNotificationManager.notify(Constants.NOTIFICATION_ID.FOREGROUND_SERVICE, notification);
    }


    @Override
    public void onDestroy() {
        super.onDestroy();
        mSensorManager.unregisterListener(this);
        Log.i(LOG_TAG, "In onDestroy");
        if (mServiceConnection != null)
            unbindService(mServiceConnection);

        // save state
        storeIntArray(mWorkRssis, Constants.WORK_KEY );
        storeIntArray(mRelaxRssis, Constants.RELAX__KEY );
    }

    @Override
    public IBinder onBind(Intent intent) {
        // Used only in case of bound services.
        return null;
    }


    private void setButtonEnable() {
        flag = true;
        connState = true;
    }

    private void setButtonDisable() {
        flag = false;
        connState = false;
    }

    private boolean checkLearning() // check if the app has collected enough data
    {
        boolean state = (mWorkRssis.size() >= Constants.TRAINING_SIZE && mRelaxRssis.size() >= Constants.TRAINING_SIZE  );

        if (state)
        {
            int averageWork = getAverage(mWorkRssis);
            int averageRelax = getAverage(mRelaxRssis);
            int mThreshRelax = (averageWork + averageRelax) / 2;

            NotificationManager notificationManager =
                    (NotificationManager) getSystemService(Service.NOTIFICATION_SERVICE);

            Bitmap icon = BitmapFactory.decodeResource(getResources(),
                    R.mipmap.ic_launcher);

            Notification notification = new NotificationCompat.Builder(this)
                    .setContentTitle("CompanionLight has learnt from your habits! ")
                    .setTicker("CompanionLight has learnt from your habits! ")
                    .setContentText("threshold: " + mThreshRelax)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setLargeIcon(
                            Bitmap.createScaledBitmap(icon, 128, 128, false))
                    .build();
            notificationManager.notify(002, notification);
        }

        return state;
    }
}
