package com.villageclock.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.*;
import android.widget.*;

/** Offline release guide with expandable earlier releases. */
public final class WhatsNewActivity extends Activity {
    private static final int GOLD=0xFFFFC531,TEXT=0xFFFFF6E6,MUTED=0xFFCDBDA5;
    private LinearLayout content;
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(0xFF24180F);
        content=column();content.setPadding(dp(20),dp(24),dp(20),dp(24));scroll.addView(content);setContentView(scroll);
        scroll.setOnApplyWindowInsetsListener((view,insets)->{
            if(android.os.Build.VERSION.SDK_INT>=30){android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars());content.setPadding(dp(20)+bars.left,dp(24)+bars.top,dp(20)+bars.right,dp(24)+bars.bottom);}
            else content.setPadding(dp(20),dp(24)+insets.getSystemWindowInsetTop(),dp(20),dp(24)+insets.getSystemWindowInsetBottom());return insets;
        });
        button(content,"‹ Back",false,v->finish());space(content,16);
        content.addView(text("What’s New",30,GOLD,true));
        content.addView(text(getString(R.string.app_name)+" v"+BuildConfig.VERSION_NAME,15,TEXT,true));space(content,18);
        try{
            ReleaseNotes notes=ReleaseNotes.load(this);ReleaseNotes.Entry current=notes.current();ReleaseNotes.markSeen(this);
            addRelease(current,true);
            space(content,20);content.addView(text("Earlier Releases",21,GOLD,true));space(content,10);
            for(ReleaseNotes.Entry entry:notes.entries)if(entry.versionCode<current.versionCode)addRelease(entry,false);
        }catch(Exception unavailable){content.addView(text("Release notes are not available for this build. You can view published notes on GitHub Releases.",15,TEXT,false));}
        space(content,20);
        button(content,"View GitHub Releases",false,v->{
            try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(UpdateChecker.RELEASES_URL)).addCategory(Intent.CATEGORY_BROWSABLE));}
            catch(android.content.ActivityNotFoundException missing){Toast.makeText(this,"Open GitHub Releases in a browser: "+UpdateChecker.RELEASES_URL,Toast.LENGTH_LONG).show();}
        });
        space(content,10);content.addView(text("This guide is included with the app and can be read offline.",12,MUTED,false));
    }
    private void addRelease(ReleaseNotes.Entry entry,boolean current){
        LinearLayout card=column();card.setPadding(dp(14),dp(14),dp(14),dp(14));card.setBackgroundResource(R.drawable.card_bg);
        LinearLayout details=column();
        if(current){card.addView(text("v"+entry.version+" · "+entry.title,22,GOLD,true));space(card,10);}
        else{
            Button toggle=button(card,"v"+entry.version+" · "+entry.title,false,v->{
                boolean open=details.getVisibility()!=View.VISIBLE;details.setVisibility(open?View.VISIBLE:View.GONE);
                ((Button)v).setContentDescription((open?"Collapse ":"Expand ")+"v"+entry.version+" release notes");
            });toggle.setTextSize(16);toggle.setContentDescription("Expand v"+entry.version+" release notes");details.setVisibility(View.GONE);
        }
        for(ReleaseNotes.Highlight point:entry.highlights){space(details,10);details.addView(text(point.title,17,TEXT,true));space(details,4);details.addView(text(point.body,14,MUTED,false));}
        if(!entry.steps.isEmpty()){
            space(details,16);details.addView(text("How to Use It",17,GOLD,true));
            for(int i=0;i<entry.steps.size();i++){space(details,7);details.addView(text((i+1)+". "+entry.steps.get(i),14,TEXT,false));}
        }
        card.addView(details);content.addView(card);space(content,10);
    }
    private TextView text(String value,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setTypeface(bold?getResources().getFont(R.font.lilita_one):Typeface.create("sans-serif",Typeface.NORMAL));t.setLineSpacing(dp(2),1);return t;}
    private Button button(LinearLayout parent,String value,boolean primary,View.OnClickListener click){Button b=new Button(this);b.setText(value);b.setAllCaps(false);b.setTextSize(16);b.setTypeface(getResources().getFont(R.font.lilita_one));b.setTextColor(primary?0xFF3B2508:GOLD);b.setBackgroundResource(primary?R.drawable.btn_primary:R.drawable.btn_secondary);b.setMinHeight(dp(48));b.setPadding(dp(10),dp(5),dp(10),dp(5));b.setOnClickListener(click);parent.addView(b,new LinearLayout.LayoutParams(-1,-2));return b;}
    private LinearLayout column(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);return v;}
    private void space(LinearLayout p,int height){p.addView(new View(this),new LinearLayout.LayoutParams(1,dp(height)));}
    private int dp(float n){return (int)(getResources().getDisplayMetrics().density*n+.5f);}
}
