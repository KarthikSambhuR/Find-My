package dev.sambhu.findmyfamily.workers;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.BatteryManager;
import android.telephony.SmsManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;

import java.util.concurrent.ExecutionException;

import dev.sambhu.findmyfamily.utils.LocationUpdater;

public class SmsReplyWorker extends Worker {

    private static final String TAG = "SmsReplyWorker";
    public static final String KEY_SENDER_PHONE = "KEY_SENDER_PHONE";
    public static final String KEY_REQUESTER_ID = "KEY_REQUESTER_ID";

    public SmsReplyWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        String senderPhone = getInputData().getString(KEY_SENDER_PHONE);
        String myId = FirebaseAuth.getInstance().getUid();

        if (senderPhone == null || myId == null) {
            return Result.failure();
        }

        try {
            // ONE line to get location and battery! Block until it's done.
            LocationUpdater.LocationData data = Tasks.await(
                    LocationUpdater.fetchCurrentLocationAndBattery(getApplicationContext())
            );

            String replyMessage = String.format("SAMBHUFINDMY:%s,%s,%s,%s",
                    data.location.getLatitude(),
                    data.location.getLongitude(),
                    data.batteryLevel,
                    myId
            );

            SmsManager.getDefault().sendTextMessage(senderPhone, null, replyMessage, null, null);
            Log.d(TAG, "Successfully sent location reply SMS to " + senderPhone);
            return Result.success();

        } catch (ExecutionException | InterruptedException e) {
            Log.e(TAG, "Error waiting for location result", e);
            Thread.currentThread().interrupt(); // Restore the interrupted status
            return Result.failure();
        }
    }
}