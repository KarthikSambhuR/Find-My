package dev.sambhu.findmyfamily.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

import dev.sambhu.findmyfamily.R;
import dev.sambhu.findmyfamily.models.PhoneNumber;

public class PhoneNumberListAdapter extends ArrayAdapter<PhoneNumber> {

    public PhoneNumberListAdapter(Context context, List<PhoneNumber> phoneNumbers) {
        super(context, 0, phoneNumbers);
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        if (convertView == null) {
            convertView = LayoutInflater.from(getContext()).inflate(R.layout.item_phone_number_modal, parent, false);
        }

        PhoneNumber phoneNumber = getItem(position);

        TextView numberTextView = convertView.findViewById(R.id.phone_number_text);
        TextView aliasTextView = convertView.findViewById(R.id.phone_alias_text);

        if (phoneNumber != null) {
            numberTextView.setText(phoneNumber.getNumber());
            // Show the alias if it exists, otherwise hide the text view
            if (phoneNumber.getAlias() != null && !phoneNumber.getAlias().isEmpty()) {
                aliasTextView.setText(phoneNumber.getAlias());
                aliasTextView.setVisibility(View.VISIBLE);
            } else {
                aliasTextView.setVisibility(View.GONE);
            }
        }

        return convertView;
    }
}
