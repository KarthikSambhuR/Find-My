package dev.sambhu.findmyfamily;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.telephony.SmsManager;
import android.text.SpannableString;
import android.text.format.DateUtils;
import android.text.style.StyleSpan;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;

import java.util.Date;
import java.util.List;

import dev.sambhu.findmyfamily.adapters.PhoneNumberListAdapter;
import dev.sambhu.findmyfamily.models.LastKnown;
import dev.sambhu.findmyfamily.models.PhoneNumber;
import dev.sambhu.findmyfamily.models.User;
import dev.sambhu.findmyfamily.utils.OtpManager;

public class UserDetailActivity extends AppCompatActivity {
    private static final String TAG = "UserDetailActivity";
    private static final String WORKER_BASE_URL = BuildConfig.cloudflareWorkerBaseUrl;

    private ImageView profileImageView;
    private TextView displayNameTextView, lastSeenTextView, batteryPercentageTextView;
    private MaterialButton locateButton, ringButton, stopRingButton, openInGoogleMapsButton;
    private LinearLayout buttonRow;
    private LinearProgressIndicator locatingProgressBar;
    private MapView mapView;
    private CardView mapCardView;

    private User targetUser;
    private RequestQueue requestQueue;
    private CountDownTimer locatingTimer, ringingTimer;
    private BroadcastReceiver locationUpdateReceiver;
    private String ringInitiationMethod = "internet";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Configuration.getInstance().setUserAgentValue(getPackageName());
        setContentView(R.layout.activity_user_detail);
        requestQueue = Volley.newRequestQueue(this);
        initViews();
        targetUser = (User) getIntent().getSerializableExtra("user_data");
        if (targetUser == null) {
            Toast.makeText(this, "User data not found.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        populateUi(targetUser);
        setupButtonListeners();
        setupBroadcastReceiver();
    }

    private void initViews() {
        profileImageView = findViewById(R.id.detail_profile_image);
        displayNameTextView = findViewById(R.id.detail_display_name);
        lastSeenTextView = findViewById(R.id.detail_last_seen);
        batteryPercentageTextView = findViewById(R.id.detail_battery_percentage);
        ringButton = findViewById(R.id.ring_button);
        mapView = findViewById(R.id.map_view);
        mapCardView = findViewById(R.id.map_card_view);
        openInGoogleMapsButton = findViewById(R.id.open_in_google_maps_button);
        locateButton = findViewById(R.id.locate_button);
        stopRingButton = findViewById(R.id.stop_ring_button);
        buttonRow = findViewById(R.id.button_row);
        locatingProgressBar = findViewById(R.id.locating_progress_bar);
    }

    private void setupButtonListeners() {
        locateButton.setOnClickListener(v -> sendLocationRequest());
        locateButton.setOnLongClickListener(v -> {
            showSmsOptions("Select number to locate via SMS", "locate");
            return true;
        });

        ringButton.setOnClickListener(v -> sendRingRequest());
        ringButton.setOnLongClickListener(v -> {
            showSmsOptions("Select number to ring via SMS", "ring");
            return true;
        });

        // --- MODIFIED: This button's behavior is now smarter ---
        stopRingButton.setOnClickListener(v -> {
            if ("sms".equals(ringInitiationMethod)) {
                // If started with SMS, show the SMS options to stop it
                showSmsOptions("Select number to stop ring via SMS", "stop_ring");
            } else {
                // Otherwise, use the default internet method
                sendStopRingRequest();
            }
        });
    }

    private void populateUi(User user) {
        if (user.getPhotoUrl() != null && !user.getPhotoUrl().isEmpty()) {
            Glide.with(this).load(user.getPhotoUrl()).into(profileImageView);
        } else {
            profileImageView.setImageResource(R.drawable.ic_profile_placeholder);
        }
        displayNameTextView.setText(user.getDisplayName());

        if (user.getLastKnown() != null && user.getLastKnown().getUpdatedAt() != null) {
            Date lastSeenDate = user.getLastKnown().getUpdatedAt();
            long now = System.currentTimeMillis();
            long time = lastSeenDate.getTime();

            if (now - time < DateUtils.MINUTE_IN_MILLIS) {
                lastSeenTextView.setText("Last seen: Just now");
            } else {
                CharSequence relativeTime = DateUtils.getRelativeTimeSpanString(time, now, DateUtils.MINUTE_IN_MILLIS);
                lastSeenTextView.setText("Last seen: " + relativeTime);
            }

            batteryPercentageTextView.setText(user.getLastKnown().getBattery() + "%");
            double lat = user.getLastKnown().getLatitude();
            double lon = user.getLastKnown().getLongitude();
            setupMap(lat, lon);
            openInGoogleMapsButton.setOnClickListener(v -> openInGoogleMaps(lat, lon));
            openInGoogleMapsButton.setVisibility(View.VISIBLE);
        } else {
            lastSeenTextView.setText("Last seen: N/A");
            batteryPercentageTextView.setText("N/A");
            openInGoogleMapsButton.setVisibility(View.GONE);
        }
    }

    private void sendLocationRequest() {
        setButtonsEnabled(false);
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "Could not verify sender.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Force refresh the token to ensure it's not stale before making the request.
        user.getIdToken(true).addOnSuccessListener(getTokenResult -> {
            // Now that we have a fresh token, we know our UID is valid. Proceed with the request.
            String senderId = user.getUid();
            String url = WORKER_BASE_URL + "/getLocation?target=" + targetUser.getUid() + "&sender=" + senderId;

            StringRequest stringRequest = new StringRequest(Request.Method.GET, url,
                    response -> {
                        Log.d(TAG, "Location request sent successfully.");
                        startLocatingTimer(90000);
                    },
                    error -> {
                        Log.e(TAG, "Failed to send location request", error);
                        Toast.makeText(this, "Error: Could not request location.", Toast.LENGTH_SHORT).show();
                    }
            );
            requestQueue.add(stringRequest);

        }).addOnFailureListener(e -> {
            // Failed to get a fresh token, which is a serious auth problem.
            Log.e(TAG, "Failed to refresh auth token", e);
            Toast.makeText(this, "Authentication error. Please try again.", Toast.LENGTH_SHORT).show();
        });
    }

    private void sendRingRequest() {
        setButtonsEnabled(false);
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "Could not verify sender.", Toast.LENGTH_SHORT).show();
            return;
        }

