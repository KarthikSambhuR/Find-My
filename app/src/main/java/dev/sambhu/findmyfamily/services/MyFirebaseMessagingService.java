package dev.sambhu.findmyfamily.services;

import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;

import dev.sambhu.findmyfamily.workers.LocationWorker;

public class MyFirebaseMessagingService extends FirebaseMessagingService {

    private static final String TAG = "MyFirebaseMsgService";

    @Override
    public void onNewToken(@NonNull String token) {
        Log.d(TAG, "Refreshed token: " + token);
        sendRegistrationToServer(token);
    }

    private void sendRegistrationToServer(String token) {
        FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        if (firebaseUser != null) {
            FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(firebaseUser.getUid())
                    .update("fcmToken", token);
        }
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        Log.d(TAG, "FCM Message Received: " + remoteMessage.getData());
        Map<String, String> data = remoteMessage.getData();
        String type = data.get("type");

        if ("getLocation".equals(type)) {
            String senderId = data.get("sender");
            if (senderId != null) {
                Log.d(TAG, "getLocation request received. Scheduling worker.");
                scheduleLocationUpdateWorker(senderId);
            }
        } else if ("sendLocation".equals(type)) {
            Log.d(TAG, "sendLocation data received. Broadcasting to UI.");
            broadcastLocationUpdate(data);
        } else if ("ring".equals(type)) {
            Log.d(TAG, "Ring request received. Starting RingtoneService.");
            Intent serviceIntent = new Intent(this, RingtoneService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }
        } else if ("stopRing".equals(type)) {
            Log.d(TAG, "Stop ring request received. Stopping RingtoneService.");
            Intent serviceIntent = new Intent(this, RingtoneService.class);
            stopService(serviceIntent);
        }
    }

    private void scheduleLocationUpdateWorker(String targetId) {
        Data workerData = new Data.Builder()
                .putString(LocationWorker.KEY_TARGET_ID, targetId)
                .build();
        OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(LocationWorker.class)
                .setInputData(workerData)
                .build();
        WorkManager.getInstance(getApplicationContext()).enqueue(workRequest);
    }

    private void broadcastLocationUpdate(Map<String, String> data) {
        Intent intent = new Intent("dev.sambhu.findmyfamily.LOCATION_UPDATE");
        // The 'sender' from this payload is the user whose location was updated
        intent.putExtra("userId", data.get("sender"));
        intent.putExtra("latitude", data.get("latitude"));
        intent.putExtra("longitude", data.get("longitude"));
        intent.putExtra("battery", data.get("battery"));
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }
}