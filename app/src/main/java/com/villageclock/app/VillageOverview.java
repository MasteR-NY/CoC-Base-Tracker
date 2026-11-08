package com.villageclock.app;

import org.json.*;
import java.util.*;

/** Snapshot inventory and level-based progress. Timers never promote saved levels. */
public final class VillageOverview {
    public static final class Owned {
        public final int id,level,count;public final JSONObject data;
        Owned(JSONObject data){this.data=data;id=data.optInt("data",-1);level=Math.max(0,Math.min(1000,data.optInt("lvl")));count=Math.max(1,Math.min(1000,data.optInt("cnt",1)));}
    }
    public static final class Progress {
        public final String name;public long owned,target,levels,goal,maxed;public int unknown;
        Progress(String name){this.name=name;}
        public int percent(){return goal==0?0:(int)Math.min(100,levels*100/goal);}
    }
    public static final class Craft {
        public final int type,id,level;public final JSONObject data;
        Craft(int type,JSONObject module){this.type=type;data=module;id=module.optInt("data",-1);level=Math.max(0,Math.min(1000,module.optInt("lvl")));}
    }
    public static final class CollectionItem {
        public final int id,count,level;public final String section;
        CollectionItem(String section,int id,int count,int level){this.section=section;this.id=id;this.count=count;this.level=level;}
    }
    public final JSONObject root;
    public VillageOverview(String raw)throws JSONException{root=new JSONObject(raw);}
    public int hall(boolean builder){JSONArray a=root.optJSONArray(builder?"buildings2":"buildings");int result=0;if(a!=null)for(int i=0;i<a.length();i++){JSONObject e=a.optJSONObject(i);if(e!=null&&e.optInt("data")== (builder?1000034:1000001))result=e.optInt("lvl");}return result;}
    public boolean hasBase(boolean builder){return root.optJSONArray(builder?"buildings2":"buildings")!=null;}
    public List<Owned> owned(boolean builder){
        List<Owned> out=new ArrayList<>();String[] sections=builder?new String[]{"buildings2","traps2","heroes2","units2","spells2"}:new String[]{"buildings","traps","heroes","guardians","units","spells","siege_machines","pets","equipment"};
        for(String section:sections){JSONArray a=root.optJSONArray(section);if(a==null)continue;for(int i=0;i<a.length();i++){JSONObject e=a.optJSONObject(i);if(e!=null&&e.optInt("data",-1)>=0)out.add(new Owned(e));}}return out;
    }
    static String group(UpgradePlanner.Item item){
        if(item.category.equals("wall"))return "Walls";if(item.category.equals("trap"))return "Traps";
        if(item.category.equals("hero"))return "Heroes";if(item.tab==UpgradePlanner.Tab.ARMY)return "Army";if(item.tab==UpgradePlanner.Tab.PETS)return "Pets";
        if(item.tab==UpgradePlanner.Tab.EQUIPMENT||item.seasonal()||item.category.equals("guardian"))return null;return "Buildings";
    }
    public List<Progress> progress(UpgradePlanner planner,boolean builder,boolean global){
        if(!hasBase(builder))return Collections.emptyList();String base=builder?"builder":"home";int hall=hall(builder);
        if(global)for(UpgradePlanner.Item item:planner.items())if(item.base.equals(base)&&item.category.equals(builder?"builder-hall":"town-hall"))hall=item.max();
        Map<Integer,List<Owned>> inventory=new HashMap<>();for(Owned o:owned(builder))inventory.computeIfAbsent(o.id,k->new ArrayList<>()).add(o);
        LinkedHashMap<String,Progress> totals=new LinkedHashMap<>();for(String name:new String[]{"Buildings","Walls","Traps","Army","Heroes","Pets"})totals.put(name,new Progress(name));
        for(UpgradePlanner.Item item:planner.items()){
            if(!item.base.equals(base))continue;String group=group(item);if(group==null)continue;Progress p=totals.get(group);
            List<Owned> existing=inventory.getOrDefault(item.id,Collections.emptyList());int cap=global?item.max():planner.capAtHall(item,hall);
            long count=0;for(Owned o:existing)count+=o.count;p.owned+=count;
            if(cap<=0){if(count>0)p.unknown+=count;continue;}
            int expected=planner.countAtHall(item,hall);
            if(expected<0)expected=(int)count;
            if(count==0&&expected==0&&(group.equals("Army")||group.equals("Heroes")||group.equals("Pets")))expected=1;
            long target=Math.max(count,expected);p.target+=target;p.goal+=target*cap;
            for(Owned o:existing){p.levels+=(long)Math.min(cap,o.level)*o.count;if(o.level>=cap)p.maxed+=o.count;}
        }
        for(Owned o:owned(builder))if(planner.item(o.id)==null)totals.get("Buildings").unknown+=o.count;
        List<Progress> result=new ArrayList<>();for(Progress p:totals.values())if(p.target>0||p.owned>0||p.unknown>0)result.add(p);return result;
    }
    public List<Craft> crafts(){List<Craft> out=new ArrayList<>();JSONArray buildings=root.optJSONArray("buildings");if(buildings!=null)for(int i=0;i<buildings.length();i++){
        JSONObject building=buildings.optJSONObject(i);JSONArray types=building==null?null:building.optJSONArray("types");if(types==null)continue;
        for(int t=0;t<Math.min(100,types.length());t++){JSONObject type=types.optJSONObject(t);JSONArray modules=type==null?null:type.optJSONArray("modules");if(modules==null)continue;
            for(int m=0;m<Math.min(100,modules.length());m++){JSONObject module=modules.optJSONObject(m);if(module!=null&&module.optInt("data",-1)>=0)out.add(new Craft(type.optInt("data",-1),module));}}
    }return out;}
    public List<CollectionItem> collection(String section){List<CollectionItem> out=new ArrayList<>();JSONArray a=root.optJSONArray(section);if(a==null)return out;
        TreeMap<Integer,CollectionItem> combined=new TreeMap<>();for(int i=0;i<a.length();i++){Object value=a.opt(i);JSONObject obj=value instanceof JSONObject?(JSONObject)value:null;
            int id=obj!=null?obj.optInt("data",-1):value instanceof Number?((Number)value).intValue():-1;if(id<0)continue;
            int count=obj==null?1:Math.max(1,Math.min(1000,obj.optInt("cnt",1))),level=obj==null?-1:obj.optInt("lvl",-1);CollectionItem previous=combined.get(id);
            combined.put(id,new CollectionItem(section,id,count+(previous==null?0:previous.count),level));
        }out.addAll(combined.values());return out;}
}
