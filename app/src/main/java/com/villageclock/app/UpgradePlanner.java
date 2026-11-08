package com.villageclock.app;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.*;

/** Offline, snapshot-based planning. Catalog prices never replace exported running timers. */
public final class UpgradePlanner {
    public enum Tab { BUILDINGS("Buildings & Heroes"), ARMY("Army Upgrades"), EQUIPMENT("Hero Equipment"), PETS("Hero Pets");
        public final String label; Tab(String label) { this.label = label; } }
    public enum State { RUNNING, SYNC, AVAILABLE, LOCKED, UNOWNED, MAXED, UNKNOWN }
    public enum Sort { DEFAULT, COST_MIN, COST_MAX, TIME_MIN, NAME }
    public static final class Item {
        public final JSONObject data; public final int id; public final String name, base, category, hero; public final Tab tab;
        Item(JSONObject d) { data=d; id=d.optInt("dataId"); name=d.optString("name"); base=d.optString("base"); category=d.optString("category");
            hero=title(d.optString("hero")); tab=category.equals("hero-equipment")?Tab.EQUIPMENT:category.equals("pet")?Tab.PETS:
                Arrays.asList("troop","spell","siege-machine").contains(category)?Tab.ARMY:Tab.BUILDINGS; }
        public JSONObject next(int level) { JSONObject found=null; JSONArray levels=data.optJSONArray("levels");
            if(levels!=null) for(int i=0;i<levels.length();i++){JSONObject l=levels.optJSONObject(i); if(l!=null&&!l.optBoolean("supercharge")&&l.optInt("level")>level&&(found==null||l.optInt("level")<found.optInt("level"))) found=l;}
            return found; }
        public int max() { int max=0; JSONArray a=data.optJSONArray("levels"); if(a!=null)for(int i=0;i<a.length();i++){JSONObject l=a.optJSONObject(i);if(l!=null&&!l.optBoolean("supercharge"))max=Math.max(max,l.optInt("level"));}return max; }
        public boolean seasonal(){return category.equals("crafted-defense");}
        public String type(){if(category.equals("hero")||category.equals("guardian"))return "Hero"; if(category.equals("army")||category.equals("research"))return "Army";
            if(category.equals("resource"))return "Resource"; if(Arrays.asList("defense","trap","wall","crafted-defense").contains(category))return "Defense";
            if(tab==Tab.ARMY)return title(category); return "Other";}
    }
    public static final class Row {
        public final Item item; public final int level, count; public final JSONObject next; public final State state; public final long finishAt;
        public final String requirements, missing; public final boolean fresh;
        Row(Item item,int level,int count,State state,long finishAt,String requirements,String missing,boolean fresh){this.item=item;this.level=level;this.count=count;
            this.next=item.next(level);this.state=state;this.finishAt=finishAt;this.requirements=requirements;this.missing=missing;this.fresh=fresh;}
        public long seconds(){if(next==null)return 0;for(String k:new String[]{"buildTime","researchTime","upgradeTime"}){JSONObject t=next.optJSONObject(k);if(t!=null)return t.optLong("days")*86400+t.optLong("hours")*3600+t.optLong("minutes")*60+t.optLong("seconds");}return 0;}
        public Map<String,Long> costs(){LinkedHashMap<String,Long> out=new LinkedHashMap<>();if(next==null)return out;
            for(String prefix:new String[]{"build","research","upgrade"})if(next.has(prefix+"Cost")){out.put(next.optString(prefix+"CostResource","Unknown resource"),next.optLong(prefix+"Cost"));return out;}
            for(String ore:new String[]{"Shiny","Glowing","Starry"})if(next.optLong("upgrade"+ore+"Ore")>0)out.put(ore+" Ore",next.optLong("upgrade"+ore+"Ore"));return out;}
        public long price(Set<String> resource){long n=0;for(Map.Entry<String,Long> e:costs().entrySet())if(resource.isEmpty()||matchesResource(e.getKey(),resource))n+=e.getValue();return n;}
        public String costText(){if(next==null)return "";Map<String,Long> costs=costs();if(costs.isEmpty())return item.tab==Tab.EQUIPMENT?"No ore cost listed":"Cost not listed";
            List<String> parts=new ArrayList<>();for(Map.Entry<String,Long> e:costs.entrySet())parts.add(String.format(Locale.US,"%,d %s",e.getValue(),e.getKey()));return String.join(" · ",parts);}
        public String status(){switch(state){case RUNNING:return "Upgrading";case SYNC:return "Timer ended · sync to confirm";case LOCKED:return "Requires "+missing;
            case MAXED:return "Catalog max level";case UNOWNED:return "Not in export · check unlock in game";case UNKNOWN:return "Check requirements in game";
            default:return fresh?"New · requirements listed below":"Next upgrade";}}
        public int levelsLeft(){return Math.max(0,item.max()-level);}
    }
    public static final class Filter {
        public final Set<String> resources=new HashSet<>(),types=new HashSet<>(),heroes=new HashSet<>();
        public String status="Remaining"; public Sort sort=Sort.DEFAULT;
        public void clear(){resources.clear();types.clear();heroes.clear();status="Remaining";sort=Sort.DEFAULT;}
        public boolean accepts(Row r){if(!resources.isEmpty()&&r.costs().keySet().stream().noneMatch(k->matchesResource(k,resources)))return false;
            if(!types.isEmpty()&&!types.contains(r.item.type())&&!(types.contains("New")&&r.fresh)&&!(types.contains("Non-Seasonal")&&!r.item.seasonal()))return false;
            if(!heroes.isEmpty()&&!heroes.contains(r.item.hero))return false;
            switch(status){case "All":return true;case "Requirements met":return r.state==State.AVAILABLE;case "In progress":return r.state==State.RUNNING||r.state==State.SYNC;
                case "Locked / unowned":return r.state==State.LOCKED||r.state==State.UNOWNED||r.state==State.UNKNOWN;case "Maxed":return r.state==State.MAXED;default:return r.state!=State.MAXED;}}
    }
    private final List<Item> items=new ArrayList<>(); private final Map<Integer,Item> byId=new HashMap<>();private final Map<String,Integer> hallCaps=new HashMap<>(); public final String revision;
    public UpgradePlanner(String json)throws Exception{JSONObject root=new JSONObject(json);revision=root.optString("revision");JSONArray a=root.getJSONArray("items");
        for(int i=0;i<a.length();i++){Item item=new Item(a.getJSONObject(i));items.add(item);byId.put(item.id,item);}}
    public List<Item> items(){return Collections.unmodifiableList(items);}
    public Item item(int id){return byId.get(id);}
    private final Map<Integer,String> displayNames=new HashMap<>();
    public void setDisplayName(int id,String name){if(name!=null&&!name.isEmpty())displayNames.put(id,name);}
    public int capAtHall(Item item,int hall){return capAtHall(item,hall,new HashSet<>());}
    private int capAtHall(Item item,int hall,Set<Integer> visiting){
        String cacheKey=item.id+":"+hall;Integer known=hallCaps.get(cacheKey);if(known!=null)return known;
        if(!visiting.add(item.id))return 0;
        try{
            if(item.category.equals("town-hall")||item.category.equals("builder-hall"))return Math.min(hall,item.max());
            int cap=0;JSONArray levels=item.data.optJSONArray("levels");if(levels==null)return 0;
            for(int i=0;i<levels.length();i++){JSONObject level=levels.optJSONObject(i);if(level==null||level.optBoolean("supercharge"))continue;boolean allowed=true;
                for(Map.Entry<String,Integer> gate:reqMap(item,level).entrySet()){
                    if(gate.getKey().equals("Town Hall")||gate.getKey().equals("Builder Hall")){if(gate.getValue()>hall)allowed=false;}
                    else{Item facility=null;for(Item candidate:items)if(candidate.base.equals(item.base)&&candidate.name.equals(gate.getKey())){facility=candidate;break;}
                        if(facility==null||capAtHall(facility,hall,visiting)<gate.getValue())allowed=false;}
                }
                if(allowed)cap=Math.max(cap,level.optInt("level"));
            }hallCaps.put(cacheKey,cap);return cap;
        }finally{visiting.remove(item.id);}
    }
    public int countAtHall(Item item,int hall){return hasMergeCounts(item,hall,item.base.equals("builder"))?-1:allowed(item,hall,item.base.equals("builder"));}
    public List<Row> rows(String json,Village village,String base,Tab tab,long now)throws Exception{
        JSONObject root=new JSONObject(json);Map<Integer,List<JSONObject>> owned=new HashMap<>();Map<Integer,Integer> levels=new HashMap<>();
        boolean builder=base.equals("builder");String[] sections=builder?new String[]{"buildings2","traps2","heroes2","units2","spells2"}:
            new String[]{"buildings","traps","heroes","guardians","units","spells","siege_machines","pets","equipment"};
        boolean hasBase=false;int entries=0;
        for(String section:sections){JSONArray a=root.optJSONArray(section);if(a==null)continue;hasBase=true;for(int i=0;i<a.length();i++){
            if(++entries>10000)throw new IllegalArgumentException("Too many inventory entries");JSONObject e=a.optJSONObject(i);if(e==null)continue;addOwned(e,owned,levels);
            JSONArray types=e.optJSONArray("types");if(types!=null)for(int t=0;t<Math.min(100,types.length());t++){JSONObject type=types.optJSONObject(t);JSONArray modules=type==null?null:type.optJSONArray("modules");if(modules!=null)for(int m=0;m<Math.min(100,modules.length());m++){JSONObject module=modules.optJSONObject(m);if(module!=null)addOwned(module,owned,levels);}}}}
        if(!hasBase)return Collections.emptyList(); // An omitted base is not an empty village.
        int hall=levels.getOrDefault(builder?1000034:1000001,0);List<Row> result=new ArrayList<>();
        List<Village.Upgrade> exportOrder=new ArrayList<>(village.upgrades);
        exportOrder.sort(Comparator.comparingInt(u->Integer.parseInt(u.key.substring(u.key.lastIndexOf(':')+1))));
        Map<String,Integer> timerOffsets=new HashMap<>();
        for(Item item:items){if(!item.base.equals(base)||item.tab!=tab)continue;List<JSONObject> inventory=owned.getOrDefault(item.id,Collections.emptyList());int total=0;
            for(JSONObject e:inventory){int level=Math.max(0,Math.min(1000,e.optInt("lvl")));int count=Math.max(1,Math.min(1000,e.optInt("cnt",1)));total+=count;
                JSONObject next=item.next(level);long finish=0;boolean timer=e.optLong("timer")>0;
                if(timer){String key=item.id+":"+level;int skip=timerOffsets.getOrDefault(key,0),seen=0;
                    for(Village.Upgrade u:exportOrder)if(u.dataId==item.id&&u.level==level+1){if(seen++==skip){finish=u.finishAt;break;}}
                    timerOffsets.put(key,skip+count);}
                if(timer&&finish==0)finish=village.exportedAt+Math.min(10L*366*86400,e.optLong("timer"))*1000;
                List<String> reqs=requirements(item,next);String missing=missing(item,next,levels,builder);
                State state=timer?(finish>now?State.RUNNING:State.SYNC):next==null?State.MAXED:!missing.isEmpty()?State.LOCKED:needsGameCheck(item)?State.UNKNOWN:State.AVAILABLE;
                result.add(new Row(item,level,count,state,finish,String.join(" · ",reqs),missing,false));}
            int allowed=allowed(item,hall,builder);int absent=Math.max(0,allowed-total);
            // Merge-consumed defenses cannot be reconstructed from just the remaining count.
            if(hasMergeCounts(item,hall,builder))absent=0;
            if(total==0&&allowed<0)absent=1;
            if(absent>0){JSONObject next=item.next(0);String missing=missing(item,next,levels,builder);State state=item.tab==Tab.EQUIPMENT?State.UNOWNED:!missing.isEmpty()?State.LOCKED:needsGameCheck(item)?State.UNKNOWN:State.AVAILABLE;
                result.add(new Row(item,0,absent,state,0,String.join(" · ",requirements(item,next)),missing,true));}
        }
        // A new data ID remains visible rather than silently disappearing from the planner.
        for(Map.Entry<Integer,List<JSONObject>> e:owned.entrySet())if(!byId.containsKey(e.getKey())){
            JSONObject meta=new JSONObject();meta.put("dataId",e.getKey());meta.put("name",displayNames.getOrDefault(e.getKey(),"Unknown item #"+e.getKey()));meta.put("base",base);meta.put("category","other");meta.put("levels",new JSONArray());
            Item unknown=new Item(meta);if(tab!=Tab.BUILDINGS)continue;for(JSONObject entry:e.getValue())result.add(new Row(unknown,entry.optInt("lvl"),Math.max(1,entry.optInt("cnt",1)),State.UNKNOWN,0,"Catalog update needed","",false));}
        return group(result);
    }
    public static List<Row> select(List<Row> rows,Filter f){List<Row> out=new ArrayList<>();for(Row r:rows)if(f.accepts(r))out.add(r);
        Comparator<Row> name=Comparator.comparing(r->r.item.name);Comparator<Row> c;
        switch(f.sort){case COST_MIN:c=Comparator.comparingLong(r->r.price(f.resources));break;case COST_MAX:c=Comparator.<Row>comparingLong(r->r.price(f.resources)).reversed();break;
            case TIME_MIN:c=Comparator.comparingLong(Row::seconds);break;case NAME:c=name;break;default:c=Comparator.<Row>comparingInt(r->r.state==State.RUNNING?0:r.state==State.SYNC?1:r.state==State.AVAILABLE?2:3)
                .thenComparingLong(r->r.state==State.RUNNING?r.finishAt:0);}
        out.sort(c.thenComparing(name).thenComparingInt(r->r.level));return out;}
    private static List<Row> group(List<Row> rows){LinkedHashMap<String,Row> grouped=new LinkedHashMap<>();for(Row r:rows){String key=r.item.id+":"+r.level+":"+r.state+":"+r.finishAt+":"+r.fresh;
        Row prev=grouped.get(key);grouped.put(key,prev==null?r:new Row(r.item,r.level,prev.count+r.count,r.state,r.finishAt,r.requirements,r.missing,r.fresh));}return new ArrayList<>(grouped.values());}
    private static void addOwned(JSONObject e,Map<Integer,List<JSONObject>> owned,Map<Integer,Integer> levels){int id=e.optInt("data",-1);if(id<0)return;owned.computeIfAbsent(id,k->new ArrayList<>()).add(e);
        // A prerequisite currently upgrading still has its exported current level.
        levels.merge(id,Math.max(0,Math.min(1000,e.optInt("lvl"))),Math::max);}
    private static int allowed(Item item,int hall,boolean builder){JSONArray a=item.data.optJSONArray(builder?"availablePerBuilderHall":"availablePerTownHall");if(a==null)return -1;int n=0,best=0;
        for(int i=0;i<a.length();i++){JSONObject p=a.optJSONObject(i);int h=p.optInt(builder?"builderHallLevel":"townHallLevel");if(h<=hall&&h>=best){best=h;n=p.optInt("count");}}return n;}
    private static boolean hasMergeCounts(Item item,int hall,boolean builder){JSONArray a=item.data.optJSONArray(builder?"availablePerBuilderHall":"availablePerTownHall");if(a!=null)for(int i=0;i<a.length();i++){JSONObject p=a.optJSONObject(i);if(p.optInt(builder?"builderHallLevel":"townHallLevel")<=hall&&p.has("countAfterMerges"))return true;}return false;}
    private static final Map<String,String> REQUIREMENT_NAMES=new LinkedHashMap<>();
    static{REQUIREMENT_NAMES.put("townHallRequired","Town Hall");REQUIREMENT_NAMES.put("builderHallRequired","Builder Hall");REQUIREMENT_NAMES.put("builderHallLevelRequired","Builder Hall");
        REQUIREMENT_NAMES.put("laboratoryRequired","Laboratory");REQUIREMENT_NAMES.put("starLabRequired","Star Laboratory");REQUIREMENT_NAMES.put("heroHallLevelRequired","Hero Hall");
        REQUIREMENT_NAMES.put("blacksmithLevelRequired","Blacksmith");REQUIREMENT_NAMES.put("petHouseLevelRequired","Pet House");REQUIREMENT_NAMES.put("builderBarracksRequired","Builder Barracks");
        REQUIREMENT_NAMES.put("barrackLevelRequired","Barracks");REQUIREMENT_NAMES.put("spellFactoryLevelRequired","Spell Factory");REQUIREMENT_NAMES.put("workshopLevelRequired","Workshop");}
    private Map<String,Integer> reqMap(Item item,JSONObject level){LinkedHashMap<String,Integer> out=new LinkedHashMap<>();JSONObject root=item.data.optJSONObject("requirements");
        for(String key:REQUIREMENT_NAMES.keySet()){int n=Math.max(root==null?0:root.optInt(key),level==null?0:level.optInt(key));if(n>0){String label=REQUIREMENT_NAMES.get(key);
            if(key.equals("barrackLevelRequired")&&item.data.optString("troopType").equals("dark"))label="Dark Barracks";
            if(key.equals("spellFactoryLevelRequired")&&item.data.optString("spellType").equals("dark"))label="Dark Spell Factory";out.merge(label,n,Math::max);}}
        return out;}
    private List<String> requirements(Item item,JSONObject level){List<String> out=new ArrayList<>();for(Map.Entry<String,Integer> e:reqMap(item,level).entrySet())out.add(e.getKey()+" "+e.getValue());
        if(item.tab==Tab.EQUIPMENT&&!item.hero.isEmpty())out.add(item.hero);return out;}
    private String missing(Item item,JSONObject level,Map<Integer,Integer> owned,boolean builder){List<String> out=new ArrayList<>();for(Map.Entry<String,Integer> req:reqMap(item,level).entrySet()){
        int current=0;for(Item candidate:items)if(candidate.base.equals(builder?"builder":"home")&&candidate.name.equals(req.getKey()))current=Math.max(current,owned.getOrDefault(candidate.id,0));
        if(current<req.getValue())out.add(req.getKey()+" "+req.getValue());}return String.join(" · ",out);}
    private static boolean needsGameCheck(Item item){return item.seasonal()||item.category.equals("guardian")||item.category.equals("town-hall")||item.category.equals("builder-hall")||Arrays.asList("ricochet-cannon","multi-archer-tower","multi-gear-tower","inferno-artillery").contains(item.data.optString("id"));}
    private static boolean matchesResource(String cost,Set<String> selected){for(String s:selected)if(cost.equals(s)||Arrays.asList(cost.split(" or ")).contains(s))return true;return false;}
    public static String title(String slug){StringBuilder b=new StringBuilder();for(String word:slug.split("-")){if(word.isEmpty())continue;if(b.length()>0)b.append(' ');b.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));}return b.toString();}
}
