package dev.sambhu.findmyfamily;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.messaging.FirebaseMessaging;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import dev.sambhu.findmyfamily.adapters.UserAdapter;
import dev.sambhu.findmyfamily.models.Family;
import dev.sambhu.findmyfamily.models.LastKnown;
import dev.sambhu.findmyfamily.models.PhoneNumber;
import dev.sambhu.findmyfamily.models.User;
import dev.sambhu.findmyfamily.utils.LocationUpdater; // Assuming your refactored class is here

public class MainActivity extends AppCompatActivity implements UserAdapter.OnItemClickListener {

    private static final String TAG = "MainActivity";
    private UserAdapter userAdapter;
    private List<User> userList;
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private ProgressBar progressBar;
    // FusedLocationProviderClient is now handled by LocationUpdater
    private ActivityResultLauncher<String[]> foregroundPermissionLauncher;
    private ActivityResultLauncher<String> backgroundPermissionLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        RecyclerView userRecyclerView = findViewById(R.id.user_recycler_view);
        progressBar = findViewById(R.id.progress_bar);
        MaterialToolbar topAppBar = findViewById(R.id.top_app_bar);

        userList = new ArrayList<>();
        userAdapter = new UserAdapter(userList);
        userAdapter.setOnItemClickListener(this);
        userRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        userRecyclerView.setAdapter(userAdapter);

