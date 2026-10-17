package cn.studylock;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.SystemClock;
import java.time.ZoneId;

final class Scheduler {
    static PendingIntent pending(Context context, int id) {
        return PendingIntent.getBroadcast(context, id, new Intent(context, AlarmReceiver.class)
                .setAction(id == 1 ? "cn.studylock.BOUNDARY" : "cn.studylock.DEADLINE"), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static boolean exact(Context context) { return context.getSystemService(AlarmManager.class).canScheduleExactAlarms(); }

    static void schedule(Context context, long end) {
        AlarmManager manager = context.getSystemService(AlarmManager.class);
        manager.cancel(pending(context, 1));
        manager.cancel(pending(context, 2));
        SharedPreferences prefs = Store.prefs(context);
        if (end == 0 && !prefs.getBoolean("enabled", false) && Store.trialEnd(context) == 0) return;
        if (!exact(context)) throw new IllegalStateException("缺少“闹钟和提醒”权限，已停止锁定。");
        long now = System.currentTimeMillis();
        long next = end > 0 ? end : Store.nextBoundary(context);
        PendingIntent show = PendingIntent.getActivity(context, 3, new Intent(context, MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        manager.setAlarmClock(new AlarmManager.AlarmClockInfo(next, show), pending(context, 1));
        if (end > 0) {
            long deadline = prefs.getLong("deadline_elapsed", 0);
            if (prefs.getLong("deadline_wall", 0) != end || prefs.getInt("deadline_boot", -2) != Store.boot(context)) {
                deadline = SystemClock.elapsedRealtime() + Math.max(1, end - now);
                prefs.edit().putLong("deadline_wall", end).putLong("deadline_elapsed", deadline)
                        .putInt("deadline_boot", Store.boot(context)).commit();
            }
            // Elapsed time provides a second release path if the wall clock moves backwards.
            manager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, deadline, pending(context, 2));
        }
    }
}
