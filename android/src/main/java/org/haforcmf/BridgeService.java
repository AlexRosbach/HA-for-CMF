package org.haforcmf;

import android.annotation.SuppressLint;
import android.app.*;
import android.bluetooth.BluetoothManager;
import android.content.Intent;
import android.content.Context;
import android.os.SystemClock;
import android.os.IBinder;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

@SuppressLint("MissingPermission")
public final class BridgeService extends Service {
    private static final String CHANNEL = "wp2-bridge";
    static volatile String status = "Bridge stopped";
    private Thread worker;
    static final String ACTION_CONFIRM_LOCK = "org.haforcmf.CONFIRM_LOCK";
    private volatile String pendingLockId;
    private volatile boolean pendingUnlock;
    private volatile long pendingLockAt;
    private volatile String currentUrl;
    private volatile String currentToken;

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public int onStartCommand(Intent intent, int flags, int id) {
        if (intent != null && ACTION_CONFIRM_LOCK.equals(intent.getAction())) {
            final String requested=intent.getStringExtra("lock_id");
            final boolean unlock=intent.getBooleanExtra("unlock",false);
            if (requested != null && requested.equals(pendingLockId)
                    && pendingUnlock==unlock
                    && SystemClock.elapsedRealtime()-pendingLockAt<45000
                    && currentUrl!=null && currentToken!=null) {
                pendingLockId=null;
                new Thread(()->{
                    try {
                        new Home(currentUrl,currentToken).setLock(requested,unlock);
                        status="Schlossaktion bestätigt: "+requested;
                    } catch(Exception ex){status="Schlossaktion fehlgeschlagen: "+ex.getMessage();}
                },"Confirmed watch lock").start();
            }
            return START_NOT_STICKY;
        }
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
            String value = Home.ascii(entry == null ? "Offline" : entry.display(), 10);
            int flags = 0;
            if (entry != null && entry.isLock()) {
                if ("locked".equals(entry.state())) flags = 5; // Action + confirmation required
                if ("unlocked".equals(entry.state())) flags = 7; // Active + confirmation required
            } else if (entry != null && entry.canSwitch()) {
                flags = ("on".equals(entry.state()) ? 3 : 1);
            }
            out.write(flags);
            out.write(title.length());
            out.write(value.length());
            writeAscii(out, title);
            writeAscii(out, value);
        }
        return out.toByteArray();
    }

    record Page(String room, List<String> ids) {}
    static List<Page> pagesFor(List<String> ids, String[] roomNames) {
        var groups = new LinkedHashMap<String, List<String>>();
        for (int i = 0; i < ids.size(); i++) {
            String name = roomNames != null && i < roomNames.length ? roomNames[i] : "Favoriten";
            if (name == null || name.isBlank()) name = "Favoriten";
            groups.computeIfAbsent(name, key -> new ArrayList<>()).add(ids.get(i));
        }
        var pages = new ArrayList<Page>();
        for (var group : groups.entrySet()) {
            var members = group.getValue();
            for (int start = 0; start < members.size(); start += 4) {
                int end = Math.min(start + 4, members.size());
                pages.add(new Page(group.getKey(), List.copyOf(members.subList(start, end))));
            }
        }
        return pages;
    }

    static byte[] snapshotPage(char kind, Page page, int index, int total,
            Map<String, Home.Entity> states) {
        var out = new ByteArrayOutputStream();
        byte[] tiles = snapshot(kind, page.ids(), states);
        out.write(tiles, 0, tiles.length);
        String label = Home.ascii(page.room(), 18);
        out.write(index);
        out.write(total);
        out.write(label.length());
        writeAscii(out, label);
        return out.toByteArray();
    }

    private static void writeAscii(ByteArrayOutputStream out, String text) {
        byte[] bytes = text.getBytes(StandardCharsets.US_ASCII);
        out.write(bytes, 0, bytes.length);
    }

    private void notifyWatchVoice() {
        var intent=new Intent(this,MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra("watch_voice",true);
        var pending=PendingIntent.getActivity(this,41,intent,
                PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        var note=new Notification.Builder(this,CHANNEL)
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentTitle("HA for CMF · Spracheingabe")
                .setContentText("Tippen, um am Handy mit Home Assistant zu sprechen")
                .setContentIntent(pending).setAutoCancel(true).build();
        getSystemService(NotificationManager.class).notify(41,note);
    }

    private void notifyLockConfirmation(Home.Entity entity, boolean unlock) {
        pendingLockId=entity.id();
        pendingUnlock=unlock;
        pendingLockAt=SystemClock.elapsedRealtime();
        var intent=new Intent(this,MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra("watch_lock_id",entity.id())
                .putExtra("watch_lock_unlock",unlock)
                .putExtra("watch_lock_title",entity.label());
        var pending=PendingIntent.getActivity(this,42,intent,
                PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        var note=new Notification.Builder(this,CHANNEL)
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .setContentTitle("Schlossaktion am Handy bestätigen")
                .setContentText(entity.label()+": "+(unlock?"Entriegeln":"Verriegeln")+"?")
                .setContentIntent(pending).setAutoCancel(true).build();
        getSystemService(NotificationManager.class).notify(42,note);
        status="Schlossaktion wartet auf Bestätigung am Handy";
    }

    static List<String> searchEntities(Home.Catalog catalog,String query) {
        String normalized=Home.ascii(query,64).toLowerCase(java.util.Locale.ROOT);
        ArrayList<String> results=new ArrayList<>();
        for (Home.Entity entity : catalog.entities()) {
            String data=Home.ascii(entity.label()+" "+entity.id()+" "+entity.areaName(),100)
                    .toLowerCase(java.util.Locale.ROOT);
            boolean matches=true;
            for(String word:normalized.trim().split("\\s+")) {
                if (!word.isBlank() && !data.contains(word)){matches=false;break;}
            }
            if(matches)results.add(entity.id());
        }
        return results;
    }

    private void sendWatchAnswer(WatchLink link,int slot,String answer) throws Exception {
        byte[] data=("ha2Y"+Home.ascii(answer,93)).getBytes(StandardCharsets.US_ASCII);
        link.send(slot,data);
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
            currentUrl=intent.getStringExtra("url");
            currentToken=intent.getStringExtra("token");
            var ids = List.of(intent.getStringArrayExtra("entities"));
            if (ids.isEmpty() || ids.size() > 24) throw new IllegalArgumentException("Choose one to 24 entities");
            var pages = pagesFor(ids, intent.getStringArrayExtra("rooms"));
            Home.Catalog catalog=home.catalog();
            boolean searching=false;
            String query="";
            List<String> matching=List.of();
            String voiceAgent=intent.getStringExtra("voice_agent");
            int pageIndex = 0;
            int slot = link.findApp();
            status = "Ready. Dismiss Paired on the watch, then open ha-for-cmf.";
            while (!Thread.currentThread().isInterrupted()) {
                link.ping();
                for (byte[] event; (event = link.pollEvent()) != null;) {
                    if (event.length < 6 || Byte.toUnsignedInt(event[1]) != slot) continue;
                    String command = new String(event, 2, 4, StandardCharsets.US_ASCII);
                    boolean action = event.length == 7 && ("ha2U".equals(command) || "ha2D".equals(command));
                    boolean navigate = event.length == 6 && ("ha2R".equals(command)
                            || "ha2N".equals(command) || "ha2P".equals(command)
                            || "ha2H".equals(command));
                    boolean find = event.length >= 6 && "ha2Q".equals(command);
                    boolean textQuery = event.length > 6 && "ha2T".equals(command);
                    boolean voiceRequest = event.length == 6 && "ha2V".equals(command);
                    if (!action && !navigate && !find && !textQuery && !voiceRequest) continue;
                    if (voiceRequest) {
                        notifyWatchVoice();
                        sendWatchAnswer(link,slot,"Bitte am Handy sprechen");
                        continue;
                    }
                    if (textQuery) {
                        String input=new String(event,6,event.length-6,StandardCharsets.US_ASCII).trim();
                        try {
                            Home.ConversationReply reply=home.converse(input,voiceAgent,"");
                            sendWatchAnswer(link,slot,reply.text());
                        } catch(Exception ex) {
                            sendWatchAnswer(link,slot,"Assist Fehler");
                        }
                        continue;
                    }
                    if (find) {
                        query=new String(event,6,event.length-6,StandardCharsets.US_ASCII).trim();
                        if (query.length()>16)query=query.substring(0,16);
                        searching=true;
                        catalog=home.catalog();
                        matching=searchEntities(catalog,query);
                    }
                    if ("ha2H".equals(command)) searching=false;
                    if (!searching) {
                        if ("ha2N".equals(command)) pageIndex=(pageIndex+1)%pages.size();
                        if ("ha2P".equals(command)) pageIndex=(pageIndex+pages.size()-1)%pages.size();
                    }
                    Page page;
                    if (searching) {
                        // The watch sends a narrowed query. It displays three results at a time.
                        var hit=matching.subList(0,Math.min(3,matching.size()));
                        page=new Page(query.isEmpty()?"SUCHE":("SUCHE "+query),hit);
                    } else page=pages.get(pageIndex);
                    Map<String, Home.Entity> states;
                    try {
                        states=home.states();
                        if (action) {
                            int index=Byte.toUnsignedInt(event[6]);
                            Home.Entity entity=index < page.ids().size()
                                    ? states.get(page.ids().get(index)) : null;
                            if (entity != null) {
                                if (entity.isLock()) {
                                    // BLE requests are untrusted. Never unlock directly from a watch frame.
                                    notifyLockConfirmation(entity,"ha2U".equals(command));
                                } else if(entity.canSwitch()) {
                                    home.set(entity,"ha2U".equals(command));
                                }
                            }
                            states=home.states();
                        }
                    } catch(Exception ex) {
                        states=Map.of();
                    }
                    int current=searching?0:pageIndex;
                    int total=searching?1:pages.size();
                    link.send(slot,snapshotPage(action?'A':'S',page,current,total,states));
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
            currentUrl=null;currentToken=null;pendingLockId=null;
            worker=null;
            stopSelf(startId);
        }
    }

    @Override public void onDestroy() {
        if (worker != null) worker.interrupt();
        super.onDestroy();
    }
}
