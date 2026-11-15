package org.haforcmf;

import android.annotation.SuppressLint;
import android.bluetooth.*;
import android.content.Context;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@SuppressLint("MissingPermission")
final class WatchLink implements AutoCloseable {
    static final int EVENT = 0xb0a6;
    private static final UUID CMD = UUID.fromString("0000fff2-0000-1000-8000-00805f9b34fb");
    private static final UUID NOTIFY = UUID.fromString("0000fff1-0000-1000-8000-00805f9b34fb");
    private static final UUID SHELL = UUID.fromString("77d4ff01-2fe2-2334-0d35-9ccd078f529c");
    private static final UUID SHELL_NOTIFY = UUID.fromString("77d4ff02-2fe2-2334-0d35-9ccd078f529c");
    private static final UUID DATA_NOTIFY = UUID.fromString("02f00000-0000-0000-0000-00000000ffe2");
    private static final UUID CCCD = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    private static final byte[] ACK = {(byte) 0xa5};
    private static final byte[] PHONE = Wire.join(ACK, "CMF Watch Pro 2".getBytes(StandardCharsets.US_ASCII));

    private final LinkedBlockingQueue<Integer> operations = new LinkedBlockingQueue<>();
    private final LinkedBlockingQueue<Wire.Frame> replies = new LinkedBlockingQueue<>(64);
    private final LinkedBlockingQueue<Wire.Frame> events = new LinkedBlockingQueue<>(32);
    private final LinkedBlockingQueue<byte[]> shell = new LinkedBlockingQueue<>(32);
    private final Wire wire = new Wire();
    private BluetoothGatt gatt;
    private volatile boolean connected;
    private byte[] key;

    private final BluetoothGattCallback callbacks = new BluetoothGattCallback() {
        @Override public void onConnectionStateChange(BluetoothGatt client, int status, int state) {
            connected = status == 0 && state == BluetoothProfile.STATE_CONNECTED;
            operations.offer(connected ? 0 : -1);
        }
        @Override public void onServicesDiscovered(BluetoothGatt client, int status) { operations.offer(status); }
        @Override public void onMtuChanged(BluetoothGatt client, int mtu, int status) {
            operations.offer(status == 0 && mtu >= 160 ? 0 : -1);
        }
        @Override public void onDescriptorWrite(BluetoothGatt client, BluetoothGattDescriptor desc, int status) {
            operations.offer(status);
        }
        @Override public void onCharacteristicWrite(BluetoothGatt client, BluetoothGattCharacteristic c, int status) {
            operations.offer(status);
        }
        @Override public void onCharacteristicChanged(BluetoothGatt client, BluetoothGattCharacteristic c, byte[] data) {
            receive(c.getUuid(), data);
        }
        @Override public void onCharacteristicChanged(BluetoothGatt client, BluetoothGattCharacteristic c) {
            receive(c.getUuid(), c.getValue());
        }
    };

    private void receive(UUID id, byte[] data) {
        if (data == null) return;
        if (id.equals(SHELL_NOTIFY)) shell.offer(data.clone());
        if (!id.equals(NOTIFY)) return;
        wire.append(data);
        for (Wire.Frame frame; (frame = wire.next()) != null;) {
            (frame.command() == EVENT ? events : replies).offer(frame);
        }
    }

    private void awaitOperation() throws Exception {
        Integer status = operations.poll(15, TimeUnit.SECONDS);
        if (status == null || status != 0 || !connected) throw new IOException("Bluetooth connection failed");
    }

    void connect(Context context, BluetoothDevice device) throws Exception {
        gatt = device.connectGatt(context, false, callbacks, BluetoothDevice.TRANSPORT_LE);
        awaitOperation();
        if (!gatt.discoverServices()) throw new IOException("Cannot discover watch services");
        awaitOperation();
        if (!gatt.requestMtu(247)) throw new IOException("Cannot request Bluetooth MTU");
        awaitOperation();
        subscribe(NOTIFY);
        subscribe(SHELL_NOTIFY);
        subscribe(DATA_NOTIFY);
        Thread.sleep(600);
    }

    private BluetoothGattCharacteristic find(UUID id) throws IOException {
        for (BluetoothGattService service : gatt.getServices()) {
            BluetoothGattCharacteristic c = service.getCharacteristic(id);
            if (c != null) return c;
        }
        throw new IOException("WP2 Bluetooth service missing");
    }

    private void subscribe(UUID id) throws Exception {
        var c = find(id);
        var d = c.getDescriptor(CCCD);
        if (d == null || !gatt.setCharacteristicNotification(c, true)) throw new IOException("Cannot subscribe");
        d.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        if (!gatt.writeDescriptor(d)) throw new IOException("Cannot enable notification");
        awaitOperation();
    }

    private void write(UUID id, byte[] value) throws Exception {
        var c = find(id);
        c.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
        c.setValue(value);
        if (!gatt.writeCharacteristic(c)) throw new IOException("Cannot write to watch");
        awaitOperation();
    }

    private byte[] exchange(int cmd, byte[] data, int expected, byte[] cryptoKey, long seconds) throws Exception {
        write(CMD, Wire.frame(cmd, data, cryptoKey));
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
        while (System.nanoTime() < end && connected) {
            Wire.Frame next = replies.poll(Math.max(1, end - System.nanoTime()), TimeUnit.NANOSECONDS);
            if (next != null && next.command() == expected) {
                if (cryptoKey == null) return next.body();
                try { return Wire.decrypt(cryptoKey, next.body()); }
                catch (java.security.GeneralSecurityException | IllegalArgumentException ignored) { }
            }
        }
        throw new TimeoutException("Watch did not answer");
    }