        user.getIdToken(true).addOnSuccessListener(getTokenResult -> {
            ringInitiationMethod = "internet";
            String url = WORKER_BASE_URL + "/ring?target=" + targetUser.getUid();
            StringRequest ringRequest = new StringRequest(Request.Method.GET, url,
                    response -> {
                        //Toast.makeText(this, "Ringing " + targetUser.getDisplayName() + "'s phone...", Toast.LENGTH_SHORT).show();
                        startRingingTimer();
                    },
                    error -> {
                        Toast.makeText(this, "Failed to send ring request.", Toast.LENGTH_SHORT).show();
                        Log.e(TAG, "Ring request failed", error);
                    }
            );
            requestQueue.add(ringRequest);
        }).addOnFailureListener(e -> {
            Log.e(TAG, "Failed to refresh auth token for ring request", e);
            Toast.makeText(this, "Authentication error. Please try again.", Toast.LENGTH_SHORT).show();
        });
    }

    private void sendStopRingRequest() {
        String url = WORKER_BASE_URL + "/stopRing?target=" + targetUser.getUid();
        StringRequest stopRingRequest = new StringRequest(Request.Method.GET, url,
                response -> {
                    //Toast.makeText(this, "Stopping ring...", Toast.LENGTH_SHORT).show();
                    if (ringingTimer != null) {
                        ringingTimer.cancel();
                    }
                    onRingingFinished();
                },
                error -> {
                    //Toast.makeText(this, "Failed to stop ring.", Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "Stop ring request failed", error);
                }
        );
        requestQueue.add(stopRingRequest);
    }

    private void startLocatingTimer(long durationMillis) {
        locatingProgressBar.setVisibility(View.VISIBLE);
        locatingProgressBar.setMax((int) (durationMillis / 1000));
        locatingProgressBar.setProgress(0);
        locatingTimer = new CountDownTimer(durationMillis, 1000) {
            public void onTick(long millisUntilFinished) {
                int progress = (int) (durationMillis / 1000) - (int) (millisUntilFinished / 1000);
                locatingProgressBar.setProgress(progress, true);
            }
            public void onFinish() {
                onLocatingFinished();
            }
        }.start();
    }

    private void onLocatingFinished() {
        setButtonsEnabled(true);
        locatingProgressBar.setVisibility(View.INVISIBLE);
        locatingProgressBar.setProgress(0);
        locatingTimer = null;
    }

    private void startRingingTimer() {
        buttonRow.setVisibility(View.GONE);
        stopRingButton.setVisibility(View.VISIBLE);
        mapCardView.setVisibility(View.GONE); // Hide the map
        locatingProgressBar.setVisibility(View.VISIBLE);
        locatingProgressBar.setMax(300); // 5 minutes
        locatingProgressBar.setProgress(0);

        ringingTimer = new CountDownTimer(300000, 1000) {
            public void onTick(long millisUntilFinished) {
                int progress = 300 - (int) (millisUntilFinished / 1000);
                locatingProgressBar.setProgress(progress, true);
            }
            public void onFinish() {
                sendStopRingRequest();
            }
        }.start();
    }

    private void onRingingFinished() {
        buttonRow.setVisibility(View.VISIBLE);
        stopRingButton.setVisibility(View.GONE);
        mapCardView.setVisibility(View.VISIBLE);
        locatingProgressBar.setVisibility(View.INVISIBLE);
        locatingProgressBar.setProgress(0);
        ringingTimer = null;
        // Reset the method to default when finished
        ringInitiationMethod = "internet";
        setButtonsEnabled(true);
    }

    private void setButtonsEnabled(boolean enabled) {
        locateButton.setEnabled(enabled);
        ringButton.setEnabled(enabled);
        if (enabled) {
            locateButton.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.surface_container)));
            locateButton.setTextColor(ContextCompat.getColor(this, R.color.primary_text));
            locateButton.setIconTint(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.primary_text)));
            ringButton.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.surface_container)));
            ringButton.setTextColor(ContextCompat.getColor(this, R.color.primary_text));
            ringButton.setIconTint(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.primary_text)));
        } else {
            locateButton.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.surface_container_disabled)));
            locateButton.setTextColor(ContextCompat.getColor(this, R.color.primary_text_disabled));
            locateButton.setIconTint(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.primary_text_disabled)));
            ringButton.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.surface_container_disabled)));
            ringButton.setTextColor(ContextCompat.getColor(this, R.color.primary_text_disabled));
            ringButton.setIconTint(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.primary_text_disabled)));
        }
    }

    private void setupBroadcastReceiver() {
        locationUpdateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (action == null) return;

                String userId = intent.getStringExtra("userId");
                if (userId != null && userId.equals(targetUser.getUid())) {
                    // If a location update arrives, stop any ongoing process (locating or ringing)
                    if (locatingTimer != null) {
                        locatingTimer.cancel();
                        onLocatingFinished();
                    }
                    if (ringingTimer != null) {
                        ringingTimer.cancel();
                        onRingingFinished();
                        // We also need to send a stopRing request to the device
                        sendStopRingRequest();
                    }

                    try {
                        LastKnown newLastKnown = new LastKnown();
                        if ("dev.sambhu.findmyfamily.LOCATION_UPDATE".equals(action)) {
                            newLastKnown.setLatitude(Double.parseDouble(intent.getStringExtra("latitude")));
                            newLastKnown.setLongitude(Double.parseDouble(intent.getStringExtra("longitude")));
                            newLastKnown.setBattery(Long.parseLong(intent.getStringExtra("battery")));
                            newLastKnown.setUpdatedAt(new Date());
                        } else if ("dev.sambhu.findmyfamily.SMS_LOCATION_UPDATE".equals(action)) {
                            newLastKnown.setLatitude(intent.getDoubleExtra("latitude", 0));
                            newLastKnown.setLongitude(intent.getDoubleExtra("longitude", 0));
                            newLastKnown.setBattery(intent.getLongExtra("battery", 0));
                            newLastKnown.setUpdatedAt(new Date(intent.getLongExtra("updatedAt", System.currentTimeMillis())));
                        }

                        targetUser.setLastKnown(newLastKnown);
                        populateUi(targetUser);
                        //Toast.makeText(UserDetailActivity.this, "Location refreshed!", Toast.LENGTH_SHORT).show();
                    } catch (Exception e) {
                        Log.e(TAG, "Could not parse location data from broadcast", e);
                    }
                }
            }
        };
    }

    @Override
    protected void onResume() {
        super.onResume();
        mapView.onResume();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction("dev.sambhu.findmyfamily.LOCATION_UPDATE");
        intentFilter.addAction("dev.sambhu.findmyfamily.SMS_LOCATION_UPDATE");
        LocalBroadcastManager.getInstance(this).registerReceiver(locationUpdateReceiver, intentFilter);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mapView.onPause();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(locationUpdateReceiver);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (locatingTimer != null) {
            locatingTimer.cancel();
            onLocatingFinished();
        }
        if (ringingTimer != null) {
            ringingTimer.cancel();
            onRingingFinished();
        }
    }

    private void setupMap(double latitude, double longitude) {
        mapView.setTileSource(TileSourceFactory.MAPNIK);
        mapView.setBuiltInZoomControls(true);
        mapView.setMultiTouchControls(true);
        GeoPoint startPoint = new GeoPoint(latitude, longitude);
        mapView.getController().setZoom(18.0);
        mapView.getController().setCenter(startPoint);
        mapView.getOverlays().clear();
        Drawable icon = getResources().getDrawable(R.drawable.ic_google_maps_logo);
        icon.setTint(Color.RED);
        Marker marker = new Marker(mapView);
        marker.setPosition(new GeoPoint(latitude, longitude));
        marker.setIcon(icon);
        if (targetUser != null) {
            marker.setTitle(targetUser.getDisplayName());
        }
        mapView.getOverlays().add(marker);
        mapView.invalidate();
    }

    private void openInGoogleMaps(double latitude, double longitude) {
        if (targetUser == null) return;

        // Get the name to use as a label for the marker
        String markerLabel = targetUser.getDisplayName();

        // This URI format drops a pin at the specified lat/lng with a label
        // The "0,0?q=" is a trick to ensure it drops a pin instead of searching
        Uri gmmIntentUri = Uri.parse("geo:0,0?q=" + latitude + "," + longitude + "(" + Uri.encode(markerLabel) + ")");

        Intent mapIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri);
        mapIntent.setPackage("com.google.android.apps.maps");

        if (mapIntent.resolveActivity(getPackageManager()) != null) {
            startActivity(mapIntent);
        } else {
            Toast.makeText(this, "Google Maps is not installed.", Toast.LENGTH_SHORT).show();
        }
    }
    /*private void showSmsOptions(String title, final String action) {
        if (targetUser != null && targetUser.getPhoneNumbers() != null && !targetUser.getPhoneNumbers().isEmpty()) {
            List<String> phoneNumbers = targetUser.getPhoneNumbers();
            CharSequence[] phoneNumbersArray = phoneNumbers.toArray(new CharSequence[0]);
            new AlertDialog.Builder(this)
                    .setTitle(title)
                    .setItems(phoneNumbersArray, (dialog, which) -> {
                        String selectedNumber = phoneNumbers.get(which);
                        switch (action) {
                            case "locate":
                                sendLocateSms(selectedNumber);
                                break;
                            case "ring":
                                sendRingSms(selectedNumber);
                                break;
                            case "stop_ring":
                                sendStopRingSms(selectedNumber);
                                break;
                        }
                    })
                    .create().show();
        } else {
            Toast.makeText(this, "No phone numbers available for this user.", Toast.LENGTH_SHORT).show();
        }
    }*/

    /*private void showSmsOptions(String title, final String action) {
        if (targetUser != null && targetUser.getPhoneNumbers() != null && !targetUser.getPhoneNumbers().isEmpty()) {
            List<String> phoneNumbers = targetUser.getPhoneNumbers();

            // 1. Create a new AlertDialog.Builder
            AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.RoundedDialogTheme);
            builder.setTitle("Select Phone Number");

// 2. Inflate your custom dialog layout
            View dialogView = getLayoutInflater().inflate(R.layout.dialog_phone_list, null);
            builder.setView(dialogView);

            // 3. Get the ListView and set up an adapter
            ListView listView = dialogView.findViewById(R.id.phone_list_view);
            ArrayAdapter<String> adapter = new ArrayAdapter<>(
                    this,
                    R.layout.item_phone_number_modal, // Your custom row layout
                    R.id.phone_number_text,    // The ID of the TextView in your row layout
                    phoneNumbers
            );
            listView.setAdapter(adapter);

            // 4. Create and show the dialog
            AlertDialog dialog = builder.create();

            // 5. Set the click listener for the list items
            listView.setOnItemClickListener((parent, view, position, id) -> {
                String selectedNumber = phoneNumbers.get(position);
                switch (action) {
                    case "locate":
                        sendLocateSms(selectedNumber);
                        break;
                    case "ring":
                        sendRingSms(selectedNumber);
                        break;
                    case "stop_ring":
                        sendStopRingSms(selectedNumber);
                        break;
                }
                dialog.dismiss(); // Close the dialog after selection
            });

            dialog.show();
            Window window = dialog.getWindow();
            if (window != null) {
                WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams();
                layoutParams.copyFrom(window.getAttributes());
                // Set the width to 85% of the screen width
                layoutParams.width = (int) (getResources().getDisplayMetrics().widthPixels * 0.85f);
                window.setAttributes(layoutParams);
            }

        } else {
            Toast.makeText(this, "No phone numbers available for this user.", Toast.LENGTH_SHORT).show();
        }
    }*/
    private void showSmsOptions(String title, final String action) {
        if (targetUser != null && targetUser.getPhoneNumbers() != null && !targetUser.getPhoneNumbers().isEmpty()) {
            List<PhoneNumber> phoneNumbers = targetUser.getPhoneNumbers();

            SpannableString alertTitle = new SpannableString(title);
            alertTitle.setSpan(new StyleSpan(Typeface.BOLD), 0, title.length(), 0);

            AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.RoundedDialogTheme);
            builder.setTitle(alertTitle);

            View dialogView = getLayoutInflater().inflate(R.layout.dialog_phone_list, null);
            builder.setView(dialogView);

            ListView listView = dialogView.findViewById(R.id.phone_list_view);

            // Use our new custom adapter
            PhoneNumberListAdapter adapter = new PhoneNumberListAdapter(this, phoneNumbers);
            listView.setAdapter(adapter);

            AlertDialog dialog = builder.create();

            listView.setOnItemClickListener((parent, view, position, id) -> {
                // Get the number string from the selected PhoneNumber object
                String selectedNumber = phoneNumbers.get(position).getNumber();
                switch (action) {
                    case "locate":
                        sendLocateSms(selectedNumber);
                        break;
                    case "ring":
                        sendRingSms(selectedNumber);
                        break;
                    case "stop_ring":
                        sendStopRingSms(selectedNumber);
                        break;
                }
                dialog.dismiss();
            });

            dialog.show();

            // Optional: Keep the code to resize the dialog width
            Window window = dialog.getWindow();
            if (window != null) {
                WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams();
                layoutParams.copyFrom(window.getAttributes());
                layoutParams.width = (int) (getResources().getDisplayMetrics().widthPixels * 0.85f);
                window.setAttributes(layoutParams);
            }

        } else {
            Toast.makeText(this, "No phone numbers available for this user.", Toast.LENGTH_SHORT).show();
        }
    }

    private void sendLocateSms(String phoneNumber) {
        setButtonsEnabled(false);
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "Could not verify your ID.", Toast.LENGTH_SHORT).show();
            return;
        }

        user.getIdToken(true).addOnSuccessListener(getTokenResult -> {
            String myId = user.getUid();
            String otp = OtpManager.generateOtp(targetUser.getUid());
            String message = "SAMBHUFINDMY:" + otp + "," + myId;

            try {
                SmsManager.getDefault().sendTextMessage(phoneNumber, null, message, null, null);
                Toast.makeText(getApplicationContext(), "Location request sent via SMS.", Toast.LENGTH_LONG).show();
                startLocatingTimer(120000);
            } catch (Exception e) {
                Toast.makeText(getApplicationContext(), "Failed to send SMS.", Toast.LENGTH_LONG).show();
                Log.e(TAG, "SMS sending failed", e);
            }
        }).addOnFailureListener(e -> {
            Log.e(TAG, "Failed to refresh auth token for SMS locate", e);
            Toast.makeText(this, "Authentication error. Please try again.", Toast.LENGTH_SHORT).show();
        });
    }

    private void sendRingSms(String phoneNumber) {
        setButtonsEnabled(false);
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "Could not verify your ID.", Toast.LENGTH_SHORT).show();
            return;
        }

        user.getIdToken(true).addOnSuccessListener(getTokenResult -> {
            ringInitiationMethod = "sms";
            String otp = OtpManager.generateOtp(targetUser.getUid());
            String message = "SAMBHUFINDMY_R:" + otp + ",RING";

            try {
                SmsManager.getDefault().sendTextMessage(phoneNumber, null, message, null, null);
                Toast.makeText(getApplicationContext(), "Ring request sent via SMS.", Toast.LENGTH_LONG).show();
                startRingingTimer();
            } catch (Exception e) {
                Toast.makeText(getApplicationContext(), "Failed to send SMS.", Toast.LENGTH_LONG).show();
                Log.e(TAG, "SMS sending failed", e);
            }
        }).addOnFailureListener(e -> {
            Log.e(TAG, "Failed to refresh auth token for SMS ring", e);
            Toast.makeText(this, "Authentication error. Please try again.", Toast.LENGTH_SHORT).show();
        });
    }

    private void sendStopRingSms(String phoneNumber) {
        String otp = OtpManager.generateOtp(targetUser.getUid());
        String message = "SAMBHUFINDMY_R:" + otp + ",STOP_RING";

        try {
            SmsManager.getDefault().sendTextMessage(phoneNumber, null, message, null, null);
            //Toast.makeText(getApplicationContext(), "Stop ring request sent via SMS.", Toast.LENGTH_LONG).show();
            if (ringingTimer != null) {
                ringingTimer.cancel();
            }
            onRingingFinished();
        } catch (Exception e) {
            Toast.makeText(getApplicationContext(), "Failed to send SMS.", Toast.LENGTH_LONG).show();
            Log.e(TAG, "SMS sending failed", e);
        }
        setButtonsEnabled(true);
    }
}