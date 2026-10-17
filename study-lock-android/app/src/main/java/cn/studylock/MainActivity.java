package cn.studylock;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.Locale;

public final class MainActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView status;
    private String displayed = "";
    private boolean resumed;
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (Store.end(MainActivity.this) > 0 && permissionsReady()) {
                if (Controller.reconcile(MainActivity.this, true) > 0) return;
            }
            String current = signature();
            if (!current.equals(displayed)) showHome();
            if (status != null && Store.committed(MainActivity.this)) {
                long seconds = Math.max(0, (Store.nextBoundary(MainActivity.this) - System.currentTimeMillis() + 999) / 1000);
                status.setText(String.format(Locale.ROOT, "休息中，%02d:%02d 后继续学习", seconds / 60, seconds % 60));
            }
            handler.postDelayed(this, 1000);
        }
    };

    @Override public void onResume() {
        super.onResume();
        resumed = true;
        OverlayService.appVisible(true);
        if (Controller.reconcile(this, true) > 0) return;
        showHome();
        handler.removeCallbacks(tick);
        tick.run();
    }

    @Override public void onPause() {
        resumed = false;
        handler.removeCallbacks(tick);
        OverlayService.appVisible(false);
        super.onPause();
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (resumed && Store.end(this) == 0) showHome();
    }

    private boolean permissionsReady() {
        return OverlayGrants.ready(this)
                && Policy.phonePermission(this) && Scheduler.exact(this)
                && BreakReminder.allowed(this) && Policy.dialer(this) != null;
    }

    private String signature() {
        return permissionsReady() + "|" + OverlayService.connected() + "|"
                + Store.prefs(this).getBoolean("enabled", false) + "|" + Store.committed(this)
                + "|" + RecoveryVault.ready(this);
    }

    private void open(Intent intent) {
        try { startActivity(intent); }
        catch (RuntimeException error) { Toast.makeText(this, "请从手机设置进入学习锁屏的权限页面", Toast.LENGTH_LONG).show(); }
    }

    private void appInfo() { open(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()))); }

    private void grantNext() {
        if (!OverlayGrants.consented(this)) {
            new AlertDialog.Builder(this).setTitle("悬浮窗与使用情况访问")
                    .setMessage("悬浮窗用于学习时遮住普通应用。使用情况访问用于识别当前应用及界面类名，只放行电话和已适配的微信通话页。\n\n信息仅在本机即时处理，不保存或上传应用使用历史，不读取屏幕文字、聊天、联系人或通话记录。学习期间保留运行通知。\n\n本版不使用无障碍；系统设置可撤销授权或卸载。")
                    .setNegativeButton("暂不开启", null)
                    .setPositiveButton("同意并授权", (dialog, which) -> {
                        Store.prefs(this).edit().putBoolean("overlay_consent_v5", true).commit();
                        grantNext();
                    }).show();
        } else if (!OverlayGrants.overlay(this)) {
            open(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())));
        } else if (!OverlayGrants.usage(this)) {
            open(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.parse("package:" + getPackageName())));
        } else if (!Policy.phonePermission(this)
                || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ArrayList<String> missing = new ArrayList<>();
            if (!Policy.phonePermission(this)) missing.add(Manifest.permission.READ_PHONE_STATE);
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) missing.add(Manifest.permission.POST_NOTIFICATIONS);
            new AlertDialog.Builder(this).setTitle("电话与提醒权限")
                    .setMessage("电话权限仅检测是否来电或通话，不读取号码、联系人和通话记录。通知用于提醒休息开始与结束。")
                    .setNegativeButton("取消", null)
                    .setPositiveButton("授权", (dialog, which) -> requestPermissions(missing.toArray(new String[0]), 20)).show();
        } else if (!Scheduler.exact(this)) {
            open(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + getPackageName())));
        } else if (!BreakReminder.allowed(this)) {
            open(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()));
        } else if (Policy.dialer(this) == null) appInfo();
    }

    private void startPlan() {
        if (!permissionsReady() || Store.committed(this)) return;
        String error = RecoveryVault.checkLocal(this);
        if (error != null) { new AlertDialog.Builder(this).setMessage(error).setPositiveButton("知道了", null).show(); return; }
        Store.prefs(this).edit().putBoolean("enabled", true).putLong("skip_until", 0)
                .putString("verified_for", Policy.validationKey(this)).putString("error", "").commit();
        if (Controller.reconcile(this, true) == 0) showHome();
    }

    private void showHome() {
        SharedPreferences prefs = Store.prefs(this);
        boolean enabled = prefs.getBoolean("enabled", false);
        boolean committed = Store.committed(this);
        boolean ready = permissionsReady();
        boolean recovery = RecoveryVault.ready(this);
        ScrollView scroll = new ScrollView(this);
        LinearLayout layout = Ui.column(this);
        scroll.addView(layout);
        Ui.text(this, layout, "学习锁屏", 30, true);
        Ui.text(this, layout, "每天 19:00—23:00", 25, true);
        Ui.text(this, layout, "学习 50 分钟，休息 10 分钟。\n可正常息屏，亮屏后按原时间继续。", 16, false);
        status = Ui.text(this, layout, enabled ? "每日计划已启用" : "尚未开始", 20, true);
        if (Store.committed(this) && (!ready || !OverlayService.connected())) status.setText("计划保留，完成授权后继续；当前无法拦截其他应用");
        String error = prefs.getString("error", "");
        if (!error.isEmpty()) Ui.text(this, layout, error, 14, false);
        if (!ready) Ui.button(this, layout, "1. 完成手机授权", false, view -> grantNext());
        else Ui.text(this, layout, "1. 手机授权已完成", 15, false);
        if (!recovery) Ui.button(this, layout, "2. 保存急救码备份", false, view -> RecoveryUi.setup(this, this::showHome)).setEnabled(!enabled && !committed);
        else Ui.text(this, layout, "2. 急救码已保存，长期有效，不按天更新", 15, false);
        Ui.button(this, layout, enabled ? "每日计划运行中" : "3. 验证并开始学习", true, view -> startPlan())
                .setEnabled(ready && recovery && !enabled && !committed);
        Ui.text(this, layout, "开始前自动校验本机急救码；无需妻子现在在线。妻子端离线网页仍需稍后单独验证。", 13, false);
        if (enabled || committed) Ui.button(this, layout, "离线急救：当晚解除", false, view -> RecoveryUi.unlock(this, this::showHome));
        Ui.button(this, layout, "更多与帮助", false, view -> more());
        Ui.text(this, layout, "普通来电和已适配的微信通话页可接听；聊天、桌面仍有遮罩。微信版本差异需实测。\n无需无障碍 · 0.5.0", 13, false);
        displayed = signature();
        setContentView(scroll);
    }

    private void startTrial() {
        if (!permissionsReady() || !RecoveryVault.ready(this) || Store.committed(this)
                || Store.prefs(this).getBoolean("enabled", false)) return;
        String error = RecoveryVault.checkLocal(this);
        if (error != null) { Toast.makeText(this, error, Toast.LENGTH_LONG).show(); return; }
        Store.prefs(this).edit().putBoolean("trial_completed", false).putBoolean("trial_interrupted", false)
                .putString("error", "").putLong("trial_end", System.currentTimeMillis() + 120000)
                .putLong("trial_elapsed", SystemClock.elapsedRealtime() + 120000).putInt("trial_boot", Store.boot(this)).commit();
        Controller.reconcile(this, true);
    }

    private void more() {
        ArrayList<String> labels = new ArrayList<>();
        ArrayList<Runnable> actions = new ArrayList<>();
        labels.add("授权、息屏与后台说明");
        actions.add(() -> new AlertDialog.Builder(this).setTitle("授权与后台")
                .setMessage("本版不使用无障碍服务。悬浮窗用于遮住受限页面，使用情况访问用于区分当前应用及微信通话页。\n\n学习期间保留一条运行通知。请允许自启动、后台运行，减少省电限制。息屏不取消计划；亮屏解锁后按原时间继续。\n\n系统设置可撤销权限或卸载。系统手势仍能返回桌面，但桌面应被遮住。")
                .setNegativeButton("返回", null).setPositiveButton("打开应用详情", (dialog, which) -> appInfo()).show());
        labels.add("两分钟检测（可选）");
        actions.add(() -> new AlertDialog.Builder(this).setMessage("锁 60 秒、休息 30 秒、再锁 30 秒。请测试点击本应用图标、息屏再解锁、电话接听及自动休息。妻子不在也可先测试；每日计划启用后不能在应用内插入检测。")
                .setNegativeButton("返回", null).setPositiveButton("开始检测", (dialog, which) -> startTrial()).show());
        if (RecoveryVault.ready(this)) {
            labels.add(Store.prefs(this).getBoolean("enabled", false) || Store.committed(this)
                    ? "离线急救（当晚解除）" : "验证妻子端短码（可稍后做）");
            actions.add(() -> RecoveryUi.unlock(this, this::showHome));
        }
        if (RecoveryVault.ready(this) && Store.prefs(this).getBoolean("recovery_used", false)) {
            labels.add("急救码已用过，主动更换");
            actions.add(() -> new AlertDialog.Builder(this).setMessage("原长码在你完成保存新码前继续有效。关闭每日计划且不在学习时段时，才能设置新长码；请同步更新妻子及纸质备份。")
                    .setNegativeButton("返回", null).setPositiveButton("设置新码", (dialog, which) -> RecoveryUi.setup(this, this::showHome)).show());
        }
        if (Store.prefs(this).getBoolean("enabled", false) && !Store.committed(this)) {
            labels.add("关闭每日计划");
            actions.add(() -> { Store.fail(this, ""); Controller.reconcile(this, false); showHome(); });
        }
        if (Policy.owner(this)) {
            labels.add("移除旧版设备管理身份");
            actions.add(() -> new AlertDialog.Builder(this).setMessage("关闭计划并移除旧设备管理身份，不清空手机，不删除急救码。")
                    .setNegativeButton("取消", null).setPositiveButton("移除", (dialog, which) -> {
                        try { Store.fail(this, ""); Controller.reconcile(this, false); Policy.manager(this).clearDeviceOwnerApp(getPackageName()); showHome(); }
                        catch (RuntimeException error) { Toast.makeText(this, "系统拒绝移除，请保留提示", Toast.LENGTH_LONG).show(); }
                    }).show());
        }
        new AlertDialog.Builder(this).setTitle("更多与帮助").setItems(labels.toArray(new String[0]), (dialog, which) -> actions.get(which).run()).setNegativeButton("返回", null).show();
    }
}
