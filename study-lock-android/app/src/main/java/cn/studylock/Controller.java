package cn.studylock;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

final class Controller {
    static long reconcile(Context context, boolean launch) {
        try {
            SharedPreferences prefs = Store.prefs(context);
            if ((prefs.getBoolean("enabled", false) || prefs.getLong("trial_end", 0) > 0) && !Policy.owner(context)) {
                throw new IllegalStateException("设备管理权限不可用，已停止锁定。");
            }
            if (prefs.getBoolean("enabled", false) && !Policy.validationKey(context).equals(prefs.getString("verified_for", ""))) {
                throw new IllegalStateException("系统或拨号应用已变化，请重新试锁并验证电话。");
            }
            long end = Store.end(context);
            if (end == 0) {
                Policy.release(context);
                if (prefs.getLong("trial_end", 0) > 0) {
                    boolean sameBoot = prefs.getInt("trial_boot", -2) == Store.boot(context);
                    prefs.edit().putLong("trial_end", 0).putLong("trial_elapsed", 0)
                            .putBoolean("trial_completed", sameBoot)
                            .putString("trial_for", sameBoot ? Policy.validationKey(context) : "").commit();
                }
                Scheduler.schedule(context, 0);
                return 0;
            }
            // Arm both release alarms before changing any system policy.
            Scheduler.schedule(context, end);
            Policy.prepare(context);
            if (launch) {
                // Show behind the existing keyguard. FocusActivity enters lock task only after unlock.
                context.startActivity(new Intent(context, FocusActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP));
            }
            return end;
        } catch (RuntimeException error) {
            Log.e("StudyLock", "Lock failed; restoring access", error);
            Store.fail(context, error.getMessage() == null ? "系统拒绝锁定，已停止计划。" : error.getMessage());
            try { Policy.release(context); } catch (RuntimeException releaseError) { Log.e("StudyLock", "Policy release failed", releaseError); }
            try { Scheduler.schedule(context, 0); } catch (RuntimeException ignored) {}
            return 0;
        }
    }
}
