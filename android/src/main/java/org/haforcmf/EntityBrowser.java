package org.haforcmf;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.*;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Fullscreen, high-contrast entity browser with genuine room/type/search filters. */
final class EntityBrowser {
    interface OnApply { void accept(List<String> ids); }
    static final int MAX_SELECTION = 24;
    private static final String[] TYPES={"Alle Typen","Lichter","Schalter","Schlösser",
            "Szenen","Skripte","Buttons","Sensoren","Weitere"};
    private static final String[] SORT={"Name (A–Z)","Raum (A–Z)","Typ","Status"};
    private static String norm(String s){
        return Normalizer.normalize(s,Normalizer.Form.NFKD)
                .replaceAll("\\p{M}+","").toLowerCase(Locale.ROOT);
    }
    private static boolean match(Home.Entity e,String query){
        String data=norm(e.label()+" "+e.id()+" "+e.areaName()+" "+e.state()+" "+e.display());
        for(String part:norm(query).trim().split("\\s+")){
            if(!part.isEmpty()&&!data.contains(part))return false;
        }
        return true;
    }
    private static boolean type(Home.Entity e,int i){
        String d=e.domain();
        return switch(i){
            case 1->d.equals("light");
            case 2->d.equals("switch")||d.equals("input_boolean");
            case 3->d.equals("lock");
            case 4->d.equals("scene");
            case 5->d.equals("script");
            case 6->d.equals("button")||d.equals("input_button");
            case 7->d.equals("sensor")||d.equals("binary_sensor");
            case 8->!(d.equals("light")||d.equals("switch")||d.equals("input_boolean")
                    ||d.equals("lock")||d.equals("scene")||d.equals("script")
                    ||d.equals("button")||d.equals("input_button")
                    ||d.equals("sensor")||d.equals("binary_sensor"));
            default->true;
        };
    }
    private static String symbol(Home.Entity e){
        return switch(e.domain()){
            case "light"->"☀";
            case "lock"->"⌑";
            case "scene"->"◈";
            case "switch","input_boolean"->"⏻";
            case "sensor","binary_sensor"->"◉";
            case "script","button","input_button"->"▷";
            default->"◇";
        };
    }
    static void show(Activity a, Home.Catalog catalog, List<String> selected, OnApply save){
        ArrayList<String> chosen=new ArrayList<>(selected);
        ArrayList<Home.Entity> results=new ArrayList<>();
        String[] roomNames=new String[catalog.rooms().size()+2];
        String[] roomIds=new String[roomNames.length];
        roomNames[0]="Alle Räume"; roomIds[0]="";
        for(int i=0;i<catalog.rooms().size();i++){
            roomNames[i+1]=catalog.rooms().get(i).name();
            roomIds[i+1]=catalog.rooms().get(i).id();
        }
        roomNames[roomNames.length-1]="Ohne Raum";
        roomIds[roomIds.length-1]="__without__";
        int[] room={0}, category={0},sorting={0};
        boolean[] available={false},onlyFavorites={false};
        Dialog dialog=new Dialog(a);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout root=UiKit.column(a);
        root.setBackgroundColor(UiKit.BG);
        root.setPadding(UiKit.dp(a,18),UiKit.dp(a,16),UiKit.dp(a,18),UiKit.dp(a,8));
        dialog.setContentView(root);

        LinearLayout header=UiKit.row(a);
        TextView heading=UiKit.label(a,"Entitäten-Browser",24,true,UiKit.INK);
        header.addView(heading,new LinearLayout.LayoutParams(0,UiKit.dp(a,52),1));
        TextView exit=UiKit.label(a,"✕",23,true,UiKit.INK);
        exit.setGravity(Gravity.CENTER);
        header.addView(exit,new LinearLayout.LayoutParams(UiKit.dp(a,48),UiKit.dp(a,48)));
        exit.setOnClickListener(v->dialog.dismiss());
        root.addView(header);
        TextView subtitle=UiKit.label(a,"Suchen · Filtern · Zur Uhr hinzufügen",14,false,UiKit.MUTED);
        root.addView(subtitle);
        EditText search=UiKit.field(a,root,"","Entität, Gerät oder Raum suchen","",
                android.text.InputType.TYPE_CLASS_TEXT);
        search.setSingleLine(true);
        LinearLayout filters=UiKit.column(a);
        root.addView(filters);
        HorizontalScrollView roomScroll=new HorizontalScrollView(a);
        roomScroll.setHorizontalScrollBarEnabled(false);
        filters.addView(roomScroll);
        LinearLayout frow=UiKit.row(a);
        roomScroll.addView(frow);
        HorizontalScrollView filterScroll=new HorizontalScrollView(a);
        filterScroll.setHorizontalScrollBarEnabled(false);
        filters.addView(filterScroll);
        LinearLayout stateRow=UiKit.row(a);
        filterScroll.addView(stateRow);
        TextView roomButton=UiKit.chip(a,frow,"⌂  Alle Räume",false,null);
        TextView typeButton=UiKit.chip(a,frow,"◈  Alle Typen",false,null);
        TextView sortButton=UiKit.chip(a,stateRow,"↕  Name",false,null);
        TextView availableButton=UiKit.chip(a,stateRow,"✓  Verfügbare",false,null);
        TextView favoriteButton=UiKit.chip(a,stateRow,"♥  Favoriten",false,null);
        TextView resultCount=UiKit.label(a,"0 Ergebnisse",13,true,UiKit.MUTED);
        root.addView(resultCount);
        ListView list=new ListView(a);
        list.setDividerHeight(0);
        list.setBackgroundColor(UiKit.BG);
        root.addView(list,new LinearLayout.LayoutParams(-1,0,1));

        final Button[] saveButton={null};
        ArrayAdapter<Home.Entity> adapter=new ArrayAdapter<>(a,0,results){
            @Override public View getView(int position,View recycled,ViewGroup parent){
                Home.Entity e=getItem(position);
                LinearLayout card=UiKit.row(a);
                card.setPadding(UiKit.dp(a,12),UiKit.dp(a,10),UiKit.dp(a,12),UiKit.dp(a,10));
                card.setBackground(UiKit.bg(a,Color.WHITE,17,UiKit.EDGE));
                LinearLayout.LayoutParams iconParams=new LinearLayout.LayoutParams(
                        UiKit.dp(a,35),UiKit.dp(a,50));
                TextView icon=UiKit.label(a,symbol(e),25,true,UiKit.ORANGE);
                card.addView(icon,iconParams);
                LinearLayout texts=UiKit.column(a);
                card.addView(texts,new LinearLayout.LayoutParams(0,-2,1));
                TextView title=UiKit.label(a,e.label(),16,true,UiKit.INK);
                title.setMaxLines(2);
                texts.addView(title);
                TextView info=UiKit.label(a,e.areaName()+" · "+e.display(),13,false,UiKit.MUTED);
                texts.addView(info);
                TextView id=UiKit.label(a,e.id(),12,false,UiKit.MUTED);
                id.setSingleLine(true);id.setEllipsize(android.text.TextUtils.TruncateAt.END);
                texts.addView(id);
                CheckBox choice=new CheckBox(a);
                choice.setButtonTintList(ColorStateList.valueOf(UiKit.ORANGE));
                choice.setChecked(chosen.contains(e.id()));
                choice.setClickable(false);
                card.addView(choice,new LinearLayout.LayoutParams(
                        UiKit.dp(a,45),UiKit.dp(a,55)));
                LinearLayout outer=UiKit.column(a);
                outer.setPadding(0,UiKit.dp(a,4),0,UiKit.dp(a,4));
                outer.addView(card);
                return outer;
            }
        };
        list.setAdapter(adapter);
        Runnable update=()->{
            results.clear();
            String q=search.getText().toString();
            String roomId=roomIds[room[0]];
            for(Home.Entity e:catalog.entities()){
                if(!roomId.isEmpty() && !roomId.equals(e.areaId())
                        && !(roomId.equals("__without__")&&e.areaId().isEmpty()))continue;
                if(!type(e,category[0]) || !match(e,q))continue;
                if(available[0] && (e.state().equals("unknown") ||
                        e.state().equals("unavailable")))continue;
                if(onlyFavorites[0]&&!chosen.contains(e.id()))continue;
                results.add(e);
            }
            Comparator<Home.Entity> comparator=switch(sorting[0]){
                case 1->Comparator.comparing(Home.Entity::areaName,String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(Home.Entity::label,String.CASE_INSENSITIVE_ORDER);
                case 2->Comparator.comparing(Home.Entity::domain,String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(Home.Entity::label,String.CASE_INSENSITIVE_ORDER);
                case 3->Comparator.comparing(Home.Entity::state,String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(Home.Entity::label,String.CASE_INSENSITIVE_ORDER);
                default->Comparator.comparing(Home.Entity::label,String.CASE_INSENSITIVE_ORDER);
            };
            results.sort(comparator);
            resultCount.setText(results.size()+" Ergebnisse · "+chosen.size()+"/24 Favoriten");
            if(saveButton[0]!=null)saveButton[0].setText("✓  "+chosen.size()+"/24 Favoriten speichern");
            adapter.notifyDataSetChanged();
        };
        list.setOnItemClickListener((parent,view,index,id)->{
            Home.Entity e=results.get(index);
            if(!chosen.remove(e.id())){
                if(chosen.size()>=MAX_SELECTION){
                    Toast.makeText(a,"Maximal 24 Favoriten möglich.",Toast.LENGTH_LONG).show();
                    return;
                }
                chosen.add(e.id());
            }
            update.run();
        });
        roomButton.setOnClickListener(v->new AlertDialog.Builder(a)
                .setTitle("Raum auswählen")
                .setSingleChoiceItems(roomNames,room[0],(d,i)->{
                    room[0]=i;roomButton.setText("⌂  "+roomNames[i]);
                    d.dismiss();update.run();
                }).setNegativeButton("Abbrechen",null).show());
        typeButton.setOnClickListener(v->new AlertDialog.Builder(a)
                .setTitle("Gerätetyp auswählen")
                .setSingleChoiceItems(TYPES,category[0],(d,i)->{
                    category[0]=i;typeButton.setText("◈  "+TYPES[i]);
                    d.dismiss();update.run();
                }).setNegativeButton("Abbrechen",null).show());
        sortButton.setOnClickListener(v->new AlertDialog.Builder(a)
                .setTitle("Sortieren")
                .setSingleChoiceItems(SORT,sorting[0],(d,i)->{
                    sorting[0]=i;sortButton.setText("↕  "+SORT[i]);
                    d.dismiss();update.run();
                }).setNegativeButton("Abbrechen",null).show());
        availableButton.setOnClickListener(v->{
            available[0]=!available[0];
            availableButton.setBackground(UiKit.bg(a,available[0]?UiKit.PEACH:Color.WHITE,30,UiKit.EDGE));
            update.run();
        });
        favoriteButton.setOnClickListener(v->{
            onlyFavorites[0]=!onlyFavorites[0];
            favoriteButton.setBackground(UiKit.bg(a,onlyFavorites[0]?UiKit.PEACH:Color.WHITE,30,UiKit.EDGE));
            update.run();
        });
        Handler debounce=new Handler(Looper.getMainLooper());
        final Runnable[] work={null};
        search.addTextChangedListener(new TextWatcher(){
            public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            public void onTextChanged(CharSequence s,int start,int before,int count){
                if(work[0]!=null)debounce.removeCallbacks(work[0]);
                work[0]=update;
                debounce.postDelayed(work[0],180);
            }
            public void afterTextChanged(Editable s){}
        });
        LinearLayout footer=UiKit.row(a);
        root.addView(footer);
        LinearLayout buttons=UiKit.column(a);
        footer.addView(buttons,new LinearLayout.LayoutParams(-1,-2));
        saveButton[0]=UiKit.button(a,buttons,"✓  "+chosen.size()+"/24 Favoriten speichern",true,()->{
            save.accept(chosen);
            dialog.dismiss();
        });
        update.run();
        Window window=dialog.getWindow();
        if(window!=null){
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.setLayout(-1,-1);
        }
        dialog.show();
        window=dialog.getWindow();
        if(window!=null)window.setLayout(-1,-1);
    }
}
