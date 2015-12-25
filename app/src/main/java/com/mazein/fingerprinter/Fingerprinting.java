package com.mazein.fingerprinter;

import android.Manifest;
import android.annotation.TargetApi;
import android.support.v7.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
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

public class Fingerprinting extends AppCompatActivity
{
    private static final String LOG_TAG = "FP";
    private static final int PERMISSION_REQUEST_COARSE_LOCATION = 1;

    private WebView mapWebView;
    private ProgressDialog mProgressDialog;
    private WifiManager mWifiManager;
    private IntentFilter mIntentFilter;
    private BroadcastReceiver mBroadcastReceiver;
    private ArrayList<ScanResult> mScanResults;

    @Override
    public void onPause()
    {
        super.onPause();
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

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Android M Permission check
            if (this.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("This app needs location access");
                builder.setMessage("Please grant location access so this app can scan for WiFi.");
                builder.setPositiveButton(android.R.string.ok, null);
                builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @TargetApi(Build.VERSION_CODES.M)
                    public void onDismiss(DialogInterface dialog) {
                        requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, PERMISSION_REQUEST_COARSE_LOCATION);
                    }
                });
                builder.show();
            }
        }

        setContentView(R.layout.activity_main);

        mWifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);

        mapWebView =(WebView) findViewById(R.id.webView);
        WebSettings mapWebSettings = mapWebView.getSettings();
        mapWebSettings.setJavaScriptEnabled(true);
        mapWebView.addJavascriptInterface(new WebAppInterface(this), "Android");
        mapWebView.loadUrl("file:///android_asset/nu_f/index.html");
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings)
        {
            Toast.makeText(this, "Settings not available yet", Toast.LENGTH_SHORT).show();
        }
        else if(id == R.id.send_fingerprints)
        {
            File dir = new File(Environment.getExternalStorageDirectory(), "MazeIn Fingerprints");
            if(!dir.exists())
            {
                dir.mkdirs();
                Toast.makeText(Fingerprinting.this, "Folder doesn't exist!", Toast.LENGTH_SHORT).show();
                return false;
            }
            File outFile = new File(dir, "fingerprintData.txt");
            if(!outFile.exists())
            {
                Toast.makeText(Fingerprinting.this, "File missing!", Toast.LENGTH_SHORT).show();
                return false;
            }
            Uri U = Uri.fromFile(outFile);
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("text/plain");
            i.putExtra(Intent.EXTRA_STREAM, U);
            Toast.makeText(Fingerprinting.this, "Sending File...", Toast.LENGTH_SHORT).show();
            startActivity(Intent.createChooser(i,"Email:"));
        }
        else if(id == R.id.reset_fingerprints)
        {
            File dir = new File(Environment.getExternalStorageDirectory(), "MazeIn Fingerprints");
            if(!dir.exists())
            {
                dir.mkdirs();
                Toast.makeText(Fingerprinting.this, "Folder doesn't exist!", Toast.LENGTH_SHORT).show();
                return false;
            }
            File outFile = new File(dir, "fingerprintData.txt");
            if(!outFile.exists())
            {
                Toast.makeText(Fingerprinting.this, "File missing!", Toast.LENGTH_SHORT).show();
                return false;
            }
            outFile.delete();
            Toast.makeText(Fingerprinting.this, "File Deleted!", Toast.LENGTH_SHORT).show();
        }

        return false;
    }



    /**
     * First gets the wifi manager system service and start to scan for access
     * points. When the scan is completed an asynchonous message will be sent.
     * When this is done, the found results will be prepared in order for them
     * to be displayed in a listview.
     */


    public class WebAppInterface{
        Context mContext;

        WebAppInterface(Context c){
            mContext = c;
        }

        @JavascriptInterface
        public void initializeWifiScan(final float startX, final float startY) {

            mProgressDialog = ProgressDialog.show(Fingerprinting.this, "Wifi Scan",
                    "Scanning at: \n" + String.valueOf(startX) + ", " + String.valueOf(startY), true);

            mIntentFilter = new IntentFilter();

            mIntentFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);

            mBroadcastReceiver = new BroadcastReceiver() {

                @Override
                public void onReceive(Context context, Intent intent) {
                    Log.d("WLAN", "Receiving WLAN Scan results");

                    mScanResults = (ArrayList<ScanResult>) mWifiManager
                            .getScanResults();

                    for(ScanResult result : mScanResults)
                    {
                        Log.i("RESULT", result.BSSID + " " + result.level + " (" + startX + "," + startY + ")");
                        //osw.write("{" + startX + "," + startY + ": " + result.toString());
                        String fingerprintJSON = new String("{\"xcoord\": " + startX +
                                ", \"ycoord\": " + startY +
                                ", \"SSID\": \"" + result.SSID + "\"" +
                                ", \"BSSID\": \"" + result.BSSID + "\"" +
                                ", \"RSSI\": " + result.level +
                                ", \"mac+\": " + result.BSSID +
                                "}\n");

                        saveFile(context, fingerprintJSON);
                        new SendToServer().execute(fingerprintJSON);
                    }

                    mProgressDialog.dismiss();
                    unregisterReceiver(mBroadcastReceiver);
                    //saveResults.run();
                }

                ;
            };

            registerReceiver(mBroadcastReceiver, mIntentFilter);
            scanWifi.run();
        }
        public boolean saveFile(Context context, String mytext){
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

                    File outFile = new File(dir, "fingerprintData.txt");

                    //FileOutputStream fos = new FileOutputStream(outFile);

                    BufferedWriter out = new BufferedWriter(new FileWriter(outFile, true));
                    out.write(mytext);
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
        /**
         * Start the WIFI scan
         */
        private Runnable scanWifi = new Runnable() {

            public void run() {
                mWifiManager.startScan();
            }
        };


    }

    private void makePostRequest(String fingerprintString) throws IOException
    {

        try
        {
            JSONObject fingerprintJson = new JSONObject();
            fingerprintJson.put("utf-8", "✓");
            fingerprintJson.put("authenticity_token", "teBBgZ65BOd/SRoibfoiOZ7CxE8BWkypu/FS7tvu6a4=");
            fingerprintJson.put("commit", "Create Finger print");
            fingerprintJson.put("action", "create");
            fingerprintJson.put("controller", "finger_prints");
            fingerprintJson.put("finger_print", fingerprintString);

            //new JSONObject(fingerprintString);
            HttpURLConnection con = (HttpURLConnection) (new URL("https://127.0.0.1:3308/finger_prints").openConnection());
            con.setDoOutput(true);
            con.setDoInput(true);
            con.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            con.setRequestProperty("Accept", "application/json");
            con.setRequestMethod("POST");

            Log.d("JSON_TO_SERVER", fingerprintJson.toString());

            String test = new String("{\"utf8\"=>\"✓\", \"authenticity_token\"=>\"63ij11B+cx/h8+t30S1Ir8ywxcovAiTk+JC3YcJJEwA=\", \n" +
                    "\"finger_print\"=>{\"place_id\"=>\"1\", \"xcoord\"=>\"23\", \"ycoord\"=>\"22\", \"BSSID\"=>\"432\", \"SSID\"=>\"123\",\n" +
                    " \"RSSI\"=>\"2123\", \"SD\"=>\"\", \"mac\"=>\"12\"}, \"commit\"=>\"Create Finger print\", \"action\"=>\"create\", \"controller\"=>\"finger_prints\"}");

            OutputStreamWriter wr= new OutputStreamWriter(con.getOutputStream());
            wr.write(test);
            wr.flush();
            StringBuilder sb = new StringBuilder();
            int HttpResult = con.getResponseCode();
            if(HttpResult == HttpURLConnection.HTTP_OK){
                BufferedReader br = new BufferedReader(new InputStreamReader(con.getInputStream(),"utf-8"));
                String line = null;
                while ((line = br.readLine()) != null) {
                    sb.append(line + "\n");
                }

                br.close();
                Log.i("JSON_TO_SERVER", "Response: " + sb.toString());
                Toast.makeText(Fingerprinting.this, "Server Response: OK", Toast.LENGTH_SHORT).show();

            }else{
                System.out.println(con.getResponseMessage());
//                Toast.makeText(Fingerprinting.this, "Server Response: ERR", Toast.LENGTH_SHORT).show();
            }
        }
        catch (JSONException e)
        {
            Log.e("POST_REQ", "Couldn't convert Fingerprint to JSON");
            e.printStackTrace();
        }



    }
    class SendToServer extends AsyncTask<String, Void, Void>
    {

        @Override
        protected Void doInBackground(String... params)
        {
            try
            {
                makePostRequest(params[0]);
            } catch (IOException e)
            {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void v)
        {
            //Toast.makeText(Fingerprinting.this, "Sent to Server!", Toast.LENGTH_SHORT).show();
            return;
        }

    }
}
