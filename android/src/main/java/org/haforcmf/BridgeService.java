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
    private static volatile long pagesVersion = 0;
    static void notifyPagesChanged() { pagesVersion++; }
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

    static byte[] snapshot(char kind, Page page, Map<String, Home.Entity> states) {
        var out = new ByteArrayOutputStream();
        writeAscii(out, "ha2" + kind);
        out.write(page.ids().size());
        for (int i=0;i<page.ids().size();i++) {
            String id = page.ids().get(i);
            if (id.isBlank()) {
                out.write(0);out.write(0);out.write(0);
                continue;
            }
            Home.Entity entry = states.get(id);
            String fallback = id.substring(id.indexOf('.') + 1).replace('_', ' ');
            String alias = page.labels().size()>i ? page.labels().get(i) : "";
            String actualName = alias.isBlank()
                    ? (entry == null ? fallback : entry.label()) : alias;
            String title = Home.ascii(WatchPages.shortLabel(actualName), 10);
            if (title.isBlank()) title = Home.ascii(fallback,10);
            String value = Home.ascii(entry==null ? "Offline" : entry.display(),10);
            int flags = 0;
            if (entry != null && entry.isLock()) {
                if ("locked".equals(entry.state())) flags=5;
                else if ("unlocked".equals(entry.state())) flags=7;
            } else if (entry != null && entry.canSwitch()) {
                flags = "on".equals(entry.state()) ? 3 : 1;
            }
            out.write(flags);
            out.write(title.length());
            out.write(value.length());
            writeAscii(out,title);
            writeAscii(out,value);
        }
        return out.toByteArray();
    }

    record Page(String room, List<String> ids, List<String> labels) {}

    static List<Page> pagesFor(WatchPages config) {
        var pages = new ArrayList<Page>();
        for(WatchPages.Page source:config.pages){
            List<String> ids=new ArrayList<>(4),labels=new ArrayList<>(4);
            for(int i=0;i<WatchPages.SLOTS;i++){
                ids.add(source.ids[i]);
                labels.add(source.labels[i]);
            }
            pages.add(new Page(source.title,List.copyOf(ids),List.copyOf(labels)));
        }
        if(pages.isEmpty()){
            pages.add(new Page("Favoriten",
                    List.of("","","",""),List.of("","","","")));
        }
        return pages;
    }

    static byte[] snapshotPage(char kind, Page page, int index, int total,
            Map<String, Home.Entity> states, int uiFlags) {
        var out = new ByteArrayOutputStream();
        byte[] tiles = snapshot(kind, page, states);
        out.write(tiles, 0, tiles.length);
        String label = Home.ascii(page.room(), 18);
        out.write(index);
        out.write(total);
        out.write(label.length());
        writeAscii(out, label);
        out.write(uiFlags);
        return out.toByteArray();
    }

    private int watchDisplayFlags() {
        boolean english=LocaleKit.english(this);
        String theme=SettingsStore.load(this,"theme");
        boolean light="light".equals(theme);
        if(!"light".equals(theme) && !"dark".equals(theme)){
            light=(getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                != android.content.res.Configuration.UI_MODE_NIGHT_YES;
        }
        return (english?1:0)|(light?2:0);
    }

    private static void writeAscii(ByteArrayOutputStream out, String text) {
        byte[] bytes = text.getBytes(StandardCharsets.US_ASCII);
        out.write(bytes, 0, bytes.length);
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

    static List<String> byCategory(Home.Catalog catalog, byte category) {
        var results=new ArrayList<Home.Entity>();
        for(Home.Entity entity:catalog.entities()){
            String domain=entity.domain();
            boolean include=switch(category){
                case 'L' -> domain.equals("light");
                case 'S' -> domain.equals("switch") || domain.equals("input_boolean");
                case 'C' -> domain.equals("scene") || domain.equals("script")
                        || domain.equals("button") || domain.equals("input_button");
                case 'T' -> domain.equals("sensor") || domain.equals("binary_sensor");
                default -> false;
            };
            if(include) results.add(entity);
        }
        results.sort((a,b)->a.label().compareToIgnoreCase(b.label()));
        var ids=new ArrayList<String>();
        for(Home.Entity entity:results) ids.add(entity.id());
        return ids;
    }
    static String categoryName(byte category){
        return switch(category){
            case 'L' -> "LICHTER";
            case 'S' -> "SCHALTER";
            case 'C' -> "SZENEN";
            case 'T' -> "SENSOREN";
            default -> "SUCHE";
        };
    }
    private static Page searchPage(List<String> results,String title,int pageIndex){
        var ids=new ArrayList<String>();
        var labels=new ArrayList<String>();
        for(int slot=0;slot<4;slot++){
            int at=pageIndex*4+slot;
            ids.add(at<results.size()?results.get(at):"");
            labels.add("");
        }
        return new Page(title,List.copyOf(ids),List.copyOf(labels));
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
            String[] legacy = intent.getStringArrayExtra("entities");
            String[] areaLabels=intent.getStringArrayExtra("rooms");
            var ids=legacy==null?List.<String>of():List.of(legacy);
            var rooms=new java.util.HashMap<String,String>();
            if(areaLabels!=null)for(int i=0;i<Math.min(ids.size(),areaLabels.length);i++)
                rooms.put(ids.get(i),areaLabels[i]);
            var pages=pagesFor(WatchPages.load(this,ids,rooms));
            long currentVersion=pagesVersion;
            Home.Catalog catalog=home.catalog();
            boolean searching=false;
            List<String> matches=List.of();
            byte category='L';
            int searchIndex=0;
            int pageIndex=0;
            int slot = link.findApp();
            status = "Ready. Dismiss Paired on the watch, then open ha-for-cmf.";
            while (!Thread.currentThread().isInterrupted()) {
                if(pagesVersion!=currentVersion){
                    currentVersion=pagesVersion;
                    pages=pagesFor(WatchPages.load(this,ids,rooms));
                    pageIndex=Math.min(pageIndex,pages.size()-1);
                }
                link.ping();
                for (byte[] event; (event = link.pollEvent()) != null;) {
                    if (event.length < 6 || Byte.toUnsignedInt(event[1]) != slot) continue;
                    String command = new String(event, 2, 4, StandardCharsets.US_ASCII);
                    boolean action=event.length==7 &&
                            ("ha2U".equals(command)||"ha2D".equals(command));
                    boolean navigate=event.length==6 &&
                            ("ha2R".equals(command)||"ha2N".equals(command)
                            ||"ha2P".equals(command)||"ha2H".equals(command));
                    boolean chooseCategory=event.length==7 && "ha2Q".equals(command);
                    if(!action&&!navigate&&!chooseCategory)continue;
                    if(chooseCategory){
                        category=event[6];
                        searching=true;
                        searchIndex=0;
                        matches=byCategory(catalog,category);
                    }
                    if("ha2H".equals(command))searching=false;
                    if("ha2N".equals(command)){
                        if(searching){
                            searchIndex=(searchIndex+1)%Math.max(1,(matches.size()+3)/4);
                        } else pageIndex=(pageIndex+1)%pages.size();
                    }
                    if("ha2P".equals(command)){
                        if(searching){
                            int total=Math.max(1,(matches.size()+3)/4);
                            searchIndex=(searchIndex+total-1)%total;
                        } else pageIndex=(pageIndex+pages.size()-1)%pages.size();
                    }
                    Page page=searching ?
                            searchPage(matches,categoryName(category),searchIndex)
                            : pages.get(pageIndex);
                    Map<String, Home.Entity> states;
                    try{
                        states=home.states();
                        if(action){
                            int index=Byte.toUnsignedInt(event[6]);
                            Home.Entity entity=index<page.ids().size()?
                                    states.get(page.ids().get(index)):null;
                            if(entity!=null){
                                if(entity.isLock()){
                                    notifyLockConfirmation(entity,"ha2U".equals(command));
                                } else if(entity.canSwitch()){
                                    home.set(entity,"ha2U".equals(command));
                                }
                            }
                            states=home.states();
                        }
                    }catch(Exception ex){
                        states=Map.of();
                    }
                    int current=searching?searchIndex:pageIndex;
                    int total=searching?Math.max(1,(matches.size()+3)/4):pages.size();
                    link.send(slot,snapshotPage(action?'A':'S',
                            page,current,total,states,watchDisplayFlags()));
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