    void bind() throws Exception {
        write(SHELL, "AT GETSECRET\r\n".getBytes(StandardCharsets.US_ASCII));
        StringBuilder response = new StringBuilder();
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(7);
        while (System.nanoTime() < end && !response.toString().contains(",OK")) {
            byte[] bytes = shell.poll(Math.max(1, end - System.nanoTime()), TimeUnit.NANOSECONDS);
            if (bytes != null) response.append(new String(bytes, StandardCharsets.US_ASCII));
        }
        var match = java.util.regex.Pattern.compile("GETSECRET:([a-fA-F0-9]{32}),OK").matcher(response);
        if (!match.find()) throw new IOException("Watch did not provide its pairing response");
        byte[] secret = Wire.hex(match.group(1));
        byte[] first = new byte[16];
        new SecureRandom().nextBytes(first);
        byte[] challenge = exchange(0x8047, Wire.join(first, Wire.hash(first, secret)), 0x0048, null, 6);
        if (challenge.length != 48) throw new IOException("Invalid watch pairing response");
        byte[] second = Arrays.copyOf(challenge, 16);
        if (!java.security.MessageDigest.isEqual(Wire.hash(second, secret), Arrays.copyOfRange(challenge, 16, 48))) {
            throw new IOException("Watch rejected pairing");
        }
        byte[] auth = Arrays.copyOf(Wire.hash(first, second, secret), 16);
        try { exchange(0x8049, PHONE, 0x0049, auth, 2); }
        catch (TimeoutException ignored) { exchange(0x8049, PHONE, 0x0049, auth, 6); }
        byte[] nonce = exchange(0x804b, ACK, 0x004c, auth, 6);
        if (nonce.length < 16) throw new IOException("Invalid watch session response");
        key = Arrays.copyOf(Wire.hash(Arrays.copyOf(nonce, 16), auth), 16);
        exchange(0x804d, ACK, 0x0004, key, 6);
        exchange(0x90a0, new byte[]{(byte) 0xff}, 0xb0a0, key, 3);
    }

    private byte[] checked(int cmd, byte[] payload) throws Exception {
        byte[] response = exchange(cmd, payload, cmd | 0xa000, key, 6);
        if (response.length == 0 || response[0] != 0) {
            throw new IOException("Watch refused operation. Exit the watch app before updating.");
        }
        return Arrays.copyOfRange(response, 1, response.length);
    }

    private byte[] packageHeader(int slot) throws Exception {
        byte[] data = checked(0x90a0, new byte[]{(byte) slot});
        if (data.length != 0 && (data.length != 64 || data[0] != 'W' || data[1] != 'P'
                || data[2] != '2' || data[3] != 'A')) throw new IOException("Unexpected package header");
        return data;
    }

    private static boolean isOurApp(byte[] header) {
        if (header.length < 64 || header[6] != 0) return false;
        int end = 8;
        while (end < 32 && header[end] != 0) end++;
        return "ha-for-cmf".equals(new String(header, 8, end - 8, StandardCharsets.US_ASCII));
    }

    int findApp() throws Exception {
        for (int slot = 0; slot < 8; slot++) {
            byte[] header = packageHeader(slot);
            if (isOurApp(header) && ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN).getInt(32) == 2) return slot;
        }
        throw new IOException("Install the bundled watch app first.");
    }

    int installApp(byte[] data) throws Exception {
        if (!isOurApp(data) || data.length > 140_000) throw new IOException("Invalid bundled watch app");
        if (packageHeader(255).length == 0) throw new IOException("Install the WP2 Apps loader first.");
        int slot = -1;
        for (int i = 0; i < 8; i++) {
            byte[] header = packageHeader(i);
            if (isOurApp(header)) { slot = i; break; }
            if (slot == -1 && header.length == 0) slot = i;
        }
        if (slot == -1) throw new IOException("No free watch app slot.");
        var crc = new CRC32();
        crc.update(data);
        checked(0x90a1, ByteBuffer.allocate(9).order(ByteOrder.LITTLE_ENDIAN)
                .put((byte) slot).putInt(data.length).putInt((int) crc.getValue()).array());
        for (int offset = 0; offset < data.length;) {
            int size = Math.min(128, data.length - offset);
            byte[] chunk = ByteBuffer.allocate(4 + size).order(ByteOrder.LITTLE_ENDIAN)
                    .putInt(offset).put(data, offset, size).array();
            offset += size;
            byte[] reply = checked(0x90a2, chunk);
            if (reply.length != 4 || ByteBuffer.wrap(reply).order(ByteOrder.LITTLE_ENDIAN).getInt() != offset) {
                throw new IOException("Watch rejected a package chunk");
            }
        }
        checked(0x90a3, new byte[]{0});
        if (!isOurApp(packageHeader(slot))) throw new IOException("Watch app not listed after transfer");
        return slot;
    }

    void ping() throws Exception { exchange(0x90a7, new byte[]{0}, 0xb0a7, key, 5); }

    byte[] pollEvent() {
        for (Wire.Frame event; (event = events.poll()) != null;) {
            try { return Wire.decrypt(key, event.body()); }
            catch (Exception ignored) { }
        }
        return null;
    }

    void send(int slot, byte[] message) throws Exception {
        exchange(0x90a5, Wire.join(new byte[]{(byte) slot}, message), 0xb0a5, key, 5);
    }

    @Override public void close() {
        if (gatt != null) { gatt.disconnect(); gatt.close(); }
    }
}
