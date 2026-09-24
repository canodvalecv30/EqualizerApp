package com.example.equalizerapp;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.audiofx.AudioEffect;
import android.media.audiofx.BassBoost;
import android.media.audiofx.Equalizer;
import android.media.audiofx.LoudnessEnhancer;
import android.os.Build;
import android.os.IBinder;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioPlaybackCaptureConfiguration;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.projection.MediaProjection;

import android.os.PowerManager;

import androidx.core.app.NotificationCompat;

import java.util.HashMap;
import java.util.Map;

public class FxService extends Service {

    public static final String CHANNEL_ID = "fx_channel";
    public static final int NOTIF_ID = 42;

    public static final String ACTION_NOTIF = "com.example.equalizerapp.NOTIF";
    public static final String EXTRA_CMD = "cmd";
    public static final String CMD_PLAY_PAUSE = "play_pause";
    public static final String CMD_PREV = "prev";
    public static final String CMD_NEXT = "next";
    public static final String CMD_SPEED_DOWN = "speed_down";
    public static final String CMD_SPEED_UP = "speed_up";

    public static short bassStrength = 1000;
    public static int loudnessGain = 2000;
    public static boolean enabled = true;
    public static float pcmGain = 3.0f;  // 3.0x = +9.5 dB extra

    private final Map<Integer, SessionFx> sessions = new HashMap<>();
    private PowerManager.WakeLock wakeLock;

    public static final String ACTION_START_CAPTURE = "com.example.equalizerapp.START_CAPTURE";
    public static final String EXTRA_PROJECTION_CODE = "projection_code";
    public static final String EXTRA_PROJECTION_DATA = "projection_data";

    private MediaProjection mProjection;
    private AudioRecord mRecorder;
    private AudioTrack mTrack;
    private Thread mCaptureThread;
    private volatile boolean mCapturing = false;
    private int mCaptureSessionId = -1;
    private String mTargetPackage = null;

    private static class SessionFx {
        Equalizer eq;
        BassBoost bb;
        LoudnessEnhancer le;
    }

