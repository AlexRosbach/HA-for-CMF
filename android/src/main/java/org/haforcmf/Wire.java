package org.haforcmf;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.zip.CRC32;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

final class Wire {
    record Frame(int command, byte[] body) {}
    static final byte[] ZERO = new byte[16];
    private static final byte[] IV = hex("5051525354555657606162636465665a");
    private byte[] buffer = new byte[0];

    static byte[] hex(String value) {
        byte[] bytes = new byte[value.length() / 2];
        for (int i = 0; i < bytes.length; i++) bytes[i] = (byte) Integer.parseInt(value.substring(2 * i, 2 * i + 2), 16);
        return bytes;
    }

    static byte[] join(byte[] first, byte[] second) {
        byte[] out = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, out, first.length, second.length);
        return out;
    }

    static byte[] hash(byte[]... inputs) throws Exception {
        var md = MessageDigest.getInstance("SHA-256");
        for (byte[] input : inputs) md.update(input);
        return md.digest();
    }

    private static byte[] checked(byte[] value) {
        var crc = new CRC32();
        crc.update(value);
        return ByteBuffer.allocate(value.length + 4).order(ByteOrder.LITTLE_ENDIAN)
                .put(value).putInt((int) crc.getValue()).array();
    }

    private static Cipher cipher(int mode, byte[] key) throws Exception {
        var cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(mode, new SecretKeySpec(key, "AES"), new IvParameterSpec(IV));
        return cipher;
    }

    static byte[] decrypt(byte[] key, byte[] encrypted) throws Exception {
        byte[] decoded = cipher(Cipher.DECRYPT_MODE, key).doFinal(encrypted);
        if (decoded.length < 4) throw new IllegalArgumentException("Missing message checksum");
        byte[] value = Arrays.copyOf(decoded, decoded.length - 4);
        if (!Arrays.equals(decoded, checked(value))) throw new IllegalArgumentException("Bad message checksum");
        return value;
    }

    static byte[] frame(int cmd, byte[] value, byte[] key) throws Exception {
        byte[] body = checked(value);
        if (key != null) body = cipher(Cipher.ENCRYPT_MODE, key).doFinal(body);
        return ByteBuffer.allocate(11 + body.length).put((byte) 0xf5).putShort((short) body.length)
                .putShort((short) 0xffff).putShort((short) 1).putShort((short) 1)
                .putShort((short) cmd).put(body).array();
    }

    synchronized void append(byte[] value) { buffer = join(buffer, value); }

    synchronized Frame next() {
        while (buffer.length > 0 && buffer[0] != (byte) 0xf5) buffer = Arrays.copyOfRange(buffer, 1, buffer.length);
        if (buffer.length < 11) return null;
        ByteBuffer header = ByteBuffer.wrap(buffer);
        int size = Short.toUnsignedInt(header.getShort(1));
        int cmd = Short.toUnsignedInt(header.getShort(9));
        if (cmd == 0x0048) size -= 4;
        if (size < 0 || size > 4096) {
            buffer = new byte[0];
            return null;
        }
        if (buffer.length < 11 + size) return null;
        byte[] body = Arrays.copyOfRange(buffer, 11, 11 + size);
        buffer = Arrays.copyOfRange(buffer, 11 + size, buffer.length);
        return new Frame(cmd, body);
    }
}
