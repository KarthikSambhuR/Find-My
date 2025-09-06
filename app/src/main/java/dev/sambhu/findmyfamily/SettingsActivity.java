package dev.sambhu.findmyfamily;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;

import dev.sambhu.findmyfamily.models.Family;
import dev.sambhu.findmyfamily.models.PhoneNumber;
import dev.sambhu.findmyfamily.models.User;

public class SettingsActivity extends AppCompatActivity {

    private static final String TAG = "SettingsActivity";

    private RecyclerView phoneNumbersRecyclerView;
    private PhoneNumberSettingsAdapter adapter;
    private List<PhoneNumber> phoneNumbers = new ArrayList<>();
    private TextView familyCodeTextView;
    private TextView displayNameTextView;

    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private DocumentReference userRef;
    private User currentUser;
    private Family currentFamily;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        FirebaseUser firebaseUser = mAuth.getCurrentUser();

        if (firebaseUser == null) {
            finish();
            return;
        }
        userRef = db.collection("users").document(firebaseUser.getUid());

        setupToolbar();
        initViews();
        loadUserData();
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.settings_toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void initViews() {
        displayNameTextView = findViewById(R.id.display_name_text_view);
        ImageButton editDisplayNameButton = findViewById(R.id.edit_display_name_button);
        familyCodeTextView = findViewById(R.id.family_id_text_view);
        ImageButton copyIdButton = findViewById(R.id.copy_id_button);
        Button addNumberButton = findViewById(R.id.add_number_button);
        Button leaveFamilyButton = findViewById(R.id.leave_family_button);
        Button logoutButton = findViewById(R.id.logout_button);

        phoneNumbersRecyclerView = findViewById(R.id.phone_numbers_recycler_view);
        phoneNumbersRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new PhoneNumberSettingsAdapter(phoneNumbers, this::showEditDialog);
        phoneNumbersRecyclerView.setAdapter(adapter);

        addNumberButton.setOnClickListener(v -> showEditDialog(null)); // Pass null to add new
        editDisplayNameButton.setOnClickListener(v -> showEditDisplayNameDialog());
        copyIdButton.setOnClickListener(v -> copyFamilyCode());
        leaveFamilyButton.setOnClickListener(v -> showLeaveFamilyDialog());
        logoutButton.setOnClickListener(v -> logoutUser());
    }

    private void loadUserData() {
        userRef.get().addOnSuccessListener(documentSnapshot -> {
            if (documentSnapshot.exists()) {
                currentUser = documentSnapshot.toObject(User.class);
                if (currentUser != null) {
                    phoneNumbers.clear();
                    if (currentUser.getPhoneNumbers() != null) {
                        phoneNumbers.addAll(currentUser.getPhoneNumbers());
                    }
                    adapter.notifyDataSetChanged();

                    if (currentUser.getFamilyId() != null && !currentUser.getFamilyId().isEmpty()) {
                        loadFamilyData(currentUser.getFamilyId());
                    } else {
                        currentFamily = null;
                        updateUi();
                    }
                }
            }
        }).addOnFailureListener(e -> Log.e(TAG, "Error loading user data", e));
    }

    private void loadFamilyData(String familyId) {
        db.collection("families").document(familyId).get()
                .addOnSuccessListener(documentSnapshot -> {
                    currentFamily = documentSnapshot.exists() ? documentSnapshot.toObject(Family.class) : null;
                    updateUi();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error loading family data", e);
                    currentFamily = null;
                    updateUi();
                });
    }

    private void updateUi() {
        if (currentFamily != null && currentFamily.getCode() != null) {
            familyCodeTextView.setText(currentFamily.getCode());
        } else {
            familyCodeTextView.setText("Not in a family");
        }

        // Update display name
        if (currentUser != null && currentUser.getDisplayName() != null && !currentUser.getDisplayName().isEmpty()) {
            displayNameTextView.setText(currentUser.getDisplayName());
        } else {
            displayNameTextView.setText("No name set");
        }
    }

