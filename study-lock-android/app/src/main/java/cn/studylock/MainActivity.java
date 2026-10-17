package cn.studylock;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Toast;
import android.widget.TextView;
import java.util.Locale;

public final class MainActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView restStatus;
    private boolean displayedCommitment;
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (Store.end(MainActivity.this) > 0) { Controller.reconcile(MainActivity.this, true); return; }
            if (displayedCommitment != Store.committed(MainActivity.this)) {
                Controller.reconcile(MainActivity.this, false);
                showHome();
            }
            if (restStatus != null && Store.committed(MainActivity.this)) {
                long seconds = Math.max(0, (Store.nextBoundary(MainActivity.this) - System.currentTimeMillis() + 999) / 1000);
                restStatus.setText(String.format(Locale.ROOT, "休息中，%02d:%02d 后自动切换", seconds / 60, seconds % 60));
            }
            handler.postDelayed(this, 1000);
        }
    };
    @Override public void onCreate(Bundle state) { super.onCreate(state); }

    @Override public void onResume() {
        super.onResume();
        if (Controller.reconcile(this, true) > 0) return;
        showHome();
        handler.removeCallbacks(tick);
        tick.run();
    }

    @Override public void onPause() { handler.removeCallbacks(tick); super.onPause(); }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (Store.end(this) == 0) showHome();
    }

    private void showHome() {
        SharedPreferences prefs = Store.prefs(this);
        boolean owner = Policy.owner(this);
        boolean ready = owner && Scheduler.exact(this) && Policy.dialer(this) != null && RecoveryVault.ready(this) && BreakReminder.allowed(this);
        boolean enabled = prefs.getBoolean("enabled", false);
        boolean frozen = Store.committed(this);
        displayedCommitment = frozen;
        boolean tested = prefs.getBoolean("trial_completed", false)
                && Policy.validationKey(this).equals(prefs.getString("trial_for", ""));
        ScrollView scroll = new ScrollView(this);
        LinearLayout layout = Ui.column(this);
        scroll.addView(layout);
        Ui.text(this, layout, "学习锁屏", 30, true);
        Ui.text(this, layout, "每天 19:00—23:00", 27, true);
        Ui.text(this, layout, "每小时锁 50 分钟、休息 10 分钟。\n休息开始与结束时提醒，23:00 全部解除。", 16, false);
        restStatus = Ui.text(this, layout, frozen ? "休息中，闹钟将自动切换" : enabled ? "每日锁定已启用" : "每日锁定尚未启用", 18, true);
        String error = prefs.getString("error", "");
        if (!error.isEmpty()) Ui.text(this, layout, error, 15, false);

        if (!owner) {
            Ui.text(this, layout, "需要先配置设备管理权限，才能限制返回桌面和系统手势。安装本身不会锁住手机。", 15, false);
            Ui.button(this, layout, "查看配置说明", false, view -> new AlertDialog.Builder(this)
                    .setTitle("设备管理配置")
                    .setMessage("此版本需要连接电脑配置设备所有者，普通“设备管理员”权限不够。\n\n命令：\nadb shell dpm set-device-owner cn.studylock/.AdminReceiver\n\n手机已有账户或受系统限制时可能失败。失败后停止；不要为此直接清空手机。\n\n配置后先进行 2 分钟试锁。")
                    .setPositiveButton("知道了", null).show());
        }
        if (!Scheduler.exact(this)) {
            Ui.button(this, layout, "允许到点启动和解除", false, view -> {
                try { startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + getPackageName()))); }
                catch (RuntimeException errorOpen) { Toast.makeText(this, "请在系统设置中允许本应用设置闹钟和提醒", Toast.LENGTH_LONG).show(); }
            });
        }

        if (!BreakReminder.allowed(this)) {
            Ui.button(this, layout, "允许休息闹钟提醒", false, view -> {
                if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 20);
                } else {
                    startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()));
                }
            }).setEnabled(!frozen);
            Ui.text(this, layout, "允许提醒后才能开始试运行；声音还受闹钟音量与通知渠道设置影响。", 13, false);
        }

        Ui.button(this, layout, RecoveryVault.ready(this) ? "重新托管急救码" : "先把急救码交给妻子", false,
                view -> RecoveryUi.setup(this, this::showHome)).setEnabled(!enabled && !frozen);
        Ui.text(this, layout, RecoveryVault.ready(this) ? "急救码已托管。锁屏内可输入 8 位一次性码；不需要打开微信。" : "未托管急救码前不会启用锁定。长码只显示一次，可一键复制给妻子。", 14, false);
        if (RecoveryVault.ready(this)) Ui.button(this, layout, enabled || frozen ? "离线急救：当晚解除" : "验证急救码（不改变计划）", false, view -> RecoveryUi.unlock(this, this::showHome));

        Ui.button(this, layout, "先试运行 2 分钟", false, view -> new AlertDialog.Builder(this)
                .setTitle("开始 2 分钟试运行")
                .setMessage("锁定 60 秒、休息 30 秒、再锁定 30 秒，之后全部恢复。请准备另一部电话，验证接听、拨打、休息提醒与自动锁回。\n\n请确认锁屏内的急救数字键盘及电源菜单紧急呼叫入口可打开，无需实际拨打紧急号码。")
                .setNegativeButton("取消", null)
                .setPositiveButton("开始试锁", (dialog, which) -> {
                    if (Store.committed(this) || prefs.getBoolean("enabled", false)) return;
                    prefs.edit().putBoolean("enabled", false).putBoolean("trial_completed", false)
                            .putString("error", "").putLong("trial_end", System.currentTimeMillis() + 120000)
                            .putLong("trial_elapsed", SystemClock.elapsedRealtime() + 120000)
                            .putInt("trial_boot", Store.boot(this)).commit();
                    Controller.reconcile(this, true);
                }).show()).setEnabled(ready && !enabled && !frozen);

        CheckBox calls = new CheckBox(this);
        calls.setText("试运行结束：电话、休息提醒、自动锁回和急救入口均正常");
        calls.setTextColor(Ui.INK);
        calls.setEnabled(tested && !enabled && !frozen);
        layout.addView(calls);
        android.widget.Button arm = Ui.button(this, layout, enabled ? "关闭每日锁定" : "启用每日锁定", true, view -> {
            if (Store.committed(this)) return;
            if (enabled) {
                Store.fail(this, "");
                Controller.reconcile(this, false);
                showHome();
            } else {
                new AlertDialog.Builder(this).setTitle("启用每日锁定")
                        .setMessage("每天 19:00–23:00，每小时锁 50 分钟、休息 10 分钟。休息时可以使用手机，当天计划与急救码仍不能修改。\n\n若当前在锁定段，确认后立即开始。急救成功后当晚不再锁，次日 19:00 恢复。")
                        .setNegativeButton("取消", null)
                        .setPositiveButton("启用", (dialog, which) -> {
                            prefs.edit().putBoolean("enabled", true).putString("error", "")
                                    .putString("verified_for", Policy.validationKey(this)).commit();
                            if (Controller.reconcile(this, true) == 0) showHome();
                        }).show();
            }
        });
        arm.setEnabled(!frozen && (enabled || (ready && tested && calls.isChecked())));
        calls.setOnCheckedChangeListener((button, checked) -> arm.setEnabled(!frozen && ready && tested && checked));

        if (owner) {
            Ui.button(this, layout, "移除设备管理", false, view -> new AlertDialog.Builder(this)
                    .setTitle("移除设备管理")
                    .setMessage("关闭每日计划，移除本应用的设备管理身份，然后可以正常卸载。本操作不会清空手机。")
                    .setNegativeButton("取消", null)
                    .setPositiveButton("移除", (dialog, which) -> {
                        if (Store.committed(this)) return;
                        try {
                            Store.fail(this, "");
                            Controller.reconcile(this, false);
                            Policy.manager(this).clearDeviceOwnerApp(getPackageName());
                            showHome();
                        } catch (RuntimeException removeError) { Toast.makeText(this, "系统拒绝移除，请保留错误信息以便排查", Toast.LENGTH_LONG).show(); }
                    }).show()).setEnabled(!frozen);
        }
        Ui.text(this, layout, "本地计时和急救验证无需联网。\n休息期间不能关闭当天计划。", 13, false);
        setContentView(scroll);
    }
}
