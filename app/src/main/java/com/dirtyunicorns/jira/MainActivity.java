package com.dirtyunicorns.jira;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.Html;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Switch;
import android.widget.TextView;

import com.android.volley.AuthFailureError;
import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import cz.msebera.android.httpclient.HttpEntity;
import cz.msebera.android.httpclient.HttpResponse;
import cz.msebera.android.httpclient.client.ClientProtocolException;
import cz.msebera.android.httpclient.client.HttpClient;
import cz.msebera.android.httpclient.client.methods.HttpPost;
import cz.msebera.android.httpclient.entity.mime.MultipartEntityBuilder;
import cz.msebera.android.httpclient.entity.mime.content.FileBody;
import cz.msebera.android.httpclient.impl.client.DefaultHttpClient;
import cz.msebera.android.httpclient.util.EntityUtils;


public class MainActivity extends AppCompatActivity implements ActivityCompat.OnRequestPermissionsResultCallback {

    private static String TAG = AppCompatActivity.class.toString();

    private String jiraUrl = "http://jira.dirtyunicorns.com";
    private Boolean isSystemApp;
    private Boolean mBugreportChecked = null;

    private EditText mUsername;
    private EditText mPassword;
    private EditText mIssueText;
    private EditText mSummary;
    private Switch mBugreport;
    private RadioButton radioBugreport;
    private RadioButton radioFeatureRequest;
    private Button buttonAttachment;
    private TextView mUrl;
    private static final int PICKFILE_RESULT_CODE = 1;
    private Uri mUserPickedFile;

    private static String[] PERMISSIONS_STORAGE = {Manifest.permission.READ_EXTERNAL_STORAGE};
    private static final int REQUEST_STORAGE = 1;

    private View mLayout;

