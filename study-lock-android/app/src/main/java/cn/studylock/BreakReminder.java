package cn.studylock;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.provider.Settings;

final class BreakReminder {
    private static final String CHANNEL = "study-break-alarm-v1";
    static void channel(Context context) {
        NotificationChannel channel = new NotificationChannel(CHANNEL, "学习与休息闹钟", NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("休息开始与结束时提醒；切换由本地闹钟执行，无需联网。");
        channel.setSound(Settings.System.DEFAULT_ALARM_ALERT_URI, new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
        channel.enableVibration(true);
        context.getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    static boolean allowed(Context context) {
        channel(context);
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        NotificationChannel channel = manager.getNotificationChannel(CHANNEL);
        return context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                && manager.areNotificationsEnabled() && channel.getImportance() != NotificationManager.IMPORTANCE_NONE;
    }

    static void transition(Context context, String phase) {
        String old = Store.prefs(context).getString("last_phase", "off");
        Store.prefs(context).edit().putString("last_phase", phase).commit();
        if (old.equals(phase) || old.equals("off") && phase.equals("off") || !allowed(context)) return;
        boolean rest = phase.equals("rest");
        String title = rest ? "休息开始，屏幕已恢复" : phase.equals("lock") ? (old.equals("off") ? "学习开始" : "休息结束，继续学习") : "本次学习已结束";
        String text = rest ? (Store.trialEnd(context) > 0 ? "试运行休息 30 秒，之后自动锁回。" : "每小时最后 10 分钟可使用手机；到整点自动锁回。")
                : phase.equals("lock") ? "电话与离线急救入口仍然可用。" : "屏幕限制已解除。";
        PendingIntent show = PendingIntent.getActivity(context, 4, new Intent(context, MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        context.getSystemService(NotificationManager.class).notify(50, new Notification.Builder(context, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm).setContentTitle(title).setContentText(text)
                .setCategory(Notification.CATEGORY_ALARM).setContentIntent(show).setAutoCancel(true).build());
    }
}