    private final BroadcastReceiver sessionReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) return;
            String action = intent.getAction();
            int sessionId = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, 0);
            if (sessionId == 0 || action == null) return;
            if (AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION.equals(action)) {
                attach(sessionId);
            } else if (AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION.equals(action)) {
                detach(sessionId);
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
        // Arrancar SOLO como mediaPlayback. mediaProjection se activa despues,
        // solo cuando el usuario acepta la captura. (Android 14+ exige esto)
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTIF_ID, buildNotification(),
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
            } else {
                startForeground(NOTIF_ID, buildNotification());
            }
        } catch (Exception e) {
            // Si falla, seguir sin foreground (mejor que crashear)
        }

        // Mantener CPU despierta (parcial) para que el sistema no mate el servicio
        try {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (pm != null) {
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                        "EqualizerApp::FxWakeLock");
                wakeLock.setReferenceCounted(false);
                wakeLock.acquire();
            }
        } catch (Exception ignored) {}

        IntentFilter f = new IntentFilter();
        f.addAction(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION);
        f.addAction(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(sessionReceiver, f, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(sessionReceiver, f);
        }
    }

    private PendingIntent pi(String cmd, int code) {
        Intent i = new Intent(ACTION_NOTIF);
        i.setPackage(getPackageName());
        i.putExtra(EXTRA_CMD, cmd);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getBroadcast(this, code, i, flags);
    }

    private Notification buildNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("EqualizerApp activo")
                .setContentText("Controles de reproduccion")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .addAction(android.R.drawable.ic_media_previous, "Atras", pi(CMD_PREV, 1))
                .addAction(android.R.drawable.ic_media_play, "Play", pi(CMD_PLAY_PAUSE, 2))
                .addAction(android.R.drawable.ic_media_next, "Sig", pi(CMD_NEXT, 3))
                .addAction(android.R.drawable.ic_media_rew, "Lento", pi(CMD_SPEED_DOWN, 4))
                .addAction(android.R.drawable.ic_media_ff, "Rapido", pi(CMD_SPEED_UP, 5))
                .build();
    }


    public void startCapture(Intent resultData) {
        stopCapture();
        try {
            android.media.projection.MediaProjectionManager mgr =
                (android.media.projection.MediaProjectionManager)
                    getSystemService(MEDIA_PROJECTION_SERVICE);
            if (mgr == null) return;

            mProjection = mgr.getMediaProjection(
                android.app.Activity.RESULT_OK, resultData);
            if (mProjection == null) return;

            // Android 14+ EXIGE registrar un Callback antes de usar la proyeccion
            try {
                mProjection.registerCallback(new MediaProjection.Callback() {
                    @Override
                    public void onStop() {
                        stopCapture();
                    }
                }, new android.os.Handler(android.os.Looper.getMainLooper()));
            } catch (Exception ignored) {}

            AudioPlaybackCaptureConfiguration.Builder cfgBuilder =
                new AudioPlaybackCaptureConfiguration.Builder(mProjection)
                    .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                    .addMatchingUsage(AudioAttributes.USAGE_GAME)
                    .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                    .addMatchingUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION);

            if (mTargetPackage != null && !mTargetPackage.isEmpty()) {
                try {
                    int uid = getPackageManager().getApplicationInfo(
                            mTargetPackage, 0).uid;
                    cfgBuilder.addMatchingUid(uid);
                } catch (Exception ignored) {}
            }

            AudioPlaybackCaptureConfiguration cfg = cfgBuilder.build();

            int sampleRate = 44100;
            AudioFormat fmt = new AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(sampleRate)
                .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
                .build();

            int minBuf = AudioRecord.getMinBufferSize(sampleRate,
                    AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT);
            int bufSize = Math.max(minBuf, 8192);

            mRecorder = new AudioRecord.Builder()
                .setAudioFormat(fmt)
                .setBufferSizeInBytes(bufSize * 2)
                .setAudioPlaybackCaptureConfig(cfg)
                .build();

            mTrack = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build())
                .setAudioFormat(new AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build())
                .setBufferSizeInBytes(bufSize * 2)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build();

            mCaptureSessionId = mTrack.getAudioSessionId();
            applyEffectsToSession(mCaptureSessionId);

            mRecorder.startRecording();
            mTrack.play();
            mCapturing = true;

            final int finalBufSize = bufSize;
            mCaptureThread = new Thread(() -> {
                byte[] buf = new byte[finalBufSize];
                while (mCapturing) {
                    int n = mRecorder.read(buf, 0, buf.length);
                    if (n > 0 && mTrack != null) {
                        // Amplificacion PCM: multiplica cada muestra por pcmGain
                        float gain = pcmGain;
                        for (int i = 0; i + 1 < n; i += 2) {
                            short sample = (short)((buf[i] & 0xFF) | (buf[i+1] << 8));
                            int amplified = (int)(sample * gain);
                            if (amplified > Short.MAX_VALUE) amplified = Short.MAX_VALUE;
                            if (amplified < Short.MIN_VALUE) amplified = Short.MIN_VALUE;
                            buf[i]   = (byte)(amplified & 0xFF);
                            buf[i+1] = (byte)((amplified >> 8) & 0xFF);
                        }
                        mTrack.write(buf, 0, n);
                    }
                }
            });
            mCaptureThread.start();

        } catch (Exception e) {
            stopCapture();
        }
    }

    private void applyEffectsToSession(int sessionId) {
        try {
            Equalizer eq = new Equalizer(0, sessionId);
            eq.setEnabled(true);
            BassBoost bb = new BassBoost(0, sessionId);
            if (bb.getStrengthSupported()) {
                bb.setEnabled(true);
                bb.setStrength(bassStrength);
            }
            LoudnessEnhancer le = new LoudnessEnhancer(sessionId);
            le.setTargetGain(loudnessGain);
            le.setEnabled(true);
            SessionFx fx = new SessionFx();
            fx.eq = eq; fx.bb = bb; fx.le = le;
            sessions.put(sessionId, fx);
        } catch (Exception ignored) {}
    }

    public void stopCapture() {
        mCapturing = false;
        try { if (mCaptureThread != null) mCaptureThread.join(500); } catch (Exception ignored) {}
        try { if (mRecorder != null) { mRecorder.stop(); mRecorder.release(); } } catch (Exception ignored) {}
        try { if (mTrack != null) { mTrack.stop(); mTrack.release(); } } catch (Exception ignored) {}
        try { if (mProjection != null) mProjection.stop(); } catch (Exception ignored) {}
        mRecorder = null; mTrack = null; mProjection = null; mCaptureThread = null;
        if (mCaptureSessionId != -1) {
            SessionFx fx = sessions.remove(mCaptureSessionId);
            if (fx != null) {
                try { fx.eq.release(); } catch (Exception ignored) {}
                try { fx.bb.release(); } catch (Exception ignored) {}
                try { fx.le.release(); } catch (Exception ignored) {}
            }
            mCaptureSessionId = -1;
        }
    }

    private void attach(int sessionId) {
        if (sessions.containsKey(sessionId)) return;
        try {
            SessionFx fx = new SessionFx();
            fx.eq = new Equalizer(0, sessionId);
            fx.eq.setEnabled(enabled);
            fx.bb = new BassBoost(0, sessionId);
            if (fx.bb.getStrengthSupported()) {
                fx.bb.setEnabled(enabled);
                fx.bb.setStrength(bassStrength);
            }
            fx.le = new LoudnessEnhancer(sessionId);
            fx.le.setTargetGain(loudnessGain);
            fx.le.setEnabled(enabled);
            sessions.put(sessionId, fx);
        } catch (RuntimeException ignored) {}
    }

    private void detach(int sessionId) {
        SessionFx fx = sessions.remove(sessionId);
        if (fx == null) return;
        try { fx.eq.release(); } catch (Exception ignored) {}
        try { fx.bb.release(); } catch (Exception ignored) {}
        try { fx.le.release(); } catch (Exception ignored) {}
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, "Efectos de audio",
                    NotificationManager.IMPORTANCE_LOW);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_START_CAPTURE.equals(intent.getAction())) {
            Intent data = intent.getParcelableExtra(EXTRA_PROJECTION_DATA);
            String target = intent.getStringExtra("target_package");
            mTargetPackage = target;
            if (data != null) {
                // 1. Promover el Foreground Service a tipo mediaProjection ANTES de usar MediaProjection
                if (Build.VERSION.SDK_INT >= 29) {
                    try {
                        startForeground(NOTIF_ID, buildNotification(),
                            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
                    } catch (Exception e) {
                        // Si falla, seguimos — puede funcionar igual
                    }
                }
                startCapture(data);
            }
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        try { unregisterReceiver(sessionReceiver); } catch (Exception ignored) {}
        for (SessionFx fx : sessions.values()) {
            try { fx.eq.release(); } catch (Exception ignored) {}
            try { fx.bb.release(); } catch (Exception ignored) {}
            try { fx.le.release(); } catch (Exception ignored) {}
        }
        stopCapture();
        sessions.clear();
        try { if (wakeLock != null && wakeLock.isHeld()) wakeLock.release(); } catch (Exception ignored) {}
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
