package cn.studylock;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

final class RecoveryVault {
    private static final String ALIAS = "studylock.recovery.aes.v2";
    static boolean ready(Context context) {
        SharedPreferences prefs = Store.prefs(context);
        return prefs.getBoolean("recovery_confirmed", false) && !prefs.getString("recovery_hash", "").isEmpty()
                && !prefs.getString("recovery_cipher", "").isEmpty();
    }

    static boolean replaceable(Context context) {
        SharedPreferences prefs = Store.prefs(context);
        return RecoveryRules.mayReplace(ready(context), prefs.getBoolean("recovery_used", false),
                prefs.getBoolean("enabled", false), Store.committed(context));
    }

    /** Checks local storage and the public protocol fixture without revealing or consuming a code. */
    static String checkLocal(Context context) {
        if (!ready(context)) return "请先保存急救码备份。";
        try {
            if (!"11359506".equals(RecoveryCrypto.response("0123456789".repeat(6) + "0123", "123456789012"))) {
                return "急救算法自检失败，请勿开始学习。";
            }
            String saved = master(context);
            if (!saved.matches("[0-9]{64}") || !RecoveryCrypto.equal(RecoveryCrypto.hash(saved),
                    Store.prefs(context).getString("recovery_hash", ""))) return "急救码本地校验失败，请先处理备份。";
            return null;
        } catch (Exception error) { return "急救码存储暂不可用，请保持计划关闭并保留纸质备份。"; }
    }

    private static SecretKey key() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        if (!store.containsAlias(ALIAS)) {
            KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
            generator.generateKey();
        }
        return (SecretKey) store.getKey(ALIAS, null);
    }

    static void install(Context context, String master) throws Exception {
        if (!master.matches("[0-9]{64}")) throw new IllegalArgumentException("Invalid recovery key");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key());
        byte[] encrypted = cipher.doFinal(master.getBytes(StandardCharsets.US_ASCII));
        Store.prefs(context).edit().putString("recovery_cipher", Base64.encodeToString(encrypted, Base64.NO_WRAP))
                .putString("recovery_iv", Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP))
                .putString("recovery_hash", RecoveryCrypto.hash(master)).putBoolean("recovery_confirmed", true)
                .putBoolean("recovery_used", false)
                .remove("recovery_challenge").putInt("recovery_failures", 0).putLong("recovery_wait_wall", 0)
                .putLong("recovery_wait_elapsed", 0).commit();
    }

    private static String master(Context context) throws Exception {
        SharedPreferences prefs = Store.prefs(context);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, Base64.decode(prefs.getString("recovery_iv", ""), Base64.NO_WRAP)));
        byte[] plain = cipher.doFinal(Base64.decode(prefs.getString("recovery_cipher", ""), Base64.NO_WRAP));
        try { return new String(plain, StandardCharsets.US_ASCII); }
        finally { java.util.Arrays.fill(plain, (byte) 0); }
    }

    private static boolean validChallenge(Context context) {
        SharedPreferences prefs = Store.prefs(context);
        return prefs.getString("recovery_challenge", "").matches("[0-9]{12}")
                && RecoveryRules.challengeValid(System.currentTimeMillis(), SystemClock.elapsedRealtime(),
                prefs.getLong("recovery_issued_wall", 0), prefs.getLong("recovery_issued_elapsed", 0), prefs.getInt("recovery_boot", -2) == Store.boot(context));
    }

    static String challenge(Context context) {
        SharedPreferences prefs = Store.prefs(context);
        if (!validChallenge(context)) prefs.edit().putString("recovery_challenge", RecoveryCrypto.digits(12))
                .putLong("recovery_issued_wall", System.currentTimeMillis()).putLong("recovery_issued_elapsed", SystemClock.elapsedRealtime())
                .putInt("recovery_boot", Store.boot(context)).commit();
        return prefs.getString("recovery_challenge", "");
    }

    static String verify(Context context, String value, boolean longCode) {
        SharedPreferences prefs = Store.prefs(context);
        value = RecoveryCrypto.normalize(value);
        if (!ready(context)) return "急救码尚未托管，请等待休息或自动解除。";
        if (longCode) {
            if (!value.matches("[0-9]{64}") || !RecoveryCrypto.equal(RecoveryCrypto.hash(value), prefs.getString("recovery_hash", ""))) return "长码不正确，请按每组 4 位核对。";
        } else {
            long wait = RecoveryRules.cooldown(System.currentTimeMillis(), SystemClock.elapsedRealtime(),
                    prefs.getLong("recovery_wait_wall", 0), prefs.getLong("recovery_wait_elapsed", 0), prefs.getInt("recovery_wait_boot", -2) == Store.boot(context));
            if (wait > 0) return "短码连续输错，请等待 " + (wait + 999) / 1000 + " 秒；纸质长码仍可立即验证。";
            if (!validChallenge(context)) return "挑战号已过期。请取消后重新打开，将新的挑战号告诉妻子。";
            boolean matches;
            try { matches = value.matches("[0-9]{8}") && RecoveryCrypto.equal(value, RecoveryCrypto.response(master(context), prefs.getString("recovery_challenge", ""))); }
            catch (Exception error) { return "短码验证暂不可用，请切换到封存的 64 位纸质长码。"; }
            if (!matches) {
                int failures = prefs.getInt("recovery_failures", 0) + 1;
                SharedPreferences.Editor edit = prefs.edit().putInt("recovery_failures", failures % 5);
                if (failures >= 5) edit.putLong("recovery_wait_wall", System.currentTimeMillis() + 60000)
                        .putLong("recovery_wait_elapsed", SystemClock.elapsedRealtime() + 60000).putInt("recovery_wait_boot", Store.boot(context));
                edit.commit();
                return failures >= 5 ? "已连续输错 5 次，短码暂停验证 60 秒。纸质长码仍可使用。" : "短码不正确，请核对本机挑战号和妻子读出的 8 位数字。";
            }
        }
        // A successful response is consumed before the screen restriction is released.
        prefs.edit().remove("recovery_challenge").putInt("recovery_failures", 0).putLong("recovery_wait_wall", 0).putLong("recovery_wait_elapsed", 0).commit();
        return null;
    }
}