    private void showEditDisplayNameDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.RoundedDialogTheme);

        View dialogView = getLayoutInflater().inflate(R.layout.dialog_edit_display_name, null);
        final EditText nameInput = dialogView.findViewById(R.id.edit_display_name_input);
        builder.setView(dialogView);

        builder.setTitle("Edit Display Name");

        if (currentUser != null && currentUser.getDisplayName() != null) {
            nameInput.setText(currentUser.getDisplayName());
        }

        builder.setPositiveButton("Save", (dialog, which) -> {
            String newName = nameInput.getText().toString().trim();
            if (TextUtils.isEmpty(newName)) {
                Toast.makeText(this, "Name cannot be empty", Toast.LENGTH_SHORT).show();
                return;
            }
            updateDisplayName(newName);
        });
        builder.setNegativeButton("Cancel", null);

        AlertDialog dialog = builder.create();
        dialog.show();
        resizeDialog(dialog);
    }

    private void updateDisplayName(String newName) {
        if (userRef != null) {
            userRef.update("displayName", newName)
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(this, "Display name updated!", Toast.LENGTH_SHORT).show();
                        if (currentUser != null) {
                            currentUser.setDisplayName(newName);
                            updateUi();
                        }
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(this, "Failed to update name.", Toast.LENGTH_SHORT).show();
                        Log.e(TAG, "Error updating display name", e);
                    });
        }
    }

    private void showEditDialog(final PhoneNumber phoneNumberToEdit) {
        final boolean isEditing = phoneNumberToEdit != null;
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.RoundedDialogTheme);

        View dialogView = getLayoutInflater().inflate(R.layout.dialog_edit_phone, null);
        final EditText numberInput = dialogView.findViewById(R.id.edit_phone_number);
        final EditText aliasInput = dialogView.findViewById(R.id.edit_phone_alias);
        builder.setView(dialogView);

        // This is a subtle but important detail. We set a title on the builder,
        // which uses the dialog's default title text style.
        builder.setTitle(isEditing ? "Edit Phone Number" : "Add Phone Number");

        if (isEditing) {
            numberInput.setText(phoneNumberToEdit.getNumber());
            numberInput.setEnabled(false);
            aliasInput.setText(phoneNumberToEdit.getAlias());
        }

        builder.setPositiveButton("Save", (dialog, which) -> {
            String number = numberInput.getText().toString().trim();
            String alias = aliasInput.getText().toString().trim();
            if (TextUtils.isEmpty(number)) {
                Toast.makeText(this, "Number cannot be empty", Toast.LENGTH_SHORT).show();
                return;
            }
            if (isEditing) {
                phoneNumberToEdit.setAlias(alias);
            } else {
                phoneNumbers.add(new PhoneNumber(number, alias));
            }
            savePhoneNumbersToFirestore();
        });
        builder.setNegativeButton("Cancel", null);

        if (isEditing) {
            builder.setNeutralButton("Delete", (dialog, which) -> {
                phoneNumbers.remove(phoneNumberToEdit);
                savePhoneNumbersToFirestore();
            });
        }
        AlertDialog dialog = builder.create();
        dialog.show();
        resizeDialog(dialog);
    }

    private void showLeaveFamilyDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.RoundedDialogTheme);
        View view = getLayoutInflater().inflate(R.layout.dialog_confirmation, null);
        builder.setView(view);

        TextView title = view.findViewById(R.id.dialog_title);
        TextView message = view.findViewById(R.id.dialog_message);

        title.setText("Leave Family");
        message.setText("Are you sure you want to leave your family? This action cannot be undone.");

        builder.setPositiveButton("Leave", (dialog, which) -> leaveFamily());
        builder.setNegativeButton("Cancel", null);

        AlertDialog dialog = builder.create();
        dialog.show();
        resizeDialog(dialog);
    }

    private void savePhoneNumbersToFirestore() {
        if (userRef != null) {
            userRef.update("phoneNumbers", phoneNumbers)
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(this, "Phone numbers updated!", Toast.LENGTH_SHORT).show();
                        adapter.notifyDataSetChanged();
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(this, "Failed to update numbers.", Toast.LENGTH_SHORT).show();
                        loadUserData(); // Revert changes on failure
                    });
        }
    }

    private void leaveFamily() {
        // ... leaveFamily logic remains the same ...
        if (currentUser == null || currentUser.getFamilyId() == null) {
            Toast.makeText(this, "You are not in a family.", Toast.LENGTH_SHORT).show();
            return;
        }

        DocumentReference familyRef = db.collection("families").document(currentUser.getFamilyId());

        db.runTransaction(transaction -> {
            Family family = transaction.get(familyRef).toObject(Family.class);
            if (family == null) {
                transaction.update(userRef, "familyId", null);
                return null;
            }

            if (family.getAdminId().equals(userRef.getId()) && family.getMembers().size() == 1) {
                transaction.delete(familyRef);
            } else {
                transaction.update(familyRef, "members", FieldValue.arrayRemove(userRef.getId()));
            }

            transaction.update(userRef, "familyId", null);
            return null;
        }).addOnSuccessListener(aVoid -> {
            Toast.makeText(this, "You have left the family.", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(SettingsActivity.this, FamilyOptionsActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        }).addOnFailureListener(e -> {
            Toast.makeText(this, "Failed to leave family.", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Error leaving family", e);
        });
    }

    private void logoutUser() {
        // ... logoutUser logic remains the same ...
        mAuth.signOut();
        Intent intent = new Intent(SettingsActivity.this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void copyFamilyCode() {
        // ... copyFamilyCode logic remains the same ...
        String familyCode = familyCodeTextView.getText().toString();
        if (!familyCode.isEmpty() && !"Not in a family".equals(familyCode)) {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("Family Code", familyCode);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, "Family Code copied!", Toast.LENGTH_SHORT).show();
        }
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


/**
 * The RecyclerView Adapter for the settings screen.
 * This class does not need to be changed.
 */
class PhoneNumberSettingsAdapter extends RecyclerView.Adapter<PhoneNumberSettingsAdapter.ViewHolder> {

    private final List<PhoneNumber> phoneNumbers;
    private final OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(PhoneNumber phoneNumber);
    }

    public PhoneNumberSettingsAdapter(List<PhoneNumber> phoneNumbers, OnItemClickListener listener) {
        this.phoneNumbers = phoneNumbers;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_phone_number_settings, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        PhoneNumber phoneNumber = phoneNumbers.get(position);
        holder.bind(phoneNumber, listener);
    }

    @Override
    public int getItemCount() {
        return phoneNumbers.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView numberTextView;
        TextView aliasTextView;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            numberTextView = itemView.findViewById(R.id.phone_number_text);
            aliasTextView = itemView.findViewById(R.id.phone_alias_text);
        }

        public void bind(final PhoneNumber phoneNumber, final OnItemClickListener listener) {
            numberTextView.setText(phoneNumber.getNumber());
            if (phoneNumber.getAlias() != null && !phoneNumber.getAlias().isEmpty()) {
                aliasTextView.setText(phoneNumber.getAlias());
                aliasTextView.setVisibility(View.VISIBLE);
            } else {
                aliasTextView.setVisibility(View.GONE);
            }
            itemView.setOnClickListener(v -> listener.onItemClick(phoneNumber));
        }
    }
}