package org.haforcmf;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashSet;

/**
 * Ordered, editable pages for the CMF watch. One page contains four fixed slots.
 * Stored encrypted alongside the existing Home Assistant configuration.
 */
final class WatchPages {
    static final int MAX_PAGES = 6;
    static final int SLOTS = 4;
    static final String KEY = "watch_pages_v06";

    static final class Page {
        String title;
        final String[] ids = new String[SLOTS];
        final String[] labels = new String[SLOTS];

        Page(String title) {
            this.title = name(title, "Favoriten");
            for (int i=0; i<SLOTS; i++) { ids[i]=""; labels[i]=""; }
        }
        int count() {
            int n=0;
            for (String id:ids) if (!id.isBlank()) n++;
            return n;
        }
    }
    final ArrayList<Page> pages = new ArrayList<>();

    WatchPages() { pages.add(new Page("Favoriten")); }

    static String name(String value, String fallback) {
        if (value==null || value.isBlank()) return fallback;
        String text=value.strip().replaceAll("[\\p{Cntrl}]", "");
        return text.length() > 24 ? text.substring(0,24) : text;
    }

    static WatchPages load(Context context, List<String> legacy, Map<String,String> rooms) {
        WatchPages result = new WatchPages();
        String saved = SettingsStore.load(context, KEY);
        if (!saved.isBlank()) {
            try {
                JSONArray data = new JSONArray(saved);
                result.pages.clear();
                for (int p=0; p<Math.min(data.length(),MAX_PAGES);p++) {
                    JSONObject json = data.getJSONObject(p);
                    Page page = new Page(json.optString("title","Seite "+(p+1)));
                    JSONArray slots=json.optJSONArray("slots");
                    if (slots!=null) for(int i=0;i<Math.min(slots.length(),SLOTS);i++){
                        JSONObject slot=slots.optJSONObject(i);
                        if(slot==null)continue;
                        String id=slot.optString("id","");
                        if(validId(id)) page.ids[i]=id;
                        page.labels[i]=name(slot.optString("label",""),"");
                    }
                    result.pages.add(page);
                }
                if(result.pages.isEmpty())result.pages.add(new Page("Favoriten"));
                return result;
            }catch(Exception ignored){
                result.pages.clear();
                result.pages.add(new Page("Favoriten"));
            }
        }
        // Migration: preserve old watch selection, but expose explicit pages instead
        // of silently grouping all favourite entities by the Home Assistant area.
        result.pages.clear();
        if(legacy!=null)for(String id:legacy){
            if(!validId(id))continue;
            if(result.pages.isEmpty() || result.pages.get(result.pages.size()-1).count()==SLOTS){
                if(result.pages.size()==MAX_PAGES)break;
                String room=rooms==null ? "" : rooms.getOrDefault(id,"");
                String title=room.isBlank() ? "Favoriten "+(result.pages.size()+1) : room;
                result.pages.add(new Page(title));
            }
            Page page=result.pages.get(result.pages.size()-1);
            page.ids[page.count()]=id;
        }
        if(result.pages.isEmpty())result.pages.add(new Page("Favoriten"));
        return result;
    }

    static boolean validId(String id) {
        return id!=null && id.matches("[a-z0-9_]+\\.[a-z0-9_]+");
    }

    void save(Context context) {
        JSONArray data=new JSONArray();
        try{
            for(Page page:pages){
                JSONObject entry=new JSONObject().put("title",page.title);
                JSONArray slots=new JSONArray();
                for(int i=0;i<SLOTS;i++){
                    slots.put(new JSONObject().put("id",page.ids[i])
                            .put("label",page.labels[i]));
                }
                entry.put("slots",slots);
                data.put(entry);
            }
            SettingsStore.save(context,KEY,data.toString());
        }catch(Exception ex){throw new IllegalStateException("Uhr-Seiten konnten nicht gespeichert werden.",ex);}
    }

    boolean addPage() {
        if(pages.size()>=MAX_PAGES)return false;
        pages.add(new Page("Seite "+(pages.size()+1)));
        return true;
    }
    void deletePage(int index) {
        if(index<0 || index>=pages.size())return;
        if(pages.size()==1){pages.set(0,new Page("Favoriten"));return;}
        pages.remove(index);
    }
    void movePage(int from,int to){
        if(from>=0&&to>=0&&from<pages.size()&&to<pages.size()){
            java.util.Collections.swap(pages,from,to);
        }
    }
    List<String> uniqueEntities(){
        var ids = new LinkedHashSet<String>();
        for(Page page:pages){
            for(String id:page.ids)if(validId(id))ids.add(id);
        }
        return new ArrayList<>(ids);
    }
    void reconcile(List<String> selected){
        var permitted=new LinkedHashSet<>(selected);
        // Preserve custom page names and aliases for existing entities.
        for(Page page:pages)for(int i=0;i<SLOTS;i++){
            if(!page.ids[i].isBlank()&&!permitted.contains(page.ids[i])){
                page.ids[i]="";
                page.labels[i]="";
            }
        }
        var present=new LinkedHashSet<>(uniqueEntities());
        for(String id:permitted){
            if(!validId(id)||present.contains(id))continue;
            boolean placed=false;
            for(Page page:pages){
                for(int i=0;i<SLOTS;i++)if(page.ids[i].isBlank()){
                    page.ids[i]=id;placed=true;break;
                }
                if(placed)break;
            }
            if(!placed && addPage()){
                pages.get(pages.size()-1).ids[0]=id;placed=true;
            }
            if(placed)present.add(id);
        }
    }
}
