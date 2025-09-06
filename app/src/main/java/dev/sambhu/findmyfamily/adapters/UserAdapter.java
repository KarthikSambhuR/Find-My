package dev.sambhu.findmyfamily.adapters;

import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.Date;
import java.util.List;

import dev.sambhu.findmyfamily.R;
import dev.sambhu.findmyfamily.models.User;

public class UserAdapter extends RecyclerView.Adapter<UserAdapter.UserViewHolder> {

    private final List<User> userList;
    private OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(User user);
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    public UserAdapter(List<User> userList) {
        this.userList = userList;
    }

    @NonNull
    @Override
    public UserViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_user, parent, false);
        return new UserViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull UserViewHolder holder, int position) {
        User user = userList.get(position);
        holder.bind(user);
    }

    @Override
    public int getItemCount() {
        return userList.size();
    }

    class UserViewHolder extends RecyclerView.ViewHolder {
        private final TextView displayNameTextView;
        private final TextView lastSeenTextView;
        private final ImageView profileImageView;

        UserViewHolder(@NonNull View itemView) {
            super(itemView);
            displayNameTextView = itemView.findViewById(R.id.display_name_text_view);
            lastSeenTextView = itemView.findViewById(R.id.last_seen_text_view);
            profileImageView = itemView.findViewById(R.id.profile_image_view);

            itemView.setOnClickListener(v -> {
                if (listener != null && getAdapterPosition() != RecyclerView.NO_POSITION) {
                    listener.onItemClick(userList.get(getAdapterPosition()));
                }
            });
        }

        void bind(User user) {
            displayNameTextView.setText(user.getDisplayName());

            // --- MODIFIED LOGIC ---
            if (user.getLastKnown() != null && user.getLastKnown().getUpdatedAt() != null) {
                Date lastSeenDate = user.getLastKnown().getUpdatedAt();
                long now = System.currentTimeMillis();
                long time = lastSeenDate.getTime();

                // If the difference is less than a minute, show "Just now"
                if (now - time < DateUtils.MINUTE_IN_MILLIS) {
                    lastSeenTextView.setText("Last seen: Just now");
                } else {
                    // Otherwise, show the relative time span
                    CharSequence relativeTime = DateUtils.getRelativeTimeSpanString(
                            time,
                            now,
                            DateUtils.MINUTE_IN_MILLIS);
                    lastSeenTextView.setText("Last seen: " + relativeTime);
                }
            } else {
                lastSeenTextView.setText("Last seen: N/A");
            }
            // --- END OF MODIFICATION ---

            if (user.getPhotoUrl() != null && !user.getPhotoUrl().isEmpty()) {
                Glide.with(itemView.getContext())
                        .load(user.getPhotoUrl())
                        .into(profileImageView);
            } else {
                profileImageView.setImageResource(R.drawable.ic_profile_placeholder);
            }
        }
    }
}