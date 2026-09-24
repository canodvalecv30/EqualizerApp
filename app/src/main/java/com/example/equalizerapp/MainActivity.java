package com.example.equalizerapp;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.media.PlaybackParams;
import android.media.audiofx.BassBoost;
import android.media.audiofx.Equalizer;
import android.media.audiofx.LoudnessEnhancer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int BG_MAIN   = 0xFF0D0D0D;
    private static final int BG_CARD   = 0xFF1A1A1A;
    private static final int ACCENT    = 0xFF1DB954;
    private static final int TEXT_MAIN = 0xFFFFFFFF;
    private static final int TEXT_SUB  = 0xFFB3B3B3;

    private static final int PICK_AUDIO       = 1001;
    private static final int REQ_PROJECTION   = 2001;
    private static final int SHIZUKU_REQ_CODE = 5001;

    private LinearLayout mRoot;
    private LinearLayout mEqContainer;
    private TextView mStatus;
    private TextView mAppLabel;
    private TextView mBassValue;
    private TextView mVolValue;
    private TextView mSpeedValue;
    private Button mPlayButton;

    private MediaPlayer mMediaPlayer;
    private Equalizer mEqualizer;
    private BassBoost mBassBoost;
    private LoudnessEnhancer mLoudness;
    private float mSpeed = 1.0f;
    private String mSelectedPackage = null;
    private String mSelectedAppName = "(ninguna)";

    private final BroadcastReceiver notifReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            try {
                String cmd = i.getStringExtra(FxService.EXTRA_CMD);
                if (cmd != null) handleCommand(cmd);
            } catch (Exception ignored) {}
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try { buildUI(); } catch (Throwable t) { fallbackUI(t); }
        try { setupNotifications(); } catch (Exception ignored) {}
        try { startServiceSafe(); } catch (Exception ignored) {}
        try { registerNotifReceiver(); } catch (Exception ignored) {}
    }

    private void fallbackUI(Throwable t) {
        TextView tv = new TextView(this);
        tv.setText("EqualizerApp\n\n" + t.getMessage());
        tv.setTextSize(18);
        tv.setPadding(50, 50, 50, 50);
        setContentView(tv);
    }

    private int dp(int v) {
        return (int) TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    private GradientDrawable round(int color, int r) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(r));
        return g;
    }

    private LinearLayout newCard() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(round(BG_CARD, 16));
        c.setPadding(dp(18), dp(16), dp(18), dp(16));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(6), 0, dp(6));
        c.setLayoutParams(lp);
        return c;
    }

    private TextView cardTitle(String t) {
        TextView tv = new TextView(this);
        tv.setText(t);
        tv.setTextColor(TEXT_MAIN);
        tv.setTextSize(15);
        tv.setPadding(0, 0, 0, dp(10));
        return tv;
    }

    private TextView smallLabel(String t) {
        TextView tv = new TextView(this);
        tv.setText(t);
        tv.setTextColor(TEXT_SUB);
        tv.setTextSize(12);
        return tv;
    }

    private Button accentButton(String t) {
        Button b = new Button(this);
        b.setText(t);
        b.setTextColor(0xFF000000);
        b.setTextSize(13);
        b.setAllCaps(false);
        b.setBackground(round(ACCENT, 10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(6), 0, dp(6));
        b.setLayoutParams(lp);
        return b;
    }

    private Button ghostButton(String t) {
        Button b = new Button(this);
        b.setText(t);
        b.setTextColor(TEXT_MAIN);
        b.setTextSize(13);
        b.setAllCaps(false);
        b.setBackground(round(0xFF2A2A2A, 10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(dp(4), dp(4), dp(4), dp(4));
        b.setLayoutParams(lp);
        return b;
    }

    private void buildUI() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG_MAIN);
        scroll.setFillViewport(true);

        mRoot = new LinearLayout(this);
        mRoot.setOrientation(LinearLayout.VERTICAL);
        mRoot.setPadding(dp(16), dp(20), dp(16), dp(40));
        scroll.addView(mRoot);
        setContentView(scroll);

        buildHeaderCard();
        buildPlayerCard();
        buildBassVolumeCard();
        buildEqCard();
        buildTargetAppCard();
        buildShizukuCard();
    }

    private void buildHeaderCard() {
        LinearLayout card = newCard();
        TextView title = new TextView(this);
        title.setText("EqualizerApp");
        title.setTextColor(TEXT_MAIN);
        title.setTextSize(26);
        title.setGravity(Gravity.CENTER);
        card.addView(title);

        TextView sub = new TextView(this);
        sub.setText("Ecualizador y potenciador de audio");
        sub.setTextColor(TEXT_SUB);
        sub.setTextSize(12);
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, dp(4), 0, dp(12));
        card.addView(sub);

        mStatus = new TextView(this);
        mStatus.setText("Listo. Elige una cancion o captura audio.");
        mStatus.setTextColor(TEXT_SUB);
        mStatus.setTextSize(12);
        mStatus.setGravity(Gravity.CENTER);
        mStatus.setPadding(dp(10), dp(10), dp(10), dp(10));
        mStatus.setBackground(round(0xFF141414, 8));
        card.addView(mStatus);

        mRoot.addView(card);
    }

    private void buildPlayerCard() {
        LinearLayout card = newCard();
        card.addView(cardTitle("Reproduccion"));

        Button pick = accentButton("Elegir cancion local");
        pick.setOnClickListener(v -> { try { selectAudio(); } catch (Exception e) { setStatus(e.getMessage()); } });
        card.addView(pick);

        mPlayButton = new Button(this);
        mPlayButton.setText("Reproducir / Pausar");
        mPlayButton.setTextColor(TEXT_MAIN);
        mPlayButton.setTextSize(13);
        mPlayButton.setAllCaps(false);
        mPlayButton.setBackground(round(0xFF2A2A2A, 10));
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        plp.setMargins(0, dp(6), 0, dp(6));
        mPlayButton.setLayoutParams(plp);
        mPlayButton.setEnabled(false);
        mPlayButton.setOnClickListener(v -> { try { togglePlayback(); } catch (Exception e) { setStatus(e.getMessage()); } });
        card.addView(mPlayButton);

        LinearLayout sh = new LinearLayout(this);
        sh.setOrientation(LinearLayout.HORIZONTAL);
        sh.setGravity(Gravity.CENTER_VERTICAL);
        sh.setPadding(0, dp(10), 0, dp(4));
        TextView sl = smallLabel("Velocidad");
        sl.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        sh.addView(sl);
        mSpeedValue = smallLabel("1.0x");
        mSpeedValue.setTextColor(ACCENT);
        sh.addView(mSpeedValue);
        card.addView(sh);

        LinearLayout speedRow = new LinearLayout(this);
        speedRow.setOrientation(LinearLayout.HORIZONTAL);
        float[] speeds = {0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f};
        for (final float sp : speeds) {
            Button sb = ghostButton(sp + "x");
            sb.setTextSize(10);
            sb.setOnClickListener(v -> { try { setSpeed(sp); } catch (Exception e) { setStatus(e.getMessage()); } });
            speedRow.addView(sb);
        }
        card.addView(speedRow);

        mRoot.addView(card);
    }

    private void buildBassVolumeCard() {
        LinearLayout card = newCard();
        card.addView(cardTitle("Bass y Volumen"));

        LinearLayout bassRow = new LinearLayout(this);
        bassRow.setOrientation(LinearLayout.HORIZONTAL);
        bassRow.setGravity(Gravity.CENTER_VERTICAL);

        Button bassMinus = ghostButton("Bass -");
        bassMinus.setOnClickListener(v -> {
            FxService.bassStrength = (short) Math.max(0, FxService.bassStrength - 100);
            applyBass();
        });
        bassRow.addView(bassMinus);

        mBassValue = new TextView(this);
        mBassValue.setText(FxService.bassStrength + " / 1000");
        mBassValue.setTextColor(ACCENT);
        mBassValue.setTextSize(13);
        mBassValue.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.4f);
        mBassValue.setLayoutParams(blp);
        bassRow.addView(mBassValue);

        Button bassPlus = ghostButton("Bass +");
        bassPlus.setOnClickListener(v -> {
            FxService.bassStrength = (short) Math.min(1000, FxService.bassStrength + 100);
            applyBass();
        });
        bassRow.addView(bassPlus);
        card.addView(bassRow);

        LinearLayout volRow = new LinearLayout(this);
        volRow.setOrientation(LinearLayout.HORIZONTAL);
        volRow.setGravity(Gravity.CENTER_VERTICAL);

        Button volMinus = ghostButton("Vol -");
        volMinus.setOnClickListener(v -> {
            FxService.loudnessGain = Math.max(0, FxService.loudnessGain - 200);
            applyLoudness();
        });
        volRow.addView(volMinus);

        mVolValue = new TextView(this);
        mVolValue.setText("+" + (FxService.loudnessGain / 100) + " dB");
        mVolValue.setTextColor(ACCENT);
        mVolValue.setTextSize(13);
        mVolValue.setGravity(Gravity.CENTER);
        mVolValue.setLayoutParams(new LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.4f));
        volRow.addView(mVolValue);

        Button volPlus = ghostButton("Vol +");
        volPlus.setOnClickListener(v -> {
            FxService.loudnessGain = Math.min(2000, FxService.loudnessGain + 200);
            applyLoudness();
        });
        volRow.addView(volPlus);
        card.addView(volRow);

        mRoot.addView(card);
    }

    private void buildEqCard() {
        LinearLayout card = newCard();
        card.addView(cardTitle("Ecualizador"));

        mEqContainer = new LinearLayout(this);
        mEqContainer.setOrientation(LinearLayout.VERTICAL);

        TextView hint = smallLabel("Carga una cancion para ver las bandas.");
        hint.setPadding(0, 0, 0, dp(6));
        mEqContainer.addView(hint);

        Button showBands = accentButton("Mostrar bandas ahora");
        showBands.setOnClickListener(v -> { try { showBandsGlobal(); } catch (Exception e) { setStatus(e.getMessage()); } });
        card.addView(showBands);

        card.addView(mEqContainer);
        mRoot.addView(card);
    }

    private void showBandsGlobal() {
        try {
            releaseEffects();
            // Crear un Equalizer con session 0 para leer las bandas del dispositivo
            mEqualizer = new Equalizer(0, 0);
            mEqualizer.setEnabled(false); // solo para leer, no aplicar
            buildEqSliders();
            setStatus("Bandas mostradas. Carga una cancion para activarlas.");
        } catch (Exception e) {
            setStatus("Error: " + e.getMessage());
        }
    }

    private void buildTargetAppCard() {
        LinearLayout card = newCard();
        card.addView(cardTitle("App objetivo"));

        Button picker = accentButton("Elegir app");
        picker.setOnClickListener(v -> { try { showAppPicker(); } catch (Exception e) { setStatus(e.getMessage()); } });
        card.addView(picker);

        mAppLabel = new TextView(this);
        mAppLabel.setText("App seleccionada: (ninguna)");
        mAppLabel.setTextColor(TEXT_SUB);
        mAppLabel.setTextSize(12);
        mAppLabel.setGravity(Gravity.CENTER);
        mAppLabel.setPadding(dp(10), dp(10), dp(10), dp(10));
        mAppLabel.setBackground(round(0xFF141414, 8));
        card.addView(mAppLabel);

        Button capture = accentButton("Capturar audio del sistema");
        capture.setOnClickListener(v -> { try { requestCapture(); } catch (Exception e) { setStatus(e.getMessage()); } });
        card.addView(capture);

        LinearLayout pcmRow = new LinearLayout(this);
        pcmRow.setOrientation(LinearLayout.HORIZONTAL);
        pcmRow.setGravity(Gravity.CENTER_VERTICAL);
        pcmRow.setPadding(0, dp(10), 0, 0);

        Button pcmMinus = ghostButton("Gain -");
        pcmMinus.setOnClickListener(v -> {
            FxService.pcmGain = Math.max(1.0f, FxService.pcmGain - 0.5f);
            setStatus("PCM Gain: " + FxService.pcmGain + "x (+" +
                String.format("%.1f", 20 * Math.log10(FxService.pcmGain)) + " dB)");
        });
        pcmRow.addView(pcmMinus);

        TextView pcmLabel = new TextView(this);
        pcmLabel.setText("PCM Gain");
        pcmLabel.setTextColor(TEXT_MAIN);
        pcmLabel.setTextSize(12);
        pcmLabel.setGravity(Gravity.CENTER);
        pcmLabel.setLayoutParams(new LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.4f));
        pcmRow.addView(pcmLabel);

        Button pcmPlus = ghostButton("Gain +");
        pcmPlus.setOnClickListener(v -> {
            FxService.pcmGain = Math.min(10.0f, FxService.pcmGain + 0.5f);
            setStatus("PCM Gain: " + FxService.pcmGain + "x (+" +
                String.format("%.1f", 20 * Math.log10(FxService.pcmGain)) + " dB)");
        });
        pcmRow.addView(pcmPlus);

        card.addView(pcmRow);

        mRoot.addView(card);
    }

    private void buildShizukuCard() {
        LinearLayout card = newCard();
        card.addView(cardTitle("Permisos avanzados (Shizuku)"));

        TextView hint = smallLabel("Activa Shizuku para permisos elevados.");
        hint.setPadding(0, 0, 0, dp(8));
        card.addView(hint);

        Button b = accentButton("Activar Shizuku");
        b.setOnClickListener(v -> { try { checkShizukuAndRequest(); } catch (Exception e) { setStatus("Shizuku: " + e.getMessage()); } });
        card.addView(b);

        mRoot.addView(card);
    }

    private void setupNotifications() {
        if (Build.VERSION.SDK_INT >= 33) {
            try {
                if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{
                        android.Manifest.permission.POST_NOTIFICATIONS}, 1);
                }
            } catch (Exception ignored) {}
        }
    }

    private void startServiceSafe() {
        try {
            Intent svc = new Intent(this, FxService.class);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(svc);
            else startService(svc);
        } catch (Exception ignored) {}
    }

    private void registerNotifReceiver() {
        try {
            IntentFilter f = new IntentFilter(FxService.ACTION_NOTIF);
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(notifReceiver, f, Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(notifReceiver, f);
            }
        } catch (Exception ignored) {}
    }

    private void requestCapture() {
        if (Build.VERSION.SDK_INT < 29) { setStatus("Requiere Android 10+"); return; }
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO}, 3001);
            return;
        }
        try {
            android.media.projection.MediaProjectionManager mgr =
                (android.media.projection.MediaProjectionManager)
                    getSystemService(MEDIA_PROJECTION_SERVICE);
            if (mgr == null) return;
            startActivityForResult(mgr.createScreenCaptureIntent(), REQ_PROJECTION);
        } catch (Exception e) { setStatus("Error: " + e.getMessage()); }
    }

    private void showAppPicker() {
        try {
            Intent main = new Intent(Intent.ACTION_MAIN, null);
            main.addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> apps = getPackageManager().queryIntentActivities(main, 0);

            List<ResolveInfo> sorted = new ArrayList<>(apps);
            Collections.sort(sorted, new Comparator<ResolveInfo>() {
                @Override public int compare(ResolveInfo a, ResolveInfo b) {
                    return a.loadLabel(getPackageManager()).toString()
                        .compareToIgnoreCase(b.loadLabel(getPackageManager()).toString());
                }
            });

            final List<ResolveInfo> shown = new ArrayList<>();
            final List<String> labels = new ArrayList<>();
            for (ResolveInfo ri : sorted) {
                String pkg = ri.activityInfo.packageName;
                if (pkg.equals(getPackageName())) continue;
                shown.add(ri);
                labels.add(ri.loadLabel(getPackageManager()).toString() + "\n" + pkg);
            }

            new AlertDialog.Builder(this)
                .setTitle("Elige la app")
                .setItems(labels.toArray(new CharSequence[0]), (d, w) -> {
                    ResolveInfo ri = shown.get(w);
                    mSelectedPackage = ri.activityInfo.packageName;
                    mSelectedAppName = ri.loadLabel(getPackageManager()).toString();
                    mAppLabel.setText("App: " + mSelectedAppName + "\n" + mSelectedPackage);
                    setStatus("Captura el audio para " + mSelectedAppName);
                })
                .setNegativeButton("Cancelar", null)
                .show();
        } catch (Exception e) { setStatus("Error: " + e.getMessage()); }
    }

    private void checkShizukuAndRequest() {
        try {
            if (!rikka.shizuku.Shizuku.pingBinder()) { setStatus("Shizuku no esta corriendo"); return; }
            if (rikka.shizuku.Shizuku.isPreV11()) { setStatus("Shizuku pre-v11 no soportado"); return; }
            if (rikka.shizuku.Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                setStatus("Shizuku activo. Permisos de audio listos.");
            } else if (rikka.shizuku.Shizuku.shouldShowRequestPermissionRationale()) {
                setStatus("Permiso Shizuku pendiente");
            } else {
                rikka.shizuku.Shizuku.requestPermission(SHIZUKU_REQ_CODE);
            }
        } catch (Exception e) { setStatus("Shizuku: " + e.getMessage()); }
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] p, int[] r) {
        super.onRequestPermissionsResult(req, p, r);
        if (req == SHIZUKU_REQ_CODE) {
            if (r.length > 0 && r[0] == PackageManager.PERMISSION_GRANTED) {
                setStatus("Shizuku concedido");
            } else setStatus("Permiso Shizuku denegado");
        }
    }

    private void selectAudio() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("audio/*");
        startActivityForResult(i, PICK_AUDIO);
    }

    @Override
    protected void onActivityResult(int rc, int res, Intent data) {
        super.onActivityResult(rc, res, data);

        if (rc == REQ_PROJECTION) {
            if (res == RESULT_OK && data != null) {
                try {
                    Intent svc = new Intent(this, FxService.class);
                    svc.setAction(FxService.ACTION_START_CAPTURE);
                    svc.putExtra(FxService.EXTRA_PROJECTION_DATA, data);
                    if (mSelectedPackage != null) svc.putExtra("target_package", mSelectedPackage);
                    if (Build.VERSION.SDK_INT >= 26) startForegroundService(svc);
                    else startService(svc);
                    setStatus("Capturando audio del sistema...");
                } catch (Exception e) { setStatus("Error captura: " + e.getMessage()); }
            }
            return;
        }

        if (rc != PICK_AUDIO || res != RESULT_OK || data == null || data.getData() == null) return;
        try {
            Uri uri = data.getData();
            releaseAudio();
            mMediaPlayer = new MediaPlayer();
            mMediaPlayer.setDataSource(this, uri);
            mMediaPlayer.setOnPreparedListener(mp -> {
                try {
                    mp.setLooping(true);
                    mPlayButton.setEnabled(true);
                    setStatus("Audio cargado");
                    attachEqToCurrentSession();
                } catch (Exception e) { setStatus(e.getMessage()); }
            });
            mMediaPlayer.prepareAsync();
            setStatus("Cargando audio...");
        } catch (Exception e) { setStatus("Error: " + e.getMessage()); }
    }

    private void togglePlayback() {
        if (mMediaPlayer == null) { setStatus("Elige una cancion primero"); return; }
        try {
            if (mMediaPlayer.isPlaying()) { mMediaPlayer.pause(); setStatus("Pausado"); }
            else { mMediaPlayer.start(); setStatus("Reproduciendo"); }
        } catch (Exception e) { setStatus(e.getMessage()); }
    }

    private void attachEqToCurrentSession() {
        if (mMediaPlayer == null) return;
        try {
            releaseEffects();
            int sid = mMediaPlayer.getAudioSessionId();
            if (sid == 0) return;

            mEqualizer = new Equalizer(0, sid);
            mEqualizer.setEnabled(true);

            try {
                mBassBoost = new BassBoost(0, sid);
                if (mBassBoost.getStrengthSupported()) {
                    mBassBoost.setEnabled(true);
                    mBassBoost.setStrength(FxService.bassStrength);
                }
            } catch (Exception ignored) {}

            try {
                mLoudness = new LoudnessEnhancer(sid);
                mLoudness.setTargetGain(FxService.loudnessGain);
                mLoudness.setEnabled(true);
            } catch (Exception ignored) {}

            buildEqSliders();
            setStatus("EQ activado");
        } catch (Exception e) { setStatus("Error EQ: " + e.getMessage()); }
    }

    private void buildEqSliders() {
        try {
            mEqContainer.removeAllViews();
            if (mEqualizer == null) return;

            short bands = mEqualizer.getNumberOfBands();
            short[] range = mEqualizer.getBandLevelRange();
            final short minL = range[0];
            final short maxL = range[1];

            TextView header = new TextView(this);
            header.setText(bands + " bandas disponibles");
            header.setTextColor(TEXT_SUB);
            header.setTextSize(11);
            header.setPadding(0, 0, 0, dp(10));
            mEqContainer.addView(header);

            for (short i = 0; i < bands; i++) {
                final short band = i;

                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.VERTICAL);
                row.setPadding(0, dp(6), 0, dp(6));

                LinearLayout labelRow = new LinearLayout(this);
                labelRow.setOrientation(LinearLayout.HORIZONTAL);

                int hz = mEqualizer.getCenterFreq(band) / 1000;
                String freqTxt = hz >= 1000
                    ? String.format("%.1f kHz", hz / 1000.0)
                    : hz + " Hz";

                TextView freqTv = new TextView(this);
                freqTv.setText(freqTxt);
                freqTv.setTextColor(TEXT_MAIN);
                freqTv.setTextSize(12);
                freqTv.setLayoutParams(new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                labelRow.addView(freqTv);

                final TextView dbTv = new TextView(this);
                short cur = mEqualizer.getBandLevel(band);
                dbTv.setText(String.format("%.1f dB", cur / 100.0));
                dbTv.setTextColor(ACCENT);
                dbTv.setTextSize(12);
                labelRow.addView(dbTv);

                row.addView(labelRow);

                SeekBar bar = new SeekBar(this);
                bar.setMax(maxL - minL);
                bar.setProgress(cur - minL);
                bar.setPadding(0, dp(4), 0, 0);

                final short fMin = minL;
                bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                    @Override public void onProgressChanged(SeekBar sb, int prog, boolean fromUser) {
                        try {
                            short level = (short) (prog + fMin);
                            if (mEqualizer != null) mEqualizer.setBandLevel(band, level);
                            dbTv.setText(String.format("%.1f dB", level / 100.0));
                        } catch (Exception ignored) {}
                    }
                    @Override public void onStartTrackingTouch(SeekBar sb) {}
                    @Override public void onStopTrackingTouch(SeekBar sb) {}
                });
                row.addView(bar);

                mEqContainer.addView(row);
            }
        } catch (Exception e) { setStatus("Error sliders: " + e.getMessage()); }
    }

    private void setSpeed(float sp) {
        if (mMediaPlayer == null) { setStatus("Sin audio"); return; }
        try {
            PlaybackParams pp = new PlaybackParams();
            pp.setSpeed(sp);
            mMediaPlayer.setPlaybackParams(pp);
            mSpeed = sp;
            if (mSpeedValue != null) mSpeedValue.setText(sp + "x");
            setStatus("Velocidad: " + sp + "x");
        } catch (Exception e) { setStatus("Error velocidad: " + e.getMessage()); }
    }

    private void applyBass() {
        try { if (mBassBoost != null && mBassBoost.getStrengthSupported()) mBassBoost.setStrength(FxService.bassStrength); } catch (Exception ignored) {}
        if (mBassValue != null) mBassValue.setText(FxService.bassStrength + " / 1000");
        setStatus("Bass: " + FxService.bassStrength);
    }

    private void applyLoudness() {
        try { if (mLoudness != null) mLoudness.setTargetGain(FxService.loudnessGain); } catch (Exception ignored) {}
        if (mVolValue != null) mVolValue.setText("+" + (FxService.loudnessGain / 100) + " dB");
        setStatus("Volumen: +" + (FxService.loudnessGain / 100) + " dB");
    }

    private void handleCommand(String cmd) {
        if (mMediaPlayer == null) return;
        try {
            switch (cmd) {
                case FxService.CMD_PLAY_PAUSE: togglePlayback(); break;
                case FxService.CMD_PREV:
                    mMediaPlayer.seekTo(Math.max(0, mMediaPlayer.getCurrentPosition() - 10000));
                    break;
                case FxService.CMD_NEXT:
                    mMediaPlayer.seekTo(Math.min(mMediaPlayer.getDuration(),
                        mMediaPlayer.getCurrentPosition() + 10000));
                    break;
                case FxService.CMD_SPEED_UP: setSpeed(Math.min(2.0f, mSpeed + 0.25f)); break;
                case FxService.CMD_SPEED_DOWN: setSpeed(Math.max(0.5f, mSpeed - 0.25f)); break;
            }
        } catch (Exception ignored) {}
    }

    private void setStatus(String s) {
        if (mStatus != null && s != null) mStatus.setText(s);
    }

    @Override
    protected void onDestroy() {
        try { unregisterReceiver(notifReceiver); } catch (Exception ignored) {}
        releaseAudio();
        super.onDestroy();
    }

    private void releaseEffects() {
        try { if (mBassBoost != null) { mBassBoost.release(); mBassBoost = null; } } catch (Exception ignored) {}
        try { if (mLoudness != null) { mLoudness.release(); mLoudness = null; } } catch (Exception ignored) {}
        try { if (mEqualizer != null) { mEqualizer.release(); mEqualizer = null; } } catch (Exception ignored) {}
    }

    private void releaseAudio() {
        releaseEffects();
        try { if (mMediaPlayer != null) { mMediaPlayer.release(); mMediaPlayer = null; } } catch (Exception ignored) {}
    }
}
