package dev.sambhu.findmyfamily.services;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.telephony.SmsMessage;
import android.util.Log;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Date;

import dev.sambhu.findmyfamily.models.LastKnown;
import dev.sambhu.findmyfamily.utils.OtpManager;
import dev.sambhu.findmyfamily.workers.SmsReplyWorker;
import dev.sambhu.findmyfamily.BuildConfig;

public class SmsReceiver extends BroadcastReceiver {

    private static final String TAG = "SmsReceiver";
    private static final String SMS_LOCATE_PREFIX = BuildConfig.SMSKeywordLocate + ":";
    private static final String SMS_RING_PREFIX = BuildConfig.SMSKeywordRing + ":";;

    @Override
    public void onReceive(Context context, Intent intent) {
        if ("android.provider.Telephony.SMS_RECEIVED".equals(intent.getAction())) {
            Bundle bundle = intent.getExtras();
            if (bundle == null) return;

            Object[] pdus = (Object[]) bundle.get("pdus");
            if (pdus == null) return;

            final SmsMessage[] messages = new SmsMessage[pdus.length];
            for (int i = 0; i < pdus.length; i++) {
                messages[i] = SmsMessage.createFromPdu((byte[]) pdus[i]);
            }

            if (messages.length > 0) {
                String messageBody = messages[0].getMessageBody();
                String originatingAddress = messages[0].getOriginatingAddress();
                long timestamp = messages[0].getTimestampMillis();

                if (messageBody.startsWith(SMS_LOCATE_PREFIX)) {
                    handleLocateSms(context, messageBody, originatingAddress, timestamp);
                } else if (messageBody.startsWith(SMS_RING_PREFIX)) {
                    handleRingSms(context, messageBody);
                }
            }
        }
    }

    private void handleLocateSms(Context context, String message, String from, long timestamp) {
        String content = message.substring(SMS_LOCATE_PREFIX.length());
        String[] parts = content.split(",");

        if (parts.length == 2) { // This is an OTP request for location
            String otp = parts[0];
            String requesterId = parts[1];
            String myId = FirebaseAuth.getInstance().getUid();
            if (myId == null) return;

            if (OtpManager.isValidOtp(myId, otp)) {
                Log.d(TAG, "Valid location OTP received. Scheduling reply.");
                scheduleSmsReply(context, from, requesterId);
            } else {
                Log.w(TAG, "Invalid location OTP received.");
            }

        } else if (parts.length == 4) { // This is a location data reply
            Log.d(TAG, "Received location data via SMS.");
            try {
                double latitude = Double.parseDouble(parts[0]);
                double longitude = Double.parseDouble(parts[1]);
                long battery = Long.parseLong(parts[2]);
                String userIdOfLocation = parts[3];
                updateFirestore(userIdOfLocation, latitude, longitude, battery, timestamp);
                broadcastSmsUpdateToUi(context, userIdOfLocation, latitude, longitude, battery, timestamp);
            } catch (NumberFormatException e) {
                Log.e(TAG, "Failed to parse SMS location data", e);
            }
        }
    }

    private void handleRingSms(Context context, String message) {
        String content = message.substring(SMS_RING_PREFIX.length());
        String[] parts = content.split(",");
        String myId = FirebaseAuth.getInstance().getUid();
        if (myId == null) return;

        if (parts.length == 2) {
            String otp = parts[0];
            String command = parts[1];

            if (OtpManager.isValidOtp(myId, otp)) {
                Log.d(TAG, "Valid ring OTP received. Command: " + command);
                Intent serviceIntent = new Intent(context, RingtoneService.class);
                if ("RING".equals(command)) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent);
                    } else {
                        context.startService(serviceIntent);
                    }
                } else if ("STOP_RING".equals(command)) {
                    context.stopService(serviceIntent);
                }
            } else {
                Log.w(TAG, "Invalid ring OTP received.");
            }
        }
    }

    private void scheduleSmsReply(Context context, String senderPhone, String requesterId) {
        Data workerData = new Data.Builder()
                .putString(SmsReplyWorker.KEY_SENDER_PHONE, senderPhone)
                .putString(SmsReplyWorker.KEY_REQUESTER_ID, requesterId)
                .build();
        OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(SmsReplyWorker.class)
                .setInputData(workerData)
                .build();
        WorkManager.getInstance(context).enqueue(workRequest);
    }

    private void updateFirestore(String userId, double lat, double lon, long bat, long time) {
        LastKnown lastKnown = new LastKnown();
        lastKnown.setLatitude(lat);
        lastKnown.setLongitude(lon);
        lastKnown.setBattery(bat);
        lastKnown.setUpdatedAt(new Date(time));

        FirebaseFirestore.getInstance().collection("users")
                .document(userId)
                .update("lastKnown", lastKnown)
                .addOnSuccessListener(aVoid -> Log.d(TAG, "Firestore updated from SMS data for user: " + userId))
                .addOnFailureListener(e -> Log.e(TAG, "Firestore update from SMS failed", e));
    }

    private void broadcastSmsUpdateToUi(Context context, String userId, double lat, double lon, long bat, long time) {
        Intent intent = new Intent("dev.sambhu.findmyfamily.SMS_LOCATION_UPDATE");
        intent.putExtra("userId", userId);
        intent.putExtra("latitude", lat);
        intent.putExtra("longitude", lon);
        intent.putExtra("battery", bat);
        intent.putExtra("updatedAt", time);
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
    }
}