package com.villageclock.app;

import android.app.AlertDialog;
import android.content.Context;
import android.view.*;
import android.widget.*;

/** Shared compact boost and availability rows for both upgrade screens. */
final class TimerPanels {
    static View effect(Context c,ActiveEffects.Effect effect){
        LinearLayout row=new LinearLayout(c);row.setId(R.id.active_effect_row);row.setTag(effect.key);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(c,7),dp(c,5),dp(c,7),dp(c,5));row.setBackgroundResource(R.drawable.row_bg);
        ImageView icon=new ImageView(c);icon.setImageResource(ActiveEffects.icon(c,effect));icon.setContentDescription(effect.name);icon.setScaleType(ImageView.ScaleType.FIT_CENTER);row.addView(icon,new LinearLayout.LayoutParams(dp(c,28),dp(c,30)));
        LinearLayout left=new LinearLayout(c);left.setOrientation(LinearLayout.VERTICAL);left.setPadding(dp(c,6),0,dp(c,4),0);left.addView(text(c,effect.name+" "+effect.rate,13,true));TextView remaining=text(c,"",11,false);CountdownText.bind(remaining,effect.endsAt,"Ended · sync to refresh",System::currentTimeMillis,now->DurationText.compactRemaining(effect.endsAt,now));left.addView(remaining);row.addView(left,new LinearLayout.LayoutParams(0,-2,1));
        TextView end=text(c,"",10,false);end.setGravity(Gravity.END);CountdownText.bind(end,effect.endsAt,"Ended",System::currentTimeMillis,now->"Ends at\n"+ClockText.at(c,effect.endsAt,now));row.addView(end,new LinearLayout.LayoutParams(dp(c,84),-2));
        row.setOnClickListener(v->new AlertDialog.Builder(c).setTitle(effect.name).setMessage(effect.event?"Official event: 50% lower cost and duration for eligible Home Village upgrades. Supercharges and Crafted Defenses are excluded. Exported timers already include applicable reductions; no second discount is applied.\n\nSupercell’s November 1, 2026 announcement gives the end as November 16 at 08:00 UTC.":effect.key.equals("lab_consumable")?"Study Soup adds 3× work speed: 4× alone, or 27× with a Research/Pet Potion. Duration comes from your last export.":"Duration comes from your last export. Stacked doses extend the deadline. Builder Potion takes priority over Builder Bite while both are active. Sync after using another item.").setPositiveButton("Close",null).show());return row;
    }
    static View nextFree(Context c,Village village,Village.Queue queue,long now){
        long finish=village.nextFree(queue,now);if(finish==0)return null;
        LinearLayout row=new LinearLayout(c);row.setId(R.id.next_free_row);row.setGravity(Gravity.CENTER_VERTICAL);row.setBackgroundResource(R.drawable.row_idle);row.setPadding(dp(c,8),dp(c,5),dp(c,8),dp(c,5));TextView left=text(c,"",12,true),right=text(c,"",11,true);right.setGravity(Gravity.END);
        CountdownText.bind(left,finish,"Slot may be free · sync",System::currentTimeMillis,time->"Next free in "+DurationText.compactRemaining(finish,time));CountdownText.bind(right,finish,ClockText.at(c,finish,now),System::currentTimeMillis,time->ClockText.at(c,finish,time));row.addView(left,new LinearLayout.LayoutParams(0,-2,1));row.addView(right,new LinearLayout.LayoutParams(dp(c,84),-2));return row;
    }
    private static TextView text(Context c,String value,int size,boolean bold){TextView text=new TextView(c);text.setText(value);text.setTextSize(size);text.setTextColor(0xFFFFF6E6);if(bold)text.setTypeface(c.getResources().getFont(R.font.lilita_one));return text;}
    private static int dp(Context c,int value){return (int)(c.getResources().getDisplayMetrics().density*value+.5f);}
    private TimerPanels(){}
}
