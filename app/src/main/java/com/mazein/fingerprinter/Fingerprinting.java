package com.mazein.fingerprinter;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;

public class Fingerprinting extends AppCompatActivity implements SensorEventListener
{
    private static final String LOG_TAG = "FP";
    private static final String SERVER_BASE_URL = "http://mazein.herokuapp.com/";

    private static final String WIFI_SERVER_CONTROLLER = "finger_prints";
    private static final String MAGNETIC_SERVER_CONTROLLER = "magnetics";

    private static final String WIFI_SERVER_COMMIT = "Create Finger print";
    private static final String MAGNETIC_SERVER_COMMIT = "Create Magnetic";

    private static final String WIFI_SERVER_FP_KEY = "finger_print";
    private static final String MAGNETIC_SERVER_FP_KEY = "magnetic";

    private static final String WIFI_SERVER_ROUTE = "finger_prints.json";
    private static final String MAGNETIC_SERVER_ROUTE = "magnetics.json";

    private static final int PERMISSION_REQUEST_COARSE_LOCATION = 1;
    private static final int PERMISSION_REQUEST_WRITE_EXTERNAL_STORAGE = 1;
    private static boolean WIFI_ENABLED = false;
    private static boolean MAGNETIC_ENABLED = false;
    private static boolean SERVER_ENABLED = false;
    private static boolean FILE_WRITE_ENABLED = false;
    private static String ACTIVE_FILE_NAME = "log.txt";


    private WebView mapWebView;
    private ProgressDialog mProgressDialog;
    private ProgressDialog magneticProgressDialog;
    private WifiManager mWifiManager;
    private IntentFilter mIntentFilter;
    private SensorManager mSensorManager;
    private Sensor mMagneticSensor;
    private BroadcastReceiver mBroadcastReceiver;
    private ArrayList<ScanResult> mScanResults;

    private double[] magneticReadings = new double[3];

    @Override
    protected void onPause()
    {
        mSensorManager.unregisterListener(this);
        super.onPause();
    }

    @Override
    protected void onResume()
    {
        mSensorManager.registerListener(this, mMagneticSensor, SensorManager.SENSOR_DELAY_NORMAL);
        super.onResume();
    }
    @Override
    public void onSaveInstanceState(Bundle outState)
    {
        super.onSaveInstanceState(outState);
        mapWebView.saveState(outState);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState)
    {
        super.onRestoreInstanceState(savedInstanceState);
        mapWebView.restoreState(savedInstanceState);
    }

    private void checkPermissions()
    {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
        {
            // Android M Permission check
            if (this.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            {
                final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("This app needs location access");
                builder.setMessage("Please grant location access so this app can scan for WiFi.");
                builder.setPositiveButton(android.R.string.ok, null);
                builder.setOnDismissListener(new DialogInterface.OnDismissListener()
                {
                    @TargetApi(Build.VERSION_CODES.M)
                    public void onDismiss(DialogInterface dialog)
                    {
                        requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, PERMISSION_REQUEST_COARSE_LOCATION);
                    }
                });
                builder.show();
            }
            if (this.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
            {
                final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("This app needs external storage access");
                builder.setMessage("Please grant access so this app can write results to file.");
                builder.setPositiveButton(android.R.string.ok, null);
                builder.setOnDismissListener(new DialogInterface.OnDismissListener()
                {
                    @TargetApi(Build.VERSION_CODES.M)
                    public void onDismiss(DialogInterface dialog)
                    {
                        requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, PERMISSION_REQUEST_WRITE_EXTERNAL_STORAGE);
                    }
                });
                builder.show();
            }
        }
    }
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        checkPermissions(); // Check Android M Permissions Dynamically
        setContentView(R.layout.activity_main);

        mWifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

        if (mSensorManager.getSensorList(Sensor.TYPE_MAGNETIC_FIELD).size() != 0)
        {
            mMagneticSensor = mSensorManager.getSensorList(Sensor.TYPE_MAGNETIC_FIELD).get(0);
            mSensorManager.registerListener(this, mMagneticSensor, SensorManager.SENSOR_DELAY_NORMAL);
        }

