package com.villageclock.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import java.util.*;
import org.json.*;

/** Current Build Menu layout adapted for a portrait offline companion. */
public final class PlannerActivity extends Activity {
    private static final int WHITE=0xFFFFF7E8, GOLD=0xFFFFD15A, INK=0xFF38281C, GREEN=0xFFBCFF72;
    private UpgradePlanner planner; private Village village; private String raw,base="home"; private boolean sample;
    private UpgradePlanner.Tab tab=UpgradePlanner.Tab.BUILDINGS;
    private final Map<String,UpgradePlanner.Filter> filters=new HashMap<>();
    private LinearLayout root,top,bottom; private ListView list; private TextView summary;
    private List<UpgradePlanner.Row> all=Collections.emptyList(); private List<UpgradePlanner.Row> visible=Collections.emptyList();
    @Override public void onCreate(Bundle state){super.onCreate(state);
        try{planner=Store.planner(this);sample=getIntent().getBooleanExtra("sample",false);String tag=getIntent().getStringExtra("tag");
            if(sample){raw=sampleExport();village=ExportParser.parse(raw,Store.catalog(this),System.currentTimeMillis());}
            else {village=Store.load(this,tag);if(village==null){finish();return;}raw=Store.raw(this,village.tag);}
            if(state!=null){base=state.getString("base","home");tab=UpgradePlanner.Tab.values()[Math.min(3,Math.max(0,state.getInt("tab")))];}
            if(base.equals("builder")&&tab.ordinal()>1)tab=UpgradePlanner.Tab.BUILDINGS;
            shell();render();
        }catch(Exception e){TextView error=label("Could not load upgrade data. Import a fresh village export and try again.",18,WHITE,true);error.setPadding(dp(24),dp(40),dp(24),dp(24));error.setBackgroundColor(INK);setContentView(error);}}
    @Override public void onSaveInstanceState(Bundle out){out.putString("base",base);out.putInt("tab",tab.ordinal());super.onSaveInstanceState(out);}
    private void shell(){root=column();root.setBackgroundColor(0xFF49301F);root.setPadding(dp(10),dp(12),dp(10),dp(10));setContentView(root);
        root.setOnApplyWindowInsetsListener((v,insets)->{if(android.os.Build.VERSION.SDK_INT>=30){android.graphics.Insets b=insets.getInsets(WindowInsets.Type.systemBars());root.setPadding(dp(10)+b.left,dp(12)+b.top,dp(10)+b.right,dp(10)+b.bottom);}else root.setPadding(dp(10),dp(12)+insets.getSystemWindowInsetTop(),dp(10),dp(10)+insets.getSystemWindowInsetBottom());return insets;});
        top=column();root.addView(top);list=new ListView(this);list.setDivider(null);list.setDividerHeight(dp(6));list.setClipToPadding(false);list.setPadding(0,dp(6),0,dp(6));
        root.addView(list,new LinearLayout.LayoutParams(-1,0,1));bottom=column();root.addView(bottom);
        list.setOnItemClickListener((parent,v,position,id)->details(visible.get(position)));}
    private UpgradePlanner.Filter filter(){return filters.computeIfAbsent(base+":"+tab,k->new UpgradePlanner.Filter());}
    private void render(){try{all=planner.rows(raw,village,base,tab,System.currentTimeMillis());}catch(Exception e){all=Collections.emptyList();}
        visible=UpgradePlanner.select(all,filter());boolean auto=false;
        if(visible.isEmpty()&&!all.isEmpty()&&!filter().status.equals("Maxed")&&!filter().status.equals("All")&&(!filter().resources.isEmpty()||!filter().types.isEmpty()||!filter().heroes.isEmpty()||!filter().status.equals("Remaining"))){filter().clear();visible=UpgradePlanner.select(all,filter());auto=true;}
        top.removeAllViews();LinearLayout caption=row();Button back=control("‹",false,v->finish());caption.addView(back,new LinearLayout.LayoutParams(dp(44),dp(40)));
        TextView name=label(sample?"SAMPLE · BUILD MENU":Store.title(this,village)+" · BUILD MENU",16,GOLD,true);name.setMaxLines(1);name.setEllipsize(android.text.TextUtils.TruncateAt.END);caption.addView(name,new LinearLayout.LayoutParams(0,-2,1));top.addView(caption);space(top,6);
        LinearLayout bases=row();for(String b:new String[]{"home","builder"}){Button button=control(b.equals("home")?"Village Base":"Builder Base",base.equals(b),v->{base=b;if(base.equals("builder")&&tab.ordinal()>1)tab=UpgradePlanner.Tab.BUILDINGS;render();list.setSelection(0);});bases.addView(button,new LinearLayout.LayoutParams(0,dp(44),1));}top.addView(bases);space(top,8);
        LinearLayout header=row();header.setPadding(dp(8),dp(6),dp(8),dp(6));header.setBackgroundResource(R.drawable.build_header);
        TextView title=label(tab.label,23,WHITE,true);title.setShadowLayer(dp(1),0,dp(2),0xFF191512);header.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        header.addView(control("☷",false,v->filters()),new LinearLayout.LayoutParams(dp(52),dp(46)));header.getChildAt(1).setContentDescription("Sort and filter upgrades");top.addView(header);
        summary=label(visible.size()+" groups · "+filter().status+" · "+sortName(filter().sort),12,GOLD,false);summary.setPadding(dp(5),dp(8),dp(5),dp(3));top.addView(summary);
        if(auto)top.addView(label("Filters cleared: no matching upgrades remain.",11,WHITE,false));
        TextView baseline=label("Baseline costs / times · tap a card for requirements",11,WHITE,false);baseline.setPadding(dp(5),0,dp(5),dp(3));top.addView(baseline);
        list.setAdapter(new BaseAdapter(){public int getCount(){return visible.size()+(visible.isEmpty()?1:0);}public Object getItem(int n){return visible.isEmpty()?null:visible.get(n);}public long getItemId(int n){return n;}
            public boolean isEnabled(int n){return !visible.isEmpty();}public View getView(int n,View old,ViewGroup p){return visible.isEmpty()?empty():card(visible.get(n));}});
        bottom.removeAllViews();LinearLayout tabs=row();String[] icons={"⌂","⚔","◆","♞"};int count=base.equals("builder")?2:4;
        int[] tabArt={1000015,4000000,90000000,73000000};
        for(int i=0;i<count;i++){final UpgradePlanner.Tab selected=UpgradePlanner.Tab.values()[i];Button b=control(selected.label,tab==selected,v->{tab=selected;render();list.setSelection(0);});b.setTextSize(12);b.setPadding(dp(2),dp(5),dp(2),dp(4));
            android.graphics.drawable.Drawable art=getDrawable(ItemArt.icon(this,tabArt[i]));art.setBounds(0,0,dp(26),dp(26));b.setCompoundDrawables(null,art,null,null);tabs.addView(b,new LinearLayout.LayoutParams(0,dp(82),1));}bottom.addView(tabs);
        TextView identity=label(Store.identity(this,village),11,GOLD,false);identity.setGravity(Gravity.CENTER);bottom.addView(identity);
        TextView footer=label("Export snapshot · "+android.text.format.DateFormat.format("MMM d, HH:mm",village.exportedAt)+" · sync after changes",10,WHITE,false);footer.setGravity(Gravity.CENTER);bottom.addView(footer);
    }
    private View empty(){TextView v=label("No upgrades match this view. Try All, another tab, or a fresh export. An omitted base is not treated as an empty village.",16,WHITE,false);v.setPadding(dp(18),dp(26),dp(18),dp(26));return v;}
    private View card(UpgradePlanner.Row r){LinearLayout box=column();box.setPadding(dp(10),dp(10),dp(10),dp(10));box.setBackgroundResource(R.drawable.build_card);
        LinearLayout line=row();FrameLayout art=new FrameLayout(this);art.setBackgroundResource(R.drawable.icon_circle);ImageView icon=new ImageView(this);icon.setImageResource(ItemArt.icon(this,r.item.id));icon.setScaleType(ImageView.ScaleType.FIT_CENTER);art.addView(icon,new FrameLayout.LayoutParams(-1,-1));
        TextView level=label(String.valueOf(r.level),12,WHITE,true);level.setGravity(Gravity.CENTER);level.setBackgroundColor(0xFF292420);FrameLayout.LayoutParams badge=new FrameLayout.LayoutParams(dp(24),dp(20),Gravity.BOTTOM|Gravity.START);art.addView(level,badge);line.addView(art,new LinearLayout.LayoutParams(dp(54),dp(54)));
        LinearLayout words=column();words.setPadding(dp(10),0,0,0);TextView name=label(r.item.name+(r.count>1?" × "+r.count:""),20,r.fresh?GREEN:WHITE,true);name.setShadowLayer(dp(1),0,dp(2),0xFF392417);words.addView(name);
        words.addView(label(r.state==UpgradePlanner.State.RUNNING?"Upgrading → "+(r.level+1):r.next==null?"Level "+r.level:"Level "+r.level+" → "+r.next.optInt("level")+" · max "+r.item.max(),12,INK,true));line.addView(words,new LinearLayout.LayoutParams(0,-2,1));box.addView(line);space(box,6);
        if(r.state==UpgradePlanner.State.RUNNING){TextView timer=label("",16,WHITE,true);CountdownText.bind(timer,r.finishAt,"Timer ended · sync to confirm");box.addView(timer);}
        else if(r.next!=null){box.addView(label(r.costText()+(r.count>1?" each":""),14,WHITE,true));box.addView(label("◷ "+duration(r.seconds())+(r.count>1?" each":""),12,INK,true));}
        TextView state=label(r.status(),12,r.state==UpgradePlanner.State.LOCKED?0xFF44251C:INK,true);state.setMaxLines(2);box.addView(state);
        if(!r.requirements.isEmpty()){TextView req=label(r.requirements,11,INK,false);req.setMaxLines(2);box.addView(req);}return box;}
    private void details(UpgradePlanner.Row r){String text=r.status()+"\n\n";
        if(r.next!=null)text+="Next level: "+r.next.optInt("level")+"\nBaseline cost: "+r.costText()+"\nBaseline duration: "+duration(r.seconds())+"\n\n";
        text+="Requirements: "+(r.requirements.isEmpty()?"No level gate listed in the catalog.":r.requirements)+"\n\nCatalog max: "+r.item.max()+"\n"+r.levelsLeft()+" levels left per item to catalog max (including an upgrade in progress).\n";
        if(r.count>1)text+="This group represents "+r.count+" items at the same snapshot level. Prices and times are per item.\n";
        JSONArray unlock=r.item.data.optJSONArray("unlockRequirement");if(unlock!=null&&r.fresh)text+="\nCatalog unlock notes: "+unlock.toString()+"\n";
        if(r.item.seasonal()||Arrays.asList("town-hall","builder-hall").contains(r.item.category)||r.item.data.optString("id").contains("ricochet")||r.item.data.optString("id").contains("multi-"))text+="\nMerge recipes, crafted modules, hall prerequisites and special conditions require confirmation in game.\n";
        text+="\nCosts and future durations are undiscounted catalog values. Event / Gold Pass discounts, potions and helpers can change the game quote. Requirements met does not mean affordable or that a builder is free. Finished timers do not change your saved levels; sync a fresh export.\n\nOffline catalog checked 8 Nov 2026. Special upgrades, supercharges and IDs without verified data may be unavailable.\n\nSource: "+r.item.data.optString("source","No catalog entry");
        new AlertDialog.Builder(this).setTitle(r.item.name).setMessage(text).setPositiveButton("Close",null).setNeutralButton("Open Clash",(d,w)->startActivity(new Intent(this,MainActivity.class).setAction("com.villageclock.app.OPEN_GAME").putExtra("tag",village.tag))).show();}
    private void filters(){UpgradePlanner.Filter f=filter();ScrollView scroll=new ScrollView(this);LinearLayout body=column();body.setPadding(dp(18),dp(10),dp(18),dp(10));scroll.addView(body);
        Set<String> resources=new TreeSet<>(),types=new TreeSet<>(),heroes=new TreeSet<>();for(UpgradePlanner.Row r:all){for(String currency:r.costs().keySet())resources.addAll(Arrays.asList(currency.split(" or ")));types.add(r.item.type());if(!r.item.hero.isEmpty())heroes.add(r.item.hero);}
        if(tab==UpgradePlanner.Tab.BUILDINGS){types.add("New");types.add("Non-Seasonal");}checks(body,"Resource",resources,f.resources);
        if(tab==UpgradePlanner.Tab.EQUIPMENT)checks(body,"Hero",heroes,f.heroes);else checks(body,"Type",types,f.types);
        body.addView(label("Status",17,INK,true));String[] statuses={"Remaining","All","Requirements met","In progress","Locked / unowned","Maxed"};RadioGroup status=new RadioGroup(this);
        for(String value:statuses){RadioButton b=new RadioButton(this);b.setText(value);b.setId(View.generateViewId());status.addView(b);if(value.equals(f.status))status.check(b.getId());b.setOnClickListener(v->f.status=value);}body.addView(status);
        body.addView(label("Sorting",17,INK,true));RadioGroup sorts=new RadioGroup(this);for(UpgradePlanner.Sort s:UpgradePlanner.Sort.values()){RadioButton b=new RadioButton(this);b.setId(View.generateViewId());b.setText(sortName(s));sorts.addView(b);if(f.sort==s)sorts.check(b.getId());b.setOnClickListener(v->f.sort=s);}body.addView(sorts);
        body.addView(label("Cost sorting compares amounts per item. Filter one currency for a meaningful comparison. Equipment uses selected ore amounts, or the sum of listed ores. Filters are separate per base/tab and reset when this menu closes.",12,INK,false));
        new AlertDialog.Builder(this).setTitle("Sort / filter · "+tab.label).setView(scroll).setPositiveButton("Apply",(d,w)->{render();list.setSelection(0);}).setNeutralButton("Reset",(d,w)->{f.clear();render();list.setSelection(0);}).setNegativeButton("Close",(d,w)->render()).show();}
    private void checks(LinearLayout body,String title,Set<String> values,Set<String> selected){body.addView(label(title,17,INK,true));for(String value:values){CheckBox b=new CheckBox(this);b.setText(value);b.setChecked(selected.contains(value));b.setOnCheckedChangeListener((v,checked)->{if(checked)selected.add(value);else selected.remove(value);});body.addView(b);}}
    public static String sampleExport(){return "{\"tag\":\"#SAMPLE\",\"name\":\"Sample Chief\",\"timestamp\":"+(System.currentTimeMillis()/1000)+",\"buildings\":[{\"data\":1000001,\"lvl\":15},{\"data\":1000015,\"lvl\":2,\"cnt\":5},{\"data\":1000009,\"lvl\":18,\"cnt\":3},{\"data\":1000008,\"lvl\":19,\"timer\":96000},{\"data\":1000007,\"lvl\":11},{\"data\":1000070,\"lvl\":7},{\"data\":1000071,\"lvl\":7},{\"data\":1000068,\"lvl\":4},{\"data\":1000010,\"lvl\":15,\"cnt\":50}],\"heroes\":[{\"data\":28000000,\"lvl\":50}],\"units\":[{\"data\":4000000,\"lvl\":8}],\"equipment\":[{\"data\":90000000,\"lvl\":9}],\"pets\":[{\"data\":73000000,\"lvl\":5}],\"buildings2\":[{\"data\":1000034,\"lvl\":8},{\"data\":1000044,\"lvl\":7,\"cnt\":3}],\"units2\":[{\"data\":4000031,\"lvl\":14}]}";}
    private String sortName(UpgradePlanner.Sort s){switch(s){case COST_MIN:return "Cost Min";case COST_MAX:return "Cost Max";case TIME_MIN:return "Time Min";case NAME:return "Name";default:return "In progress first";}}
    static String duration(long seconds){return seconds==0?"Instant / unlock":DurationText.seconds(seconds);}
    private LinearLayout column(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);return v;}
    private LinearLayout row(){LinearLayout v=new LinearLayout(this);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    private TextView label(String value,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setTypeface(bold?getResources().getFont(R.font.lilita_one):Typeface.create("sans-serif",Typeface.NORMAL));return t;}
    private Button control(String label,boolean selected,View.OnClickListener click){Button b=new Button(this);b.setAllCaps(false);b.setText(label);b.setTextSize(15);b.setTypeface(getResources().getFont(R.font.lilita_one));b.setTextColor(selected?INK:WHITE);b.setBackgroundResource(selected?R.drawable.btn_primary:R.drawable.build_header);b.setOnClickListener(click);return b;}
    private void space(LinearLayout parent,int size){parent.addView(new View(this),new LinearLayout.LayoutParams(1,dp(size)));}
    private int dp(float n){return (int)(getResources().getDisplayMetrics().density*n+.5f);}
}
