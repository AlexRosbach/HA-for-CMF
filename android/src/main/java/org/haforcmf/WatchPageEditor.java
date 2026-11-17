package org.haforcmf;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.graphics.Color;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/** Edits watch pages on the phone and renders an interactive preview. */
final class WatchPageEditor {
    interface Host {
        void changed(WatchPages pages);
        void fetchCatalog(Consumer<Home.Catalog> callback);
    }
    private final Activity activity;
    private final WatchPages pages;
    private final Host host;
    private Home.Catalog catalog;
    private int active=0;
    private final WatchPreview preview;
    private final LinearLayout slots;
    private final TextView heading, counter;
    private final LinearLayout parent;

    WatchPageEditor(Activity activity,LinearLayout container,WatchPages pages,Host host){
        this.activity=activity;this.pages=pages;this.host=host;this.parent=container;
        UiKit.title(activity,container,"Uhr-Oberfläche gestalten",
                "Seiten und Kacheln direkt in der Vorschau bearbeiten.");
        var card=UiKit.card(activity,container);
        var controls=UiKit.row(activity);
        card.addView(controls,new LinearLayout.LayoutParams(-1,UiKit.dp(activity,54)));
        TextView prev=UiKit.label(activity,"❮",25,true,UiKit.INK);
        prev.setGravity(Gravity.CENTER);
        controls.addView(prev,new LinearLayout.LayoutParams(UiKit.dp(activity,54),-1));
        LinearLayout names=UiKit.column(activity);
        controls.addView(names,new LinearLayout.LayoutParams(0,-2,1));
        heading=UiKit.label(activity,"Favoriten",18,true,UiKit.INK);
        heading.setGravity(Gravity.CENTER);
        names.addView(heading);
        counter=UiKit.label(activity,"Seite 1 von 1",13,false,UiKit.MUTED);
        counter.setGravity(Gravity.CENTER);
        names.addView(counter);
        TextView next=UiKit.label(activity,"❯",25,true,UiKit.INK);
        next.setGravity(Gravity.CENTER);
        controls.addView(next,new LinearLayout.LayoutParams(UiKit.dp(activity,54),-1));
        prev.setOnClickListener(v->turn(-1));
        next.setOnClickListener(v->turn(1));

        preview=new WatchPreview(activity);
        card.addView(preview,new LinearLayout.LayoutParams(-1,-2));
        preview.setListener(new WatchPreview.Listener(){
            public void onSlot(int i){slotMenu(i);}
            public void onPrevious(){turn(-1);}
            public void onNext(){turn(1);}
            public void onTitle(){rename();}
        });
        TextView hint=UiKit.label(activity,
                "Tippe in der Vorschau auf eine Kachel, um sie zu belegen.",13,false,UiKit.MUTED);
        hint.setGravity(Gravity.CENTER);
        card.addView(hint);

        slots=UiKit.column(activity);
        card.addView(slots);
        UiKit.button(activity,card,"+  Neue Favoritenseite",true,()->{
            if(!pages.addPage()){
                Toast.makeText(activity,"Maximal sechs Seiten möglich.",Toast.LENGTH_LONG).show();
                return;
            }
            active=pages.pages.size()-1;
            save();
        });
        var buttons=UiKit.row(activity);
        card.addView(buttons);
        linkButton(buttons,"Umbenennen",()->rename());
        linkButton(buttons,"Verschieben",()->move());
        linkButton(buttons,"Löschen",()->delete());
        UiKit.button(activity,card,"✓  Seiten speichern & an Uhr senden",true,()->{
            save();
            Toast.makeText(activity,"Gespeichert. Die Uhr lädt die Seiten neu.",
                    Toast.LENGTH_LONG).show();
        });
        refresh();
    }
    private void linkButton(LinearLayout parent,String label,Runnable action){
        TextView view=UiKit.label(activity,label,13,true,UiKit.ORANGE);
        view.setGravity(Gravity.CENTER);
        parent.addView(view,new LinearLayout.LayoutParams(0,UiKit.dp(activity,50),1));
        view.setOnClickListener(v->action.run());
    }
    void setCatalog(Home.Catalog catalog){
        this.catalog=catalog;
        preview.setCatalog(catalog);
        refresh();
    }
    private void turn(int direction){
        int size=pages.pages.size();
        active=(active+direction+size)%size;
        refresh();
    }
    private WatchPages.Page page(){return pages.pages.get(active);}
    void refreshFromConfig(){refresh();}
    private void refresh(){
        if(active>=pages.pages.size())active=pages.pages.size()-1;
        heading.setText(page().title);
        counter.setText(LocaleKit.t(activity,"Seite "+(active+1)+" von "+pages.pages.size()));
        preview.setPage(page(),active,pages.pages.size());
        slots.removeAllViews();
        for(int i=0;i<WatchPages.SLOTS;i++){
            final int slot=i;
            WatchPages.Page page=page();
            String id=page.ids[i];
            String label=id.isBlank()?"Kachel "+(i+1)+" · Nicht belegt":
                    "Kachel "+(i+1)+" · "+displayName(i);
            var row=UiKit.row(activity);
            row.setPadding(UiKit.dp(activity,8),UiKit.dp(activity,6),
                    UiKit.dp(activity,8),UiKit.dp(activity,6));
            var text=UiKit.label(activity,label,14,!id.isBlank(),UiKit.INK);
            row.addView(text,new LinearLayout.LayoutParams(0,UiKit.dp(activity,48),1));
            var edit=UiKit.label(activity,"Bearbeiten ›",13,true,UiKit.ORANGE);
            row.addView(edit,new LinearLayout.LayoutParams(-2,-2));
            slots.addView(row);
            row.setOnClickListener(v->slotMenu(slot));
        }
    }
    private String displayName(int index){
        WatchPages.Page p=page();
        if(!p.labels[index].isBlank())return p.labels[index];
        String id=p.ids[index];
        if(catalog!=null)for(Home.Entity e:catalog.entities()){
            if(e.id().equals(id))return e.label();
        }
        return id.substring(id.indexOf('.')+1).replace('_',' ');
    }
    private void save(){
        host.changed(pages);
        refresh();
    }
    private EditText entry(String text,int max){
        EditText edit=new EditText(activity);
        edit.setText(text);edit.setSelectAllOnFocus(true);edit.setSingleLine(true);
        edit.setFilters(new InputFilter[]{new InputFilter.LengthFilter(max)});
        edit.setTextColor(UiKit.INK);
        edit.setHintTextColor(UiKit.MUTED);
        edit.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        return edit;
    }
    private void rename(){
        EditText input=entry(page().title,24);
        new AlertDialog.Builder(activity)
                .setTitle(LocaleKit.t(activity,"Seite umbenennen"))
                .setMessage(LocaleKit.t(activity,"Dieser Name erscheint auch auf der Uhr."))
                .setView(input)
                .setNegativeButton(LocaleKit.t(activity,"Abbrechen"),null)
                .setPositiveButton(LocaleKit.t(activity,"Speichern"),(d,w)->{
                    page().title=WatchPages.name(input.getText().toString(),"Favoriten");
                    save();
                }).show();
    }
    private void move(){
        String[] options={"Seite nach links verschieben","Seite nach rechts verschieben"};
        new AlertDialog.Builder(activity).setTitle(LocaleKit.t(activity,"Seite verschieben"))
                .setItems(options,(d,position)->{
                    int next=active+(position==0?-1:1);
                    if(next<0||next>=pages.pages.size())return;
                    pages.movePage(active,next);
                    active=next;save();
                }).show();
    }
    private void delete(){
        new AlertDialog.Builder(activity).setTitle(LocaleKit.t(activity,"Seite löschen?"))
                .setMessage(LocaleKit.t(activity,"Alle Kacheln dieser Seite werden entfernt. Mindestens eine Seite bleibt bestehen."))
                .setNegativeButton(LocaleKit.t(activity,"Abbrechen"),null)
                .setPositiveButton(LocaleKit.t(activity,"Löschen"),(d,w)->{
                    pages.deletePage(active);
                    active=Math.min(active,pages.pages.size()-1);
                    save();
                }).show();
    }
    private void slotMenu(int slot){
        String id=page().ids[slot];
        if(id.isBlank()){pick(slot);return;}
        new AlertDialog.Builder(activity).setTitle(displayName(slot))
                .setItems(new String[]{LocaleKit.t(activity,"Entität ändern"),LocaleKit.t(activity,"Kurzname ändern (Uhr)"),LocaleKit.t(activity,"Kachel leeren")},
                    (d,selection)->{
                        if(selection==0)pick(slot);
                        else if(selection==1)alias(slot);
                        else {
                            page().ids[slot]="";
                            page().labels[slot]="";
                            save();
                        }
                    }).setNegativeButton(LocaleKit.t(activity,"Abbrechen"),null).show();
    }
    private void alias(int slot){
        String existing=page().labels[slot];
        EditText input=entry(existing.isBlank()?displayName(slot):existing,10);
        new AlertDialog.Builder(activity).setTitle(LocaleKit.t(activity,"Kurzname für die Uhr"))
                .setMessage(LocaleKit.t(activity,"Maximal zehn Zeichen. Kurze Namen verhindern abgeschnittene Kacheln."))
                .setView(input)
                .setNegativeButton(LocaleKit.t(activity,"Abbrechen"),null)
                .setPositiveButton(LocaleKit.t(activity,"Speichern"),(d,w)->{
                    page().labels[slot]=WatchPages.name(input.getText().toString(),"");
                    save();
                }).show();
    }
    private void pick(int slot){
        if(catalog==null){
            host.fetchCatalog(data->{
                if(data==null){
                    Toast.makeText(activity,"Bitte zuerst Home Assistant verbinden.",Toast.LENGTH_LONG).show();
                    return;
                }
                setCatalog(data);
                choose(slot);
            });
        }else choose(slot);
    }
    private void choose(int slot){
        Dialog dialog=new Dialog(activity);
        var layout=UiKit.column(activity);
        layout.setPadding(UiKit.dp(activity,20),UiKit.dp(activity,20),
                UiKit.dp(activity,20),UiKit.dp(activity,22));
        layout.setBackgroundColor(UiKit.BG);
        TextView title=UiKit.label(activity,"Kachel "+(slot+1)+" belegen",23,true,UiKit.INK);
        layout.addView(title);
        EditText search=UiKit.field(activity,layout,"Entität auswählen",
                "Gerät, Entität oder Raum suchen","",InputType.TYPE_CLASS_TEXT);
        var info=UiKit.label(activity,"Tippe auf einen Eintrag",13,false,UiKit.MUTED);
        layout.addView(info);
        ListView list=new ListView(activity);
        ArrayList<Home.Entity> results=new ArrayList<>();
        ArrayAdapter<Home.Entity> adapter=new ArrayAdapter<>(activity,
                android.R.layout.simple_list_item_2,android.R.id.text1,results){
            @Override public View getView(int position,View convert,android.view.ViewGroup parent){
                View view=super.getView(position,convert,parent);
                Home.Entity item=getItem(position);
                TextView name=view.findViewById(android.R.id.text1);
                name.setText(item.label());name.setTextColor(UiKit.INK);
                TextView description=view.findViewById(android.R.id.text2);
                description.setText(item.areaName()+" · "+item.id());
                description.setTextColor(UiKit.MUTED);
                return view;
            }
        };
        list.setAdapter(adapter);
        layout.addView(list,new LinearLayout.LayoutParams(-1,0,1));
        Runnable filter=()->{
            String[] parts=normalized(search.getText().toString()).split("\\s+");
            results.clear();
            for(Home.Entity e:catalog.entities()){
                String hay=normalized(e.label()+" "+e.id()+" "+e.areaName());
                boolean match=true;
                for(String part:parts)if(!part.isEmpty()&&!hay.contains(part)){match=false;break;}
                if(match)results.add(e);
                if(results.size()>=250)break;
            }
            results.sort(Comparator.comparing(Home.Entity::label,String.CASE_INSENSITIVE_ORDER));
            info.setText(results.size()+" "+LocaleKit.t(activity,"Treffer")+" · "+LocaleKit.t(activity,"Entität auswählen"));
            adapter.notifyDataSetChanged();
        };
        list.setOnItemClickListener((parent,view,position,id)->{
            Home.Entity chosen=results.get(position);
            page().ids[slot]=chosen.id();
            page().labels[slot]="";
            dialog.dismiss();
            save();
        });
        search.addTextChangedListener(new TextWatcher(){
            public void beforeTextChanged(CharSequence value,int start,int count,int after){}
            public void onTextChanged(CharSequence value,int start,int before,int count){filter.run();}
            public void afterTextChanged(Editable value){}
        });
        UiKit.button(activity,layout,"Abbrechen",false,dialog::dismiss);
        filter.run();
        dialog.setContentView(layout);
        dialog.show();
        if(dialog.getWindow()!=null)dialog.getWindow().setLayout(-1,-1);
    }
    private String normalized(String value){
        return Normalizer.normalize(value,Normalizer.Form.NFKD)
                .replaceAll("\\p{M}+","").toLowerCase(Locale.ROOT);
    }
}
