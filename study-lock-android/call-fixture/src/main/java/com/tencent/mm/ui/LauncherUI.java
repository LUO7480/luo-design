package com.tencent.mm.ui;

public final class LauncherUI extends android.app.Activity {
    @Override public void onCreate(android.os.Bundle state) {
        super.onCreate(state);
        android.widget.TextView text = new android.widget.TextView(this);
        text.setText("CI simulated chat surface");
        setContentView(text);
    }
}
