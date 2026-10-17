package cn.studylock;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.Instrumentation;
import android.app.NotificationManager;
import android.app.UiAutomation;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;
import java.nio.charset.StandardCharsets;
import java.util.function.BooleanSupplier;

/** Runs only on the disposable CI emulator; no test code is included in the release APK. */
public final class ContinuityInstrumentation extends Instrumentation {
    private Context target;
    private UiAutomation automation;
    private int checks;

    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }

    private String shell(String command) throws Exception {
        ParcelFileDescriptor descriptor = automation.executeShellCommand(command);
        try (ParcelFileDescriptor.AutoCloseInputStream input = new ParcelFileDescriptor.AutoCloseInputStream(descriptor)) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private void await(BooleanSupplier condition, String message) {
        long deadline = SystemClock.elapsedRealtime() + 15000;
        while (!condition.getAsBoolean() && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(100);
        check(condition.getAsBoolean(), message);
    }

    private void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
        Bundle progress = new Bundle();
        progress.putString("stream", "PASS: " + message + "\n");
        sendStatus(0, progress);
    }

    private void icon() throws Exception {
        shell("am start -W -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n cn.studylock/.MainActivity");
        SystemClock.sleep(500);
    }

    private void home() throws Exception { shell("input keyevent KEYCODE_HOME"); }

    @Override public void onStart() {
        Bundle result = new Bundle();
        int outcome = Activity.RESULT_CANCELED;
        try {
            target = getTargetContext();
            automation = getUiAutomation(UiAutomation.FLAG_DONT_USE_ACCESSIBILITY);
            shell("settings put secure enabled_accessibility_services ''");
            shell("settings put secure accessibility_enabled 0");
            shell("pm grant cn.studylock android.permission.READ_PHONE_STATE");
            shell("pm grant cn.studylock android.permission.POST_NOTIFICATIONS");
            shell("appops set cn.studylock SYSTEM_ALERT_WINDOW allow");
            shell("appops set cn.studylock GET_USAGE_STATS allow");
            shell("appops set cn.studylock SCHEDULE_EXACT_ALARM allow");
            Store.prefs(target).edit().clear().commit();
            Store.prefs(target).edit().putBoolean("overlay_consent_v5", true).commit();
            RecoveryVault.install(target, "0123456789".repeat(6) + "0123");
            check(RecoveryVault.checkLocal(target) == null, "real Android Keystore and recovery self-check");
            check(target.getPackageManager().queryIntentServices(new Intent("android.accessibilityservice.AccessibilityService")
                    .setPackage(target.getPackageName()), 0).isEmpty(), "APK declares no accessibility service");
            check(Settings.Secure.getInt(target.getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED, 0) == 0,
                    "all accessibility remains off");
            check(OverlayGrants.ready(target), "overlay and usage access grants are sufficient");
            String hash = Store.prefs(target).getString("recovery_hash", "");
            long end = System.currentTimeMillis() + 120000;
            Store.prefs(target).edit().putLong("trial_end", end)
                    .putLong("trial_elapsed", SystemClock.elapsedRealtime() + 120000)
                    .putInt("trial_boot", Store.boot(target)).putBoolean("trial_interrupted", false).commit();
            icon();
            await(OverlayService::connected, "normal foreground service starts without accessibility");
            check(Store.trialEnd(target) > System.currentTimeMillis(), "first app icon launch opens active study");
            icon();
            check(Store.prefs(target).getLong("trial_end", 0) == end, "reopening icon preserves original session deadline");
            home();
            await(OverlayService::covering, "home is covered by normal app overlay");
            check(shell("dumpsys window windows").contains("学习锁屏 · 专注遮罩"), "Android WindowManager contains the real overlay window");
            check(java.util.Arrays.stream(target.getSystemService(NotificationManager.class).getActiveNotifications())
                    .anyMatch(n -> n.getId() == OverlayService.NOTIFICATION_ID), "foreground running notification is present");
            shell("input keyevent KEYCODE_SLEEP");
            await(() -> !target.getSystemService(PowerManager.class).isInteractive(), "screen can turn off");
            check(Store.prefs(target).getLong("trial_end", 0) == end, "screen off preserves session");
            shell("input keyevent KEYCODE_WAKEUP");
            shell("wm dismiss-keyguard");
            await(() -> target.getSystemService(PowerManager.class).isInteractive(), "screen wakes");
            home();
            await(OverlayService::covering, "home overlay returns after waking and unlocking");
            check(Store.prefs(target).getLong("trial_end", 0) == end, "wake preserves original deadline");

            // This separate CI-only APK simulates WeChat's activity identities, without an account.
            shell("am start -W -n com.tencent.mm/com.tencent.mm.plugin.voip.ui.VideoActivity");
            await(() -> !OverlayService.covering(), "separate app call surface is allowed using real usage events");
            shell("am start -W -n com.tencent.mm/com.tencent.mm.ui.LauncherUI");
            await(OverlayService::covering, "same app chat surface is covered using real usage events");
            home();
            await(OverlayService::covering, "leaving call or chat never unlocks home");
            check(Store.prefs(target).getLong("trial_end", 0) == end, "call surface switches do not stop study");

            runOnMainSync(() -> target.startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:"))
                    .setPackage(Policy.dialer(target)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));
            await(() -> !OverlayService.covering(), "default phone surface is allowed");
            home();
            await(OverlayService::covering, "home remains blocked after leaving the phone surface");

            runOnMainSync(() -> target.stopService(new Intent(target, OverlayService.class)));
            await(() -> !OverlayService.running(), "foreground service can stop");
            check(Store.prefs(target).getLong("trial_end", 0) == end, "service stop does not cancel session");
            target.getSystemService(AlarmManager.class).setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    SystemClock.elapsedRealtime() + 1000, Scheduler.pending(target, 1));
            await(OverlayService::connected, "scheduled exact alarm restarts service from background");
            await(OverlayService::covering, "alarm restart restores home overlay");
            check(Store.prefs(target).getLong("trial_end", 0) == end, "alarm restart keeps the original deadline");
            check(hash.equals(Store.prefs(target).getString("recovery_hash", ""))
                    && !Store.prefs(target).getBoolean("recovery_used", false), "unused recovery key is unchanged");

            // Inject a boundary near the end of the normal 30-second trial break.
            Store.prefs(target).edit().putLong("trial_end", System.currentTimeMillis() + 35000)
                    .putLong("trial_elapsed", SystemClock.elapsedRealtime() + 35000).commit();
            runOnMainSync(() -> Controller.reconcile(target, false));
            await(() -> !OverlayService.covering(), "rest boundary removes overlay");
            await(OverlayService::covering, "local alarm restores overlay when rest ends");
            Store.prefs(target).edit().putLong("trial_end", System.currentTimeMillis() - 1)
                    .putLong("trial_elapsed", SystemClock.elapsedRealtime() - 1).commit();
            runOnMainSync(() -> Controller.reconcile(target, false));
            await(() -> !OverlayService.running() && !OverlayService.covering(), "expiry removes overlay and stops foreground service");
            check(Settings.Secure.getInt(target.getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED, 0) == 0,
                    "all checks finish with accessibility still off");
            result.putString("stream", "STUDYLOCK_NATIVE_PASS " + checks + " no-accessibility lifecycle checks\n");
            outcome = Activity.RESULT_OK;
        } catch (Throwable error) {
            result.putString("stream", "STUDYLOCK_NATIVE_FAIL " + android.util.Log.getStackTraceString(error));
        } finally {
            if (target != null) {
                Store.fail(target, "");
                runOnMainSync(() -> Controller.reconcile(target, false));
            }
        }
        finish(outcome, result);
    }
}
