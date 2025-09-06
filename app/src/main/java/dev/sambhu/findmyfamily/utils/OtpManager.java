package dev.sambhu.findmyfamily.utils;

import android.util.Log;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import dev.sambhu.findmyfamily.BuildConfig;

public class OtpManager {

    private static final String TAG = "OtpManager";
    // IMPORTANT: This should be a random, complex string unique to your app.
    private static final String SECRET_KEY = BuildConfig.SecretKey;
    private static final String HASHING_ALGORITHM = "HmacSHA256";

    private static String getCurrentDateUTC() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        return sdf.format(new Date());
    }

    private static String calculateHmac(String data) {
        try {
            Mac sha256_HMAC = Mac.getInstance(HASHING_ALGORITHM);
            SecretKeySpec secret_key = new SecretKeySpec(SECRET_KEY.getBytes(StandardCharsets.UTF_8), HASHING_ALGORITHM);
            sha256_HMAC.init(secret_key);

            byte[] hash = sha256_HMAC.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte b : hash) {
                result.append(String.format("%02x", b));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            Log.e(TAG, "Error calculating HMAC", e);
            return null;
        }
    }

    public static String generateOtp(String userId) {
        String data = userId + ":" + getCurrentDateUTC();
        String hmac = calculateHmac(data);
        if (hmac == null) {
            return "000000"; // Fallback OTP on error
        }
        // Take the first 6 characters of the hex hash and convert to a number string
        long decimal = Long.parseLong(hmac.substring(0, 6), 16);
        return String.format(Locale.getDefault(), "%06d", decimal % 1000000);
    }

    public static boolean isValidOtp(String userId, String otp) {
        String expectedOtp = generateOtp(userId);
        return expectedOtp.equals(otp);
    }
}