        setupPermissionLaunchers();
        setupToolbar(topAppBar);
    }

    private void setupToolbar(MaterialToolbar toolbar) {
        toolbar.setOnMenuItemClickListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.action_refresh) {
                updateLastKnownInfo();
                return true;
            } else if (itemId == R.id.action_settings) {
                startActivity(new Intent(MainActivity.this, SettingsActivity.class));
                return true;
            }
            return false;
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }
        checkAndRequestPermissions();
    }

    private void onAllPermissionsGranted() {
        Log.d(TAG, "All permissions granted. Fetching data.");
        updateFCMToken();
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            fetchFamilyMembers(currentUser.getUid());
        }
    }

    @SuppressLint("MissingPermission")
    private void updateLastKnownInfo() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) return;

        //Toast.makeText(this, "Refreshing your location...", Toast.LENGTH_SHORT).show();

        // Using the refactored, unified method
        LocationUpdater.fetchCurrentLocationAndBattery(this)
                .addOnSuccessListener(data -> {
                    LastKnown lastKnown = new LastKnown();
                    lastKnown.setLatitude(data.location.getLatitude());
                    lastKnown.setLongitude(data.location.getLongitude());
                    lastKnown.setBattery(data.batteryLevel);

                    db.collection("users").document(currentUser.getUid())
                            .update("lastKnown", lastKnown)
                            //.addOnSuccessListener(aVoid -> Toast.makeText(this, "Your location updated!", Toast.LENGTH_SHORT).show())
                            .addOnFailureListener(e -> Toast.makeText(this, "Failed to update location.", Toast.LENGTH_SHORT).show());
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to get current location.", e);
                    Toast.makeText(this, "Error getting location. Is GPS on?", Toast.LENGTH_LONG).show();
                });
    }

    // This method is no longer needed if LocationUpdater handles it
    // private long getBatteryLevel() { ... }

    private void setupPermissionLaunchers() {
        foregroundPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                permissions -> {
                    boolean fineLocationGranted = Boolean.TRUE.equals(permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false));
                    if (fineLocationGranted) {
                        checkAndRequestBackgroundLocation();
                    } else {
                        showPermissionDeniedDialog("This app requires Location access to function.");
                    }
                });
        backgroundPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted) {
                        onAllPermissionsGranted();
                    } else {
                        showPermissionDeniedDialog("Background location is needed for continuous tracking. Please enable it in settings.");
                    }
                });
    }

    private void checkAndRequestPermissions() {
        List<String> requiredPermissions = new ArrayList<>();
        requiredPermissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        requiredPermissions.add(Manifest.permission.SEND_SMS);
        requiredPermissions.add(Manifest.permission.RECEIVE_SMS);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requiredPermissions.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        List<String> missingPermissions = new ArrayList<>();
        for (String permission : requiredPermissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                missingPermissions.add(permission);
            }
        }
        if (!missingPermissions.isEmpty()) {
            foregroundPermissionLauncher.launch(missingPermissions.toArray(new String[0]));
        } else {
            checkAndRequestBackgroundLocation();
        }
    }

    private void checkAndRequestBackgroundLocation() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                showBackgroundLocationRationaleDialog();
            } else {
                onAllPermissionsGranted();
            }
        } else {
            onAllPermissionsGranted();
        }
    }

    private void showBackgroundLocationRationaleDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Background Location Required")
                .setMessage("This app needs access to your location even when closed to keep your family updated on your whereabouts.")
                .setPositiveButton("Grant Access", (dialog, which) -> backgroundPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION))
                .setNegativeButton("Cancel", (dialog, which) -> {
                    Toast.makeText(this, "Background location is recommended for full functionality.", Toast.LENGTH_LONG).show();
                    onAllPermissionsGranted();
                })
                .setCancelable(false)
                .show();
    }

    private void showPermissionDeniedDialog(String message) {
        new AlertDialog.Builder(this)
                .setTitle("Permission Required")
                .setMessage(message + " Please grant it in app settings.")
                .setPositiveButton("Go to Settings", (dialog, which) -> {
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    Uri uri = Uri.fromParts("package", getPackageName(), null);
                    intent.setData(uri);
                    startActivity(intent);
                    finish();
                })
                .setNegativeButton("Cancel", (dialog, which) -> {
                    Toast.makeText(this, "Permissions were denied. The app will now close.", Toast.LENGTH_LONG).show();
                    finish();
                })
                .setCancelable(false)
                .show();
    }

    private void updateFCMToken() {
        FirebaseMessaging.getInstance().getToken().addOnCompleteListener(task -> {
            if (!task.isSuccessful()) {
                Log.w(TAG, "Fetching FCM registration token failed", task.getException());
                return;
            }
            String token = task.getResult();
            FirebaseUser user = mAuth.getCurrentUser();
            if (user != null) {
                db.collection("users").document(user.getUid()).update("fcmToken", token);
            }
        });
    }

    private void fetchFamilyMembers(String uid) {
        progressBar.setVisibility(View.VISIBLE);
        db.collection("users").document(uid).get().addOnCompleteListener(userTask -> {
            if (userTask.isSuccessful() && userTask.getResult() != null) {
                User currentUser = userTask.getResult().toObject(User.class);
                if (currentUser != null && currentUser.getFamilyId() != null) {
                    db.collection("families").document(currentUser.getFamilyId()).get().addOnCompleteListener(familyTask -> {
                        if (familyTask.isSuccessful() && familyTask.getResult() != null) {
                            Family family = familyTask.getResult().toObject(Family.class);
                            if (family != null && family.getMembers() != null && !family.getMembers().isEmpty()) {
                                addFamilyMembersListener(family.getMembers());
                            } else {
                                progressBar.setVisibility(View.GONE);
                            }
                        } else {
                            progressBar.setVisibility(View.GONE);
                            Log.w(TAG, "Error getting family details.", familyTask.getException());
                        }
                    });
                } else {
                    startActivity(new Intent(this, FamilyOptionsActivity.class));
                    finish();
                }
            } else {
                progressBar.setVisibility(View.GONE);
                Log.w(TAG, "Error getting user details.", userTask.getException());
            }
        });
    }

    /*private void addFamilyMembersListener(List<String> memberIds) {
        db.collection("users")
                .whereIn("uid", memberIds)
                .addSnapshotListener((value, e) -> {
                    progressBar.setVisibility(View.GONE);
                    if (e != null) {
                        Log.w(TAG, "Listen failed.", e);
                        return;
                    }
                    userList.clear();
                    if (value != null) {
                        for (DocumentSnapshot doc : value.getDocuments()) {
                            userList.add(doc.toObject(User.class));
                        }
                        userAdapter.notifyDataSetChanged();
                    }
                });
    }*/

    private void addFamilyMembersListener(List<String> memberIds) {
        db.collection("users")
                .whereIn("uid", memberIds)
                .addSnapshotListener((value, e) -> {
                    progressBar.setVisibility(View.GONE);
                    if (e != null) {
                        Log.w(TAG, "Listen failed.", e);
                        return;
                    }
                    userList.clear();
                    if (value != null) {
                        for (QueryDocumentSnapshot doc : value) { // Iterating directly is cleaner
                            User user = null;
                            try {
                                // This works perfectly for users with the NEW data format.
                                user = doc.toObject(User.class);
                            } catch (Exception conversionError) {
                                // This block runs if the data is in the OLD format for this specific user.
                                Log.w(TAG, "Could not auto-convert user " + doc.getId() + ". Manually converting for display.", conversionError);

                                Map<String, Object> userDataMap = doc.getData();
                                if (userDataMap != null) {
                                    // Manually construct the User object from the raw map data
                                    user = new User();
                                    user.setUid((String) userDataMap.get("uid"));
                                    user.setDisplayName((String) userDataMap.get("displayName"));
                                    user.setPhotoUrl((String) userDataMap.get("photoUrl"));
                                    user.setFamilyId((String) userDataMap.get("familyId"));

                                    // Manually convert the phone numbers list
                                    List<PhoneNumber> convertedPhoneNumbers = new ArrayList<>();
                                    Object rawPhoneNumbers = userDataMap.get("phoneNumbers");

                                    // Check if the field is a List of Strings
                                    if (rawPhoneNumbers instanceof List) {
                                        for (Object rawNumber : (List<?>) rawPhoneNumbers) {
                                            if (rawNumber instanceof String) {
                                                // Create a PhoneNumber object with an empty alias
                                                convertedPhoneNumbers.add(new PhoneNumber((String) rawNumber, ""));
                                            }
                                        }
                                    }
                                    user.setPhoneNumbers(convertedPhoneNumbers);
                                }
                            }

                            // Add the user to the list only if it was successfully created
                            if (user != null) {
                                userList.add(user);
                            }
                        }
                        userAdapter.notifyDataSetChanged();
                    }
                });
    }

    @Override
    public void onItemClick(User user) {
        Intent intent = new Intent(MainActivity.this, UserDetailActivity.class);
        intent.putExtra("user_data", user);
        startActivity(intent);
    }
}