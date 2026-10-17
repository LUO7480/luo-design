package cn.studylock;

import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.telecom.TelecomManager;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.Collections;
import java.util.Locale;
import java.util.Set;

public final class OverlayService extends Service {
    static final int NOTIFICATION_ID = 51;
    private static final String CHANNEL = "study-overlay-running-v1";
    private static volatile OverlayService instance;
    private static boolean appVisible;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ForegroundReader reader = new ForegroundReader();
    private Context windowContext;
    private WindowManager windows;
    private ScrollView overlay;
    private TextView countdown;
    private Set<String> permitted = Collections.emptySet();
    private volatile boolean attached;
    private volatile boolean renderingFailed;
    private long retryAt;
    private long observedEnd = -1;
    private final BroadcastReceiver screen = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            reader.reset();
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) hide();
            refreshSoon();
        }
    };

    static boolean connected() { return instance != null && !instance.renderingFailed; }
    static boolean running() { return instance != null; }
    static boolean covering() { return instance != null && instance.attached; }
    static void appVisible(boolean visible) { appVisible = visible; refreshSoon(); }
    static void refreshSoon() {
        OverlayService service = instance;
        if (service != null) {
            service.handler.removeCallbacks(service.tick);
            service.handler.post(service.tick);
        }
    }

    static void sync(Context context) {
        if (!Store.committed(context) || !OverlayGrants.ready(context) || !Policy.phonePermission(context)) {
            context.stopService(new Intent(context, OverlayService.class));
            return;
        }
        if (instance != null) { refreshSoon(); return; }
        try { context.startForegroundService(new Intent(context, OverlayService.class)); }
        catch (RuntimeException error) {
            Log.e("StudyLock", "Foreground overlay start rejected", error);
            Store.prefs(context).edit().putString("error", "后台启动被系统暂停，计时保留。请打开学习锁屏恢复。").commit();
        }
    }

    @Override public void onCreate() {
        super.onCreate();
        instance = this;
        NotificationManager notifications = getSystemService(NotificationManager.class);
        NotificationChannel channel = new NotificationChannel(CHANNEL, "学习运行状态", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("学习期间保持计时和遮罩运行；休息切换使用另一提醒渠道。");
        channel.setSound(null, null);
        notifications.createNotificationChannel(channel);
        PendingIntent show = PendingIntent.getActivity(this, 5, new Intent(this, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm).setContentTitle("学习计划运行中")
                .setContentText("每小时休息 10 分钟 · 电话与离线急救可用")
                .setContentIntent(show).setOngoing(true).setOnlyAlertOnce(true)
                .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE).build();
        if (Build.VERSION.SDK_INT >= 34) startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        else startForeground(NOTIFICATION_ID, notification);
        Display display = getSystemService(DisplayManager.class).getDisplay(Display.DEFAULT_DISPLAY);
        windowContext = createDisplayContext(display).createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null);
        windows = windowContext.getSystemService(WindowManager.class);
        permitted = Policy.permittedPackages(this);
        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        registerReceiver(screen, filter, Context.RECEIVER_NOT_EXPORTED);
        Store.prefs(this).edit().putString("service_status", "悬浮窗服务已连接").commit();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        Controller.reconcile(this, false);
        refreshSoon();
        return START_STICKY;
    }

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            long end = Store.end(OverlayService.this);
            if (observedEnd != end) {
                observedEnd = end;
                Controller.reconcile(OverlayService.this, false);
                end = Store.end(OverlayService.this);
                observedEnd = end;
            }
            if (!Store.committed(OverlayService.this)) { hide(); stopSelf(); return; }
            render(end);
            handler.removeCallbacks(this);
            boolean interactive = getSystemService(PowerManager.class).isInteractive();
            handler.postDelayed(this, end > 0 && interactive ? 250 : 1000);
        }
    };

    private void render(long end) {
        if (renderingFailed && SystemClock.elapsedRealtime() < retryAt) { hide(); return; }
        renderingFailed = false;
        try {
            boolean screenReady = getSystemService(PowerManager.class).isInteractive()
                    && !getSystemService(KeyguardManager.class).isKeyguardLocked();
            boolean grants = OverlayGrants.ready(this);
            boolean phone = Policy.phonePermission(this);
            if (end > 0 && screenReady && grants) reader.refresh(this);
            boolean calling = phone && getSystemService(TelecomManager.class).isInCall();
            String name = reader.surface.packageName();
            boolean cover = BlockingRules.cover(end, System.currentTimeMillis(), grants, phone, !screenReady,
                    CallRules.allowed(name, reader.surface.className(), calling), appVisible, permitted.contains(name));
            if (!cover) { hide(); return; }
            if (overlay == null) createOverlay();
            long seconds = Math.max(0, (end - System.currentTimeMillis() + 999) / 1000);
            countdown.setText(String.format(Locale.ROOT, "%02d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60));
            if (!attached) {
                WindowManager.LayoutParams params = new WindowManager.LayoutParams(-1, -1,
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT);
                params.gravity = Gravity.TOP | Gravity.START;
                params.setTitle("学习锁屏 · 专注遮罩");
                windows.addView(overlay, params);
                attached = true;
            }
        } catch (RuntimeException error) {
            Log.e("StudyLock", "Overlay render failed", error);
            renderingFailed = true;
            retryAt = SystemClock.elapsedRealtime() + 2000;
            hide();
            overlay = null;
            countdown = null;
            Store.prefs(this).edit().putString("error", "悬浮窗暂不可用，计时保留。正在重试，请检查悬浮窗和使用情况访问权限。").commit();
        }
    }

    private void createOverlay() {
        overlay = new ScrollView(windowContext);
        overlay.setFillViewport(true);
        overlay.setBackgroundColor(Ui.PAPER);
        LinearLayout content = Ui.column(windowContext);
        content.setGravity(Gravity.CENTER);
        content.setClickable(true);
        overlay.addView(content);
        TextView title = Ui.text(windowContext, content, "现在是学习时间", 26, true);
        title.setGravity(Gravity.CENTER);
        countdown = Ui.text(windowContext, content, "", 42, true);
        countdown.setGravity(Gravity.CENTER);
        Ui.text(windowContext, content, "到点自动休息；可正常息屏", 16, false).setGravity(Gravity.CENTER);
        Ui.button(windowContext, content, "拨打电话", true, view -> {
            try {
                startActivity(new Intent(Intent.ACTION_DIAL, android.net.Uri.parse("tel:")).setPackage(Policy.dialer(this))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (RuntimeException error) {
                Store.fail(this, "电话界面未能打开，已解除限制。请检查默认电话应用。");
                Controller.reconcile(this, false);
            }
        });
        Ui.button(windowContext, content, "离线急救解锁", false, view -> {
            try {
                startActivity(new Intent(this, FocusActivity.class).putExtra("recovery", true)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP));
            } catch (RuntimeException error) {
                hide();
                Store.prefs(this).edit().putString("error", "请点击运行通知打开学习锁屏，再进入离线急救。").commit();
            }
        });
        Ui.text(windowContext, content, "微信只放行已识别的通话页；聊天、桌面仍受限。", 13, false);
    }

    private void hide() {
        if (!attached) return;
        try { windows.removeViewImmediate(overlay); } catch (RuntimeException ignored) {}
        attached = false;
    }

    @Override public IBinder onBind(Intent intent) { return null; }
    @Override protected void dump(java.io.FileDescriptor fd, java.io.PrintWriter out, String[] args) {
        out.println("overlayAttached=" + attached + " renderFailed=" + renderingFailed + " appVisible=" + appVisible);
        out.println("foregroundPackage=" + reader.surface.packageName() + " foregroundClass=" + reader.surface.className());
        out.println("overlayGrant=" + OverlayGrants.overlay(this) + " usageGrant=" + OverlayGrants.usage(this));
    }
    @Override public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        hide();
        unregisterReceiver(screen);
        stopForeground(STOP_FOREGROUND_REMOVE);
        if (instance == this) instance = null;
        // A disrupted rehearsal cannot certify the consumer lock.
        if (Store.trialEnd(this) > 0) Store.prefs(this).edit().putBoolean("trial_interrupted", true).commit();
        super.onDestroy();
    }
}
