package com.lzqlove.jiaowu;

import java.security.MessageDigest;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.Locale;

/**
 * 学校统一认证服务器证书指纹的唯一来源。
 * 与 res/xml/network_security_config.xml 的 SPKI pin 对应；证书轮换时两处必须一起更新。
 */
final class SsoPin {

    static final String SHA256 =
            "85:BA:F1:69:43:27:A8:B7:82:D2:CE:BC:A6:A1:A8:14:"
                    + "E1:22:19:D3:F2:D6:4F:1B:F3:6B:A5:66:E9:13:4A:50";

    private SsoPin() {
    }

    static boolean matches(X509Certificate certificate) {
        if (certificate == null) return false;
        try {
            return SHA256.equals(sha256(certificate.getEncoded()));
        } catch (CertificateEncodingException error) {
            return false;
        }
    }

    static String sha256(byte[] data) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(data);
            StringBuilder builder = new StringBuilder();
            for (int index = 0; index < hash.length; index += 1) {
                if (index > 0) builder.append(':');
                builder.append(String.format(Locale.US, "%02X", hash[index]));
            }
            return builder.toString();
        } catch (Exception error) {
            return "";
        }
    }
}
