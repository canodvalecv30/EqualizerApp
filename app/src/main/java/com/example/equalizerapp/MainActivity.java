package com.example.equalizerapp;

import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.audiofx.BassBoost;
import android.media.audiofx.LoudnessEnhancer;
import android.os.Bundle;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private MediaPlayer mediaPlayer;
    private BassBoost bassBoost;
    private LoudnessEnhancer loudness;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button btnPlay = findViewById(R.id.btnPlay);
        btnPlay.setOnClickListener(v -> playWithBass());
    }

    private void playWithBass() {
        try {
            if (mediaPlayer != null) {
                mediaPlayer.release();
            }

            mediaPlayer = MediaPlayer.create(this, R.raw.test);
            if (mediaPlayer == null) return;

            int sessionId = mediaPlayer.getAudioSessionId();

            bassBoost = new BassBoost(0, sessionId);
            if (bassBoost.getStrengthSupported()) {
                bassBoost.setEnabled(true);
                bassBoost.setStrength((short) 800);
            }

            loudness = new LoudnessEnhancer(sessionId);
            loudness.setTargetGain(600);
            loudness.setEnabled(true);

            mediaPlayer.start();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
        if (bassBoost != null) {
            bassBoost.release();
            bassBoost = null;
        }
        if (loudness != null) {
            loudness.release();
            loudness = null;
        }
    }
}
