package dev.sambhu.findmyfamily;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;

import dev.sambhu.findmyfamily.models.PhoneNumber;
import dev.sambhu.findmyfamily.models.User;

public class OnboardingActivity extends AppCompatActivity {

    private EditText phoneNumberEditText;
    private EditText phoneAliasEditText; // New field for alias
    private ProgressBar progressBar;
    private RecyclerView phoneNumbersRecyclerView;
    private OnboardingPhoneNumberAdapter adapter;

    private List<PhoneNumber> phoneNumbers = new ArrayList<>();
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_onboarding);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Initialize new views
        phoneNumberEditText = findViewById(R.id.phone_number_edit_text);
        phoneAliasEditText = findViewById(R.id.phone_alias_edit_text);
        phoneNumbersRecyclerView = findViewById(R.id.phone_numbers_recycler_view);
        Button addPhoneNumberButton = findViewById(R.id.add_phone_number_button);
        Button continueButton = findViewById(R.id.continue_button);
        progressBar = findViewById(R.id.onboarding_progress_bar);

        setupRecyclerView();

        addPhoneNumberButton.setOnClickListener(v -> addPhoneNumber());
        continueButton.setOnClickListener(v -> saveUserAndContinue());
    }

    private void setupRecyclerView() {
        adapter = new OnboardingPhoneNumberAdapter(phoneNumbers, position -> {
            phoneNumbers.remove(position);
            adapter.notifyItemRemoved(position);
            adapter.notifyItemRangeChanged(position, phoneNumbers.size());
        });
        phoneNumbersRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        phoneNumbersRecyclerView.setAdapter(adapter);
    }

    private void addPhoneNumber() {
        String numberStr = phoneNumberEditText.getText().toString().trim();
        String aliasStr = phoneAliasEditText.getText().toString().trim();

        if (TextUtils.isEmpty(numberStr)) {
            Toast.makeText(this, "Phone number cannot be empty.", Toast.LENGTH_SHORT).show();
            return;
        }

        boolean alreadyExists = false;
        for (PhoneNumber pn : phoneNumbers) {
            if (pn.getNumber().equals(numberStr)) {
                alreadyExists = true;
                break;
            }
        }

        if (alreadyExists) {
            Toast.makeText(this, "This number has already been added.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Add the new number with its alias
        phoneNumbers.add(new PhoneNumber(numberStr, aliasStr));
        adapter.notifyItemInserted(phoneNumbers.size() - 1);

        // Clear input fields
        phoneNumberEditText.setText("");
        phoneAliasEditText.setText("");
        phoneNumberEditText.requestFocus();
    }

    private void saveUserAndContinue() {
        if (phoneNumbers.isEmpty()) {
            Toast.makeText(this, "Please add at least one phone number.", Toast.LENGTH_SHORT).show();
            return;
        }

        FirebaseUser firebaseUser = mAuth.getCurrentUser();
        if (firebaseUser == null) {
            Toast.makeText(this, "Not logged in!", Toast.LENGTH_SHORT).show();
            return;
        }

        progressBar.setVisibility(View.VISIBLE);

        User newUser = new User();
        newUser.setUid(firebaseUser.getUid());
        newUser.setDisplayName(firebaseUser.getDisplayName());
        newUser.setPhotoUrl(firebaseUser.getPhotoUrl() != null ? firebaseUser.getPhotoUrl().toString() : null);
        newUser.setPhoneNumbers(phoneNumbers); // This now includes aliases

        db.collection("users").document(firebaseUser.getUid()).set(newUser)
                .addOnSuccessListener(aVoid -> {
                    progressBar.setVisibility(View.GONE);
                    Intent intent = new Intent(OnboardingActivity.this, FamilyOptionsActivity.class);
                    startActivity(intent);
                    finish();
                })
                .addOnFailureListener(e -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(OnboardingActivity.this, "Failed to save data.", Toast.LENGTH_SHORT).show();
                });
    }
}

class OnboardingPhoneNumberAdapter extends RecyclerView.Adapter<OnboardingPhoneNumberAdapter.ViewHolder> {

    private final List<PhoneNumber> phoneNumbers;
    private final OnRemoveClickListener listener;

    public interface OnRemoveClickListener {
        void onRemoveClick(int position);
    }

    public OnboardingPhoneNumberAdapter(List<PhoneNumber> phoneNumbers, OnRemoveClickListener listener) {
        this.phoneNumbers = phoneNumbers;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_onboarding_phone_number, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        PhoneNumber phoneNumber = phoneNumbers.get(position);
        holder.bind(phoneNumber);
    }

    @Override
    public int getItemCount() {
        return phoneNumbers.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        TextView numberTextView;
        TextView aliasTextView;
        ImageButton removeButton;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            numberTextView = itemView.findViewById(R.id.phone_number_text);
            aliasTextView = itemView.findViewById(R.id.phone_alias_text);
            removeButton = itemView.findViewById(R.id.remove_button);

            removeButton.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION) {
                    listener.onRemoveClick(position);
                }
            });
        }

        public void bind(final PhoneNumber phoneNumber) {
            numberTextView.setText(phoneNumber.getNumber());
            if (phoneNumber.getAlias() != null && !phoneNumber.getAlias().isEmpty()) {
                aliasTextView.setText(phoneNumber.getAlias());
                aliasTextView.setVisibility(View.VISIBLE);
            } else {
                aliasTextView.setVisibility(View.GONE);
            }
        }
    }
}