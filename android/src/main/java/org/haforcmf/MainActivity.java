package org.haforcmf;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.*;
import android.bluetooth.BluetoothManager;
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

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        var scroll = new ScrollView(this);
        var content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int pad = Math.round(24 * getResources().getDisplayMetrics().density);
        content.setPadding(pad, pad * 2, pad, pad);
        scroll.setFillViewport(true);
        scroll.addView(content);
        setContentView(scroll);
        var title = text(content, "HA for CMF");
        title.setTextSize(32);
        text(content, "Choose up to four Home Assistant entities for your Watch Pro 2.");
        url = input(content, "Home Assistant URL", "http://homeassistant.local:8123", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        token = input(content, "Access token", "", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        token.setSaveEnabled(false);
        button(content, "Choose entities", this::chooseEntities);
        selected = text(content, "No entities selected");
        button(content, "Find watch", this::scan);
        watch = text(content, "No watch selected");
        button(content, "Install or update watch app", () -> {
            if (!bluetoothAllowed()) return;
            if (address == null) { toast("Find and select your watch first."); return; }
            startForegroundService(new Intent(this, BridgeService.class)
                    .putExtra("install", true).putExtra("address", address));
        });
        button(content, "Start bridge", this::startBridge);
        button(content, "Stop bridge", () -> {
            stopService(new Intent(this, BridgeService.class));
            BridgeService.status = "Bridge stopped";
        });
        status = text(content, BridgeService.status);
        ui.post(new Runnable() {
            @Override public void run() { status.setText(BridgeService.status); ui.postDelayed(this, 750); }
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
        button.setOnClickListener(v -> action.run());
        parent.addView(button);
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

    private boolean bluetoothAllowed() {
        var needed = new ArrayList<String>();
        for (String p : new String[]{Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT}) {
            if (checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) needed.add(p);
        }
        if (needed.isEmpty()) return true;
        requestPermissions(needed.toArray(String[]::new), 1);
        toast("Grant Nearby devices, then try again.");
        return false;
    }

    private void scan() {
        if (!bluetoothAllowed()) return;
        stopScan.run();
        var adapter = getSystemService(BluetoothManager.class).getAdapter();
        var scanner = adapter == null ? null : adapter.getBluetoothLeScanner();
        if (scanner == null) { toast("Turn on Bluetooth first."); return; }
        var found = new LinkedHashMap<String, String>();
        var callback = new ScanCallback() {
            @Override public void onScanResult(int type, ScanResult result) {
                var record = result.getScanRecord();
                var device = result.getDevice();
                String name = record == null ? null : record.getDeviceName();
                if (name == null) name = device.getName();
                if (name == null || !name.startsWith("CMF Watch Pro 2")) return;
                String label = name;
                runOnUiThread(() -> found.put(device.getAddress(), label));
            }
        };
        final boolean[] active = {true};
        stopScan = () -> { if (active[0]) { active[0] = false; scanner.stopScan(callback); } };
        scanner.startScan(callback);
        toast("Looking for nearby watches…");
        ui.postDelayed(() -> {
            if (!active[0] || isDestroyed()) return;
            stopScan.run();
            var addresses = new ArrayList<>(found.keySet());
            if (addresses.isEmpty()) { toast("No watch found. Wake it and disconnect other Bluetooth clients."); return; }
            new AlertDialog.Builder(this).setTitle("Select your watch")
                    .setItems(found.values().toArray(String[]::new), (dialog, which) -> {
                        address = addresses.get(which);
                        watch.setText(found.get(address));
                    }).show();
        }, 7000);
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
