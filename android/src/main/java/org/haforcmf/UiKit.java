package org.haforcmf;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import java.util.function.Consumer;

/** High-contrast, light CMF design primitives. No inherited dark theme colors. */
final class UiKit {
    static final int ORANGE = Color.rgb(255,106,24);
    static final int INK = Color.rgb(16,19,25);
    static final int MUTED = Color.rgb(79,86,98);
    static final int BG = Color.rgb(248,249,251);
    static final int CARD = Color.WHITE;
    static final int EDGE = Color.rgb(217,222,229);
    static final int PEACH = Color.rgb(255,236,225);
    static final int GREEN = Color.rgb(23,114,69);
    static int dp(Activity a,int n){return Math.round(a.getResources().getDisplayMetrics().density*n);}
    static GradientDrawable bg(Activity a,int color,int corner,int border){
        GradientDrawable d=new GradientDrawable();
        d.setColor(color);d.setCornerRadius(dp(a,corner));
        if(border!=0)d.setStroke(dp(a,1),border);
        return d;
    }
    static TextView label(Activity a,String value,int size,boolean strong,int color) {
        TextView t=new TextView(a);
        t.setText(value);t.setTextSize(size);t.setTextColor(color);
        if(strong)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        t.setGravity(android.view.Gravity.CENTER_VERTICAL);
        t.setIncludeFontPadding(true);
        return t;
    }
    static LinearLayout column(Activity a){
        LinearLayout l=new LinearLayout(a);l.setOrientation(LinearLayout.VERTICAL);return l;
    }
    static LinearLayout row(Activity a){
        LinearLayout l=new LinearLayout(a);l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(android.view.Gravity.CENTER_VERTICAL);return l;
    }
    static LinearLayout card(Activity a,LinearLayout parent) {
        LinearLayout l=column(a);l.setPadding(dp(a,16),dp(a,15),dp(a,16),dp(a,16));
        l.setBackground(bg(a,CARD,20,EDGE));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);
        p.setMargins(0,dp(a,7),0,dp(a,7));parent.addView(l,p);return l;
    }
    static TextView title(Activity a,LinearLayout parent,String name,String detail){
        TextView t=label(a,name,23,true,INK);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);
        p.setMargins(0,dp(a,18),0,dp(a,5));parent.addView(t,p);
        if(detail!=null){
            TextView sub=label(a,detail,14,false,MUTED);parent.addView(sub);
        }
        return t;
    }
    static Button button(Activity a,LinearLayout parent,String title,boolean primary,Runnable action){
        Button b=new Button(a);
        b.setAllCaps(false);
        b.setText(title);b.setTextSize(15);
        b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        b.setTextColor(INK);b.setBackgroundTintList(null);
        b.setBackground(bg(a,primary?ORANGE:PEACH,16,primary?0:EDGE));
        b.setMinHeight(dp(a,52));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(a,52));
        p.setMargins(0,dp(a,8),0,dp(a,2));
        parent.addView(b,p);b.setOnClickListener(v->action.run());
        return b;
    }
    static EditText field(Activity a,LinearLayout parent,String label,String hint,String value,int inputType){
        TextView l=label(a,label,14,true,INK);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(a,10),0,dp(a,3));parent.addView(l,lp);
        EditText e=new EditText(a);e.setSingleLine(true);
        e.setText(value);e.setHint(hint);e.setTextSize(15);
        e.setInputType(inputType);
        e.setTextColor(INK);e.setHintTextColor(MUTED);
        e.setPadding(dp(a,14),0,dp(a,14),0);
        e.setBackground(bg(a,Color.WHITE,14,EDGE));
        parent.addView(e,new LinearLayout.LayoutParams(-1,dp(a,52)));
        return e;
    }
    static TextView chip(Activity a,LinearLayout parent,String value,boolean selected,Runnable click){
        TextView t=label(a,value,14,selected,INK);
        t.setPadding(dp(a,14),dp(a,9),dp(a,14),dp(a,9));
        t.setGravity(android.view.Gravity.CENTER);
        t.setBackground(bg(a,selected?ORANGE:Color.WHITE,30,selected?0:EDGE));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,-2);
        p.setMargins(0,dp(a,4),dp(a,7),dp(a,4));parent.addView(t,p);
        if(click!=null)t.setOnClickListener(v->click.run());
        return t;
    }
}
