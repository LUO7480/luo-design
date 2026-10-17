package cn.studylock;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.UserManager;

public final class AlarmReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if ("cn.studylock.DEADLINE".equals(intent.getAction())
                && System.currentTimeMillis() < Store.prefs(context).getLong("deadline_wall", 0)) {
            Store.fail(context, "设备时间发生变化，已自动解除。请检查时间后重新启用。");
        }
        if (!context.getSystemService(UserManager.class).isUserUnlocked()) {
            // Credential-protected phone services may be unavailable before first unlock.
            // Still release expired policy; BOOT_COMPLETED enforces after credential unlock.
            try {
                long end = Store.end(context);
                if (end == 0) {
                    if (Store.committed(context)) Policy.pause(context); else Policy.release(context);
                }
                Scheduler.schedule(context, end);
            } catch (RuntimeException error) {
                Store.fail(context, "重启后定时权限不可用，请解锁手机后检查。");
                Policy.release(context);
            }
            return;
        }
        Controller.reconcile(context, true);
    }
}
