package dev.sambhu.findmyfamily.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import dev.sambhu.findmyfamily.R;

public class RingtoneService extends Service {

    private static final String TAG = "RingtoneService";
    private static final String CHANNEL_ID = "RingtoneServiceChannel";
    private static final int NOTIFICATION_ID = 1;

    private MediaPlayer mediaPlayer;
    private AudioManager audioManager;
    private int originalVolume;
    private final Handler timeoutHandler = new Handler(Looper.getMainLooper());
    private boolean areReceiversRegistered = false; // Flag to track registration

    private final BroadcastReceiver screenStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Intent.ACTION_SCREEN_ON.equals(action) || Intent.ACTION_USER_PRESENT.equals(action)) {
                Log.d(TAG, "Screen activity detected, stopping ringtone.");
                stopSelf();
            }
        }
    };

    private final BroadcastReceiver volumeChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if ("android.media.VOLUME_CHANGED_ACTION".equals(intent.getAction())) {
                // This check ensures we don't react to our own programmatic volume change
                if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                    Log.d(TAG, "User volume change detected, stopping ringtone.");
                    stopSelf();
                }
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        // We will register receivers in onStartCommand to avoid race conditions
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startRinging();
        timeoutHandler.postDelayed(this::stopSelf, 5 * 60 * 1000); // 5 minutes
        return START_NOT_STICKY;
    }

    private void startRinging() {
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Find My Family")
                .setContentText("Your phone is being located...")
                .setSmallIcon(R.drawable.ic_ring_24)
                .build();

        startForeground(NOTIFICATION_ID, notification);

        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        originalVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        int maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);

        // 1. Set the volume first
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxVolume, 0);

        // 2. Start the media player
        mediaPlayer = MediaPlayer.create(this, R.raw.loud_alarm);
        mediaPlayer.setLooping(true);
        mediaPlayer.start();

        // 3. NOW register the receivers after all programmatic changes are done
        if (!areReceiversRegistered) {
            IntentFilter screenFilter = new IntentFilter(Intent.ACTION_SCREEN_ON);
            screenFilter.addAction(Intent.ACTION_USER_PRESENT);
            registerReceiver(screenStateReceiver, screenFilter);

            IntentFilter volumeFilter = new IntentFilter("android.media.VOLUME_CHANGED_ACTION");
            registerReceiver(volumeChangeReceiver, volumeFilter);
            areReceiversRegistered = true;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "RingtoneService destroyed.");
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.release();
            mediaPlayer = null;
        }
        if (audioManager != null) {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, originalVolume, 0);
        }
        timeoutHandler.removeCallbacksAndMessages(null);

        // Only unregister if they were actually registered
        if (areReceiversRegistered) {
            unregisterReceiver(screenStateReceiver);
            unregisterReceiver(volumeChangeReceiver);
            areReceiversRegistered = false;
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Ringtone Service Channel",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(serviceChannel);
        }
    }
}