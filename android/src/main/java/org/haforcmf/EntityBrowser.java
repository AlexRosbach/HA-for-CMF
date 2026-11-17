package org.haforcmf;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.*;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Searchable, room-aware, multi-select browser for watch favourites. */
final class EntityBrowser {
    interface OnApply { void accept(List<String> ids); }
    static final int MAX_SELECTION = 24;
    private static final int INK = Color.rgb(24, 27, 31);
    private static final int MUTED = Color.rgb(99, 105, 113);
    private static final String[] TYPES = {
            "Alle Typen", "Lichter", "Schalter", "Schlösser", "Szenen",
            "Skripte", "Buttons", "Sensoren", "Weitere"
    };
    private static final String[] SORT = {"Name", "Raum", "Typ", "Status"};

    private static int dp(Activity activity, int value) {
        return Math.round(activity.getResources().getDisplayMetrics().density * value);
    }
    private static String normalized(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFKD)
                .replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT);
    }
    private static boolean matches(Home.Entity e, String query) {
        String haystack = normalized(e.label() + " " + e.id() + " "
                + e.areaName() + " " + e.state() + " " + e.display());
        for (String part : normalized(query).trim().split("\\s+")) {
            if (!part.isEmpty() && !haystack.contains(part)) return false;
        }
        return true;
    }
    private static boolean typeMatches(Home.Entity e, int category) {
        String domain = e.domain();
        return switch (category) {
            case 1 -> domain.equals("light");
            case 2 -> domain.equals("switch") || domain.equals("input_boolean");
            case 3 -> domain.equals("lock");
            case 4 -> domain.equals("scene");
            case 5 -> domain.equals("script");
            case 6 -> domain.equals("button") || domain.equals("input_button");
            case 7 -> domain.equals("sensor") || domain.equals("binary_sensor");
            case 8 -> !(domain.equals("light") || domain.equals("switch") || domain.equals("lock")
                        || domain.equals("scene") || domain.equals("script")
                        || domain.equals("button") || domain.equals("input_button")
                        || domain.equals("sensor") || domain.equals("binary_sensor"));
            default -> true;
        };
    }
    private static Spinner spinner(Activity activity, LinearLayout parent, List<String> values) {
        Spinner spinner = new Spinner(activity);
        ArrayAdapter<String> labels = new ArrayAdapter<>(activity,
                android.R.layout.simple_spinner_dropdown_item, values);
        spinner.setAdapter(labels);
        var layout = new LinearLayout.LayoutParams(-1, dp(activity, 44));
        layout.setMargins(0, dp(activity, 3), 0, dp(activity, 3));
        parent.addView(spinner, layout);
        return spinner;
    }
    static void show(Activity activity, Home.Catalog catalog, List<String> existing, OnApply apply) {
        ArrayList<String> chosen = new ArrayList<>(existing);
        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(activity, 18), dp(activity, 6), dp(activity, 18), dp(activity, 10));
        TextView info = new TextView(activity);
        info.setTextColor(MUTED);
        info.setTextSize(13);
        panel.addView(info);
        EditText search = new EditText(activity);
        search.setSingleLine(true);
        search.setHint("Name, Raum, Entität oder Zustand suchen");
        search.setTextColor(INK);
        search.setHintTextColor(MUTED);
        panel.addView(search);

        ArrayList<String> rooms = new ArrayList<>();
        rooms.add("Alle Räume");
        for (Home.Room room : catalog.rooms()) rooms.add(room.name());
        rooms.add("Ohne Raum");
        Spinner room = spinner(activity, panel, rooms);
        Spinner type = spinner(activity, panel, List.of(TYPES));
        CheckBox onlyAvailable = new CheckBox(activity);
        onlyAvailable.setText("Nur verfügbare");
        onlyAvailable.setTextColor(INK);
        CheckBox onlySelected = new CheckBox(activity);
        onlySelected.setText("Nur Favoriten");
        onlySelected.setTextColor(INK);
        LinearLayout options = new LinearLayout(activity);
        options.setOrientation(LinearLayout.HORIZONTAL);
        options.addView(onlyAvailable, new LinearLayout.LayoutParams(0, -2, 1));
        options.addView(onlySelected, new LinearLayout.LayoutParams(0, -2, 1));
        panel.addView(options);
        Spinner sort = spinner(activity, panel, List.of(SORT));
        ListView list = new ListView(activity);
        list.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        panel.addView(list, new LinearLayout.LayoutParams(-1, dp(activity, 285)));
        ArrayList<Home.Entity> visible = new ArrayList<>();
        ArrayAdapter<Home.Entity> adapter = new ArrayAdapter<>(activity,
                android.R.layout.simple_list_item_multiple_choice, visible) {
            @Override public View getView(int position, View recycled, android.view.ViewGroup parent) {
                View view = super.getView(position, recycled, parent);
                TextView name = (TextView) view.findViewById(android.R.id.text1);
                Home.Entity item = getItem(position);
                name.setText(item.label() + "\n" + item.areaName() + " · " + item.display()
                        + "\n" + item.id());
                name.setTextColor(INK);
                name.setTextSize(13);
                name.setMinHeight(dp(activity, 74));
                name.setPadding(dp(activity, 6), dp(activity, 8), dp(activity, 4), dp(activity, 8));
                return view;
            }
        };
        list.setAdapter(adapter);
        Runnable update = () -> {
            String q = search.getText().toString();
            int category = type.getSelectedItemPosition();
            String selectedRoom = rooms.get(Math.max(0, room.getSelectedItemPosition()));
            ArrayList<Home.Entity> match = new ArrayList<>();
            for (Home.Entity e : catalog.entities()) {
                if (!selectedRoom.equals("Alle Räume") && !selectedRoom.equals(e.areaName())) continue;
                if (!typeMatches(e, category) || !matches(e, q)) continue;
                if (onlyAvailable.isChecked() && (e.state().equals("unavailable")
                        || e.state().equals("unknown"))) continue;
                if (onlySelected.isChecked() && !chosen.contains(e.id())) continue;
                match.add(e);
            }
            Comparator<Home.Entity> cmp = switch (sort.getSelectedItemPosition()) {
                case 1 -> Comparator.comparing(Home.Entity::areaName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(Home.Entity::label, String.CASE_INSENSITIVE_ORDER);
                case 2 -> Comparator.comparing(Home.Entity::domain, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(Home.Entity::label, String.CASE_INSENSITIVE_ORDER);
                case 3 -> Comparator.comparing(Home.Entity::state, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(Home.Entity::label, String.CASE_INSENSITIVE_ORDER);
                default -> Comparator.comparing(Home.Entity::label, String.CASE_INSENSITIVE_ORDER);
            };
            match.sort(cmp);
            visible.clear();
            visible.addAll(match);
            adapter.notifyDataSetChanged();
            list.clearChoices();
            for (int i = 0; i < visible.size(); i++) {
                list.setItemChecked(i, chosen.contains(visible.get(i).id()));
            }
            info.setText(chosen.size() + " / " + MAX_SELECTION + " Favoriten für die Uhr · "
                    + match.size() + " Treffer");
        };
        list.setOnItemClickListener((parent, view, position, id) -> {
            Home.Entity item = visible.get(position);
            if (!chosen.remove(item.id())) {
                if (chosen.size() == MAX_SELECTION) {
                    list.setItemChecked(position, false);
                    Toast.makeText(activity, "Maximal 24 Favoriten auf der Uhr.", Toast.LENGTH_LONG).show();
                    return;
                }
                chosen.add(item.id());
            }
            if (onlySelected.isChecked()) update.run();
            else info.setText(chosen.size() + " / " + MAX_SELECTION + " Favoriten · "
                    + visible.size() + " Treffer");
        });
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) { update.run(); }
            public void afterTextChanged(Editable s) {}
        });
        android.widget.AdapterView.OnItemSelectedListener listener = new android.widget.AdapterView.OnItemSelectedListener() {
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                update.run();
            }
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        };
        room.setOnItemSelectedListener(listener);
        type.setOnItemSelectedListener(listener);
        sort.setOnItemSelectedListener(listener);
        onlyAvailable.setOnCheckedChangeListener((buttonView, checked) -> update.run());
        onlySelected.setOnCheckedChangeListener((buttonView, checked) -> update.run());
        update.run();
        new AlertDialog.Builder(activity)
                .setTitle("Favoriten auswählen")
                .setView(panel)
                .setNegativeButton("Abbrechen", null)
                .setPositiveButton("Übernehmen", (dialog, which) -> apply.accept(chosen))
                .show();
    }
}
