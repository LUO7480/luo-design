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
    private boolean displayedConnection;
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (Store.end(MainActivity.this) > 0 && FocusAccessibilityService.connected()) {
                if (Controller.reconcile(MainActivity.this, true) > 0) return;
            }
            if (displayedCommitment != Store.committed(MainActivity.this)
                    || displayedConnection != FocusAccessibilityService.connected()) {
                Controller.reconcile(MainActivity.this, false);
                showHome();
            }
            if (restStatus != null && Store.committed(MainActivity.this)) {
                if (!FocusAccessibilityService.connected()) restStatus.setText("无障碍服务未连接，当前不能限制其他应用");
                else {
                    long seconds = Math.max(0, (Store.nextBoundary(MainActivity.this) - System.currentTimeMillis() + 999) / 1000);
                    restStatus.setText(String.format(Locale.ROOT, "休息中，%02d:%02d 后自动切换", seconds / 60, seconds % 60));
                }
            }
            handler.postDelayed(this, 1000);
        }
    };

    @Override public void onCreate(Bundle state) { super.onCreate(state); }

    @Override public void onResume() {
        super.onResume();
        FocusAccessibilityService.appVisible(true);
        if (Controller.reconcile(this, true) > 0) return;
        showHome();
        handler.removeCallbacks(tick);
        tick.run();
    }

    @Override public void onPause() {
        handler.removeCallbacks(tick);
        FocusAccessibilityService.appVisible(false);
        super.onPause();
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (Store.end(this) == 0) showHome();
    }

    private void openSettings(Intent intent) {
        try { startActivity(intent); }
        catch (RuntimeException error) { Toast.makeText(this, "请从手机设置进入本应用的权限页面", Toast.LENGTH_LONG).show(); }
    }

    private void appInfo() { openSettings(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()))); }

    private void accessibility() {
        if (FocusAccessibilityService.consented(this)) {
            openSettings(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            return;
        }
        new AlertDialog.Builder(this).setTitle("无障碍权限用途")
                .setMessage("学习锁屏使用无障碍服务识别当前前台应用的包名，在学习时段显示可拦截触摸的遮罩。\n\n仅在本机处理包名，不读取屏幕文字、聊天内容或密码，不代你点击其他应用，不联网或上传数据。\n\n电话状态权限仅用于在来电和通话时暂时移除遮罩；此时可使用手机，挂断后恢复。系统设置与卸载入口保持可用。\n\n你可随时在系统设置关闭服务或卸载。系统手势、关闭权限、强行停止及后台清理可以绕过限制，不能保证完全锁死手机。")
                .setNegativeButton("暂不使用", null)
                .setPositiveButton("同意并打开设置", (dialog, which) -> {
                    Store.prefs(this).edit().putBoolean("accessibility_consent_v3", true).commit();
                    FocusAccessibilityService.refreshSoon();
                    openSettings(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                }).show();
    }

    private void showHome() {
        SharedPreferences prefs = Store.prefs(this);
        boolean connection = FocusAccessibilityService.connected();
        boolean ready = connection && FocusAccessibilityService.consented(this) && Policy.phonePermission(this)
                && Scheduler.exact(this) && Policy.dialer(this) != null && RecoveryVault.ready(this) && BreakReminder.allowed(this);
        boolean enabled = prefs.getBoolean("enabled", false);
        boolean frozen = Store.committed(this);
        displayedCommitment = frozen;
        displayedConnection = connection;
        boolean tested = prefs.getBoolean("trial_completed", false)
                && Policy.validationKey(this).equals(prefs.getString("trial_for", ""));
        ScrollView scroll = new ScrollView(this);
        LinearLayout layout = Ui.column(this);
        scroll.addView(layout);
        Ui.text(this, layout, "学习锁屏", 30, true);
        Ui.text(this, layout, "免 USB 大众版 · 0.3.0", 18, true);
        Ui.text(this, layout, "每天 19:00—23:00", 27, true);
        Ui.text(this, layout, "每小时锁 50 分钟、休息 10 分钟。\n到点自动切换并提醒，23:00 全部解除。", 16, false);
        restStatus = Ui.text(this, layout, frozen ? "休息中，闹钟将自动切换" : enabled ? "每日计划已启用" : "每日计划尚未启用", 18, true);
        String error = prefs.getString("error", "");
        if (!error.isEmpty()) Ui.text(this, layout, error, 15, false);
        Ui.text(this, layout, "安装后在手机内授权，无需电脑。遮罩限制普通应用；电话、系统设置和卸载入口保留。通话期间暂时恢复手机使用，挂断后锁回。", 15, false);

        Ui.button(this, layout, connection && FocusAccessibilityService.consented(this)
                ? "① 无障碍服务已连接 · 查看设置" : "① 开启无障碍专注服务", false, view -> accessibility());
        if (FocusAccessibilityService.enabled(this) && !connection) Ui.text(this, layout, "系统开关已打开，服务尚未连接。稍候或重新开关服务；当前不能锁定其他应用。", 13, false);
        Ui.button(this, layout, "无障碍开关变灰？查看办法", false, view -> new AlertDialog.Builder(this)
                .setTitle("允许受限设置")
                .setMessage("部分 Android 13 及以上手机会限制通过文件安装的应用开启无障碍。\n\n确认安装包来自可信来源后，在“设置 → 应用 → 学习锁屏 → 右上角更多”查找“允许受限设置”，再返回无障碍页面开启“学习锁屏 · 专注遮罩”。\n\n小米等系统的名称和位置可能不同，以本机提示为准。")
                .setNegativeButton("返回", null).setPositiveButton("打开应用详情", (dialog, which) -> appInfo()).show());
        if (!Policy.phonePermission(this)) {
            Ui.button(this, layout, "② 允许通话状态检测", false, view -> new AlertDialog.Builder(this)
                    .setTitle("保留来电与通话")
                    .setMessage("需要“电话”权限检测是否正在来电或通话，及时移除遮罩。本应用不读取号码、联系人或通话记录，也不会自动拨号。")
                    .setNegativeButton("取消", null)
                    .setPositiveButton("继续授权", (dialog, which) -> requestPermissions(new String[]{Manifest.permission.READ_PHONE_STATE}, 21)).show());
        } else Ui.text(this, layout, "② 通话状态权限已允许", 14, false);
        if (Policy.dialer(this) == null) Ui.text(this, layout, "未找到拨号界面，请先在系统设置选择默认电话应用。", 14, false);
        if (!Scheduler.exact(this)) {
            Ui.button(this, layout, "③ 允许闹钟和提醒", false, view ->
                    openSettings(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + getPackageName()))));
        } else Ui.text(this, layout, "③ 定时闹钟已允许", 14, false);
        if (!BreakReminder.allowed(this)) {
            Ui.button(this, layout, "④ 允许休息通知与声音", false, view -> {
                if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 20);
                } else openSettings(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()));
            });
        } else Ui.text(this, layout, "④ 休息通知已允许；请检查闹钟音量", 14, false);
        Ui.button(this, layout, "后台运行与自启动说明", false, view -> new AlertDialog.Builder(this)
                .setTitle("减少后台中断")
                .setMessage("在本机应用设置中，按需允许学习锁屏自启动、后台运行，并关闭对本应用的省电限制。不同品牌入口不同。\n\n若系统清理或关闭无障碍服务，遮罩会失效；重新打开服务后恢复有效计划。重启后需先解锁手机，再确认服务已连接。请通过试运行验证本机行为。")
                .setNegativeButton("返回", null).setPositiveButton("打开应用详情", (dialog, which) -> appInfo()).show());

        Ui.button(this, layout, RecoveryVault.ready(this) ? "⑤ 急救码已托管 · 重新设置" : "⑤ 先把急救码交给妻子", false,
                view -> RecoveryUi.setup(this, this::showHome)).setEnabled(!enabled && !frozen);
        Ui.text(this, layout, RecoveryVault.ready(this) ? "锁屏内输入 8 位一次性码，无需微信。急救成功后当晚解除，次日 19:00 恢复。" : "长码随机生成，只显示一次，可复制交给妻子。未托管前不能试锁。", 14, false);
        if (RecoveryVault.ready(this)) Ui.button(this, layout, enabled || frozen ? "离线急救：当晚解除" : "验证急救码（不改变计划）", false,
                view -> RecoveryUi.unlock(this, this::showHome));

        Ui.button(this, layout, "⑥ 先试运行 2 分钟", false, view -> new AlertDialog.Builder(this)
                .setTitle("开始 2 分钟试运行")
                .setMessage("锁 60 秒、休息 30 秒、再锁 30 秒，之后全部恢复。请切到桌面和其他应用，确认遮罩出现。\n\n准备另一部电话验证接听、拨打；挂断后应恢复遮罩。确认休息提醒、自动锁回及离线急救数字键盘可用。\n\n系统设置可关闭权限；本版不能禁止全部系统手势。")
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
        calls.setText("完整试运行通过：应用遮罩、电话、休息提醒、自动锁回和急救入口正常");
        calls.setTextColor(Ui.INK);
        calls.setEnabled(tested && !enabled && !frozen);
        layout.addView(calls);
        android.widget.Button arm = Ui.button(this, layout, enabled ? "关闭每日计划" : "启用每日计划", true, view -> {
            if (Store.committed(this)) return;
            if (enabled) {
                Store.fail(this, "");
                Controller.reconcile(this, false);
                showHome();
            } else {
                new AlertDialog.Builder(this).setTitle("启用每日计划")
                        .setMessage("每天 19:00–23:00，每小时锁 50 分钟、休息 10 分钟。应用内在当晚学习及休息期间不能修改计划或托管码。\n\n若当前在锁定段，确认后立即开始。急救成功后当晚不再锁，次日 19:00 恢复。系统设置仍可撤销权限。")
                        .setNegativeButton("取消", null)
                        .setPositiveButton("启用", (dialog, which) -> {
                            prefs.edit().putBoolean("enabled", true).putString("error", "")
                                    .putString("verified_for", Policy.validationKey(this)).commit();
                            if (Controller.reconcile(this, true) == 0) showHome();
                        }).show();
            }
        });
        arm.setEnabled(!frozen && (enabled || ready && tested && calls.isChecked()));
        calls.setOnCheckedChangeListener((button, checked) -> arm.setEnabled(!frozen && (enabled || ready && tested && checked)));

        if (Policy.owner(this)) {
            Ui.button(this, layout, "旧版升级：移除原设备管理身份", false, view -> new AlertDialog.Builder(this)
                    .setTitle("移除旧版设备管理")
                    .setMessage("大众版不需要设备所有者。此操作关闭计划并移除旧版管理身份，不清空手机，也不删除急救码。")
                    .setNegativeButton("取消", null)
                    .setPositiveButton("移除", (dialog, which) -> {
                        try {
                            Store.fail(this, "");
                            Controller.reconcile(this, false);
                            Policy.manager(this).clearDeviceOwnerApp(getPackageName());
                            showHome();
                        } catch (RuntimeException errorRemove) { Toast.makeText(this, "系统拒绝移除，请保留提示以便排查", Toast.LENGTH_LONG).show(); }
                    }).show());
        }
        Ui.text(this, layout, "无联网权限；不读屏幕文字、联系人或通话记录。包名与通话状态仅在本机用于专注遮罩。\n关闭服务、强行停止、卸载、改时间或厂商后台清理可绕过限制。", 13, false);
        setContentView(scroll);
    }
}
