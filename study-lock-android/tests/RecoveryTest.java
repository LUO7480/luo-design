import cn.studylock.RecoveryCrypto;
import cn.studylock.RecoveryRules;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;

public final class RecoveryTest {
    private static int assertions;
    static void check(boolean result, String message) {
        assertions++;
        if (!result) throw new AssertionError(message);
    }
    static long time(String value) { return ZonedDateTime.parse(value).toInstant().toEpochMilli(); }
    public static void main(String[] args) {
        byte[] key = new byte[20];
        Arrays.fill(key, (byte) 0x0b);
        byte[] digest = RecoveryCrypto.hmac(key, "Hi There".getBytes(StandardCharsets.US_ASCII));
        StringBuilder hex = new StringBuilder();
        for (byte value : digest) hex.append(String.format("%02x", value & 255));
        check(hex.toString().equals("b0344c61d8db38535ca8afceaf0bf12b881dc200c9833da726e9376c2e32cff7"), "RFC 4231 HMAC-SHA256 vector");
        String fixture = "0123456789".repeat(6) + "0123";
        check(RecoveryCrypto.response(fixture, "123456789012").equals("11359506"), "Independent Node.js reference response");
        check(RecoveryCrypto.response(RecoveryCrypto.grouped(fixture), "1234 5678 9012").equals("11359506"), "Grouped input is equivalent");
        check(!RecoveryCrypto.equal("11359506", "11359507"), "Incorrect short response rejected");
        check(RecoveryCrypto.equal("11359506", "11359506"), "Correct short response accepted");
        check(!RecoveryCrypto.hash(fixture).equals(RecoveryCrypto.hash(fixture.substring(0, 63) + "9")), "Wrong master has a different hash");
        boolean invalid = false;
        try { RecoveryCrypto.response(fixture, "123"); } catch (IllegalArgumentException expected) { invalid = true; }
        check(invalid, "Reject malformed challenge");
        check(RecoveryCrypto.digits(64).matches("[0-9]{64}"), "Generated master has 64 digits");
        check(RecoveryCrypto.digits(12).matches("[0-9]{12}"), "Generated challenge has 12 digits");
        check(RecoveryRules.challengeValid(1000, 100, 1000, 100, true), "Fresh challenge valid");
        check(!RecoveryRules.challengeValid(901000, 900100, 1000, 100, true), "Challenge expires exactly after fifteen minutes");
        check(!RecoveryRules.challengeValid(1000, 100, 1000, 100, false), "Reboot invalidates a challenge");
        check(!RecoveryRules.challengeValid(999, 100, 1000, 100, true), "Clock rollback invalidates a challenge");
        check(RecoveryRules.cooldown(1000, 100, 61000, 60100, true) == 60000, "Cooldown starts at sixty seconds");
        check(RecoveryRules.cooldown(1000000, 100, 61000, 60100, true) == 60000, "Wall clock cannot bypass same-boot cooldown");
        check(RecoveryRules.cooldown(61000, 60100, 61000, 60100, true) == 0, "Cooldown expires without extension");
        check(RecoveryRules.cooldown(1000, 10, 61000, 60100, false) == 60000, "Cooldown survives reboot using wall time");
        ZoneId zone = ZoneId.of("Asia/Shanghai");
        check(RecoveryRules.tonightEnd(time("2026-10-17T20:13:00+08:00"), zone) == time("2026-10-17T23:00:00+08:00"), "Emergency exemption ends tonight");
        check(RecoveryRules.tonightEnd(time("2026-10-17T23:01:00+08:00"), zone) == time("2026-10-17T23:01:00+08:00"), "Emergency never exempts the next day");
        check(RecoveryRules.mayReplace(false, false, false, false), "Initial setup allowed");
        check(!RecoveryRules.mayReplace(true, false, false, false), "Unused master cannot be accidentally regenerated");
        check(RecoveryRules.mayReplace(true, true, false, false), "Actual emergency permits deliberate replacement");
        check(!RecoveryRules.mayReplace(true, true, true, false), "Armed plan preserves its backup");
        check(!RecoveryRules.mayReplace(true, true, false, true), "Trial and rest keep the backup fixed");
        System.out.println("PASS: " + assertions + " recovery assertions; HMAC interoperability, expiry, cooldown, and tonight-only exemption");
    }
}
