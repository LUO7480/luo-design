package cn.studylock;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.PowerManager;
import android.os.SystemClock;
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
        SystemClock.sleep(700);
    }

    @Override public void onStart() {
        Bundle result = new Bundle();
        int outcome = Activity.RESULT_CANCELED;
        try {
            target = getTargetContext();
            automation = getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
            shell("pm grant cn.studylock android.permission.READ_PHONE_STATE");
            shell("pm grant cn.studylock android.permission.POST_NOTIFICATIONS");
            shell("appops set cn.studylock SCHEDULE_EXACT_ALARM allow");
            Store.prefs(target).edit().clear().commit();
            Store.prefs(target).edit().putBoolean("accessibility_consent_v3", true).commit();
            RecoveryVault.install(target, "0123456789".repeat(6) + "0123");
            check(RecoveryVault.checkLocal(target) == null, "real Android Keystore and recovery self-check");
            String hash = Store.prefs(target).getString("recovery_hash", "");
            shell("settings put secure enabled_accessibility_services cn.studylock/cn.studylock.FocusAccessibilityService");
            shell("settings put secure accessibility_enabled 1");
            await(FocusAccessibilityService::connected, "accessibility service connects");
            long end = System.currentTimeMillis() + 120000;
            Store.prefs(target).edit().putLong("trial_end", end)
                    .putLong("trial_elapsed", SystemClock.elapsedRealtime() + 120000)
                    .putInt("trial_boot", Store.boot(target)).putBoolean("trial_interrupted", false).commit();
            icon();
            check(Store.end(target) > System.currentTimeMillis(), "first app icon launch opens active study");
            icon();
            check(Store.prefs(target).getLong("trial_end", 0) == end && Store.end(target) > 0,
                    "reopening app icon preserves original session deadline");
            shell("input keyevent KEYCODE_SLEEP");
            await(() -> !target.getSystemService(PowerManager.class).isInteractive(), "screen can turn off");
            check(Store.prefs(target).getLong("trial_end", 0) == end, "screen off preserves session");
            shell("input keyevent KEYCODE_WAKEUP");
            shell("wm dismiss-keyguard");
            await(() -> target.getSystemService(PowerManager.class).isInteractive(), "screen wakes");
            icon();
            check(Store.prefs(target).getLong("trial_end", 0) == end && Store.end(target) > 0,
                    "wake and unlock preserve session deadline");
            shell("settings put secure enabled_accessibility_services ''");
            shell("settings put secure accessibility_enabled 0");
            await(() -> !FocusAccessibilityService.connected(), "service can disconnect");
            check(Store.prefs(target).getLong("trial_end", 0) == end, "service disconnect does not cancel session");
            icon();
            check(Store.prefs(target).getLong("trial_end", 0) == end, "icon while service unavailable keeps timer");
            shell("settings put secure enabled_accessibility_services cn.studylock/cn.studylock.FocusAccessibilityService");
            shell("settings put secure accessibility_enabled 1");
            await(FocusAccessibilityService::connected, "service reconnects");
            check(Store.prefs(target).getLong("trial_end", 0) == end && Store.end(target) > 0,
                    "reconnect resumes original deadline without starting over");
            check(hash.equals(Store.prefs(target).getString("recovery_hash", ""))
                    && !Store.prefs(target).getBoolean("recovery_used", false), "navigation and sleep never rotate unused recovery key");
            result.putString("stream", "STUDYLOCK_NATIVE_PASS " + checks + " lifecycle checks\n");
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
