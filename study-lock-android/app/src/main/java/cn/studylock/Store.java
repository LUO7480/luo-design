package cn.studylock;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.provider.Settings;
import java.time.ZoneId;

final class Store {
    static SharedPreferences prefs(Context context) {
        return context.createDeviceProtectedStorageContext().getSharedPreferences("study-lock", Context.MODE_PRIVATE);
    }

    static int boot(Context context) {
        return Settings.Global.getInt(context.getContentResolver(), Settings.Global.BOOT_COUNT, -1);
    }

    static long end(Context context) {
        SharedPreferences prefs = prefs(context);
        long now = System.currentTimeMillis();
        long trial = prefs.getLong("trial_end", 0);
        if (trial > 0) {
            return SessionTimes.trialEnd(now, SystemClock.elapsedRealtime(), trial,
                    prefs.getLong("trial_elapsed", 0), prefs.getInt("trial_boot", -2) == boot(context));
        }
        if (!prefs.getBoolean("enabled", false)) return 0;
        if (SessionTimes.movedBackAfterDeadline(now, SystemClock.elapsedRealtime(),
                prefs.getLong("deadline_wall", 0), prefs.getLong("deadline_elapsed", Long.MAX_VALUE),
                prefs.getInt("deadline_boot", -2) == boot(context))) {
            fail(context, "设备时间发生变化，已自动解除。请检查时间后重新启用。");
            return 0;
        }
        return Schedule.activeEnd(now, ZoneId.systemDefault());
    }

    static void fail(Context context, String reason) {
        prefs(context).edit().putBoolean("enabled", false).putLong("trial_end", 0)
                .putLong("trial_elapsed", 0).putString("error", reason).commit();
    }
}
