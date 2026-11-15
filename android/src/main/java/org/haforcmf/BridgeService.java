package org.haforcmf;

import android.annotation.SuppressLint;
import android.app.*;
import android.bluetooth.BluetoothManager;
import android.content.Intent;
import android.os.IBinder;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@SuppressLint("MissingPermission")
public final class BridgeService extends Service {
    private static final String CHANNEL = "wp2-bridge";
    static volatile String status = "Bridge stopped";
    private Thread worker;

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public int onStartCommand(Intent intent, int flags, int id) {
        if (worker != null) return START_NOT_STICKY;
        if (intent == null) { stopSelf(); return START_NOT_STICKY; }
        var manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel(CHANNEL, "Watch bridge", NotificationManager.IMPORTANCE_LOW));
        var open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        var notice = new Notification.Builder(this, CHANNEL)
                .setContentTitle("HA for CMF").setContentText("Watch bridge running")
                .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth).setContentIntent(open)
                .setOngoing(true).build();
        startForeground(1, notice);
        status = "Connecting to watch…";
        worker = new Thread(() -> loop(intent, id), "Watch bridge");
        worker.start();
        return START_NOT_STICKY;
    }

    static byte[] snapshot(char kind, List<String> wanted, Map<String, Home.Entity> states) {
        var out = new ByteArrayOutputStream();
        writeAscii(out, "ha2" + kind);
        out.write(wanted.size());
        for (String id : wanted) {
            Home.Entity entry = states.get(id);
            String fallback = id.substring(id.indexOf('.') + 1).replace('_', ' ');
            String title = Home.ascii(entry == null ? fallback : entry.label(), 10);
            if (title.isEmpty()) title = Home.ascii(fallback, 10);
            String value = entry == null ? "Offline" : entry.display();
            out.write(entry != null && entry.canSwitch()
                    ? ("on".equals(entry.state()) ? 3 : 1) : 0);
            out.write(title.length());
            out.write(value.length());
            writeAscii(out, title);
            writeAscii(out, value);
        }
        return out.toByteArray();
    }

    private static void writeAscii(ByteArrayOutputStream out, String text) {
        byte[] bytes = text.getBytes(StandardCharsets.US_ASCII);
        out.write(bytes, 0, bytes.length);
    }

    private void loop(Intent intent, int startId) {
        try (var link = new WatchLink()) {
            var bt = getSystemService(BluetoothManager.class).getAdapter();
            link.connect(this, bt.getRemoteDevice(intent.getStringExtra("address")));
            link.bind();
            if (intent.getBooleanExtra("install", false)) {
                status = "Installing the bundled app. Leave the watch nearby.";
                try (var input = getAssets().open("ha-for-cmf.wp2app")) {
                    int installed = link.installApp(Home.readBytes(input));
                    status = "Watch app installed in slot " + installed + ". Dismiss Paired, then start the bridge.";
                }
                return;
            }
            var home = new Home(intent.getStringExtra("url"), intent.getStringExtra("token"));
            var ids = List.of(intent.getStringArrayExtra("entities"));
            if (ids.isEmpty() || ids.size() > 4) throw new IllegalArgumentException("Choose one to four entities");
            int slot = link.findApp();
            status = "Ready. Dismiss Paired on the watch, then open ha-for-cmf.";
            while (!Thread.currentThread().isInterrupted()) {
                link.ping();
                for (byte[] event; (event = link.pollEvent()) != null;) {
                    if (event.length < 6 || Byte.toUnsignedInt(event[1]) != slot) continue;
                    String command = new String(event, 2, 4, StandardCharsets.US_ASCII);
                    boolean action = event.length == 7 && ("ha2U".equals(command) || "ha2D".equals(command));
                    if (!action && !(event.length == 6 && "ha2R".equals(command))) continue;
                    Map<String, Home.Entity> states;
                    try {
                        states = home.states();
                        if (action) {
                            int index = Byte.toUnsignedInt(event[6]);
                            Home.Entity entity = index < ids.size() ? states.get(ids.get(index)) : null;
                            if (entity != null && entity.canSwitch()) home.set(entity, "ha2U".equals(command));
                            states = home.states();
                        }
                    } catch (Exception ex) {
                        states = Map.of();
                    }
                    link.send(slot, snapshot(action ? 'A' : 'S', ids, states));
                }
                Thread.sleep(400);
            }
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
            status = "Bridge stopped";
        } catch (Exception ex) {
            status = ex instanceof java.io.IOException ? ex.getMessage()
                    : "Connection ended. Wake the watch, close Nothing X and try again.";
        } finally {
            stopSelf(startId);
        }
    }

    @Override public void onDestroy() {
        if (worker != null) worker.interrupt();
        super.onDestroy();
    }
}
