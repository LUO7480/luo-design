package cn.studylock;

import android.app.AppOpsManager;
import android.content.Context;
import android.os.Process;
import android.provider.Settings;

final class OverlayGrants {
    static boolean overlay(Context context) { return Settings.canDrawOverlays(context); }
    static boolean usage(Context context) {
        return context.getSystemService(AppOpsManager.class).unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.getPackageName()) == AppOpsManager.MODE_ALLOWED;
    }
    static boolean consented(Context context) { return Store.prefs(context).getBoolean("overlay_consent_v5", false); }
    static boolean ready(Context context) { return consented(context) && overlay(context) && usage(context); }
}
