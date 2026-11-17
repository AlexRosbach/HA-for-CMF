package org.haforcmf;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.*;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanSettings;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.content.res.ColorStateList;
import java.util.Set;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.*;
import android.text.*;
import android.view.View;
import android.widget.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Locale;

@SuppressLint("MissingPermission")
public final class MainActivity extends Activity {
    private final Handler ui = new Handler(Looper.getMainLooper());
    private EditText url, token;
    private TextView selected, watch, status;
    private final List<String> ids = new ArrayList<>();
    private String address;
    private Runnable stopScan = () -> {};
    private static final int ORANGE = Color.rgb(255, 105, 25);
    private static final int SURFACE = Color.rgb(247, 248, 250);
    private LinearLayout content;
    private LinearLayout[] pages;
    private TextView[] tabLabels;
    private ScrollView screenScroll;
    private WatchPages watchPages;
    private WatchPageEditor watchEditor;
    private LinearLayout quickActions;
    private Spinner roomPicker;
    private Home.Catalog catalog;
    private String chosenRoomId = "";
    private final Map<String,String> favoriteRooms = new HashMap<>();
    private final List<String> roomOptions = new ArrayList<>();
    private final List<String> roomOptionIds = new ArrayList<>();
    private TextView scanState;

    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private GradientDrawable shape(int color, int corner) {
        var d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(corner));
        return d;
    }
    private LinearLayout card(LinearLayout parent){return UiKit.card(this,parent);}
    private void heading(LinearLayout parent, String title, String detail) {
        var h = text(parent, title);
        h.setTextSize(25);
        h.setTypeface(null, Typeface.BOLD);
        h.setTextColor(UiKit.INK);
        if (detail != null) {
            var description = text(parent, detail);
            description.setTextSize(14);
            description.setTextColor(UiKit.MUTED);
            description.setPadding(0, dp(6), 0, dp(10));
        }
    }

    @Override protected void onCreate(Bundle state) {
        boolean dark=UiKit.dark(this);
        setTheme(dark?android.R.style.Theme_Material_NoActionBar:
                android.R.style.Theme_Material_Light_NoActionBar);
        super.onCreate(state);
        UiKit.configure(this);
        getWindow().setStatusBarColor(UiKit.BG);
        getWindow().setNavigationBarColor(UiKit.BG);
        getWindow().getDecorView().setSystemUiVisibility(dark?0:
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);

        var root = UiKit.column(this);
        root.setBackgroundColor(UiKit.BG);
        // Android 15+ enforces edge-to-edge for target 35. Respect the real system
        // status/navigation bars instead of drawing the header under the clock.
        if (android.os.Build.VERSION.SDK_INT >= 35) {
            root.setOnApplyWindowInsetsListener((view, insets) -> {
                android.graphics.Insets safe = insets.getInsets(
                        android.view.WindowInsets.Type.systemBars()
                        | android.view.WindowInsets.Type.displayCutout());
                root.setPadding(0, safe.top, 0, safe.bottom);
                return insets;
            });
        }
        setContentView(root);
        root.requestApplyInsets();

        var top = UiKit.row(this);
        top.setPadding(dp(20),dp(12),dp(20),dp(8));
        top.setBackgroundColor(UiKit.CARD);
        var logo = UiKit.label(this,"⌂",24,true,UiKit.ORANGE);
        top.addView(logo,new LinearLayout.LayoutParams(dp(36),dp(40)));
        var name = UiKit.label(this,"HA for CMF",20,true,UiKit.INK);
        top.addView(name,new LinearLayout.LayoutParams(0,dp(44),1));
        root.addView(top);

        screenScroll = new ScrollView(this);
        screenScroll.setFillViewport(true);
        root.addView(screenScroll,new LinearLayout.LayoutParams(-1,0,1));
        content = UiKit.column(this);
        content.setPadding(dp(20),dp(10),dp(20),dp(24));
        screenScroll.addView(content);
        pages = new LinearLayout[4];
        for (int i=0;i<pages.length;i++) {
            pages[i] = UiKit.column(this);
            content.addView(pages[i]);
        }
        var homePage=pages[0];
        var entityPage=pages[1];
        var watchPage=pages[2];
        var settingsPage=pages[3];

        UiKit.title(this,homePage,"Räume & Geräte",
                "Dein Zuhause. Wähle einen Raum und steuere die Geräte.");
        var quickCard=card(homePage);
        var areaLabel=text(quickCard,"RAUM AUSWÄHLEN");
        areaLabel.setTextSize(12);areaLabel.setTextColor(UiKit.MUTED);
        roomPicker=new Spinner(this);
        roomPicker.setBackground(UiKit.bg(this,UiKit.CARD,12,UiKit.EDGE));
        roomOptions.add(LocaleKit.t(this,"Alle Räume"));
        roomOptionIds.add("");
        var roomsAdapter=new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_dropdown_item,roomOptions) {
            @Override public View getView(int position, View convertView,
                    android.view.ViewGroup parent) {
                var v=(TextView)super.getView(position,convertView,parent);
                v.setTextColor(UiKit.INK);v.setPadding(dp(12),0,dp(12),0);v.setBackgroundColor(UiKit.CARD);return v;
            }
        };
        roomPicker.setAdapter(roomsAdapter);
        quickCard.addView(roomPicker,new LinearLayout.LayoutParams(-1,dp(52)));
        roomPicker.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            @Override public void onItemSelected(android.widget.AdapterView<?> parent,
                    View view,int position,long id){
                if(position<roomOptionIds.size()){
                    String next=roomOptionIds.get(position);
                    if(!next.equals(chosenRoomId)){chosenRoomId=next;renderRoom();}
                }
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent){}
        });
        button(quickCard,"↻  Räume und Status aktualisieren",this::refreshQuickActions);
        quickActions=UiKit.column(this);
        quickCard.addView(quickActions);
        var empty=text(quickActions,"Home Assistant unter Einstellungen verbinden und Räume laden.");
        empty.setTextColor(UiKit.MUTED);

        UiKit.title(this,entityPage,"Entitäten-Browser",
                "Alle Geräte, Sensoren, Szenen und Schlösser übersichtlich.");
        var entityCard=card(entityPage);
        button(entityCard,"⌕  Alle Entitäten durchsuchen",this::chooseEntities);
        button(entityCard,"♥  Favoriten für die Uhr",this::showFavorites);
        selected=text(entityCard,"Noch keine Favoriten ausgewählt");
        String storedIds=SettingsStore.load(this,"favorites");
        if(!storedIds.isBlank()){
            for(String id:storedIds.split(",")){
                if(ids.size()>=24)break;
                if(id.matches("[a-z0-9_]+\\.[a-z0-9_]+"))ids.add(id);
            }
            selected.setText(ids.size()+" Favoriten für die Uhr");
        }
        selected.setTextColor(UiKit.MUTED);
        var info=text(entityPage,"Filtere nach Raum, Gerätetyp, Status und Favoriten. "
                +"Du kannst bis zu 24 Einträge für die Uhr festlegen.");
        info.setTextColor(UiKit.MUTED);
        info.setTextSize(14);

        watchPages=WatchPages.load(this,ids,favoriteRooms);
        watchEditor=new WatchPageEditor(this,watchPage,watchPages,new WatchPageEditor.Host(){
            @Override public void changed(WatchPages changed){
                changed.save(MainActivity.this);
                ids.clear();
                ids.addAll(changed.uniqueEntities());
                selected.setText(LocaleKit.english(MainActivity.this)
                        ? ids.size()+" favourites on "+changed.pages.size()+" pages"
                        : ids.size()+" Favoriten auf "+changed.pages.size()+" Seiten");
                persistSettings();
                BridgeService.notifyPagesChanged();
            }
            @Override public void fetchCatalog(java.util.function.Consumer<Home.Catalog> callback){
                loadCatalogForEditor(callback);
            }
        });
        UiKit.title(this,watchPage,"Uhr verbinden","Deine CMF Watch Pro 2 einrichten.");
        var connection=card(watchPage);
        var watchTitle=text(connection,"CMF Watch Pro 2");
        watchTitle.setTextSize(19);watchTitle.setTypeface(null,Typeface.BOLD);
        watch=text(connection,"Noch keine Uhr ausgewählt");
        String priorAddress=SettingsStore.load(this,"watch_address");
        if(!priorAddress.isBlank()){address=priorAddress;watch.setText("Gespeicherte Uhr: "+priorAddress);}
        watch.setTextColor(UiKit.MUTED);
        scanState=text(connection,"Bluetooth-Suche bereit");
        scanState.setTextSize(13);scanState.setTextColor(UiKit.MUTED);
        button(connection,"⌕  Uhr suchen",this::scan);
        var hint=text(connection,"Uhr aufwecken. Gegebenenfalls Nothing X vorübergehend trennen.");
        hint.setTextColor(UiKit.MUTED);hint.setTextSize(13);

        UiKit.title(this,watchPage,"Uhr-App & Bridge",
                "Installieren, synchronisieren und Verbindung prüfen.");
        var actions=card(watchPage);
        button(actions,"Uhr-App installieren / aktualisieren",()->{
            if(!bluetoothAllowed())return;
            if(address==null){toast("Bitte zuerst die Uhr auswählen.");return;}
            startForegroundService(new Intent(this,BridgeService.class)
                    .putExtra("install",true).putExtra("address",address));
        });
        button(actions,"Bridge starten",this::startBridge);
        button(actions,"Bridge stoppen",()->{
            stopService(new Intent(this,BridgeService.class));
            BridgeService.status="Bridge stopped";
        });
        status=text(actions,BridgeService.status);
        status.setTextColor(UiKit.MUTED);status.setTextSize(13);
        ui.post(new Runnable(){
            @Override public void run(){
                if(status!=null)status.setText(BridgeService.status);
                ui.postDelayed(this,1000);
            }
        });

        UiKit.title(this,settingsPage,"Sprache & Darstellung",
                "Für Handy und Uhr. Die Ansicht wird nach der Auswahl neu geladen.");
        var appearance=card(settingsPage);
        TextView languageLabel=UiKit.label(this,"Sprache",15,true,UiKit.INK);
        appearance.addView(languageLabel);
        Spinner languageSpinner=new Spinner(this);
        String[] languages={"Deutsch","English"};
        var languageAdapter=new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_dropdown_item,languages){
            @Override public View getView(int position,View recycled,android.view.ViewGroup parent){
                TextView view=(TextView)super.getView(position,recycled,parent);
                view.setTextColor(UiKit.INK);return view;
            }
        };
        languageSpinner.setAdapter(languageAdapter);
        appearance.addView(languageSpinner,new LinearLayout.LayoutParams(-1,dp(52)));
        languageSpinner.setSelection(LocaleKit.english(this)?1:0);
        languageSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            @Override public void onItemSelected(android.widget.AdapterView<?> parent,
                    View view,int position,long id){
                String next=position==1?"en":"de";
                String previous=SettingsStore.load(MainActivity.this,"language");
                if(previous.isBlank())previous="de";
                if(!next.equals(previous)){
                    persistSettings();
                    SettingsStore.save(MainActivity.this,"language",next);
                    BridgeService.notifyPagesChanged();
                    recreate();
                }
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent){}
        });
        TextView designLabel=UiKit.label(this,"Design",15,true,UiKit.INK);
        appearance.addView(designLabel);
        Spinner themeSpinner=new Spinner(this);
        String[] themes={"System","Hell","Dunkel"};
        var themeAdapter=new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_dropdown_item,themes){
            @Override public View getView(int position,View recycled,android.view.ViewGroup parent){
                TextView view=(TextView)super.getView(position,recycled,parent);
                view.setTextColor(UiKit.INK);view.setText(LocaleKit.t(MainActivity.this,themes[position]));
                return view;
            }
            @Override public View getDropDownView(int position,View recycled,android.view.ViewGroup parent){
                TextView view=(TextView)super.getDropDownView(position,recycled,parent);
                view.setText(LocaleKit.t(MainActivity.this,themes[position]));return view;
            }
        };
        themeSpinner.setAdapter(themeAdapter);
        appearance.addView(themeSpinner,new LinearLayout.LayoutParams(-1,dp(52)));
        String savedTheme=SettingsStore.load(this,"theme");
        themeSpinner.setSelection("light".equals(savedTheme)?1:"dark".equals(savedTheme)?2:0);
        themeSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            @Override public void onItemSelected(android.widget.AdapterView<?> parent,View view,int position,long id){
                String next=position==1?"light":position==2?"dark":"system";
                String previous=SettingsStore.load(MainActivity.this,"theme");
                if(previous.isBlank())previous="system";
                if(!next.equals(previous)){
                    persistSettings();
                    SettingsStore.save(MainActivity.this,"theme",next);
                    BridgeService.notifyPagesChanged();
                    recreate();
                }
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent){}
        });

        UiKit.title(this,settingsPage,"Home Assistant verbinden",
                "Verbindung zu deiner Home-Assistant-Instanz.");
        var homeCard=card(settingsPage);
        url=input(homeCard,"Home Assistant URL","https://homeassistant.example.com",
                InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);
        token=input(homeCard,"Zugriffstoken","",
                InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        token.setSaveEnabled(false);
        url.setText(SettingsStore.load(this,"ha_url").isBlank()?
                "https://homeassistant.example.com":SettingsStore.load(this,"ha_url"));
        token.setText(SettingsStore.load(this,"ha_token"));
        button(homeCard,"Verbindung testen und Räume laden",()->{
            showTab(0);
            refreshQuickActions();
        });
        var note=text(homeCard,"Token in Home Assistant → Profil → Sicherheit "
                +"→ Langlebige Zugriffstoken erstellen.");
        note.setTextColor(UiKit.MUTED);note.setTextSize(13);

        UiKit.title(this,settingsPage,"Sicherheit",
                "Türöffner und Schlösser werden nur nach Bestätigung auf dem Handy ausgeführt.");
        var security=card(settingsPage);
        var safe=text(security,"✓  Entriegeln und Tür öffnen erfordern eine ausdrückliche "
                +"Bestätigung mit vollständigem Schlossnamen. Der Watch-Bridge-Befehl allein öffnet keine Tür.");
        safe.setTextColor(UiKit.INK);
        safe.setTextSize(14);

        var nav=UiKit.row(this);
        nav.setPadding(dp(4),dp(4),dp(4),dp(9));
        nav.setBackgroundColor(UiKit.CARD);
        root.addView(nav,new LinearLayout.LayoutParams(-1,dp(68)));
        String[] tabs={"⌂\nZuhause","⌕\nEntitäten","◉\nUhr","⚙\nEinstellungen"};
        tabLabels=new TextView[4];
        for(int i=0;i<4;i++){
            final int index=i;
            TextView t=UiKit.label(this,tabs[i],11,true,UiKit.MUTED);
            t.setGravity(android.view.Gravity.CENTER);
            t.setBackground(UiKit.bg(this,UiKit.CARD,12,0));
            nav.addView(t,new LinearLayout.LayoutParams(0,-1,1));
            t.setOnClickListener(v->showTab(index));
            tabLabels[i]=t;
        }
        showTab(0);
        if(android.os.Build.VERSION.SDK_INT>=33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},1002);
        }
        handleWatchIntent(getIntent());
        if(SettingsStore.load(this,"welcome_seen").isBlank())showWelcome();
    }

    private void showWelcome(){
        Dialog welcome=new Dialog(this);
        welcome.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        LinearLayout panel=UiKit.column(this);
        panel.setPadding(dp(28),dp(70),dp(28),dp(32));
        panel.setBackgroundColor(UiKit.CARD);
        var spacer=new View(this);
        panel.addView(spacer,new LinearLayout.LayoutParams(-1,0,1));
        TextView icon=UiKit.label(this,"⌂",74,true,UiKit.ORANGE);
        icon.setGravity(android.view.Gravity.CENTER);
        panel.addView(icon);
        TextView title=UiKit.label(this,"HA for CMF",32,true,UiKit.INK);
        title.setGravity(android.view.Gravity.CENTER);
        panel.addView(title);
        TextView sub=UiKit.label(this,
                "Home Assistant auf deiner CMF Watch Pro 2",17,false,UiKit.MUTED);
        sub.setGravity(android.view.Gravity.CENTER);
        panel.addView(sub);
        var middle=new View(this);
        panel.addView(middle,new LinearLayout.LayoutParams(-1,0,1));
        UiKit.button(this,panel,"Los geht's  →",true,()->{
            SettingsStore.save(this,"welcome_seen","1");
            welcome.dismiss();
            showTab(3);
        });
        welcome.setContentView(panel);
        welcome.show();
        if(welcome.getWindow()!=null){
            welcome.getWindow().setLayout(-1,-1);
            welcome.getWindow().setBackgroundDrawable(UiKit.bg(this,UiKit.CARD,0,0));
        }
    }

    private void showTab(int index){
        if(pages==null)return;
        for(int i=0;i<pages.length;i++){
            pages[i].setVisibility(i==index?View.VISIBLE:View.GONE);
            if(tabLabels!=null && tabLabels[i]!=null){
                tabLabels[i].setTextColor(i==index?UiKit.INK:UiKit.MUTED);
                tabLabels[i].setBackground(UiKit.bg(this,
                        i==index?UiKit.PEACH:UiKit.CARD,12,0));
            }
        }
        if(screenScroll!=null)screenScroll.scrollTo(0,0);
    }

    private void persistSettings(){
        if(url!=null && token!=null){
            SettingsStore.save(this,"ha_url",url.getText().toString());
            SettingsStore.save(this,"ha_token",token.getText().toString());
        }
        SettingsStore.save(this,"favorites",String.join(",",ids));
        SettingsStore.save(this,"watch_address",address==null?"":address);
    }

    private void showFavorites(){
        showTab(2);
        Toast.makeText(this,"Favoritenseiten in der Vorschau bearbeiten.",Toast.LENGTH_LONG).show();
    }

    private void loadCatalogForEditor(java.util.function.Consumer<Home.Catalog> callback){
        if(catalog!=null){ callback.accept(catalog); return; }
        final Home home;
        try{home=new Home(url.getText().toString(),token.getText().toString());}
        catch(Exception ex){callback.accept(null);return;}
        new Thread(()->{
            try{
                Home.Catalog loaded=home.catalog();
                runOnUiThread(()->{
                    if(isDestroyed())return;
                    catalog=loaded;
                    if(watchEditor!=null)watchEditor.setCatalog(loaded);
                    callback.accept(loaded);
                });
            }catch(Exception ex){
                runOnUiThread(()->{
                    Toast.makeText(this,"Entitäten konnten nicht geladen werden: "+
                            ex.getMessage(),Toast.LENGTH_LONG).show();
                    callback.accept(null);
                });
            }
        },"Load Watch Preview Catalog").start();
    }

    private void handleWatchIntent(Intent intent) {
        if(intent==null)return;
        String lockId=intent.getStringExtra("watch_lock_id");
        if(lockId!=null && !lockId.isBlank()) {
            String name=intent.getStringExtra("watch_lock_title");
            boolean unlock=intent.getBooleanExtra("watch_lock_unlock",false);
            intent.removeExtra("watch_lock_id");
            showTab(0);
            new AlertDialog.Builder(this)
                    .setTitle(LocaleKit.t(this,"Schlossaktion von Uhr bestätigen?"))
                    .setMessage((name==null?lockId:name)+"\n"+lockId+"\n\n"
                            +(unlock?"Schloss entriegeln?":"Schloss verriegeln?")
                            +"\nDie Aktion wird erst nach dieser Bestätigung gesendet.")
                    .setNegativeButton(LocaleKit.t(this,"Abbrechen"),null)
                    .setPositiveButton(LocaleKit.t(this,unlock?"Ja, entriegeln":"Ja, verriegeln"),
                            (dialog,which)->{
                                startForegroundService(new Intent(this,BridgeService.class)
                                        .setAction(BridgeService.ACTION_CONFIRM_LOCK)
                                        .putExtra("lock_id",lockId)
                                        .putExtra("unlock",unlock));
                                toast("Bestätigung an die Bridge gesendet.");
                            }).show();
        }
    }

    @Override protected void onNewIntent(Intent intent){
        super.onNewIntent(intent);
        setIntent(intent);
        handleWatchIntent(intent);
    }

    private TextView text(LinearLayout parent,String value) {
        TextView t=UiKit.label(this,value,16,false,UiKit.INK);
        parent.addView(t);return t;
    }
    private EditText input(LinearLayout parent,String label,String value,int type) {
        return UiKit.field(this,parent,label,"",value,type);
    }
    private void button(LinearLayout parent,String label,Runnable action){
        UiKit.button(this,parent,label,true,action);
    }

    private void toast(String value) { Toast.makeText(this, value, Toast.LENGTH_LONG).show(); }

    private void chooseEntities() {
        final Home home;
        try {
            home = new Home(url.getText().toString(), token.getText().toString());
        } catch (Exception ex) {
            toast("Bitte gültige Home-Assistant-URL und Token eingeben.");
            return;
        }
        toast("Lade Entitäten und Räume …");
        new Thread(() -> {
            try {
                Home.Catalog result = home.catalog();
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    catalog = result;
                    updateRoomPicker();
                    for (Home.Entity entity : result.entities()) {
                        favoriteRooms.put(entity.id(), entity.areaName());
                    }
                    if(watchEditor!=null)watchEditor.setCatalog(result);
                    EntityBrowser.show(this, result, ids, chosen -> {
                        if(watchPages!=null){
                            watchPages.reconcile(chosen);
                            watchPages.save(this);
                            BridgeService.notifyPagesChanged();
                        }
                        ids.clear();
                        ids.addAll(watchPages==null?chosen:watchPages.uniqueEntities());
                        if(watchEditor!=null)watchEditor.refreshFromConfig();
                        selected.setText(chosen.isEmpty() ? "Keine Favoriten ausgewählt"
                                : chosen.size() + " Favoriten für die Uhr ausgewählt");
                        persistSettings();
                        renderRoom();
                    });
                });
            } catch (Exception ex) {
                runOnUiThread(() -> toast("Entitäten konnten nicht geladen werden: " + ex.getMessage()));
            }
        }, "Load entity browser").start();
    }

    private void updateRoomPicker() {
        if (catalog == null || roomPicker == null) return;
        roomOptions.clear();
        roomOptionIds.clear();
        roomOptions.add("Alle Räume");
        roomOptionIds.add("");
        for (Home.Room room : catalog.rooms()) {
            roomOptions.add(room.name());
            roomOptionIds.add(room.id());
        }
        roomOptions.add(LocaleKit.t(this,"Ohne Raum"));
        roomOptionIds.add("no_room");
        ((ArrayAdapter<?>) roomPicker.getAdapter()).notifyDataSetChanged();
        int selectedIndex = roomOptionIds.indexOf(chosenRoomId);
        if (selectedIndex < 0) {
            chosenRoomId = "";
            selectedIndex = 0;
        }
        roomPicker.setSelection(selectedIndex);
    }

    private void refreshQuickActions() {
        if (quickActions == null) return;
        quickActions.removeAllViews();
        text(quickActions, "Lade Räume und Geräte …");
        final Home home;
        try {
            home = new Home(url.getText().toString(), token.getText().toString());
        } catch (IllegalArgumentException ex) {
            quickActions.removeAllViews();
            text(quickActions, "Bitte zuerst URL und Token eingeben.");
            return;
        }
        new Thread(() -> {
            try {
                Home.Catalog result = home.catalog();
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    catalog = result;
                    if(watchEditor!=null)watchEditor.setCatalog(result);
                    persistSettings();
                    for (Home.Entity entity : result.entities()) {
                        favoriteRooms.put(entity.id(), entity.areaName());
                    }
                    updateRoomPicker();
                    renderRoom();
                });
            } catch (Exception ex) {
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    quickActions.removeAllViews();
                    text(quickActions, "Home Assistant nicht erreichbar: " + ex.getMessage());
                });
            }
        }, "Refresh rooms").start();
    }

    private void renderRoom() {
        if (quickActions == null) return;
        quickActions.removeAllViews();
        if (catalog == null) {
            text(quickActions, "Räume laden, um Geräte anzuzeigen.");
            return;
        }
        ArrayList<Home.Entity> items = new ArrayList<>();
        for (Home.Entity entity : catalog.entities()) {
            boolean roomMatch = chosenRoomId.isEmpty()
                    || (chosenRoomId.equals("no_room") && entity.areaId().isEmpty())
                    || chosenRoomId.equals(entity.areaId());
            if (!roomMatch) continue;
            if (!chosenRoomId.isEmpty() || ids.contains(entity.id())) items.add(entity);
        }
        if (chosenRoomId.isEmpty() && items.isEmpty()) {
            text(quickActions, "Wähle einen Raum aus, oder füge Favoriten über den Entitäten-Browser hinzu.");
            return;
        }
        items.sort((a,b) -> {
            if (a.controllable() != b.controllable()) return a.controllable() ? -1 : 1;
            return a.label().compareToIgnoreCase(b.label());
        });
        TextView count = text(quickActions, items.size() + " Entitäten");
        count.setTextColor(UiKit.MUTED);
        count.setTextSize(12);
        int limit = Math.min(items.size(), 30);
        for (int i = 0; i < limit; i++) {
            Home.Entity entity = items.get(i);
            var row = card(quickActions);
            TextView label = text(row, iconFor(entity) + "  " + entity.label());
            label.setTextSize(17);
            label.setTypeface(null, Typeface.BOLD);
            TextView detail = text(row, entity.areaName() + " · " + entity.display());
            detail.setTextSize(13);
            detail.setTextColor(UiKit.MUTED);
            if(entity.dimmable() && entity.state().equals("on")){
                var brightness=UiKit.label(this,"Helligkeit "+entity.brightnessPercent()+" %",13,false,UiKit.MUTED);
                row.addView(brightness);
                SeekBar slider=new SeekBar(this);
                slider.setMax(100);
                slider.setProgress(Math.max(1,entity.brightnessPercent()));
                slider.setProgressTintList(android.content.res.ColorStateList.valueOf(UiKit.ORANGE));
                slider.setThumbTintList(android.content.res.ColorStateList.valueOf(UiKit.ORANGE));
                row.addView(slider);
                slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
                    public void onProgressChanged(SeekBar bar,int value,boolean user){
                        if(user)brightness.setText("Helligkeit "+value+" %");
                    }
                    public void onStartTrackingTouch(SeekBar bar){}
                    public void onStopTrackingTouch(SeekBar bar){
                        setBrightness(entity,Math.max(1,bar.getProgress()));
                    }
                });
            }
            if (entity.isLock() && entity.controllable()) {
                boolean unlock = entity.state().equals("locked");
                button(row, unlock ? "Schloss entsperren …" : "Schloss verriegeln …",
                        () -> confirmLock(entity, unlock));
                if (entity.supportsOpen()) {
                    button(row, "Tür öffnen (Schnapper) …", () -> confirmLatch(entity));
                }
            } else if (entity.canSwitch()) {
                boolean on = entity.actionable() || !entity.state().equals("on");
                button(row, entity.actionable() ? "Ausführen" : on ? "Einschalten" : "Ausschalten",
                        () -> runQuickAction(entity, on));
            }
        }
        if (items.size() > limit) {
            TextView hint = text(quickActions, "Weitere " + (items.size() - limit)
                    + " Geräte: Filtere im Entitäten-Browser oder wähle einen anderen Raum.");
            hint.setTextColor(UiKit.MUTED);
            hint.setTextSize(13);
        }
    }

    private void setBrightness(Home.Entity entity,int percent){
        final Home home;
        try{home=new Home(url.getText().toString(),token.getText().toString());}
        catch(Exception ex){toast("Home Assistant Zugangsdaten fehlen.");return;}
        new Thread(()->{
            try{
                home.setBrightness(entity,percent);
                runOnUiThread(()->{
                    toast("Helligkeit angepasst");
                    refreshQuickActions();
                });
            }catch(Exception ex){
                runOnUiThread(()->toast("Dimmen fehlgeschlagen: "+ex.getMessage()));
            }
        },"HA dimming").start();
    }

    private String iconFor(Home.Entity entity) {
        return switch (entity.domain()) {
            case "light" -> "☀";
            case "switch" -> "⏻";
            case "lock" -> "⌑";
            case "scene" -> "◈";
            case "script" -> "▶";
            case "sensor", "binary_sensor" -> "◉";
            default -> "◇";
        };
    }

    private void confirmLock(Home.Entity entity, boolean unlock) {
        String action = unlock ? "ENTSPERREN" : "VERRIEGELN";
        new AlertDialog.Builder(this)
                .setTitle("Schloss " + (unlock ? "entsperren" : "verriegeln") + "?")
                .setMessage("Raum: " + entity.areaName() + "\nSchloss: " + entity.label()
                        + "\n\nSoll dieses Schloss wirklich " + action + " werden?")
                .setNegativeButton(LocaleKit.t(this,"Abbrechen"), null)
                .setPositiveButton(unlock ? "Ja, entsperren" : "Ja, verriegeln",
                        (dialog, which) -> runLockAction(entity, unlock))
                .show();
    }

    private void confirmLatch(Home.Entity entity) {
        new AlertDialog.Builder(this)
                .setTitle(LocaleKit.t(this,"Tür wirklich öffnen?"))
                .setMessage("Raum: " + entity.areaName() + "\nSchloss: " + entity.label()
                        + "\n\nDas Türschloss gibt die Falle frei. Die Tür kann danach"
                        + " unter Umständen sofort geöffnet werden.")
                .setNegativeButton("Abbrechen", null)
                .setPositiveButton(LocaleKit.t(this,"Ja, Tür öffnen"), (dialog, which) -> runLatchAction(entity))
                .show();
    }

    private void runLatchAction(Home.Entity entity) {
        final Home home;
        try {
            home = new Home(url.getText().toString(), token.getText().toString());
        } catch (Exception ex) {
            toast("Home Assistant Zugangsdaten fehlen.");
            return;
        }
        new Thread(() -> {
            try {
                home.openLatch(entity.id());
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    toast("Türöffner ausgelöst: " + entity.label());
                    refreshQuickActions();
                });
            } catch (Exception ex) {
                runOnUiThread(() -> toast("Tür konnte nicht geöffnet werden: " + ex.getMessage()));
            }
        }, "Confirmed door unlatch").start();
    }

    private void runLockAction(Home.Entity entity, boolean unlock) {
        final Home home;
        try {
            home = new Home(url.getText().toString(), token.getText().toString());
        } catch (Exception ex) {
            toast("Home Assistant Zugangsdaten fehlen.");
            return;
        }
        new Thread(() -> {
            try {
                home.setLock(entity.id(), unlock);
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    toast("Schlossaktion gesendet: " + entity.label());
                    refreshQuickActions();
                });
            } catch (Exception ex) {
                runOnUiThread(() -> toast("Schlossaktion fehlgeschlagen: " + ex.getMessage()));
            }
        }, "Confirmed lock action").start();
    }

    private void runQuickAction(Home.Entity entity, boolean on) {
        try {
            var home = new Home(url.getText().toString(), token.getText().toString());
            new Thread(() -> {
                try {
                    home.set(entity, on);
                    runOnUiThread(() -> {
                        toast("Aktion gesendet: " + entity.label());
                        refreshQuickActions();
                    });
                } catch (Exception ex) {
                    runOnUiThread(() -> toast("Aktion fehlgeschlagen: " + ex.getMessage()));
                }
            }, "Home Assistant quick action").start();
        } catch (IllegalArgumentException ex) {
            toast(ex.getMessage());
        }
    }

    private boolean bluetoothAllowed() {
        var needed = new ArrayList<String>();
        for (String p : new String[]{Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.ACCESS_FINE_LOCATION}) {
            if (checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) needed.add(p);
        }
        if (needed.isEmpty()) return true;
        requestPermissions(needed.toArray(String[]::new), 1);
        toast("Bluetooth- und Standortberechtigung erlauben und erneut suchen.");
        return false;
    }

    private void scan() {
        if (!bluetoothAllowed()) return;
        stopScan.run();
        var manager = getSystemService(BluetoothManager.class);
        var adapter = manager == null ? null : manager.getAdapter();
        if (adapter == null || !adapter.isEnabled()) {
            scanState.setText("Bluetooth ist deaktiviert");
            toast("Bitte Bluetooth aktivieren.");
            return;
        }
        var scanner = adapter.getBluetoothLeScanner();
        if (scanner == null) {
            scanState.setText("Bluetooth-Scanner nicht verfügbar");
            return;
        }

        // Show all advertising devices: the watch may advertise a shortened or
        // generic name, and manufacturer/service payloads can vary by firmware.
        var found = new LinkedHashMap<String, String>();
        var descriptions = new LinkedHashMap<String, String>();
        var listLabels = new ArrayList<String>();
        var addresses = new ArrayList<String>();
        var adapterList = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, listLabels);
        var picker = new AlertDialog.Builder(this)
                .setTitle(LocaleKit.t(this,"Bluetooth-Geräte in der Nähe"))
                .setAdapter(adapterList, (dialog, which) -> {
                    if (which >= addresses.size()) return;
                    address = addresses.get(which);
                    watch.setText(descriptions.get(address));
                    scanState.setText("Ausgewählt: " + address);
                    persistSettings();
                    stopScan.run();
                })
                .setNegativeButton(LocaleKit.t(this,"Schließen"), (dialog, which) -> stopScan.run())
                .create();
        picker.setOnDismissListener(dialog -> stopScan.run());
        Runnable refresh = () -> {
            listLabels.clear();
            addresses.clear();
            for (var entry : found.entrySet()) {
                addresses.add(entry.getKey());
                listLabels.add(entry.getValue() + "\n" + entry.getKey());
            }
            adapterList.notifyDataSetChanged();
        };
        try {
            for (BluetoothDevice device : adapter.getBondedDevices()) {
                String name = device.getName();
                if (name != null && !name.isBlank()) {
                    found.put(device.getAddress(), name + " · gekoppelt");
                    descriptions.put(device.getAddress(), name);
                }
            }
        } catch (SecurityException ignored) { }
        refresh.run();
        var callback = new ScanCallback() {
            @Override public void onScanResult(int type, ScanResult result) {
                var device = result.getDevice();
                var record = result.getScanRecord();
                String name = record == null ? null : record.getDeviceName();
                if (name == null || name.isBlank()) name = device.getName();
                String id = device.getAddress();
                String label = (name == null || name.isBlank() ? "Unbekanntes BLE-Gerät" : name);
                final String display = label;
                runOnUiThread(() -> {
                    if (!found.containsKey(id) || !found.get(id).equals(display)) {
                        found.put(id, display);
                        descriptions.put(id, display);
                        refresh.run();
                    }
                    scanState.setText(found.size() + " Bluetooth-Geräte gefunden");
                });
            }
            @Override public void onScanFailed(int errorCode) {
                runOnUiThread(() -> {
                    scanState.setText("Bluetooth-Suche fehlgeschlagen: " + errorCode);
                    toast("BLE Scan-Fehler " + errorCode + ". Bluetooth und Berechtigungen prüfen.");
                    picker.dismiss();
                });
            }
        };
        final boolean[] active = {true};
        stopScan = () -> {
            if (active[0]) {
                active[0] = false;
                try { scanner.stopScan(callback); } catch (Exception ignored) { }
            }
        };
        try {
            scanner.startScan(null, new ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), callback);
            scanState.setText("Suche läuft …");
            picker.show();
            ui.postDelayed(() -> {
                if (!active[0] || isDestroyed()) return;
                stopScan.run();
                scanState.setText(found.isEmpty()
                        ? "Keine BLE-Geräte gefunden – Uhr aktivieren und erneut suchen"
                        : found.size() + " Geräte gefunden · Suche beendet");
                if (picker.isShowing() && found.isEmpty()) {
                    toast("Kein Gerät sichtbar. Uhr aufwecken, Nothing X trennen und erneut versuchen.");
                }
            }, 15000);
        } catch (Exception ex) {
            stopScan.run();
            scanState.setText("Bluetooth-Fehler: " + ex.getMessage());
        }
    }

    private void startBridge() {
        if (!bluetoothAllowed()) return;
        if (address == null) { toast("Bitte zuerst die Uhr auswählen."); return; }
        try {
            new Home(url.getText().toString(), token.getText().toString());
            persistSettings();
            startForegroundService(new Intent(this, BridgeService.class)
                    .putExtra("url", url.getText().toString()).putExtra("token", token.getText().toString())
                    .putExtra("address", address)
                    .putExtra("entities", ids.toArray(String[]::new))
                    .putExtra("rooms", ids.stream()
                            .map(id -> favoriteRooms.getOrDefault(id, "Favoriten"))
                            .toArray(String[]::new)));
        } catch (IllegalArgumentException ex) {
            toast(ex.getMessage());
        }
    }

    @Override protected void onDestroy() {
        stopScan.run();
        ui.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
