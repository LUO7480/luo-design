package cn.studylock;

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;

public final class AdminReceiver extends DeviceAdminReceiver {
    @Override public void onEnabled(Context context, Intent intent) { Controller.reconcile(context, false); }
}
