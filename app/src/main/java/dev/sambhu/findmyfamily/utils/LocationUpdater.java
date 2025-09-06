// In: dev.sambhu.findmyfamily.utils.LocationUpdater.java

package dev.sambhu.findmyfamily.utils;

import android.annotation.SuppressLint;
import android.content.Context;
import android.location.Location;
import android.os.BatteryManager;
import android.util.Log;

import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import dev.sambhu.findmyfamily.models.LastKnown;

public class LocationUpdater {

    private static final String TAG = "LocationUpdater";

    // Helper class to hold our combined result
    public static class LocationData {
        public final Location location;
        public final long batteryLevel;

        LocationData(Location location, long batteryLevel) {
            this.location = location;
            this.batteryLevel = batteryLevel;
        }
    }

    /**
     * The new, unified method. It fetches location and battery and returns a Task.
     */
    @SuppressLint("MissingPermission")
    public static Task<LocationData> fetchCurrentLocationAndBattery(Context context) {
        // A TaskCompletionSource allows us to create a Task and control its result.
        TaskCompletionSource<LocationData> taskCompletionSource = new TaskCompletionSource<>();

        LocationServices.getFusedLocationProviderClient(context)
                .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener(location -> {
                    if (location != null) {
                        BatteryManager bm = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
                        long batteryLevel = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
                        // Set the successful result
                        taskCompletionSource.setResult(new LocationData(location, batteryLevel));
                    } else {
                        // Set a failure result
                        taskCompletionSource.setException(new Exception("Failed to get location (was null)."));
                    }
                })
                .addOnFailureListener(taskCompletionSource::setException); // Pass the failure along

        return taskCompletionSource.getTask();
    }

    /**
     * The old method, now refactored to use our new unified method.
     */
    public static void updateLastKnownInfo(Context context) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            Log.w(TAG, "Cannot update location, user not logged in.");
            return;
        }

        fetchCurrentLocationAndBattery(context).addOnSuccessListener(locationData -> {
            LastKnown lastKnown = new LastKnown();
            lastKnown.setLatitude(locationData.location.getLatitude());
            lastKnown.setLongitude(locationData.location.getLongitude());
            lastKnown.setBattery(locationData.batteryLevel);

            FirebaseFirestore.getInstance().collection("users")
                    .document(currentUser.getUid())
                    .update("lastKnown", lastKnown)
                    .addOnSuccessListener(aVoid -> Log.d(TAG, "Successfully updated lastKnown info."))
                    .addOnFailureListener(e -> Log.w(TAG, "Error updating lastKnown info", e));
        }).addOnFailureListener(e -> Log.e(TAG, "Failed to get location for update.", e));
    }
}