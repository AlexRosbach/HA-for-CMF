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
    private static final int TEXT = Color.rgb(27, 29, 34);
    private static final int MUTED = Color.rgb(104, 110, 120);
    private static final int SURFACE = Color.rgb(247, 248, 250);
    private LinearLayout content;
    private LinearLayout quickActions;
    private TextView scanState;

    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private GradientDrawable shape(int color, int corner) {
        var d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(corner));
        return d;
    }
    private LinearLayout card(LinearLayout parent) {
        var box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(16), dp(18), dp(16));
        box.setBackground(shape(SURFACE, 20));
        var p = new LinearLayout.LayoutParams(-1, -2);
        p.setMargins(0, dp(10), 0, dp(8));
        parent.addView(box, p);
        return box;
    }
    private void heading(LinearLayout parent, String title, String detail) {
        var h = text(parent, title);
        h.setTextSize(25);
        h.setTypeface(null, Typeface.BOLD);
        h.setTextColor(TEXT);
        if (detail != null) {
            var description = text(parent, detail);
            description.setTextSize(14);
            description.setTextColor(MUTED);
            description.setPadding(0, dp(6), 0, dp(10));
        }
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.WHITE);
        getWindow().setNavigationBarColor(Color.WHITE);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        var scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.WHITE);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(22), dp(24), dp(22), dp(36));
        scroll.setFillViewport(true);
        scroll.addView(content);
        setContentView(scroll);

        var appTitle = text(content, "●  HA for CMF");
        appTitle.setTextColor(ORANGE);
        appTitle.setTextSize(21);
        appTitle.setTypeface(null, Typeface.BOLD);
        appTitle.setPadding(0, 0, 0, dp(26));

        heading(content, "Deine CMF Watch verbinden",
                "Home Assistant auf deiner Watch Pro 2. Verbinde zuerst die Uhr und wähle anschließend deine Geräte aus.");
        var connection = card(content);
        var watchTitle = text(connection, "CMF Watch Pro 2");
        watchTitle.setTextColor(TEXT);
        watchTitle.setTypeface(null, Typeface.BOLD);
        watchTitle.setTextSize(19);
        watch = text(connection, "Noch keine Uhr ausgewählt");
        watch.setTextColor(MUTED);
        scanState = text(connection, "Bluetooth-Suche bereit");
        scanState.setTextColor(MUTED);
        scanState.setTextSize(13);
        button(connection, "⌕  Uhr suchen", this::scan);
        var hint = text(connection, "Tipp: Uhr aufwecken. Falls Nothing X die Verbindung hält, dort vorübergehend trennen.");
        hint.setTextColor(MUTED);
        hint.setTextSize(12);

        heading(content, "Home Assistant", "URL und Zugriffstoken bleiben nur während dieser App-Sitzung im Speicher.");
        var homeCard = card(content);
        url = input(homeCard, "Home Assistant URL", "http://homeassistant.local:8123",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        token = input(homeCard, "Access token", "",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        token.setSaveEnabled(false);
        button(homeCard, "Geräte auswählen (max. 4)", this::chooseEntities);
        selected = text(homeCard, "Noch keine Geräte ausgewählt");
        selected.setTextColor(MUTED);
        heading(content, "Schnellsteuerung", "Ausgewählte Geräte direkt auf dem Handy steuern.");
        var quickCard = card(content);
        button(quickCard, "Status aktualisieren", this::refreshQuickActions);
        quickActions = new LinearLayout(this);
        quickActions.setOrientation(LinearLayout.VERTICAL);
        quickCard.addView(quickActions);
        text(quickActions, "Bitte zuerst Home-Assistant-Geräte auswählen.");

        heading(content, "Uhr-App & Verbindung", "Für die Installation wird WP2 Apps mit Loader auf der Uhr benötigt.");
        var actions = card(content);
        button(actions, "Uhr-App installieren / aktualisieren", () -> {
            if (!bluetoothAllowed()) return;
            if (address == null) { toast("Bitte zuerst die Uhr auswählen."); return; }
            startForegroundService(new Intent(this, BridgeService.class)
                    .putExtra("install", true).putExtra("address", address));
        });
        button(actions, "Bridge starten", this::startBridge);
        button(actions, "Bridge stoppen", () -> {
            stopService(new Intent(this, BridgeService.class));
            BridgeService.status = "Bridge stopped";
        });
        status = text(actions, BridgeService.status);
        status.setTextColor(MUTED);
        status.setTextSize(13);
        ui.post(new Runnable() {
            @Override public void run() {
                status.setText(BridgeService.status);
                ui.postDelayed(this, 1000);
            }
        });
    }

    private TextView text(LinearLayout parent, String value) {
        var view = new TextView(this);
        view.setText(value);
        view.setTextSize(16);
        parent.addView(view);
        return view;
    }

    private EditText input(LinearLayout parent, String label, String value, int type) {
        text(parent, label);
        var field = new EditText(this);
        field.setSingleLine(true);
        field.setInputType(type);
        field.setText(value);
        parent.addView(field);
        return field;
    }

    private void button(LinearLayout parent, String label, Runnable action) {
        var button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextColor(Color.WHITE);
        button.setTextSize(14);
        button.setTypeface(null, Typeface.BOLD);
        button.setBackgroundTintList(ColorStateList.valueOf(ORANGE));
        button.setMinHeight(dp(52));
        var p = new LinearLayout.LayoutParams(-1, dp(52));
        p.setMargins(0, dp(10), 0, dp(4));
        parent.addView(button, p);
        button.setOnClickListener(v -> action.run());
    }

    private void toast(String value) { Toast.makeText(this, value, Toast.LENGTH_LONG).show(); }

    private void chooseEntities() {
        try {
            var home = new Home(url.getText().toString(), token.getText().toString());
            toast("Loading entities…");
            new Thread(() -> {
                try {
                    var entries = home.entities();
                    runOnUiThread(() -> { if (!isDestroyed()) picker(entries); });
                } catch (Exception ex) {
                    runOnUiThread(() -> toast("Could not load entities. Check URL and access token."));
                }
            }, "Load entities").start();
        } catch (IllegalArgumentException ex) {
            toast(ex.getMessage());
        }
    }

    private void picker(List<Home.Entity> entries) {
        var checked = new ArrayList<>(ids);
        var panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        var query = new EditText(this);
        query.setHint("Search name or entity ID");
        panel.addView(query);
        var list = new ListView(this);
        list.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        panel.addView(list, new LinearLayout.LayoutParams(-1, Math.round(350 * getResources().getDisplayMetrics().density)));
        List<Home.Entity> visible = new ArrayList<>();
        Runnable filter = () -> {
            visible.clear();
            String q = query.getText().toString().toLowerCase(Locale.ROOT);
            for (var entry : entries) if ((entry.label() + " " + entry.id()).toLowerCase(Locale.ROOT).contains(q)) visible.add(entry);
            String[] labels = visible.stream().map(e -> e.label() + " · " + e.id()).toArray(String[]::new);
            list.clearChoices();
            list.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_multiple_choice, labels));
            for (int i = 0; i < visible.size(); i++) list.setItemChecked(i, checked.contains(visible.get(i).id()));
        };
        query.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) { filter.run(); }
            public void afterTextChanged(Editable e) {}
        });
        list.setOnItemClickListener((parent, view, position, row) -> {
            String id = visible.get(position).id();
            if (checked.remove(id)) return;
            if (checked.size() == 4) {
                list.setItemChecked(position, false);
                toast("Choose at most four entities.");
            } else {
                checked.add(id);
            }
        });
        filter.run();
        new AlertDialog.Builder(this).setTitle("Select up to four entities").setView(panel)
                .setNegativeButton("Cancel", null).setPositiveButton("Apply", (dialog, which) -> {
                    ids.clear();
                    ids.addAll(checked);
                    selected.setText(ids.isEmpty() ? "No entities selected" : String.join("\n", ids));
                }).show();
    }

    private void refreshQuickActions() {
        if (quickActions == null) return;
        quickActions.removeAllViews();
        if (ids.isEmpty()) {
            text(quickActions, "Keine Geräte ausgewählt.");
            return;
        }
        final Home home;
        try {
            home = new Home(url.getText().toString(), token.getText().toString());
        } catch (IllegalArgumentException ex) {
            text(quickActions, "Bitte zuerst URL und Token eingeben.");
            return;
        }
        text(quickActions, "Aktualisiere Status …");
        new Thread(() -> {
            try {
                var states = home.states();
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    quickActions.removeAllViews();
                    for (String id : ids) {
                        Home.Entity entity = states.get(id);
                        if (entity == null) {
                            text(quickActions, id + " · Nicht verfügbar");
                            continue;
                        }
                        var row = card(quickActions);
                        var title = text(row, entity.label());
                        title.setTextColor(TEXT);
                        title.setTypeface(null, Typeface.BOLD);
                        var value = text(row, entity.display() + "  ·  " + id);
                        value.setTextColor(MUTED);
                        value.setTextSize(13);
                        if (entity.canSwitch()) {
                            boolean on = entity.actionable() || !"on".equals(entity.state());
                            button(row, entity.actionable() ? "Ausführen" : (on ? "Einschalten" : "Ausschalten"),
                                    () -> runQuickAction(entity, on));
                        }
                    }
                });
            } catch (Exception ex) {
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    quickActions.removeAllViews();
                    text(quickActions, "Home Assistant nicht erreichbar: " + ex.getMessage());
                });
            }
        }, "Load quick actions").start();
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
                .setTitle("Bluetooth-Geräte in der Nähe")
                .setAdapter(adapterList, (dialog, which) -> {
                    if (which >= addresses.size()) return;
                    address = addresses.get(which);
                    watch.setText(descriptions.get(address));
                    scanState.setText("Ausgewählt: " + address);
                    stopScan.run();
                })
                .setNegativeButton("Schließen", (dialog, which) -> stopScan.run())
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
        if (address == null || ids.isEmpty()) { toast("Choose entities and a watch first."); return; }
        try {
            new Home(url.getText().toString(), token.getText().toString());
            startForegroundService(new Intent(this, BridgeService.class)
                    .putExtra("url", url.getText().toString()).putExtra("token", token.getText().toString())
                    .putExtra("address", address)
                    .putExtra("entities", ids.toArray(String[]::new)));
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
