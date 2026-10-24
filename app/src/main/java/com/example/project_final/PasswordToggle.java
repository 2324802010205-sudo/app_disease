package com.example.project_final;

import android.text.InputType;
import android.widget.EditText;
import android.widget.ImageView;

/** Show / hide toggle for a password field. */
final class PasswordToggle {
    private PasswordToggle() {}

    static void attach(EditText field, ImageView toggle) {
        toggle.setOnClickListener(v -> {
            boolean hidden = (field.getInputType() & InputType.TYPE_TEXT_VARIATION_PASSWORD) != 0;
            int type = hidden
                    ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                    : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD;
            field.setInputType(type);
            field.setSelection(field.getText().length());
            toggle.setImageResource(hidden ? R.drawable.ic_visibility : R.drawable.ic_visibility_off);
        });
    }
}
