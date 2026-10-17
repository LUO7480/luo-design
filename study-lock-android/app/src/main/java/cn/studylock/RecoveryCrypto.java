package cn.studylock;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Offline challenge/response. Never embed a real recovery key in source or build artifacts. */
public final class RecoveryCrypto {
    private static final SecureRandom RANDOM = new SecureRandom();
    private RecoveryCrypto() {}

    public static String digits(int length) {
        StringBuilder value = new StringBuilder(length);
        for (int i = 0; i < length; i++) value.append(RANDOM.nextInt(10));
        return value.toString();
    }

    public static String normalize(String value) { return value.replaceAll("[\\s-]", ""); }

    public static byte[] hmac(byte[] key, byte[] message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(message);
        } catch (GeneralSecurityException error) { throw new IllegalStateException("HMAC unavailable", error); }
    }

    public static String response(String master, String challenge) {
        master = normalize(master);
        challenge = normalize(challenge);
        if (!master.matches("[0-9]{64}") || !challenge.matches("[0-9]{12}")) throw new IllegalArgumentException("Invalid recovery key or challenge");
        byte[] digest = hmac(master.getBytes(StandardCharsets.US_ASCII), ("StudyLockRecovery:v2:" + challenge).getBytes(StandardCharsets.US_ASCII));
        int offset = digest[digest.length - 1] & 15;
        int number = (digest[offset] & 127) << 24 | (digest[offset + 1] & 255) << 16
                | (digest[offset + 2] & 255) << 8 | digest[offset + 3] & 255;
        return String.format(Locale.ROOT, "%08d", number % 100000000);
    }

    public static String hash(String master) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(("StudyLockMaster:v2:" + normalize(master)).getBytes(StandardCharsets.US_ASCII));
            StringBuilder value = new StringBuilder(64);
            for (byte item : digest) value.append(String.format(Locale.ROOT, "%02x", item & 255));
            return value.toString();
        } catch (GeneralSecurityException error) { throw new IllegalStateException("SHA-256 unavailable", error); }
    }

    public static boolean equal(String left, String right) {
        return MessageDigest.isEqual(left.getBytes(StandardCharsets.US_ASCII), right.getBytes(StandardCharsets.US_ASCII));
    }

    public static String grouped(String value) { return value.replaceAll("(.{4})(?!$)", "$1 "); }
}
