package com.example.smiledetection;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;

@RequiresApi(api = Build.VERSION_CODES.R)
public class MainActivity extends AppCompatActivity {

    // CONSTANTS
    double DURATION = 0.04;
    int SAMPLE_RATE = 44100;
    int NUMBER_OF_SAMPLE = (int) (DURATION * SAMPLE_RATE);
    double[] sample = new double[NUMBER_OF_SAMPLE];
    double F1 = 20;
    double F2 = 2000;
    byte[] GENERATED_SOUND = new byte[2 * NUMBER_OF_SAMPLE];


    // PLAYER
    AudioTrack audioTrack;

    private static final String TAG = "AudioRecordTest";
    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;
    private static final int WRITE_EXTERNAL_STORAGE_PERMISSION = 200;



    // PERMISSIONS
    private final String [] permissions = {Manifest.permission.RECORD_AUDIO,
            Manifest.permission.MANAGE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE};

    void genTone(){

        for (int i = 0; i < NUMBER_OF_SAMPLE; i++) {
            double c = (F2 - F1) / DURATION;
            sample[i] = Math.sin(2 * Math.PI * (c/2 * i/ SAMPLE_RATE + F1) * i/ SAMPLE_RATE);
        }
        int idx = 0;
        for (final double dVal : sample) {
            // scale to maximum amplitude
            final short val = (short) (dVal * 32767); // max positive sample for signed 16 bit integers is 32767
            // in 16 bit wave PCM, first byte is the low order byte (pcm: pulse control modulation)
            GENERATED_SOUND[idx++] = (byte) (val & 0x00ff);
            GENERATED_SOUND[idx++] = (byte) ((val & 0xff00) >>> 8);
        }
    }

    void playSound() {
        audioTrack.write(GENERATED_SOUND, 0, GENERATED_SOUND.length);
        audioTrack.setLoopPoints(0, GENERATED_SOUND.length/2, -1);
        audioTrack.play();
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ActivityCompat.requestPermissions(this,
                permissions,
                REQUEST_RECORD_AUDIO_PERMISSION);
        ActivityCompat.requestPermissions(this,
                permissions,
                WRITE_EXTERNAL_STORAGE_PERMISSION);

        audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC,
                SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT, GENERATED_SOUND.length,
                AudioTrack.MODE_STATIC);

        Button playChirp;
        playChirp = findViewById(R.id.playChirp);
        playChirp.setOnClickListener(view -> {
            genTone();
            Thread playThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    playSound();
                }
            });
            playThread.start();
        });
    }
}