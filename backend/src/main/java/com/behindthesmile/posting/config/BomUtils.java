package com.behindthesmile.posting.config;

final class BomUtils {
    private BomUtils() {
    }

    static byte[] stripBom(byte[] body) {
        if (body == null || body.length == 0) {
            return body;
        }

        int bomLength = detectBomLength(body);
        if (bomLength == 0) {
            return body;
        }

        byte[] sanitized = new byte[body.length - bomLength];
        System.arraycopy(body, bomLength, sanitized, 0, sanitized.length);
        return sanitized;
    }

    private static int detectBomLength(byte[] body) {
        if (startsWith(body, (byte) 0x00, (byte) 0x00, (byte) 0xFE, (byte) 0xFF)) {
            return 4;
        }
        if (startsWith(body, (byte) 0xFF, (byte) 0xFE, (byte) 0x00, (byte) 0x00)) {
            return 4;
        }
        if (startsWith(body, (byte) 0xEF, (byte) 0xBB, (byte) 0xBF)) {
            return 3;
        }
        if (startsWith(body, (byte) 0xFE, (byte) 0xFF)) {
            return 2;
        }
        if (startsWith(body, (byte) 0xFF, (byte) 0xFE)) {
            return 2;
        }
        return 0;
    }

    private static boolean startsWith(byte[] body, byte... prefix) {
        if (body.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (body[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }
}
