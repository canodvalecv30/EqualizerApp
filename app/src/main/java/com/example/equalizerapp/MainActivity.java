package com.example.equalizerapp;

import android.content.Intent;
import android.media.MediaPlayer;
import android.media.audiofx.BassBoost;
import android.media.audiofx.Equalizer;
import android.media.audiofx.LoudnessEnhancer;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private static final int PICK_AUDIO = 1001;

    private MediaPlayer mMediaPlayer;
    private Equalizer mEqualizer;
    private BassBoost mBassBoost;
    private LoudnessEnhancer mLoudness;

    private LinearLayout mLinearLayout;
    private TextView mStatusTextView;
    private Button mPlayButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        buildInterface();
    }

    private void buildInterface() {
        mLinearLayout = new LinearLayout(this);
        mLinearLayout.setOrientation(LinearLayout.VERTICAL);
        mLinearLayout.setPadding(32, 32, 32, 32);

        setContentView(mLinearLayout);

        TextView title = new TextView(this);
        title.setText("🎚 EqualizerApp");
        title.setTextSize(26);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 20, 0, 20);
        mLinearLayout.addView(title);

        mStatusTextView = new TextView(this);
        mStatusTextView.setText("Selecciona una canción para comenzar");
        mStatusTextView.setTextSize(16);
        mStatusTextView.setGravity(Gravity.CENTER);
        mStatusTextView.setPadding(0, 10, 0, 20);
        mLinearLayout.addView(mStatusTextView);

        Button selectButton = new Button(this);
        selectButton.setText("🎵 Seleccionar audio");
        selectButton.setOnClickListener(v -> selectAudio());
        mLinearLayout.addView(selectButton);

        mPlayButton = new Button(this);
        mPlayButton.setText("▶ Reproducir");
        mPlayButton.setEnabled(false);
        mPlayButton.setOnClickListener(v -> togglePlayback());
        mLinearLayout.addView(mPlayButton);
    }

    private void selectAudio() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("audio/*");

        startActivityForResult(intent, PICK_AUDIO);
    }

    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data) {

        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode != PICK_AUDIO ||
                resultCode != RESULT_OK ||
                data == null ||
                data.getData() == null) {
            return;
        }

        Uri audioUri = data.getData();

        try {
            releaseAudio();

            mMediaPlayer = new MediaPlayer();
            mMediaPlayer.setDataSource(this, audioUri);

            mMediaPlayer.setOnPreparedListener(mp -> {
                mp.setLooping(true);

                mStatusTextView.setText(
                        "Audio cargado · " +
                        getAudioName(audioUri)
                );

                mPlayButton.setEnabled(true);
                mPlayButton.setText("▶ Reproducir");

                setupEqualizer();
            });

            mMediaPlayer.setOnErrorListener((mp, what, extra) -> {
                mStatusTextView.setText(
                        "Error al reproducir el audio"
                );
                return true;
            });

            mMediaPlayer.prepareAsync();

            mStatusTextView.setText("Cargando audio...");

        } catch (Exception e) {
            mStatusTextView.setText(
                    "Error: " + e.getMessage()
            );
        }
    }

    private String getAudioName(Uri uri) {
        String name = uri.getLastPathSegment();

        if (name == null || name.isEmpty()) {
            return "audio seleccionado";
        }

        return name;
    }

    private void togglePlayback() {
        if (mMediaPlayer == null) {
            return;
        }

        if (mMediaPlayer.isPlaying()) {
            mMediaPlayer.pause();
            mPlayButton.setText("▶ Reproducir");
            mStatusTextView.setText("Audio pausado");
        } else {
            mMediaPlayer.start();
            mPlayButton.setText("⏸ Pausar");
            mStatusTextView.setText("Reproduciendo");
        }
    }

    private void setupEqualizer() {

        if (mMediaPlayer == null) {
            return;
        }

        try {
            if (mEqualizer != null) {
                mEqualizer.release();
                mEqualizer = null;
            }

            int sessionId = mMediaPlayer.getAudioSessionId();

            if (sessionId == 0) {
                mStatusTextView.setText(
                        "No se pudo obtener AudioSession"
                );
                return;
            }

            mEqualizer = new Equalizer(0, sessionId);
            mEqualizer.setEnabled(true);

            try {
                mBassBoost = new BassBoost(0, sessionId);
                if (mBassBoost.getStrengthSupported()) {
                    mBassBoost.setEnabled(true);
                    mBassBoost.setStrength((short) 500);
                }
            } catch (RuntimeException ignored) {}

            try {
                mLoudness = new LoudnessEnhancer(sessionId);
                mLoudness.setTargetGain(300);
                mLoudness.setEnabled(true);
            } catch (RuntimeException ignored) {}

            short numBands = mEqualizer.getNumberOfBands();
            short[] levelRange =
                    mEqualizer.getBandLevelRange();

            final short minLevel = levelRange[0];
            final short maxLevel = levelRange[1];

            TextView title = new TextView(this);
            title.setText(
                    "Ecualizador · " + numBands + " bandas"
            );
            title.setTextSize(20);
            title.setGravity(Gravity.CENTER);
            title.setPadding(0, 30, 0, 20);

            mLinearLayout.addView(title);

            for (short i = 0; i < numBands; i++) {

                final short band = i;

                TextView freqText = new TextView(this);

                int frequency =
                        mEqualizer.getCenterFreq(band) / 1000;

                if (frequency >= 1000) {
                    freqText.setText(
                            String.format(
                                    "%.1f kHz",
                                    frequency / 1000.0
                            )
                    );
                } else {
                    freqText.setText(
                            frequency + " Hz"
                    );
                }

                freqText.setGravity(Gravity.CENTER);
                freqText.setTextSize(14);
                freqText.setPadding(0, 10, 0, 4);

                mLinearLayout.addView(freqText);

                LinearLayout row =
                        new LinearLayout(this);

                row.setOrientation(
                        LinearLayout.HORIZONTAL
                );

                row.setGravity(
                        Gravity.CENTER_VERTICAL
                );

                TextView minLabel =
                        new TextView(this);

                minLabel.setText(
                        formatDb(minLevel)
                );

                TextView maxLabel =
                        new TextView(this);

                maxLabel.setText(
                        formatDb(maxLevel)
                );

                SeekBar bar = new SeekBar(this);

                LinearLayout.LayoutParams params =
                        new LinearLayout.LayoutParams(
                                0,
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                                1f
                        );

                bar.setLayoutParams(params);

                bar.setMax(
                        maxLevel - minLevel
                );

                bar.setProgress(
                        mEqualizer.getBandLevel(band)
                                - minLevel
                );

                bar.setOnSeekBarChangeListener(
                        new SeekBar.OnSeekBarChangeListener() {

                            @Override
                            public void onProgressChanged(
                                    SeekBar seekBar,
                                    int progress,
                                    boolean fromUser) {

                                if (mEqualizer != null) {
                                    mEqualizer.setBandLevel(
                                            band,
                                            (short)
                                                    (progress + minLevel)
                                    );
                                }
                            }

                            @Override
                            public void onStartTrackingTouch(
                                    SeekBar seekBar) {
                            }

                            @Override
                            public void onStopTrackingTouch(
                                    SeekBar seekBar) {
                            }
                        }
                );

                row.addView(minLabel);
                row.addView(bar);
                row.addView(maxLabel);

                mLinearLayout.addView(row);
            }

        } catch (RuntimeException e) {

            mStatusTextView.setText(
                    "Error del ecualizador: "
                            + e.getMessage()
            );
        }
    }

    private String formatDb(short value) {
        return String.format(
                "%.1f dB",
                value / 100.0
        );
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (mMediaPlayer != null &&
                mMediaPlayer.isPlaying()) {

            mMediaPlayer.pause();

            if (mPlayButton != null) {
                mPlayButton.setText("▶ Reproducir");
            }
        }
    }

    @Override
    protected void onDestroy() {
        releaseAudio();
        super.onDestroy();
    }

    private void releaseAudio() {

        if (mBassBoost != null) {
            mBassBoost.release();
            mBassBoost = null;
        }

        if (mLoudness != null) {
            mLoudness.release();
            mLoudness = null;
        }

        if (mEqualizer != null) {
            mEqualizer.release();
            mEqualizer = null;
        }

        if (mMediaPlayer != null) {
            mMediaPlayer.release();
            mMediaPlayer = null;
        }
    }
}
