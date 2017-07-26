package com.example.synerzip.s3bucketdemo;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.Signature;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Environment;
import android.os.Parcelable;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.amazonaws.SDKGlobalConfiguration;
import com.amazonaws.auth.CognitoCachingCredentialsProvider;
import com.amazonaws.auth.CognitoCredentialsProvider;
import com.amazonaws.auth.policy.resources.S3BucketResource;
import com.amazonaws.auth.policy.resources.S3ObjectResource;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferListener;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferObserver;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferState;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferUtility;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.Permission;
import com.facebook.AccessToken;
import com.facebook.CallbackManager;
import com.facebook.FacebookCallback;
import com.facebook.FacebookException;
import com.facebook.FacebookSdk;
import com.facebook.Profile;
import com.facebook.appevents.AppEventsLogger;
import com.facebook.login.LoginManager;
import com.facebook.login.LoginResult;
import com.facebook.login.widget.LoginButton;
import com.squareup.picasso.Picasso;

import java.io.File;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Permissions;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.jar.Manifest;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

import static android.os.Environment.DIRECTORY_PICTURES;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final int READ_EXTERNAL_STORAGE = 1;
    private AmazonS3Client s3Client;
    private String accessToken;
    private TransferUtility transferUtility;

    @BindView(R.id.upload)
    protected Button mBtnUpload;

    @BindView(R.id.recylerview)
    protected RecyclerView mRecyclerView;

    @BindView(R.id.login)
    protected LoginButton mBtnLogIn;

    @BindView(R.id.attach)
    protected ImageView mBtnAttach;
    @BindView(R.id.img_profile)
    protected ImageView mImgProfile;

    private File file;
    private File[] listFile;
    private String[] filePathStrings;
    private String[] fileNameStrings;
    private TransferObserver transferObserver;
    private CallbackManager callbackManager;
    private CustomAdapter adapter;
    private LinearLayoutManager mLayoutManager;
    private Uri captureImageOutputUri;
    private Bitmap myBitmap;
    private Uri picUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);

        callbackManager = CallbackManager.Factory.create();

        mLayoutManager = new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false);
        mRecyclerView.setLayoutManager(mLayoutManager);

        file = new File(Environment.getExternalStoragePublicDirectory(DIRECTORY_PICTURES) + "/Voters Id.jpg");

        mBtnLogIn.setReadPermissions("email");

        //to print path of each file inside directory

        if (file.isDirectory()) {
            listFile = file.listFiles();
            filePathStrings = new String[listFile.length];
            fileNameStrings = new String[listFile.length];
            for (int i = 0; i < listFile.length; i++) {
                // Get the path of the image file
                filePathStrings[i] = listFile[i].getAbsolutePath();
                Log.v("Path", listFile[i].getAbsolutePath());
                // Get the name image file
                fileNameStrings[i] = listFile[i].getName();
            }
        }

        Picasso.with(this).load("https://rc-mvp.s3.ap-south-1.amazonaws.com/Voters%2520Id.jpg").into(mImgProfile);

        credentialsProvider();

        setTransferUtility();

        try {
            PackageInfo info = getPackageManager().getPackageInfo(
                    "com.example.synerzip.s3bucketdemo",
                    PackageManager.GET_SIGNATURES);
            for (Signature signature : info.signatures) {
                MessageDigest md = MessageDigest.getInstance("SHA");
                md.update(signature.toByteArray());
                Log.d("Your Tag", Base64.encodeToString(md.digest(), Base64.DEFAULT));
            }
        } catch (PackageManager.NameNotFoundException e) {

        } catch (NoSuchAlgorithmException e) {

        }


        mBtnLogIn.registerCallback(callbackManager, new FacebookCallback<LoginResult>() {
            @Override
            public void onSuccess(LoginResult loginResult) {
                accessToken = loginResult.getAccessToken().getToken();
                Log.v("onSuccess", "Token" + accessToken);
                AccessToken.refreshCurrentAccessTokenAsync();
                Log.v(TAG, "Expires on" + loginResult.getAccessToken().getExpires());
                Log.v(TAG, "Latest Refresh at" + loginResult.getAccessToken().getLastRefresh());

                Profile profile = Profile.getCurrentProfile();
                if (profile != null) {
                    Log.v("onSuccess", "Name:" + profile.getFirstName() + " " + profile.getLastName());
                }
            }

            @Override
            public void onCancel() {
                Toast.makeText(getApplicationContext(), "Login cancelled", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(FacebookException error) {
                Toast.makeText(getApplicationContext(), "Login Error", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();

        mBtnUpload.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (ContextCompat.checkSelfPermission(MainActivity.this, android.Manifest.
                        permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                    transferObserver = transferUtility.upload("snehaldemobucket", "Voters Id.jpg", file);
                    Log.v("Bucket Name", transferObserver.getBucket());
                    transferObserverListener(transferObserver);
                    Log.v("***Link", s3Client.getUrl(transferObserver.getBucket(), "Voters Id.jpg") + "");
                } else {
                    ActivityCompat.requestPermissions(MainActivity.this,
                            new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE},
                            READ_EXTERNAL_STORAGE);
                }
            }
        });
    }

    @OnClick(R.id.attach)
    public void choosePhoto() {
        Log.v("Click", "attach");
        startActivityForResult(getPickImageChooserIntent(), 200);
    }

    private Intent getPickImageChooserIntent() {
        Uri outputFileUri = getCaptureImageOutputUri();
        List<Intent> allIntents = new ArrayList();
        PackageManager packageManager = getPackageManager();

        Intent captureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        List<ResolveInfo> lisCam = packageManager.queryIntentActivities(captureIntent, 0);
        for (ResolveInfo info : lisCam) {

            Intent intent = new Intent(captureIntent);
            intent.setComponent(new ComponentName(info.activityInfo.packageName, info.activityInfo.name));
            intent.setPackage(info.activityInfo.packageName);
            if (outputFileUri != null) {
                intent.putExtra(MediaStore.EXTRA_OUTPUT, outputFileUri);
            }
            allIntents.add(intent);
        }

        // collect all gallery intents
        Intent galleryIntent = new Intent(Intent.ACTION_GET_CONTENT);
        galleryIntent.setType("image/*");
        List<ResolveInfo> listGallery = packageManager.queryIntentActivities(galleryIntent, 0);
        for (ResolveInfo res : listGallery) {
            Intent intent = new Intent(galleryIntent);
            intent.setComponent(new ComponentName(res.activityInfo.packageName, res.activityInfo.name));
            intent.setPackage(res.activityInfo.packageName);
            allIntents.add(intent);
        }

        // the main intent is the last in the list (fucking android) so pickup the useless one
        Intent mainIntent = allIntents.get(allIntents.size() - 1);


        for (Intent intent : allIntents) {
            if (intent.getComponent().getClassName().equals("com.android.documentsui.DocumentsActivity")) {
                mainIntent = intent;
                break;
            }
        }
        allIntents.remove(mainIntent);

        // Create a chooser from the main intent
        Intent chooserIntent = Intent.createChooser(mainIntent, "Select source");
        chooserIntent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);

        // Add all other intents
        chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, allIntents.toArray(new Parcelable[allIntents.size()]));

        return chooserIntent;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(getApplicationContext(), "You can't Upload the Image", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        callbackManager.onActivityResult(requestCode, resultCode, data);

        Bitmap bitmap;
        if (resultCode == Activity.RESULT_OK) {

            ImageView imageView = (ImageView) findViewById(R.id.img_profile);

            if (getPickImageResultUri(data) != null) {
                picUri = getPickImageResultUri(data);
                Log.v("Selected Image URI", "" + picUri.toString());
                try {
                    myBitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), picUri);

                    ImageView croppedImageView = (ImageView) findViewById(R.id.img_profile);
                    croppedImageView.setImageBitmap(myBitmap);
                    imageView.setImageBitmap(myBitmap);

                } catch (IOException e) {
                    e.printStackTrace();
                }


            } else if (data.getClipData()!=null){
                Log.v("Data",data.toString());

            }else {
                bitmap = (Bitmap) data.getExtras().get("data");

                myBitmap = bitmap;
                ImageView croppedImageView = (ImageView) findViewById(R.id.img_profile);
                if (croppedImageView != null) {
                    croppedImageView.setImageBitmap(myBitmap);
                }

                imageView.setImageBitmap(myBitmap);

            }

        }


    }

    private Uri getPickImageResultUri(Intent data) {
        boolean isCamera = true;
        if (data != null) {
            String action = data.getAction();
            isCamera = action != null && action.equals(MediaStore.ACTION_IMAGE_CAPTURE);
        }
        return isCamera ? getCaptureImageOutputUri() : data.getData();
    }

    private void transferObserverListener(TransferObserver transferObserver) {
        transferObserver.setTransferListener(new TransferListener() {

            @Override
            public void onStateChanged(int id, TransferState state) {
                Log.e("statechange", state + "");
            }

            @Override
            public void onProgressChanged(int id, long bytesCurrent, long bytesTotal) {
                int percentage = (int) (bytesCurrent / bytesTotal * 100);
                if (percentage == 100) {
                    Toast.makeText(getApplicationContext(), "Image uploaded succefully",
                            Toast.LENGTH_SHORT).show();
                }
                Log.e("percentage", percentage + "");
            }

            @Override
            public void onError(int id, Exception ex) {
                Log.e("error", "");
                Log.e("ERROR", ex.toString());
            }

        });
    }

    private void setTransferUtility() {
        transferUtility = new TransferUtility(s3Client, getApplicationContext());
    }

    private void credentialsProvider() {
        CognitoCredentialsProvider credentialsProvider = new CognitoCredentialsProvider(
                "ap-south-1:0047cfa2-0aec-42c3-a012-16eeb25805e2",//Identity_pool_id
                Regions.AP_SOUTH_1); //region

        setAmazonS3Client(credentialsProvider);
    }

    private void setAmazonS3Client(CognitoCredentialsProvider credentialsProvider) {
        s3Client = new AmazonS3Client(credentialsProvider);
        s3Client.setRegion(Region.getRegion(Regions.AP_SOUTH_1));
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        Log.v(TAG, "onBackPressed");
        LoginManager.getInstance().logOut();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.v(TAG, "onDestroy");
        LoginManager.getInstance().logOut();

    }

    public Uri getCaptureImageOutputUri() {
        Uri outputFileUri = null;
        File getImage = getExternalCacheDir();
        if (getImage != null) {
            outputFileUri = Uri.fromFile(new File(getImage.getPath(), "IMG_" + new Date().getTime() + ".jpg"));
        }
        return outputFileUri;
    }
}