    private Handler mHandler;
    private ProgressBar mProgress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_issue);
        mLayout = findViewById(R.id.main_layout);

        isSystemApp = (getApplicationInfo().flags
                & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0;

        mHandler = new Handler();
        mUsername = (EditText) findViewById(R.id.editText_username);
        mPassword = (EditText) findViewById(R.id.editText_password);
        mIssueText = (EditText) findViewById(R.id.editText_issue_desc);
        mSummary = (EditText) findViewById(R.id.editText_summary);
        mBugreport = (Switch) findViewById(R.id.switch_bugreport);
        mUrl = (TextView) findViewById(R.id.textView_url);
        mProgress = (ProgressBar) findViewById(R.id.progressBar);
        mProgress.setVisibility(View.GONE);
        radioBugreport = (RadioButton) findViewById(R.id.radioBugreport);
        radioBugreport.setOnCheckedChangeListener(new RadioButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
                if (isChecked) {
                    mBugreport.setVisibility(View.VISIBLE);
                }
            }
        });
        radioFeatureRequest = (RadioButton) findViewById(R.id.radioFeatureRequest);
        radioFeatureRequest.setOnCheckedChangeListener(new RadioButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
                if (isChecked) {
                    mBugreport.setVisibility(View.GONE);
                }
            }
        });
        buttonAttachment = (Button) findViewById(R.id.radioAttachmentbutton);
        requestStoragePermissions();
        buttonAttachment.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("*/*");
                String[] mimetypes = {"image/*", "video/*", "text/*"};
                intent.putExtra(Intent.EXTRA_MIME_TYPES, mimetypes);
                startActivityForResult(intent, PICKFILE_RESULT_CODE);
            }
        });

        RadioGroup radioGroup = (RadioGroup) findViewById(R.id.radioGroup);
        radioGroup.check(radioBugreport.getId());
        radioGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                RadioButton rb = (RadioButton) group.findViewById(checkedId);
                if(null!=rb && checkedId > -1){
                    System.out.println("Radio : " + rb.getId());
                }
            }
        });



        mBugreport.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                mBugreportChecked = isChecked;
                System.out.println("mBugreportChecked : " + mBugreportChecked);
            }
        });

        FloatingActionButton mFabReport = (FloatingActionButton) findViewById(R.id.fab_report);

        mFabReport.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                attemptLogin();
            }
        });

    }

    /**
     * Requests the Storage permissions.
     * If the permission has been denied previously, a SnackBar will prompt the user to grant the
     * permission, otherwise it is requested directly.
     */
    private void requestStoragePermissions() {

        Log.d(TAG, "requestStoragePermissions");

        if ((ActivityCompat.shouldShowRequestPermissionRationale(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE))
                || (ActivityCompat.shouldShowRequestPermissionRationale(this,
                Manifest.permission.READ_EXTERNAL_STORAGE))) {

            // Provide an additional rationale to the user if the permission was not granted
            // and the user would benefit from additional context for the use of the permission.
            // For example, if the request has been denied previously.

            // Display a SnackBar with an explanation and a button to trigger the request.
            Snackbar.make(mLayout, R.string.permission_sdcard,
                    Snackbar.LENGTH_INDEFINITE)
                    .setAction(R.string.ok, new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            ActivityCompat
                                    .requestPermissions(MainActivity.this, PERMISSIONS_STORAGE,
                                            REQUEST_STORAGE);
                        }
                    })
                    .show();
        } else {
            // Storage permissions have not been granted yet. Request them directly.
            ActivityCompat.requestPermissions(this, PERMISSIONS_STORAGE, REQUEST_STORAGE);
        }
    }

    /**
     * Callback received when a permissions request has been completed.
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {

        if (requestCode == REQUEST_STORAGE) {
            Log.d(TAG, "onRequestPermissionsResult");
            if (PermissionHelper.verifyPermissions(grantResults)) {
                Snackbar.make(mLayout, R.string.permision_available_storage,
                        Snackbar.LENGTH_LONG)
                        .show();

            } else {
                Snackbar.make(mLayout, R.string.permissions_not_granted,
                        Snackbar.LENGTH_LONG)
                        .show();
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }



    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch(requestCode){
            case PICKFILE_RESULT_CODE:
                if(resultCode==RESULT_OK){
                    if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                            != PackageManager.PERMISSION_GRANTED) {
                    } else {
                        Log.i(TAG,"SDCARD permission has already been granted.");
                    }

                    mUserPickedFile = data.getData();
                    Log.d(TAG, "user attachment file: " + mUserPickedFile);

                    String selectedFilePath = GetFilePathFromDevice.getPath(this, mUserPickedFile);
                    if (selectedFilePath != null) {
                        File file = new File(selectedFilePath);
                        int fileSizeKb = Integer.parseInt(String.valueOf(file.length()/1024));
                        Log.d(TAG, "getUsableSpace: " + fileSizeKb + " kb");
                        if (fileSizeKb > 19999) {
                            Snackbar.make(mLayout, "File needs to be smaller than 20 mb",
                                    Snackbar.LENGTH_LONG)
                                    .show();
                            mUserPickedFile = null;
                            break;
                        }

                        buttonAttachment.setText(file.getName());
                    }
                }
                break;

        }
    }

    /* Test login, and try to login.  */
    /* This start's everything */
    private void attemptLogin() {
        Log.d(TAG, "attemptLogin");
        mUsername.setError(null);
        mPassword.setError(null);
        mIssueText.setError(null);
        mSummary.setError(null);

        String username = mUsername.getText().toString();
        String password = mPassword.getText().toString();
        String issueText = mIssueText.getText().toString();
        String summaryText = mSummary.getText().toString();


        mIssueText.setText(mIssueText.getText().toString().replaceAll("#", " "));
        mSummary.setText(mSummary.getText().toString().replaceAll("#", " "));

        boolean cancel = false;
        View focusView = null;

        if (TextUtils.isEmpty(username)) {
            mUsername.setError(getString(R.string.error_field_required));
            focusView = mUsername;
            cancel = true;
        }

        if (TextUtils.isEmpty(password)) {
            mPassword.setError(getString(R.string.error_field_required));
            focusView = mPassword;
            cancel = true;
        }

        if (TextUtils.isEmpty(summaryText)) {
            mSummary.setError(getString(R.string.error_field_required));
            focusView = mSummary;
            cancel = true;
        }

        if (TextUtils.isEmpty(issueText)) {
            mIssueText.setError(getString(R.string.error_field_required));
            focusView = mIssueText;
            cancel = true;
        }

        if (cancel) {
            focusView.requestFocus();
        } else {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        mProgress.setVisibility(View.VISIBLE);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                }
            });
            authJira();
        }
    }

    /* Get Auth for jira */
    private void authJira() {
        Log.d(TAG, "authJira");
        RequestQueue queue = Volley.newRequestQueue(this);
        String url = "http://jira.dirtyunicorns.com/rest/auth/1/session";
        final String username = mUsername.getText().toString();
        final String pass = mPassword.getText().toString();

        StringRequest postRequest = new StringRequest(Request.Method.GET, url,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        // response
                        Log.d("Response", response);
                        createIssue();
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError volleyError) {
                        stopProgresBar();
                        NetworkResponse networkResponse = volleyError.networkResponse;
                        if (networkResponse != null) {
                            Log.d(TAG, "authJira Statuscode: " + networkResponse.statusCode);
                            Log.d(TAG, "authJira error: " + networkResponse.headers.toString());

                            if (networkResponse.data != null) {
                                switch (networkResponse.statusCode) {
                                    case 401:
                                        Snackbar.make(mLayout, "401: Failed to authorize user on Jira",
                                                Snackbar.LENGTH_LONG)
                                                .show();
                                        break;
                                    case 403:
                                        Snackbar.make(mLayout, "403: Jira failed to authorize",
                                                Snackbar.LENGTH_LONG)
                                                .show();
                                        break;
                                    case 404:
                                        Snackbar.make(mLayout, "404: Jira server not found",
                                                Snackbar.LENGTH_LONG)
                                                .show();
                                        break;
                                    default:
                                        Snackbar.make(mLayout, "Unknown error: " + networkResponse.statusCode,
                                                Snackbar.LENGTH_LONG)
                                                .show();
                                        break;
                                }
                            }
                        }
                        volleyError.printStackTrace();
                    }
                }
        ) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String>  params = new HashMap<>();
                String creds = String.format("%s:%s",username,pass);
                String auth = "Basic " + Base64.encodeToString(creds.getBytes(), Base64.NO_WRAP);
                params.put("Authorization", auth);

                return params;
            }
        };
        queue.add(postRequest);

    }

    /* Create JSON request for creating issue or feature */
    private JSONObject writeJSON() {
        Log.d(TAG, "writeJSON");
        JSONObject jsonObj = new JSONObject();
        try {
            JSONObject jsonObjKey = new JSONObject();
            jsonObjKey.put("key", "DU6");

            JSONObject jsonObjName = new JSONObject();
            if (radioBugreport.isChecked()) {
                jsonObjName.put("name", "Bug Report");
                jsonObjName.put("id", "10108");
                Log.d(TAG, "issue type: Bug Report");
            } else if (radioFeatureRequest.isChecked()){
                jsonObjName.put("name", "Feature Request");
                jsonObjName.put("id", "10103");
                Log.d(TAG, "issue type: Feature Request");
            } else {
                Log.d(TAG, "no radio button selected");
            }

            JSONObject jsonObjProject = new JSONObject();
            jsonObjProject.put("project", jsonObjKey);

            jsonObjProject.put("summary", "(" + Build.MODEL + ") " + mSummary.getText().toString());
            jsonObjProject.put("description", getIssueText());
            jsonObjProject.put("issuetype", jsonObjName);

            jsonObj.put("fields", jsonObjProject);


        } catch (JSONException e) {
            e.printStackTrace();
        }
        System.out.println(jsonObj);

        return jsonObj;
    }

    private boolean checkXposed() {
        String filePath = "/system/xposed.prop";
        File file = new File(filePath);
        if(file.exists()) {
            Log.d(TAG, "xposed file found: true");
            return true;
        } else {
            Log.d(TAG, "xposed file found: false");
            return false;
        }
    }

    /* Add description text to JSON request */
    private String getIssueText() {
        Log.d(TAG, "getIssueText");
        String newline = System.getProperty("line.separator");
        String textInput = mIssueText.getText().toString();
        String manufacutrer = Build.MANUFACTURER;
        String model = Build.MODEL;
        String duVersion = CMDProcessor.runShellCommand("getprop ro.du.version").getStdout();
        String kernelVersion = CMDProcessor.runShellCommand("getprop ro.bootimage.build.fingerprint").getStdout();

        return textInput +
                newline + newline +
                "Device: " + manufacutrer + " " + model +
                newline +
                "Rom: " + duVersion +
                "Kernel fingerprint: " + kernelVersion +
                "Xposed installed: " + checkXposed();
    }

    /* Create JSON response for creating issue or feature request */
    private void createIssue() {
        Log.d(TAG, "createIssue");
        RequestQueue queue = Volley.newRequestQueue(this);
        String url = "http://jira.dirtyunicorns.com/rest/api/2/issue/";

        JsonObjectRequest postRequest = new JsonObjectRequest( Request.Method.POST, url,

                writeJSON(),
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        Log.d("Response", response.toString());

                        try {
                            JSONObject json= (JSONObject) new JSONTokener(response.toString()).nextValue();
                            final String key = (String) json.get("key");
                            mUrl.setText(Html.fromHtml("http://jira.dirtyunicorns.com/projects/DU6/issues/" + key));
                            mUrl.setMovementMethod(LinkMovementMethod.getInstance());
                            try {
                                Thread thread = new Thread(new Runnable()
                                {
                                    @Override
                                    public void run()
                                    {
                                        if (mUserPickedFile != null) {
                                            Log.d(TAG, "add user picked file");
                                            try {
                                                addAttachmentToIssue(key, getUserAttechmentPath(), "null");
                                            } catch (Exception e) {
                                                Log.d(TAG, "Exception adding user attachment: " + e);
                                            }
                                        }
                                    }
                                });

                                thread.start();
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError volleyError) {
                        stopProgresBar();
                        NetworkResponse networkResponse = volleyError.networkResponse;
                        if(networkResponse != null) {
                            Log.d(TAG, "createIssue Statuscode: " + networkResponse.statusCode);

                            if (networkResponse.data != null) {
                                switch (networkResponse.statusCode) {
                                    case 401:
                                        Snackbar.make(mLayout, "401: Failed to authorize user on Jira",
                                                Snackbar.LENGTH_LONG)
                                                .show();
                                        break;
                                    case 403:
                                        Snackbar.make(mLayout, "403: Jira failed to authorize",
                                                Snackbar.LENGTH_LONG)
                                                .show();
                                        break;
                                    case 404:
                                        Snackbar.make(mLayout, "404: Jira server not found",
                                                Snackbar.LENGTH_LONG)
                                                .show();
                                        break;
                                    default:
                                        Snackbar.make(mLayout, "Unknown error: " + networkResponse.statusCode,
                                                Snackbar.LENGTH_LONG)
                                                .show();
                                        break;
                                }
                            }
                        }
                        volleyError.printStackTrace();
                    }
                }) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                HashMap<String, String> headers = new HashMap<>();
                headers.put("Content-Type", "application/json; charset=utf-8");
                final String username = mUsername.getText().toString();
                final String pass = mPassword.getText().toString();
                String creds = String.format("%s:%s",username,pass);
                String auth = "Basic " + Base64.encodeToString(creds.getBytes(), Base64.NO_WRAP);
                headers.put("Authorization", auth);

                return headers;
            }
        };
        queue.add(postRequest);
    }

    /* Add attachment, to issue just created */
    private boolean addAttachmentToIssue(final String issueKey, final String fullfilename, final String lastType) throws IOException{
        Log.d(TAG, "addAttachmentToIssue");

        final String username = mUsername.getText().toString();
        final String pass = mPassword.getText().toString();

        //ssl java 7 setting, needed for jira
        System.setProperty("jsse.enableSNIExtension", "false");

        mHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    mProgress.setVisibility(View.VISIBLE);
                } catch (Exception e) {
                    e.printStackTrace();
                }

            }
        });

        Thread thread = new Thread(new Runnable()
        {
            @Override
            public void run()
            {
                try
                {

                    String creds = String.format("%s:%s",username,pass);
                    String auth = "Basic " + Base64.encodeToString(creds.getBytes(), Base64.NO_WRAP);
                    String boundary = "------------------------" + System.currentTimeMillis();

                    HttpClient httpclient = new DefaultHttpClient();
                    HttpPost httppost = new HttpPost(jiraUrl + "/rest/api/2/issue/" + issueKey + "/attachments");
                    httppost.setHeader("X-Atlassian-Token" , "no-check");
                    httppost.setHeader("Authorization", auth);
                    httppost.setHeader("Content-Type", "multipart/form-data; boundary=" + boundary);

                    File fileToUpload = new File(fullfilename);
                    FileBody fileBody = new FileBody(fileToUpload);

                    HttpEntity entity = MultipartEntityBuilder.create()
                            .setBoundary(boundary)
                            .addPart("file", fileBody)
                            .build();

                    httppost.setEntity(entity);
                    Log.d(TAG, "executing request " + httppost.getRequestLine());

                    HttpResponse response = null;
                    try {
                        response = httpclient.execute(httppost);
                        Log.d(TAG, "httpclient: " + EntityUtils.toString(response.getEntity()));
                    } catch (ClientProtocolException e) {
                        e.printStackTrace();
                    }

                    Log.d(TAG, "filename: " + fileToUpload.getName());

                    if (Objects.equals(lastType, "dmesg")) {
                        stopProgresBar();
                    } else if (Objects.equals(lastType, "dmesg")) {
                        addLogcat(issueKey);
                    } else {
                        addDmesg(issueKey);
                    }

                    if (response != null) {
                        if (response.getStatusLine().getStatusCode() == 200) {
                            Log.d(TAG, "response 200");
                        } else {
                            Log.d(TAG, "response failed");
                            Log.d(TAG, "status: " + response.getStatusLine().getStatusCode());
                            Log.d(TAG, "statusline: " + response.getStatusLine());
                        }
                    }
                }

                catch (Exception e)
                {
                    stopProgresBar();
                    Log.d(TAG, "general excetion occured");
                    e.printStackTrace();

                }
            }
        });

        thread.start();

        return true;
    }

    /* Attach the output from current logcat, only from cache */
    private void addLogcat(String key) {
        Log.d(TAG, "addLogcat");
        if (mBugreport.isChecked()) {
            try {
                try {
                    getFileStreamPath("logcat.txt").delete();
                } catch (Exception e) {
                    // Ignore this. Dirty way of always removing the old one, if it exists
                }

                FileOutputStream fos = getApplicationContext().openFileOutput("logcat.txt", Context.MODE_PRIVATE);
                OutputStreamWriter outputStreamWriter = new OutputStreamWriter(fos);
                outputStreamWriter.write(CMDProcessor.runShellCommand("logcat -d").getStdout());
                outputStreamWriter.close();
                Log.d(TAG, "key: " + key + " file: " + getFileStreamPath("logcat.txt").getAbsolutePath());

                addAttachmentToIssue(key, getFileStreamPath("logcat.txt").getAbsolutePath(), "logcat");
            } catch (IOException e) {
                Log.e("Exception", "File write failed: " + e.toString());
            }

        } else {
            stopProgresBar();
        }
    }

    /* Attach the output from dmesg command */
    private void addDmesg(String key) {
        Log.d(TAG, "addBugreport");
        if (mBugreport.isChecked()) {
            try {
                try {
                    getFileStreamPath("dmesg.txt").delete();
                } catch (Exception e) {
                    // Ignore this. Dirty way of always removing the old one, if it exists
                }

                FileOutputStream fos = getApplicationContext().openFileOutput("dmesg.txt", Context.MODE_PRIVATE);
                OutputStreamWriter outputStreamWriter = new OutputStreamWriter(fos);
                /* If app is system, it can use runShellCommand, if app is user, it needs to use runSuCommand */
                if (isSystemApp) {
                    outputStreamWriter.write(CMDProcessor.runShellCommand("dmesg").getStdout());
                } else {
                    outputStreamWriter.write(CMDProcessor.runSuCommand("dmesg").getStdout());
                }
                outputStreamWriter.close();
                Log.d(TAG, "key: " + key + " file: " + getFileStreamPath("dmesg.txt").getAbsolutePath());
                addAttachmentToIssue(key, getFileStreamPath("dmesg.txt").getAbsolutePath(), "dmesg");
            } catch (IOException e) {
                Log.e("Exception", "File write failed: " + e.toString());
            }

        } else {
            stopProgresBar();
        }
    }


    /* Stop progressbar */
    private void stopProgresBar() {
        Log.d(TAG, "Stop Progress Bar");
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    mProgress.setVisibility(View.GONE);
                } catch (Exception e) {
                    e.printStackTrace();
                }

            }
        });
    }

    private String getUserAttechmentPath() {
        String selectedFilePath = GetFilePathFromDevice.getPath(this, mUserPickedFile);
        Log.d(TAG, "getUserAttchment: " + selectedFilePath);
        return selectedFilePath;
    }
}
