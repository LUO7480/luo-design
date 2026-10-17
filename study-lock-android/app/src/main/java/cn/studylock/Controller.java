package cn.studylock;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

final class Controller {
    static long reconcile(Context context, boolean launch) {
        try {
            SharedPreferences prefs = Store.prefs(context);
            Policy.release(context);
            boolean armed = prefs.getBoolean("enabled", false) || Store.trialEnd(context) > 0;
            if (armed && !RecoveryVault.ready(context)) {
                throw new IllegalStateException("请先托管离线急救码，再启用锁定。");
            }
            if (prefs.getBoolean("enabled", false) && !Policy.validationKey(context).equals(prefs.getString("verified_for", ""))) {
                throw new IllegalStateException("版本、系统或拨号应用已变化。原有急救码保留，请完成授权后重新试锁。");
            }
            long end = Store.end(context);
            if (end == 0) {
                BreakReminder.transition(context, Store.committed(context) ? "rest" : "off");
                if (prefs.getLong("trial_end", 0) > 0 && Store.trialEnd(context) == 0) {
                    boolean sameBoot = prefs.getInt("trial_boot", -2) == Store.boot(context);
                    prefs.edit().putLong("trial_end", 0).putLong("trial_elapsed", 0)
                            .putBoolean("trial_completed", sameBoot && OverlayService.connected()
                                    && !prefs.getBoolean("trial_interrupted", false))
                            .putString("trial_for", sameBoot ? Policy.validationKey(context) : "").commit();
                }
                Scheduler.schedule(context, 0);
                OverlayService.sync(context);
                return 0;
            }
            // Arm both release alarms before displaying the application overlay.
            Scheduler.schedule(context, end);
            OverlayService.sync(context);
            boolean capable = OverlayService.connected() && OverlayGrants.ready(context)
                    && Policy.phonePermission(context);
            if (capable) BreakReminder.transition(context, "lock");
            // Reopening our activity and service interruptions do not cancel the stored session.
            // The screen reports an unavailable service instead of claiming other apps are blocked.
            OverlayService.refreshSoon();
            if (launch && OverlayGrants.ready(context) && Policy.phonePermission(context)) context.startActivity(new Intent(context, FocusActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP));
            return launch && (!OverlayGrants.ready(context) || !Policy.phonePermission(context)) ? 0 : end;
        } catch (RuntimeException error) {
            Log.e("StudyLock", "Lock failed; restoring access", error);
            Store.fail(context, error.getMessage() == null ? "系统拒绝锁定，已停止计划。" : error.getMessage());
            try { Policy.release(context); } catch (RuntimeException releaseError) { Log.e("StudyLock", "Policy release failed", releaseError); }
            try { Scheduler.schedule(context, 0); } catch (RuntimeException ignored) {}
            OverlayService.refreshSoon();
            return 0;
        }
    }
}
