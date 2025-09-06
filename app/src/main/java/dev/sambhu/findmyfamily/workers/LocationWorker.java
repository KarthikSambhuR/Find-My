package dev.sambhu.findmyfamily.workers;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.BatteryManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.concurrent.futures.CallbackToFutureAdapter;
import androidx.work.ListenableWorker;
import androidx.work.WorkerParameters;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import dev.sambhu.findmyfamily.utils.LocationUpdater;
import dev.sambhu.findmyfamily.BuildConfig;

public class LocationWorker extends ListenableWorker {

    private static final String TAG = "LocationWorker";
    private static final String WORKER_URL = BuildConfig.cloudflareWorkerUrl;

    public static final String KEY_TARGET_ID = "KEY_TARGET_ID";

    public LocationWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public ListenableFuture<Result> startWork() {
        return CallbackToFutureAdapter.getFuture(completer -> {
            String targetId = getInputData().getString(KEY_TARGET_ID);
            FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();

            if (currentUser == null || targetId == null) {
                completer.set(Result.failure());
                return "WorkFailure";
            }

            // ONE line to get location and battery! Handle the result asynchronously.
            LocationUpdater.fetchCurrentLocationAndBattery(getApplicationContext())
                    .addOnSuccessListener(data -> {
                        sendLocationUpdate(currentUser.getUid(), targetId, data.location.getLatitude(), data.location.getLongitude(), data.batteryLevel);
                        completer.set(Result.success());
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Worker failed to get location.", e);
                        completer.set(Result.failure());
                    });

            return "LocationUpdate";
        });
    }

    private void sendLocationUpdate(String sender, String target, double latitude, double longitude, long battery) {
        RequestQueue queue = Volley.newRequestQueue(getApplicationContext());
        String url = WORKER_URL + "?target=" + target + "&sender=" + sender + "&latitude=" + latitude + "&longitude=" + longitude + "&battery=" + battery;

        StringRequest stringRequest = new StringRequest(Request.Method.GET, url,
                response -> {
                    Log.d(TAG, "Location details sent successfully.");
                },
                error -> {
                    Log.e(TAG, "Failed to send location details", error);
                }
        );
        queue.add(stringRequest);
    }
}