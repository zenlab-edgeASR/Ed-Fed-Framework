package fl.android_client;

import static fl.utils.copytoappdata.copyfile;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;

import com.google.android.material.snackbar.Snackbar;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.os.Looper;
import android.text.TextUtils;
import android.view.View;

import androidx.core.app.ActivityCompat;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import fl.android_client.databinding.ActivityMainBinding;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import timber.log.Timber;

import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;
import android.widget.Toolbar;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.ref.WeakReference;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {

    //public static MainActivity speechToTextMainActivity;
    private AppBarConfiguration appBarConfiguration;
    private ActivityMainBinding binding;
    public static final int PERMISSION_RECORD_AUDIO_REQUEST_CODE = 1001;
    private final String[] appPermissions = new String[]{android.Manifest.permission.RECORD_AUDIO, android.Manifest.permission.READ_EXTERNAL_STORAGE, android.Manifest.permission.WRITE_EXTERNAL_STORAGE};


    //initial permission requests
    public void requestAudioRecordingPermission() {
        System.out.println("fl_client, Main, requestAudioRecordingPermission");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                Timber.d("fl_client, Main, permission not granted");
                requestPermissions(appPermissions, PERMISSION_RECORD_AUDIO_REQUEST_CODE);
            }

            //copy the files necessary into the internal storage of the app
            try {
                copyfile(MainActivity.this, "RoundInfo.txt");
                copyfile(MainActivity.this, "onDeviceTraining_dataset.csv");
                copyfile(MainActivity.this, "ground_truth.csv");
                copyfile(MainActivity.this, "TrainingLog.txt");
                copyfile(MainActivity.this, "PhoneData.txt");
                //comment this if you want to get global weights from the server and save them
                copyfile(MainActivity.this,"FL_weights_0.ckpt");
                copyfile(MainActivity.this,"min_checkpoint.ckpt");

            } catch (IOException e) {
                e.printStackTrace();
            }


        } else {
            Toast.makeText(getBaseContext(), "Tap to speak on the mic",
                    Toast.LENGTH_SHORT).show();
        }
    }


    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        // If request is cancelled, the result arrays are empty.
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_RECORD_AUDIO_REQUEST_CODE) {
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // permission was granted, yay! Do the
                // contacts-related task you need to do.
                Toast.makeText(getBaseContext(), "Please Hold while we setup the app",
                        Toast.LENGTH_SHORT).show();

            } else {
                // permission denied, boo! Disable the
                // functionality that depends on this permission.
                Toast.makeText(getBaseContext(), "You must enable the permission",
                        Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Runtime rt = Runtime.getRuntime();
        long maxMemory = rt.maxMemory();
        System.out.println("onCreate" + " maxMemory:" + Long.toString(maxMemory));

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);
//        binding.toolbarTitle(R.);
//        getSupportActionBar(tool_bar).setTitle(getString(R.string.beermap));

        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        appBarConfiguration = new AppBarConfiguration.Builder(navController.getGraph()).build();
        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);
        requestAudioRecordingPermission();

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

//    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            this.finishAffinity();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onSupportNavigateUp() {
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        return NavigationUI.navigateUp(navController, appBarConfiguration)
                || super.onSupportNavigateUp();
    }

}