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
    private static final int TEXT = Color.rgb(27, 29, 34);
    private static final int MUTED = Color.rgb(104, 110, 120);
    private static final int SURFACE = Color.rgb(247, 248, 250);
    private LinearLayout content;
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
        button(homeCard, "Favoriten für die Uhr auswählen", this::chooseEntities);
        selected = text(homeCard, "Noch keine Geräte ausgewählt");
        selected.setTextColor(MUTED);
        heading(content, "Räume & Geräte", "Wähle einen Raum und steuere Licht, Szenen oder Schlösser direkt.");
        var quickCard = card(content);
        var pickTitle = text(quickCard, "RAUM");
        pickTitle.setTextSize(12);
        pickTitle.setTextColor(MUTED);
        roomPicker = new Spinner(this);
        roomOptions.add("Alle Räume");
        roomOptionIds.add("");
        var options = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, roomOptions);
        roomPicker.setAdapter(options);
        quickCard.addView(roomPicker);
        roomPicker.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view,
                    int position, long id) {
                if (position < roomOptionIds.size()) {
                    String next = roomOptionIds.get(position);
                    if (!next.equals(chosenRoomId)) {
                        chosenRoomId = next;
                        renderRoom();
                    }
                }
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
        button(quickCard, "Räume und Geräte aktualisieren", this::refreshQuickActions);
        quickActions = new LinearLayout(this);
        quickActions.setOrientation(LinearLayout.VERTICAL);
        quickCard.addView(quickActions);
        text(quickActions, "Verbinde Home Assistant und lade die Räume.");

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
        view.setTextColor(TEXT);
        parent.addView(view);
        return view;
    }

    private EditText input(LinearLayout parent, String label, String value, int type) {
        text(parent, label);
        var field = new EditText(this);
        field.setSingleLine(true);
        field.setInputType(type);
        field.setText(value);
        field.setTextColor(TEXT);
        field.setHintTextColor(MUTED);
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
                    EntityBrowser.show(this, result, ids, chosen -> {
                        ids.clear();
                        ids.addAll(chosen);
                        selected.setText(chosen.isEmpty() ? "Keine Favoriten ausgewählt"
                                : chosen.size() + " Favoriten für die Uhr ausgewählt");
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
        roomOptions.add("Ohne Raum");
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
        count.setTextColor(MUTED);
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
            detail.setTextColor(MUTED);
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
            hint.setTextColor(MUTED);
            hint.setTextSize(13);
        }
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
                .setNegativeButton("Abbrechen", null)
                .setPositiveButton(unlock ? "Ja, entsperren" : "Ja, verriegeln",
                        (dialog, which) -> runLockAction(entity, unlock))
                .show();
    }

    private void confirmLatch(Home.Entity entity) {
        new AlertDialog.Builder(this)
                .setTitle("Tür wirklich öffnen?")
                .setMessage("Raum: " + entity.areaName() + "\nSchloss: " + entity.label()
                        + "\n\nDas Türschloss gibt die Falle frei. Die Tür kann danach"
                        + " unter Umständen sofort geöffnet werden.")
                .setNegativeButton("Abbrechen", null)
                .setPositiveButton("Ja, Tür öffnen", (dialog, which) -> runLatchAction(entity))
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
        if (address == null || ids.isEmpty()) { toast("Bitte Uhr und Favoriten auswählen."); return; }
        try {
            new Home(url.getText().toString(), token.getText().toString());
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
