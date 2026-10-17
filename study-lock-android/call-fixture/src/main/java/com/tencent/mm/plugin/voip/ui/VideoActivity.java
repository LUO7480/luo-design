package com.tencent.mm.plugin.voip.ui;

public final class VideoActivity extends android.app.Activity {
    @Override public void onCreate(android.os.Bundle state) {
        super.onCreate(state);
        android.widget.TextView text = new android.widget.TextView(this);
        text.setText("CI simulated call surface");
        setContentView(text);
    }
}