        mapWebView =(WebView) findViewById(R.id.webView);
        WebSettings mapWebSettings = mapWebView.getSettings();
        mapWebSettings.setJavaScriptEnabled(true);
        mapWebView.addJavascriptInterface(new WebAppInterface(this), "Android");
        //mapWebView.loadUrl("file:///android_asset/floor_maps/F.html");
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    private boolean emailFingerprintFiles()
    {
        File dir = new File(Environment.getExternalStorageDirectory(), "MazeIn Fingerprints");
        if (!dir.exists())
        {
            dir.mkdirs();
            Toast.makeText(Fingerprinting.this, "Folder doesn't exist!", Toast.LENGTH_SHORT).show();
            return false;
        }
        File wifiFile = new File(dir, ACTIVE_FILE_NAME + "wifi.txt");
        File magFile = new File(dir, ACTIVE_FILE_NAME + "magnetic.txt");
        if (!wifiFile.exists())
        {
            Toast.makeText(Fingerprinting.this, "WiFi file missing!", Toast.LENGTH_SHORT).show();
            return false;
        }
        if (!magFile.exists())
        {
            Toast.makeText(Fingerprinting.this, "Magnetic file missing!", Toast.LENGTH_SHORT).show();
            return false;
        }
        ArrayList<Uri> uris = new ArrayList<Uri>();
        uris.add(Uri.fromFile(wifiFile));
        uris.add(Uri.fromFile(magFile));

        Intent i = new Intent(Intent.ACTION_SEND_MULTIPLE);
        String timestamp = (DateFormat.format("dd-MM-yy hh:mm:ss", new java.util.Date()).toString());
        i.putExtra(Intent.EXTRA_SUBJECT, ACTIVE_FILE_NAME +
                "_" + timestamp);

        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_STREAM, uris);
        Toast.makeText(Fingerprinting.this, "Sending " + ACTIVE_FILE_NAME, Toast.LENGTH_SHORT).show();
        startActivity(Intent.createChooser(i, "Sending multiple attachments"));
        return false;
    }

    public boolean touchFingerprintFiles(Context context)
    {
        Log.i("TOUCH_FILES", "Touching...");
        String MEDIA_MOUNTED = "mounted";
        String diskState = Environment.getExternalStorageState();
        if (diskState.equals(MEDIA_MOUNTED))
        {
            File dir = new File(Environment.getExternalStorageDirectory(), "MazeIn Fingerprints");
            if (!dir.exists())
            {
                dir.mkdirs();
            }
            File wifiFile = new File(dir, ACTIVE_FILE_NAME + "wifi.txt");
            File magFile = new File(dir, ACTIVE_FILE_NAME + "magnetic.txt");

            BufferedWriter wifiOut = null;
            BufferedWriter magOut = null;

            try
            {
                wifiOut = new BufferedWriter(new FileWriter(wifiFile, true));
                magOut = new BufferedWriter(new FileWriter(magFile, true));
                magOut.flush();
                wifiOut.flush();
                magOut.close();
                wifiOut.close();
            } catch (IOException e)
            {
                e.printStackTrace();
                return false;
            }
            Toast.makeText(Fingerprinting.this, "Files touched successfully...",
                    Toast.LENGTH_SHORT).show();
            return true;
        }
        return false;
    }

    private boolean resetFingerprintsFile()
    {
        File dir = new File(Environment.getExternalStorageDirectory(), "MazeIn Fingerprints");
        if (!dir.exists())
        {
            dir.mkdirs();
            Toast.makeText(Fingerprinting.this, "Folder doesn't exist!", Toast.LENGTH_SHORT).show();
            return false;
        }
        File wifiFile = new File(dir, ACTIVE_FILE_NAME + "wifi.txt");
        File magFile = new File(dir, ACTIVE_FILE_NAME + "magnetic.txt");
        if (!wifiFile.exists())
        {
            Toast.makeText(Fingerprinting.this, "WiFi missing!", Toast.LENGTH_SHORT).show();
            return false;
        }
        else
        {
            wifiFile.delete();
        }
        if (!magFile.exists())
        {
            Toast.makeText(Fingerprinting.this, "Magnetic missing!", Toast.LENGTH_SHORT).show();
            return false;
        }
        else
        {
            magFile.delete();
        }
        Toast.makeText(Fingerprinting.this, ACTIVE_FILE_NAME + " Deleted!", Toast.LENGTH_SHORT).show();
        return false;
    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        int id = item.getItemId();

        if (id == R.id.send_fingerprints)
        {
            return emailFingerprintFiles();
        }
        else if(id == R.id.reset_fingerprints)
        {
            return resetFingerprintsFile();
        }
        else if (id == R.id.f_menu_button)
        {
            mapWebView.loadUrl("file:///android_asset/floor_maps/F.html");
            ACTIVE_FILE_NAME = "F_fingerprints_";
            Toast.makeText(Fingerprinting.this, "Switched to: " + ACTIVE_FILE_NAME, Toast.LENGTH_SHORT).show();
            touchFingerprintFiles(getApplicationContext());
        }
        else if (id == R.id.meininger_menu_button)
        {
            mapWebView.loadUrl("file:///android_asset/meininger/index.html");
            ACTIVE_FILE_NAME = "meininger_fingerprints_";
            Toast.makeText(Fingerprinting.this, "Switched to: " + ACTIVE_FILE_NAME, Toast.LENGTH_SHORT).show();
            touchFingerprintFiles(getApplicationContext());
        }
        else if (id == R.id.s12_menu_button)
        {
            mapWebView.loadUrl("file:///android_asset/S12-N3/index.html");
            ACTIVE_FILE_NAME = "S12_fingerprints_";
            Toast.makeText(Fingerprinting.this, "Switched to: " + ACTIVE_FILE_NAME,
                    Toast.LENGTH_SHORT).show();
            touchFingerprintFiles(getApplicationContext());
        }
        else if (id == R.id.hofburg_menu_button)
        {
            // TODO: Change link to Vienna's map
            mapWebView.loadUrl("file:///android_asset/ipsn/index.html");
            ACTIVE_FILE_NAME = "Hofburg_fingerprints_";
            Toast.makeText(Fingerprinting.this, "Switched to: " + ACTIVE_FILE_NAME,
                    Toast.LENGTH_SHORT).show();
            touchFingerprintFiles(getApplicationContext());
        }
        else if (id == R.id.enable_magnetic)
        {
            item.setChecked(!item.isChecked());
            MAGNETIC_ENABLED = item.isChecked();
            Toast.makeText(Fingerprinting.this, "Magnetic Fingerprints: " + Boolean.toString(MAGNETIC_ENABLED)
                    , Toast.LENGTH_SHORT).show();
            Snackbar.make(mapWebView, "Magnetic Fingerprints: " + Boolean.toString(MAGNETIC_ENABLED)
                    , Snackbar.LENGTH_LONG).show();
            return true;
        }

        else if (id == R.id.enable_wifi)
        {
            item.setChecked(!item.isChecked());
            WIFI_ENABLED = item.isChecked();
            Toast.makeText(Fingerprinting.this, "WiFi Fingerprints: " + Boolean.toString(WIFI_ENABLED)
                    , Toast.LENGTH_SHORT).show();
            return true;
        }
        else if (id == R.id.enable_send_to_server)
        {
            item.setChecked(!item.isChecked());
            SERVER_ENABLED = item.isChecked();
            Toast.makeText(Fingerprinting.this, "Sync with Server: " + Boolean.toString(SERVER_ENABLED)
                    , Toast.LENGTH_SHORT).show();
            return true;
        }
        else if (id == R.id.enable_file_write)
        {
            item.setChecked(!item.isChecked());
            FILE_WRITE_ENABLED = item.isChecked();
            Toast.makeText(Fingerprinting.this, "Writing to: " + Boolean.toString(FILE_WRITE_ENABLED)
                    , Toast.LENGTH_SHORT).show();
            return true;
        }
        return false;
    }

    private void makePostRequest(ArrayList<JSONObject> allScanResults, String pPath,
                                 String pController, String pCommit, String fpKey) throws IOException
    {
        try
        {
            //final JSONobject which would be serialized to be sent to the server including finger_print JSONObject
            for (JSONObject finger_print : allScanResults)
            {
                JSONObject fingerprintJson = new JSONObject();
                fingerprintJson.put("commit", pCommit);// Create Magnetic
                fingerprintJson.put("action", "create");
                fingerprintJson.put("controller", pController);// magnetic
                fingerprintJson.put(fpKey, finger_print);// magnetic

                HttpURLConnection con = (HttpURLConnection) (new URL(SERVER_BASE_URL + pPath).openConnection());
                con.setDoOutput(true);
                con.setDoInput(true);
                con.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                con.setRequestProperty("Accept", "application/json");
                con.setRequestMethod("POST");

                Log.d("JSON_TO_SERVER", finger_print.toString());
                OutputStreamWriter wr = new OutputStreamWriter(con.getOutputStream());
                wr.write(fingerprintJson.toString());
                Log.d("FingerPrintJson", fingerprintJson.toString());
                wr.flush();
                StringBuilder sb = new StringBuilder();
                int HttpResult = con.getResponseCode();
                if (HttpResult == HttpURLConnection.HTTP_OK)
                {
                    BufferedReader br = new BufferedReader(new InputStreamReader(con.getInputStream(), "utf-8"));
                    String line;
                    while ((line = br.readLine()) != null)
                    {
                        sb.append(line + "\n");
                    }

                    br.close();
                    Log.i("JSON_TO_SERVER", "Response: " + sb.toString());
//                    Toast.makeText(Fingerprinting.this, "Server Response: OK", Toast.LENGTH_SHORT).show();

                }
                else
                {
                    System.out.println(con.getResponseMessage());
                    //Toast.makeText(Fingerprinting.this, "Server Response: ERR", Toast.LENGTH_SHORT).show();
                }
            }
        } catch (JSONException e)
        {
            Log.e("POST_REQ", "Couldn't convert Fingerprint to JSON");
            e.printStackTrace();
        }


    }

    @Override
    public void onSensorChanged(SensorEvent e)
    {
        if (e.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD)
        {
            magneticReadings[0] = (double) e.values[0];
            magneticReadings[1] = (double) e.values[1];
            magneticReadings[2] = (double) e.values[2];
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy)
    {

    }

    /**
     * First gets the wifi manager system service and start to scan for access
     * points. When the scan is completed an asynchonous message will be sent.
     * When this is done, the found results will be prepared in order for them
     * to be displayed in a listview.
     */


    // Class responsible for interfacing with the HTML files
    // Includes methods:
    //      - initializeWifiScan
    //      - initializeMagneticScan
    //      - intializeFingerprinting
    public class WebAppInterface{
        Context mContext;
        /**
         * Start the WIFI scan
         */
        private Runnable scanWifi = new Runnable()
        {

            public void run()
            {
                mWifiManager.startScan();
            }
        };

        WebAppInterface(Context c){
            mContext = c;
        }

        @JavascriptInterface
        public void initializeFingerprinting(final String place_id, final float startX, final float startY)
        {
            if (!WIFI_ENABLED && !MAGNETIC_ENABLED)
            {
                AlertDialog alertDialog = new AlertDialog.Builder(Fingerprinting.this).create();
                alertDialog.setTitle("Action Required");
                alertDialog.setMessage("Please enable WiFi or Magnetic\n" +
                        "fingerprint collection");
                alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, "OK",
                        new DialogInterface.OnClickListener()
                        {
                            public void onClick(DialogInterface dialog, int which)
                            {
                                dialog.dismiss();
                            }
                        });
                alertDialog.show();
                return;
            }
            if (!SERVER_ENABLED && !FILE_WRITE_ENABLED)
            {
                AlertDialog alertDialog = new AlertDialog.Builder(Fingerprinting.this).create();
                alertDialog.setTitle("Action Required");
                alertDialog.setMessage("Please enable Send to Server\n" +
                        "or Write to File to continue");
                alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, "OK",
                        new DialogInterface.OnClickListener()
                        {
                            public void onClick(DialogInterface dialog, int which)
                            {
                                dialog.dismiss();
                            }
                        });
                alertDialog.show();
                return;
            }

            if (WIFI_ENABLED)
                initializeWifiScan(place_id, startX, startY);

            if (MAGNETIC_ENABLED)
                initializeMagnetic(place_id, startX, startY);
        }

        public void initializeMagnetic(final String place_id, final float startX, final float startY)
        {
            magneticProgressDialog= ProgressDialog.show(Fingerprinting.this, "Magnetic Scan",
                    "Scan at: \n" + String.valueOf(startX) + ", " + String.valueOf(startY), true);

            JSONObject magneticFingerprint = new JSONObject();
            double magnitude = Math.sqrt(
                    (magneticReadings[0] * magneticReadings[0])
                            + (magneticReadings[1] * magneticReadings[1])
                            + (magneticReadings[2] * magneticReadings[2])
            );
            try
            {
                magneticFingerprint.put("place_id", place_id);
                magneticFingerprint.put("xcoord", startX);
                magneticFingerprint.put("ycoord", startY);
                magneticFingerprint.put("x", magneticReadings[0]);
                magneticFingerprint.put("y", magneticReadings[1]);
                magneticFingerprint.put("z", magneticReadings[2]);
                magneticFingerprint.put("magnitude", magnitude);
                magneticFingerprint.put("angle", 90.0);
                // TODO: Change inclination angle
            } catch (JSONException e)
            {
                e.printStackTrace();
                Log.e("MAGNETIC", "Json Error");
            }
            ArrayList<JSONObject> allMagneticFPs = new ArrayList<>();
            allMagneticFPs.add(magneticFingerprint);

            if (FILE_WRITE_ENABLED)
            {
                saveFile(getApplicationContext(), magneticFingerprint.toString(), "magnetic");
            }
            if (SERVER_ENABLED)
            {
                new FingerprintsServerHandler(MAGNETIC_SERVER_ROUTE,
                        MAGNETIC_SERVER_CONTROLLER,
                        MAGNETIC_SERVER_COMMIT,
                        MAGNETIC_SERVER_FP_KEY).execute(allMagneticFPs);
            }
            else
            {
                magneticProgressDialog.dismiss();
            }


            //Toast.makeText(Fingerprinting.this, magneticFingerprint.toString(), Toast.LENGTH_SHORT).show();
        }

        private String stringify(double[] arr, float startX, float startY)
        {
            StringBuilder sb = new StringBuilder();
            for(double d:arr)
            {
                sb.append(d);
                sb.append(",");
            }
            sb.append(startX);
            sb.append(",");
            sb.append(startY);
            return  sb.toString();
        }

        public void initializeWifiScan(final String place_id, final float startX, final float startY)
        {
            int scanNumber = 1;
            mProgressDialog = ProgressDialog.show(Fingerprinting.this, "WiFi Scan",
                    "Scan " + String.valueOf(scanNumber) + " at: \n" + String.valueOf(startX) + ", " + String.valueOf(startY), true);

            mIntentFilter = new IntentFilter();

            mIntentFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);

            mBroadcastReceiver = new BroadcastReceiver()
            {

                @Override
                public void onReceive(Context context, Intent intent)
                {
                    Log.d("WLAN", "Receiving WLAN Scan results");

                    mScanResults = (ArrayList<ScanResult>) mWifiManager
                            .getScanResults();

                    ArrayList<JSONObject> allScanResults = new ArrayList<>();
                    double[] rssiVector = new double[3];
                    for (ScanResult result : mScanResults)
                        {
                            //Adding data to the JSON object finger_print at first
                            if (result.BSSID.equals(AccessPointMacs.AP1_MAC)
                                    || result.BSSID.equals(AccessPointMacs.AP2_MAC)
                                    || result.BSSID.equals(AccessPointMacs.AP3_MAC)
                                    )
                            {
                                rssiVector[AccessPointMacs.keys.get(result.BSSID)] = result.level;
                                // JSON Style
                                /*
                                JSONObject finger_print = new JSONObject();
                                try
                                {
                                    finger_print.put("place_id", place_id);
                                    finger_print.put("xcoord", startX);
                                    finger_print.put("ycoord", startY);
                                    finger_print.put("BSSID", result.BSSID);
                                    finger_print.put("SSID", result.SSID);
                                    finger_print.put("RSSI", result.level);
                                    finger_print.put("SD", "");
                                    finger_print.put("mac", result.BSSID);
                                    allScanResults.add(finger_print);
                                } catch (JSONException e)
                                {
                                    e.printStackTrace();
                                }
                                */
                                Log.i("RESULT", result.BSSID + " " + result.level + " (" + startX + "," + startY + ")");
                                if (FILE_WRITE_ENABLED)
                                {
                                    saveFile(context, stringify(rssiVector, startX, startY), "wifi");
                                }

                            }
                        }
                    if (SERVER_ENABLED)
                    {
                        new FingerprintsServerHandler(WIFI_SERVER_ROUTE,
                                WIFI_SERVER_CONTROLLER,
                                WIFI_SERVER_COMMIT,
                                WIFI_SERVER_FP_KEY).execute(allScanResults);
                        mProgressDialog.dismiss();
                    }
                    else
                    {
                        // Dismiss Progress Dialog since it won't be dismessed
                        // from the onPostExecute method after sending to server.
                        mProgressDialog.dismiss();
                    }
                    mWifiManager.startScan();
                    unregisterReceiver(mBroadcastReceiver);
                    //saveResults.run();
                }
            };

            registerReceiver(mBroadcastReceiver, mIntentFilter);
            scanWifi.run();
        }

        public boolean saveFile(Context context, String mytext, String fpType)
        {
            Log.i("FILE_WRITE", "SAVING");
            try {
                String MEDIA_MOUNTED = "mounted";
                String diskState = Environment.getExternalStorageState();
                if(diskState.equals(MEDIA_MOUNTED))
                {
                    File dir = new File(Environment.getExternalStorageDirectory(), "MazeIn Fingerprints");
                    if(!dir.exists())
                    {
                        dir.mkdirs();
                    }

                    File outFile = new File(dir, ACTIVE_FILE_NAME + fpType + ".txt");

                    //FileOutputStream fos = new FileOutputStream(outFile);
                    //PrintWriter pw =  new PrintWriter(fos);

                    BufferedWriter out = new BufferedWriter(new FileWriter(outFile, true));
                    out.write(mytext + "\n");
                    out.flush();
                    out.close();

                    return true;

                }

            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }
            return false;
        }


    }

    private class FingerprintsServerHandler extends AsyncTask<ArrayList<JSONObject>, Void, Void>
    {
        private ProgressDialog serverDialog;
        private String requestPath;
        private String controllerPath;
        private String commitMsg;
        private String fpKey;

        public FingerprintsServerHandler(String reqP, String controllerP, String commitMsgP, String fpKeyP)
        {
            this.requestPath = reqP;
            this.controllerPath = controllerP;
            this.commitMsg = commitMsgP;
            this.fpKey = fpKeyP;
        }


        @Override
        protected void onPreExecute()
        {
            super.onPreExecute();
            serverDialog = ProgressDialog.show(Fingerprinting.this, "Syncing", "Sending to server...", true);
            Toast.makeText(Fingerprinting.this, "Sending to server...", Toast.LENGTH_SHORT).show();
            //mProgressDialog.setMessage("Sending to server...");
        }

        @Override
        protected Void doInBackground(ArrayList<JSONObject>... params)
        {
            try
            {
                makePostRequest(params[0], requestPath, controllerPath, commitMsg, fpKey);
            } catch (IOException e)
            {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void v)
        {
            serverDialog.dismiss();
            Toast.makeText(Fingerprinting.this, "Sent to Server!", Toast.LENGTH_SHORT).show();
        }

    }
}
