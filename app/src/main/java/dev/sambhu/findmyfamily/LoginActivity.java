package dev.sambhu.findmyfamily;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import dev.sambhu.findmyfamily.models.PhoneNumber;
import dev.sambhu.findmyfamily.models.User;

public class LoginActivity extends AppCompatActivity {

    private static final String TAG = "LoginActivity";

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private GoogleSignInClient mGoogleSignInClient;
    private Button googleLoginButton;
    // The ProgressBar has been removed as it's not in the new layout

    private final ActivityResultLauncher<Intent> signInLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                googleLoginButton.setEnabled(true); // Re-enable button
                if (result.getResultCode() == RESULT_OK) {
                    Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(result.getData());
                    try {
                        GoogleSignInAccount account = task.getResult(ApiException.class);
                        firebaseAuthWithGoogle(account.getIdToken());
                    } catch (ApiException e) {
                        Log.w(TAG, "Google sign in failed", e);
                        Toast.makeText(this, "Google sign in failed.", Toast.LENGTH_SHORT).show();
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);

        googleLoginButton = findViewById(R.id.login_button_google);
        googleLoginButton.setOnClickListener(v -> signIn());
    }

    @Override
    protected void onStart() {
        super.onStart();
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            googleLoginButton.setVisibility(View.GONE);
            // Since there's no progress bar, we just navigate directly
            checkUserExistenceAndNavigate(currentUser);
        }
    }

    private void signIn() {
        googleLoginButton.setEnabled(false); // Disable button to prevent double taps

        mGoogleSignInClient.signOut().addOnCompleteListener(this, task -> {
            Intent signInIntent = mGoogleSignInClient.getSignInIntent();
            signInLauncher.launch(signInIntent);
        });
    }

    private void firebaseAuthWithGoogle(String idToken) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            checkUserExistenceAndNavigate(user);
                        }
                    } else {
                        Toast.makeText(this, "Authentication failed.", Toast.LENGTH_SHORT).show();
                        googleLoginButton.setEnabled(true);
                    }
                });
    }

    private void checkUserExistenceAndNavigate(FirebaseUser firebaseUser) {
        DocumentReference userRef = db.collection("users").document(firebaseUser.getUid());
        userRef.get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                DocumentSnapshot document = task.getResult();
                if (document.exists()) {
                    // --- THIS IS THE MODIFIED PART ---
                    try {
                        // Try to deserialize directly first
                        User user = document.toObject(User.class);
                        // If it succeeds, data is already in the new format.
                        navigateToMainActivity();
                    } catch (Exception e) {
                        // Deserialization failed! This means the data is likely in the old format.
                        Log.w(TAG, "Could not deserialize user, attempting manual conversion.", e);

                        // Manually get the raw data
                        Map<String, Object> userDataMap = document.getData();
                        if (userDataMap == null) {
                            navigateToOnboarding(); // Should not happen, but a good safeguard
                            return;
                        }

                        // Get the phoneNumbers field as a generic list
                        List<?> rawPhoneNumbers = (List<?>) userDataMap.get("phoneNumbers");
                        List<PhoneNumber> convertedPhoneNumbers = new ArrayList<>();
                        boolean needsUpdate = false;

                        if (rawPhoneNumbers != null && !rawPhoneNumbers.isEmpty()) {
                            // Check the type of the first element to detect format
                            if (rawPhoneNumbers.get(0) instanceof String) {
                                needsUpdate = true; // Mark for update
                                // This is the old format (List<String>), so we convert it
                                for (Object numberObj : rawPhoneNumbers) {
                                    convertedPhoneNumbers.add(new PhoneNumber((String) numberObj, ""));
                                }
                            }
                        }

                        // Update the map with the correctly formatted list
                        userDataMap.put("phoneNumbers", convertedPhoneNumbers);

                        // If we converted old data, save it back to Firestore for the future
                        if (needsUpdate) {
                            userRef.set(userDataMap)
                                    .addOnSuccessListener(aVoid -> Log.d(TAG, "User data migrated successfully."))
                                    .addOnFailureListener(err -> Log.e(TAG, "Failed to migrate user data.", err));
                        }

                        // Now that data is fixed, proceed to the main activity
                        navigateToMainActivity();
                    }
                } else {
                    // User document doesn't exist, go to onboarding
                    navigateToOnboarding();
                }
            } else {
                // Handle error, maybe show a toast
                Toast.makeText(this, "Failed to check user data.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void navigateToMainActivity() {
        Intent intent = new Intent(LoginActivity.this, MainActivity.class);
        startActivity(intent);
        finish();
    }

    private void navigateToOnboarding() {
        Intent intent = new Intent(LoginActivity.this, OnboardingActivity.class);
        startActivity(intent);
        finish();
    }
}