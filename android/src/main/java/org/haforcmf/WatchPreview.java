package org.haforcmf;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Color;
import android.view.MotionEvent;
import android.view.View;
import java.util.Map;
import java.util.HashMap;

/** Interactive to-scale phone preview using the same 466x466 tile geometry as watch/src/main.rs. */
final class WatchPreview extends View {
    interface Listener {
        void onSlot(int slot);
        void onPrevious();
        void onNext();
        void onTitle();
    }
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private WatchPages.Page page=new WatchPages.Page("Favoriten");
    private int index=0,total=1;
    private final Map<String,Home.Entity> catalog=new HashMap<>();
    private Listener listener;

    WatchPreview(Context context){
        super(context);
        setLayerType(View.LAYER_TYPE_SOFTWARE,null);
        setContentDescription("Interaktive Vorschau der Uhr. Entität antippen zum Bearbeiten.");
    }
    void setListener(Listener listener){this.listener=listener;}
    void setPage(WatchPages.Page page,int index,int total){
        this.page=page;this.index=index;this.total=total;
        invalidate();
    }
    void setCatalog(Home.Catalog entries){
        catalog.clear();
        if(entries!=null)for(Home.Entity e:entries.entities())catalog.put(e.id(),e);
        invalidate();
    }
    @Override protected void onMeasure(int widthSpec,int heightSpec){
        int width=MeasureSpec.getSize(widthSpec);
        int min=Math.round(getResources().getDisplayMetrics().density*230);
        int size=Math.max(min,Math.min(width,Math.round(getResources().getDisplayMetrics().density*360)));
        setMeasuredDimension(width,size);
    }
    private void fill(int color){paint.setColor(color);paint.setStyle(Paint.Style.FILL);}
    private void rounded(Canvas c,float x,float y,float w,float h,float radius,int color){
        fill(color);c.drawRoundRect(new RectF(x,y,x+w,y+h),radius,radius,paint);
    }
    private void txt(Canvas c,String str,float center,float y,int size,int color,boolean bold,int max){
        if(str==null)return;
        String line=str;
        paint.setTextSize(size);
        paint.setTypeface(bold?android.graphics.Typeface.create("sans-serif",android.graphics.Typeface.BOLD):
                android.graphics.Typeface.create("sans-serif",android.graphics.Typeface.NORMAL));
        if(max>0 && line.length()>max)line=line.substring(0,max-1)+"…";
        paint.setColor(color);paint.setStyle(Paint.Style.FILL);paint.setTextAlign(Paint.Align.CENTER);
        c.drawText(line,center,y,paint);
    }
    private String title(int slot){
        if(page.ids[slot].isBlank())return "Belegen";
        if(!page.labels[slot].isBlank())return page.labels[slot];
        Home.Entity e=catalog.get(page.ids[slot]);
        return e==null?page.ids[slot].substring(page.ids[slot].indexOf('.')+1):e.label();
    }
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);
        float size=Math.min(getWidth(),getHeight());
        float left=(getWidth()-size)/2f, top=(getHeight()-size)/2f;
        canvas.save();
        canvas.translate(left,top);
        canvas.scale(size/466f,size/466f);
        fill(Color.rgb(55,60,66));canvas.drawCircle(233,233,229,paint);
        fill(Color.rgb(8,10,13));canvas.drawCircle(233,233,217,paint);
        rounded(canvas,122,44,222,42,18,Color.rgb(37,39,44));
        txt(canvas,"VERBUNDEN",233,71,20,Color.rgb(90,220,146),true,13);
        txt(canvas,page.title,233,130,26,Color.WHITE,true,13);
        for(int i=0;i<4;i++){
            int x=(i%2==0)?75:245;
            int y=(i/2==0)?152:265;
            Home.Entity e=catalog.get(page.ids[i]);
            boolean active=e!=null && e.state().equals("on");
            boolean missing=page.ids[i].isBlank();
            int bg=active?Color.rgb(255,106,24):Color.rgb(38,42,48);
            rounded(canvas,x,y,146,99,18,bg);
            int labelColor=active?Color.rgb(16,19,25):Color.WHITE;
            txt(canvas,title(i),x+73,y+35,19,labelColor,true,11);
            String state=missing ? "+" : e==null ? "—" : e.display();
            txt(canvas,state,x+73,y+73,16,labelColor,false,10);
        }
        rounded(canvas,99,381,89,42,14,Color.rgb(37,39,44));
        txt(canvas,"SUCHE",143,408,16,Color.rgb(255,106,24),true,6);
        txt(canvas,"‹",216,411,32,Color.WHITE,true,1);
        txt(canvas,"›",255,411,32,Color.WHITE,true,1);
        txt(canvas,(index+1)+"/"+total,323,410,19,Color.WHITE,true,7);
        canvas.restore();
    }
    @Override public boolean onTouchEvent(MotionEvent event){
        if(event.getAction()!=MotionEvent.ACTION_UP)return true;
        if(listener==null)return true;
        float size=Math.min(getWidth(),getHeight());
        float x=(event.getX()-(getWidth()-size)/2f)*466f/size;
        float y=(event.getY()-(getHeight()-size)/2f)*466f/size;
        if(y>=146&&y<=365) {
            int column=x>=245&&x<=391?1:x>=75&&x<=222?0:-1;
            int row=y>=265?1:y<=251?0:-1;
            if(column>=0 && row>=0)listener.onSlot(row*2+column);
        }else if(y>=377&&y<=430){
            if(x>182&&x<234)listener.onPrevious();
            else if(x>=234&&x<285)listener.onNext();
        }else if(y>=94&&y<142)listener.onTitle();
        return true;
    }
}
