package com.villageclock.app;

import android.app.*;
import android.content.*;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.util.*;

/** Per-account progress, crafting, availability and collections in one compact destination. */
public final class OverviewActivity extends Activity {
    private static final int GOLD=0xFFFFC531,TEXT=0xFFFFF6E6,MUTED=0xFFCDBDA5;
    private LinearLayout root,header,footer;private ListView list;private Village village;private VillageOverview overview;private UpgradePlanner planner;
    private String tag="",tab="Progress",collection="equipment";private boolean builder,global,sample;private final List<View> cards=new ArrayList<>();
    @Override public void onCreate(Bundle saved){super.onCreate(saved);tag=getIntent().getStringExtra("tag");sample=getIntent().getBooleanExtra("sample",false);
        String requested=getIntent().getStringExtra("tab");if(Arrays.asList("Progress","Crafting","Helpers","Collection").contains(requested))tab=requested;builder=getIntent().getBooleanExtra("builder",false);
        if(saved!=null){tab=saved.getString("tab","Progress");builder=saved.getBoolean("builder");global=saved.getBoolean("global");collection=saved.getString("collection","equipment");}
        shell();}
    @Override public void onResume(){super.onResume();try{
        planner=Store.planner(this);String raw=sample?sampleExport():Store.raw(this,tag);village=ExportParser.parse(raw,Store.catalog(this),System.currentTimeMillis());overview=new VillageOverview(raw);render();
        if(!sample){Reminders.deliver(this);Reminders.schedule(this);}
    }catch(Exception unavailable){cards.clear();cards.add(label("Village unavailable. Return to the app and sync an export.",17,TEXT,false));showCards();}}
    @Override public void onSaveInstanceState(Bundle out){out.putString("tab",tab);out.putBoolean("builder",builder);out.putBoolean("global",global);out.putString("collection",collection);super.onSaveInstanceState(out);}
    private void shell(){
        root=column();root.setPadding(dp(16),dp(18),dp(16),dp(12));root.setBackgroundColor(0xFF24180F);setContentView(root);
        root.setOnApplyWindowInsetsListener((v,insets)->{if(android.os.Build.VERSION.SDK_INT>=30){android.graphics.Insets b=insets.getInsets(WindowInsets.Type.systemBars());root.setPadding(dp(16)+b.left,dp(18)+b.top,dp(16)+b.right,dp(12)+b.bottom);}else root.setPadding(dp(16),dp(18)+insets.getSystemWindowInsetTop(),dp(16),dp(12)+insets.getSystemWindowInsetBottom());return insets;});
        header=column();root.addView(header);list=new ListView(this);list.setId(R.id.overview_list);list.setDivider(null);list.setDividerHeight(dp(9));list.setClipToPadding(false);list.setPadding(0,dp(10),0,dp(10));root.addView(list,new LinearLayout.LayoutParams(-1,0,1));footer=column();root.addView(footer);
    }
    private void render(){
        cards.clear();header.removeAllViews();footer.removeAllViews();
        LinearLayout title=row();title.addView(button("‹",false,v->finish()),new LinearLayout.LayoutParams(dp(43),dp(43)));
        TextView name=label(sample?"SAMPLE · OVERVIEW":Store.title(this,village),22,GOLD,true);name.setMaxLines(1);name.setEllipsize(android.text.TextUtils.TruncateAt.END);name.setPadding(dp(9),0,0,0);title.addView(name,new LinearLayout.LayoutParams(0,-2,1));title.addView(button("News",false,v->OfficialUpdates.show(this)),new LinearLayout.LayoutParams(dp(55),dp(43)));header.addView(title);space(header,9);
        LinearLayout bases=row();for(boolean b:new boolean[]{false,true}){Button select=button(b?"Builder Base":"Village Base",builder==b,v->{builder=b;if(builder&&collection.equals("equipment"))collection="skins2";if(!builder&&collection.endsWith("2"))collection="equipment";render();list.setSelection(0);});bases.addView(select,new LinearLayout.LayoutParams(0,dp(44),1));}header.addView(bases);space(header,9);
        TextView heading=label(tab,27,GOLD,true);heading.setId(R.id.overview_heading);header.addView(heading);
        if(tab.equals("Progress"))renderProgress();else if(tab.equals("Crafting"))renderCrafting();else if(tab.equals("Helpers"))renderHelpers();else renderCollection();
        LinearLayout tabs=row();for(String value:new String[]{"Progress","Crafting","Helpers","Collection"}){Button b=button(value,tab.equals(value),v->{tab=value;render();list.setSelection(0);});b.setTextSize(12);b.setPadding(dp(2),0,dp(2),0);b.setContentDescription("Overview "+value);tabs.addView(b,new LinearLayout.LayoutParams(0,dp(48),1));}footer.addView(tabs);
        TextView sync=label("Synced "+Store.age(village.exportedAt,System.currentTimeMillis()),11,MUTED,false);sync.setGravity(Gravity.CENTER);footer.addView(sync);
        showCards();
    }
    private void renderProgress(){
        LinearLayout choices=row();choices.addView(button(global?"Catalog Max ▾":"Current Hall ▾",false,v->{global=!global;render();}),new LinearLayout.LayoutParams(0,dp(42),1));
        choices.addView(button("ⓘ",false,v->new AlertDialog.Builder(this).setTitle("Level Progress").setMessage("Progress counts exported level steps, not time or cost. Current Hall targets the catalog’s standard levels and quantities for your hall, including facility gates. Catalog Max uses the full offline catalog. Running timers keep their exported level until you sync. Seasonal modules, supercharges, uncertain merges and missing catalog entries do not receive an invented target. The catalog currently ends at TH18 / BH10.").setPositiveButton("Close",null).show()),new LinearLayout.LayoutParams(dp(44),dp(42)));header.addView(choices);
        if(!overview.hasBase(builder)){empty("This base was not included in the export.");return;}
        for(VillageOverview.Progress p:overview.progress(planner,builder,global)){
            LinearLayout card=card();card.addView(label(p.name,22,TEXT,true));card.addView(label(p.goal>0?p.percent()+"% · "+p.maxed+" / "+p.target+" at target":"Target unavailable",17,GOLD,true));
            if(p.goal>0){ProgressBar bar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);bar.setMax(100);bar.setProgress(p.percent());bar.setProgressTintList(android.content.res.ColorStateList.valueOf(0xFF8EE04A));card.addView(bar,new LinearLayout.LayoutParams(-1,dp(14)));}
            card.addView(label(p.owned+" owned · "+p.levels+" / "+p.goal+" level steps",13,MUTED,false));
            if(p.unknown>0)card.addView(label(p.unknown+" items need catalog data",12,GOLD,false));
            card.setOnClickListener(v->showProgressItems(p.name));cards.add(card);
        }
    }
    private void showProgressItems(String group){StringBuilder text=new StringBuilder();
        for(VillageOverview.Owned o:overview.owned(builder)){UpgradePlanner.Item item=planner.item(o.id);String g=item==null?"Buildings":VillageOverview.group(item);if(!group.equals(g))continue;
            text.append(item==null?"Item #"+o.id:item.name).append(" · Lv ").append(o.level).append(" × ").append(o.count).append('\n');}
        if(text.length()==0)text.append("No owned items in this category. Targets can include catalog items not built yet.");
        new AlertDialog.Builder(this).setTitle(group+" · Exported Levels").setMessage(text.toString()).setPositiveButton("Close",null).show();}
    private void renderCrafting(){
        if(builder){empty("Crafted defenses belong to Village Base. Switch bases to view them.");return;}
        List<VillageOverview.Craft> modules=overview.crafts();if(modules.isEmpty()){empty("No crafting modules were included in this export.");return;}
        Set<Integer> types=new HashSet<>();for(VillageOverview.Craft c:modules)types.add(c.type);header.addView(label(types.size()+" defense types · "+modules.size()+" modules",13,MUTED,false));
        int last=-1,ordinal=0;for(VillageOverview.Craft c:modules){if(c.type!=last){last=c.type;ordinal=0;cards.add(label(itemName(c.type,"Defense"),20,GOLD,true));}ordinal++;
            LinearLayout card=card();ItemIdentities.Entry identity=ItemIdentities.load(this).item(c.id);String title=identity==null?itemName(c.id,"Module "+ordinal):identity.shortName;
            portraitTitle(card,c.id,title);LinearLayout levelLine=row();if(identity!=null){ImageView stat=new ImageView(this);stat.setImageResource(ItemIdentities.moduleSymbol(identity.shortName));stat.setContentDescription(identity.shortName);levelLine.addView(stat,new LinearLayout.LayoutParams(dp(22),dp(22)));}
            levelLine.addView(label("Level "+c.level+(identity!=null&&identity.cap>0?" / "+identity.cap:""),21,GOLD,true));card.addView(levelLine);
            UpgradePlanner.Item meta=planner.item(c.id);int cap=identity!=null?identity.cap:meta==null?0:meta.max();card.addView(label(cap>0?Math.max(0,cap-c.level)+" levels to catalog max":"Maximum level not verified",12,MUTED,false));
            for(Village.Upgrade u:village.upgrades)if(u.dataId==c.id){TextView timer=label("",14,TEXT,true);CountdownText.bind(timer,u.finishAt,"Timer ended · sync to confirm");card.addView(timer);}
            card.setOnClickListener(v->new AlertDialog.Builder(this).setTitle(itemName(c.id,"Crafting Module")).setMessage("Defense: "+itemName(c.type,"Defense")+"\nModule ID: "+c.id+"\nExported level: "+c.level+(cap>0?"\nCatalog max: "+cap:"")+"\n\nOnly modules present in the export are shown. Portraits identify the defense, with a symbol for the module’s stat. Phase resets and future prices are not guessed.").setPositiveButton("Close",null).show());cards.add(card);
        }
    }
    private void renderHelpers(){
        if(!sample){Switch alerts=new Switch(this);alerts.setId(R.id.availability_alerts);alerts.setText("Helper & Clock Tower Alerts");alerts.setTextColor(TEXT);alerts.setTextSize(14);alerts.setChecked(Store.prefs(this).getBoolean(Availability.PREF,true));alerts.setOnCheckedChangeListener((v,on)->{Store.prefs(this).edit().putBoolean(Availability.PREF,on).apply();Reminders.deliver(this);Reminders.schedule(this);if(on&&(!Reminders.canNotify(this)||!Store.prefs(this).getBoolean("notify",true)))new AlertDialog.Builder(this).setMessage("Turn on Notifications for all saved villages and allow Android notifications in the app’s Settings to receive availability alerts.").setPositiveButton("Close",null).show();});header.addView(alerts);
            if(!Reminders.canNotify(this)||!Store.prefs(this).getBoolean("notify",true))header.addView(label("Notifications are off · enable in Settings",12,GOLD,false));}
        List<Availability.Entry> entries;try{entries=Availability.parse(overview.root.toString(),village.exportedAt);}catch(Exception invalid){entries=Collections.emptyList();}
        for(Availability.Entry e:entries){if(builder&&!e.key.equals("clock-tower")||!builder&&e.key.equals("clock-tower"))continue;
            LinearLayout card=card();portraitTitle(card,e.id,e.name);if(e.level>=0)card.addView(label("Level "+e.level,13,MUTED,false));TextView status=label(e.status(System.currentTimeMillis()),17,GOLD,true);
            if(e.readyAt>System.currentTimeMillis())CountdownText.bind(status,e.readyAt,"Ready · sync to confirm",System::currentTimeMillis,e::status);card.addView(status);
            if(e.readyAt>0)card.addView(label(java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT,java.text.DateFormat.SHORT).format(new Date(e.readyAt)),12,MUTED,false));cards.add(card);
        }
        if(cards.isEmpty())empty("No helper or Clock Tower cooldown data was included for this base.");
        cards.add(label("Sync after using a helper or Clock Tower. These are availability estimates from your export.",12,MUTED,false));
    }
    private void renderCollection(){
        final String[] keys=builder?new String[]{"skins2","sceneries2","decos2","obstacles2"}:new String[]{"equipment","skins","sceneries","decos","obstacles","house_parts"};
        String[] labels=builder?new String[]{"Hero Skins","Sceneries","Decorations","Obstacles"}:new String[]{"Hero Equipment","Hero Skins","Sceneries","Decorations","Obstacles","House Parts"};
        int selected=Arrays.asList(keys).indexOf(collection);if(selected<0){selected=0;collection=keys[0];}Spinner select=new Spinner(this);select.setId(R.id.collection_selector);select.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,labels));select.setSelection(selected);header.addView(select);
        select.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onNothingSelected(android.widget.AdapterView<?> p){}public void onItemSelected(android.widget.AdapterView<?> p,View v,int n,long id){if(!collection.equals(keys[n])){collection=keys[n];render();list.setSelection(0);}}});
        List<VillageOverview.CollectionItem> items=overview.collection(collection);long count=0;for(VillageOverview.CollectionItem i:items)count+=i.count;header.addView(label(count+" owned · "+items.size()+" distinct entries",13,MUTED,false));
        for(VillageOverview.CollectionItem i:items){LinearLayout card=card();portraitTitle(card,i.id,itemName(i.id,collection.equals("equipment")?"Equipment":"Collectible"));
            if(i.level>=0){UpgradePlanner.Item meta=planner.item(i.id);int cap=meta==null?0:meta.max();card.addView(label("Level "+i.level+(cap>0?" / "+cap:""),16,GOLD,true));
                if(meta!=null&&!meta.hero.isEmpty())card.addView(label(meta.hero+" · "+UpgradePlanner.title(meta.data.optString("rarity")),12,MUTED,false));
                card.setOnClickListener(v->new AlertDialog.Builder(this).setTitle(itemName(i.id,"Equipment")).setMessage("Exported level: "+i.level+"\n"+(cap>0?"Catalog max: "+cap:"Maximum level unavailable")+"\n\nEquipment costs and requirements are available in Build Menu → Hero Equipment. Ore balances are not included in the export.").setPositiveButton("Close",null).show());
            }else{card.addView(label("Owned"+(i.count>1?" × "+i.count:""),15,GOLD,true));card.setOnClickListener(v->{ItemIdentities.Entry identity=ItemIdentities.load(this).item(i.id);new AlertDialog.Builder(this).setTitle(itemName(i.id,"Collectible")).setMessage("Data ID: "+i.id+"\nOwned quantity: "+i.count+"\n\n"+(identity==null?"A verified name and artwork are not yet included for this ID.":identity.sharedArtwork?"The source uses a shared name or artwork for these variants. The data ID identifies this exported record.":"Name and artwork are included with the app for offline use.")).setPositiveButton("Close",null).show();});}cards.add(card);
        }if(items.isEmpty())empty("No entries in this collection were included in the export.");
    }
    private String itemName(int id,String fallback){String name=Store.catalog(this).name(id);return name==null||name.isEmpty()?fallback+" #"+id:name;}
    private void portraitTitle(LinearLayout card,int id,String name){LinearLayout line=row();int icon=ItemArt.icon(this,id);if(icon!=R.drawable.ic_village){ImageView image=new ImageView(this);image.setImageResource(icon);image.setScaleType(ImageView.ScaleType.FIT_CENTER);image.setContentDescription(name);line.addView(image,new LinearLayout.LayoutParams(dp(52),dp(52)));}
        TextView title=label(name,18,TEXT,true);title.setPadding(dp(8),0,0,0);line.addView(title,new LinearLayout.LayoutParams(0,-2,1));card.addView(line);}
    private void showCards(){list.setAdapter(new BaseAdapter(){public int getCount(){return cards.size();}public Object getItem(int n){return cards.get(n);}public long getItemId(int n){return n;}public View getView(int n,View old,ViewGroup p){return cards.get(n);}});}
    private void empty(String text){cards.add(label(text,16,MUTED,false));}
    private LinearLayout card(){LinearLayout c=column();c.setPadding(dp(14),dp(13),dp(14),dp(13));c.setBackgroundResource(R.drawable.card_bg);return c;}
    private TextView label(String text,int size,int color,boolean bold){TextView v=new TextView(this);v.setText(text);v.setTextSize(size);v.setTextColor(color);v.setTypeface(bold?getResources().getFont(R.font.lilita_one):Typeface.create("sans-serif",Typeface.NORMAL));v.setLineSpacing(dp(2),1);return v;}
    private Button button(String text,boolean active,View.OnClickListener click){Button b=new Button(this);b.setText(text);b.setAllCaps(false);b.setTextSize(15);b.setTypeface(getResources().getFont(R.font.lilita_one));b.setTextColor(active?0xFF352108:GOLD);b.setBackgroundResource(active?R.drawable.btn_primary:R.drawable.btn_secondary);b.setPadding(dp(6),0,dp(6),0);b.setOnClickListener(click);return b;}
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    private void space(LinearLayout p,int n){p.addView(new View(this),new LinearLayout.LayoutParams(1,dp(n)));}
    private int dp(float n){return (int)(getResources().getDisplayMetrics().density*n+.5f);}
    public static String sampleExport(){try{
        JSONObject root=new JSONObject(PlannerActivity.sampleExport());JSONArray types=new JSONArray();for(int type=0;type<3;type++){JSONArray modules=new JSONArray();for(int m=0;m<3;m++)modules.put(new JSONObject().put("data",102000033+type*3+m).put("lvl",m+1));types.put(new JSONObject().put("data",103000011+type).put("modules",modules));}
        root.getJSONArray("buildings").put(new JSONObject().put("data",1000097).put("types",types));JSONArray helpers=new JSONArray().put(new JSONObject().put("data",93000001).put("lvl",3).put("helper_cooldown",7200));helpers.put(new JSONObject().put("data",93000002).put("lvl",1).put("helper_cooldown",7200));helpers.put(new JSONObject().put("data",93000003).put("lvl",1).put("helper_cooldown",7200));root.put("helpers",helpers);
        root.put("boosts",new JSONObject().put("clocktower_cooldown",3600));root.put("skins",new JSONArray().put(52000136).put(52000226));
        JSONArray equipment=new JSONArray();int[] ids={90000000,90000001,90000002,90000003,90000008,90000016};for(int i=0;i<ids.length;i++)equipment.put(new JSONObject().put("data",ids[i]).put("lvl",9+i));root.put("equipment",equipment);return root.toString();
    }catch(JSONException impossible){throw new IllegalStateException(impossible);}}
}
