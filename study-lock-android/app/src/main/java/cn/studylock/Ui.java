package cn.studylock;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

final class Ui {
    static final int INK = Color.rgb(29, 47, 39);
    static final int GREEN = Color.rgb(39, 105, 80);
    static final int PAPER = Color.rgb(243, 245, 239);
    static int dp(Activity activity, int value) { return Math.round(value * activity.getResources().getDisplayMetrics().density); }

    static LinearLayout column(Activity activity) {
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(activity, 26), dp(activity, 20), dp(activity, 26), dp(activity, 20));
        layout.setBackgroundColor(PAPER);
        layout.setOnApplyWindowInsetsListener((view, insets) -> {
            android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            view.setPadding(dp(activity, 26) + bars.left, dp(activity, 20) + bars.top,
                    dp(activity, 26) + bars.right, dp(activity, 20) + bars.bottom);
            return insets;
        });
        return layout;
    }

    static TextView text(Activity activity, LinearLayout parent, String value, int size, boolean bold) {
        TextView text = new TextView(activity);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(INK);
        text.setLineSpacing(dp(activity, 5), 1);
        if (bold) text.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(activity, 14);
        parent.addView(text, params);
        return text;
    }

    static Button button(Activity activity, LinearLayout parent, String label, boolean primary, View.OnClickListener listener) {
        Button button = new Button(activity);
        button.setText(label);
        button.setTextSize(16);
        button.setAllCaps(false);
        button.setTextColor(primary ? Color.WHITE : GREEN);
        GradientDrawable background = new GradientDrawable();
        background.setColor(primary ? GREEN : Color.WHITE);
        background.setCornerRadius(dp(activity, 15));
        button.setBackground(background);
        button.setPadding(dp(activity, 12), dp(activity, 10), dp(activity, 12), dp(activity, 10));
        button.setOnClickListener(listener);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(activity, 55));
        params.topMargin = dp(activity, 10);
        parent.addView(button, params);
        return button;
    }
}
