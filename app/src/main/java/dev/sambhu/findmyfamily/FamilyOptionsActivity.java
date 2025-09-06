package dev.sambhu.findmyfamily;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.Collections;
import java.util.UUID;

import dev.sambhu.findmyfamily.models.Family;

public class FamilyOptionsActivity extends AppCompatActivity {

    private static final String TAG = "FamilyOptionsActivity";
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private FirebaseUser firebaseUser;
    private ProgressBar mainProgressBar; // The progress bar on the main activity layout

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_family_options);

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();
        firebaseUser = mAuth.getCurrentUser();

        if (firebaseUser == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        Button createFamilyButton = findViewById(R.id.create_family_button);
        Button joinFamilyButton = findViewById(R.id.join_family_button);
        mainProgressBar = findViewById(R.id.family_options_progress_bar);

        createFamilyButton.setOnClickListener(v -> showFamilyActionDialog(false)); // isJoining = false
        joinFamilyButton.setOnClickListener(v -> showFamilyActionDialog(true));  // isJoining = true
    }

    private void showFamilyActionDialog(boolean isJoining) {
        // Use our custom rounded theme from themes.xml
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.RoundedDialogTheme);
        View view = getLayoutInflater().inflate(R.layout.dialog_family_action, null);
        builder.setView(view);

        TextView titleTextView = view.findViewById(R.id.dialog_title);
        EditText inputEditText = view.findViewById(R.id.dialog_input);
        ProgressBar dialogProgressBar = view.findViewById(R.id.dialog_progress_bar);

        // Configure the dialog for either "Join" or "Create"
        if (isJoining) {
            titleTextView.setText("Join a Family");
            inputEditText.setHint("Enter Family Code");
        } else {
            titleTextView.setText("Create a Family");
            inputEditText.setHint("Enter Family Name");
        }

        builder.setPositiveButton(isJoining ? "Join" : "Create", (dialog, which) -> {
            // We override this later to prevent the dialog from auto-closing
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());

        AlertDialog dialog = builder.create();
        dialog.show();

        // Prevent the dialog from closing when the positive button is clicked
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String inputText = inputEditText.getText().toString().trim();
            if (TextUtils.isEmpty(inputText)) {
                Toast.makeText(this, "Input cannot be empty.", Toast.LENGTH_SHORT).show();
                return;
            }

            // Hide controls and show progress bar inside the dialog
            inputEditText.setVisibility(View.GONE);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setVisibility(View.GONE);
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setVisibility(View.GONE);
            dialogProgressBar.setVisibility(View.VISIBLE);

            if (isJoining) {
                joinFamily(inputText, dialog);
            } else {
                createFamily(inputText, dialog);
            }
        });

        // Resize the dialog to look good
        resizeDialog(dialog);
    }

    private void createFamily(String familyName, AlertDialog dialog) {
        String familyCode = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        String adminId = firebaseUser.getUid();
        DocumentReference familyRef = db.collection("families").document();

        Family newFamily = new Family(familyRef.getId(), familyName, adminId, Collections.singletonList(adminId), familyCode);

        // Use a transaction to ensure both operations succeed or fail together
        db.runTransaction(transaction -> {
            transaction.set(familyRef, newFamily);
            DocumentReference userRef = db.collection("users").document(adminId);
            transaction.update(userRef, "familyId", familyRef.getId());
            return null; // Transaction success
        }).addOnSuccessListener(aVoid -> {
            dialog.dismiss();
            navigateToMain();
        }).addOnFailureListener(e -> {
            dialog.dismiss();
            Log.w(TAG, "Transaction failure.", e);
            Toast.makeText(this, "Failed to create family.", Toast.LENGTH_SHORT).show();
        });
    }

    private void joinFamily(String familyCode, AlertDialog dialog) {
        db.collection("families")
                .whereEqualTo("code", familyCode.toUpperCase()) // Search for uppercase code
                .limit(1)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (queryDocumentSnapshots != null && !queryDocumentSnapshots.isEmpty()) {
                        DocumentReference familyRef = queryDocumentSnapshots.getDocuments().get(0).getReference();
                        addUserToFamily(familyRef, dialog);
                    } else {
                        dialog.dismiss();
                        Toast.makeText(this, "Invalid family code.", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    dialog.dismiss();
                    Log.w(TAG, "Error querying for family code.", e);
                    Toast.makeText(this, "Error finding family.", Toast.LENGTH_SHORT).show();
                });
    }

    private void addUserToFamily(DocumentReference familyRef, AlertDialog dialog) {
        DocumentReference userRef = db.collection("users").document(firebaseUser.getUid());

        // Use a transaction to ensure both operations succeed or fail together
        db.runTransaction(transaction -> {
            transaction.update(familyRef, "members", FieldValue.arrayUnion(firebaseUser.getUid()));
            transaction.update(userRef, "familyId", familyRef.getId());
            return null; // Transaction success
        }).addOnSuccessListener(aVoid -> {
            dialog.dismiss();
            navigateToMain();
        }).addOnFailureListener(e -> {
            dialog.dismiss();
            Log.w(TAG, "Transaction failure.", e);
            Toast.makeText(this, "Failed to join family.", Toast.LENGTH_SHORT).show();
        });
    }

    private void navigateToMain() {
        Intent intent = new Intent(FamilyOptionsActivity.this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    private void resizeDialog(AlertDialog dialog) {
        Window window = dialog.getWindow();
        if (window != null) {
            WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams();
            layoutParams.copyFrom(window.getAttributes());
            layoutParams.width = (int) (getResources().getDisplayMetrics().widthPixels * 0.85f);
            window.setAttributes(layoutParams);
        }
    }
}