package comvlievin.github.companionlight;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.SeekBar;

public class HomeScreen extends AppCompatActivity {

    private int mHue = 0;
    private int mGain = 100;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState); // Always call the superclass first

        Intent intent = getIntent();
        mGain = intent.getIntExtra("gain" , 100);
        mHue = intent.getIntExtra("hue" , 0);
        // Check whether we're recreating a previously destroyed instance
        if (savedInstanceState != null) {
            // Restore value of members from saved state
            mGain = savedInstanceState.getInt("gain");
            mHue = savedInstanceState.getInt("hue");
            Log.i("#home", "onCreate | gain: " + mGain);
        }
        else
        {
            Log.i("#home", "onCreate, nothing");
        }

        setContentView(R.layout.activity_home_screen);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        if (fab != null) {
            fab.setImageResource(R.drawable.ic_refresh_24dp);
            fab.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Snackbar.make(view, "Scanning..", Snackbar.LENGTH_LONG)
                            .setAction("Action", null).show();


                    Intent intent = new Intent();
                    intent.setAction(Constants.ACTION.SCAN_ACTION);
                    //intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
                    sendBroadcast(intent);
                }
            });

            // start service
            Intent startIntent = new Intent(HomeScreen.this, CompanionService.class);
            startIntent.setAction(Constants.ACTION.STARTFOREGROUND_ACTION);
            startService(startIntent);
        }

        Button clickButton = (Button) findViewById(R.id.btn_start);
        if (clickButton != null) {
            clickButton.setOnClickListener(new View.OnClickListener() {

                @Override
                public void onClick(View v) {
                    // TODO Auto-generated method stub
                    //if (!CompanionService.isInstanceCreated())
                    //{
                    Intent startIntent = new Intent(HomeScreen.this, CompanionService.class);
                    startIntent.setAction(Constants.ACTION.STARTFOREGROUND_ACTION);
                    startService(startIntent);
                    //}


                }
            });
        }
        Button resetButton = (Button) findViewById(R.id.btn_reset);
        if (resetButton != null) {
            resetButton.setOnClickListener(new View.OnClickListener() {

                @Override
                public void onClick(View v) {
                    // TODO Auto-generated method stub
                    Intent startIntent = new Intent(HomeScreen.this, CompanionService.class);
                    startIntent.setAction(Constants.ACTION.RESET_ORDER);
                    startService(startIntent);
                }
            });
        }
        Button autoBtn = (Button) findViewById(R.id.bnt_auto);
        if (autoBtn != null) {
            autoBtn.setOnClickListener(new View.OnClickListener() {

                @Override
                public void onClick(View v) {
                    // TODO Auto-generated method stub
                    Intent startIntent = new Intent(HomeScreen.this, CompanionService.class);
                    startIntent.setAction(Constants.ACTION.ON_AUTO_CHANGE);
                    startService(startIntent);

                }
            });
        }
        Button btn_stop_service = (Button) findViewById(R.id.btn_stop);
        if (btn_stop_service != null) {
            btn_stop_service.setOnClickListener( new View.OnClickListener() {

                @Override
                public void onClick(View v) {
                    // TODO Auto-generated method stub
                    Intent stopIntent = new Intent(HomeScreen.this, CompanionService.class);
                    stopIntent.setAction(Constants.ACTION.STOPFOREGROUND_ACTION);
                    startService(stopIntent);
                }
            });
        }

        SeekBar hueBar = (SeekBar) findViewById(R.id.hue_seekbar);
        hueBar.setProgress(mHue);
        hueBar.setMax(360);
        hueBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress,
                                          boolean fromUser) {
                mHue = progress;

                Intent intent = new Intent();
                intent.setAction(Constants.ACTION.ON_HUE_CHANGE);
                intent.putExtra("hue", mHue);
                sendBroadcast(intent);

                updateColorFrame();
            }
        });

        SeekBar gainBar = (SeekBar) findViewById(R.id.gain_seekbar);
        gainBar.setProgress(mGain);
        gainBar.setMax(100);
        gainBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress,
                                          boolean fromUser) {
                mGain = progress;

                Intent intent = new Intent();
                intent.setAction(Constants.ACTION.ON_GAIN_CHANGE);
                intent.putExtra("gain", mGain);
                sendBroadcast(intent);

                updateColorFrame();
            }
        });
    }

        @Override
    protected void onSaveInstanceState(Bundle outState) {

        outState.putInt("gain", mGain);
        outState.putInt("hue", mHue);

        Log.i("#Home" , "onSave");

        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        mGain = savedInstanceState.getInt("gain");
        mHue = savedInstanceState.getInt("hue");

        Log.i("#Home" , "onRestore");

        SeekBar hueBar = (SeekBar) findViewById(R.id.hue_seekbar);
        SeekBar gainBar = (SeekBar) findViewById(R.id.gain_seekbar);
        hueBar.setProgress(mHue);
        gainBar.setProgress(mGain);
    }

    private void updateColorFrame(){
        float[] hsl = new float[]{mHue, 1, 1};
        int ColorToSet = Color.HSVToColor(hsl);
        ColorToSet = Color.argb((int)(255.0f *(float)mGain/100.0f) ,Color.red(ColorToSet),Color.green(ColorToSet),Color.blue(ColorToSet) );
        FrameLayout rl = (FrameLayout)findViewById(R.id.colorFrame);
        rl.setBackgroundColor(ColorToSet);
    }

}
