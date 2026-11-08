package com.villageclock.app;

import java.util.*;

/** Display metadata only: never applies another multiplier to exported upgrade work. */
final class ActiveEffects {
    static final class Effect {
        final String key,name,rate,icon;
        final long endsAt;
        final boolean builderBase,event;
        Effect(String key,String name,String rate,long endsAt,String icon,boolean builderBase,boolean event){this.key=key;this.name=name;this.rate=rate;this.endsAt=endsAt;this.icon=icon;this.builderBase=builderBase;this.event=event;}
    }
    static Effect effect(String key,long endsAt){
        switch(key){
            case "builder_boost":return new Effect(key,"Builder Potion","10×",endsAt,"effect_builder_potion",false,false);
            case "builder_consumable":return new Effect(key,"Builder Bite","2×",endsAt,"effect_builder_bite",false,false);
            case "lab_boost":return new Effect(key,"Research Potion","24×",endsAt,"effect_research_potion",false,false);
            case "pet_boost":return new Effect(key,"Pet Potion","24×",endsAt,"effect_pet_potion",false,false);
            case "lab_consumable":return new Effect(key,"Study Soup","+3×",endsAt,"effect_study_soup",false,false);
            case "clocktower_boost":return new Effect(key,"Clock Tower","10×",endsAt,"effect_clock_tower",true,false);
            default:return null;
        }
    }
    static List<Effect> fromJobs(List<Village.Upgrade> jobs){
        Map<String,Effect> out=new LinkedHashMap<>();for(Village.Upgrade job:jobs)if(job.timing!=null)for(UpgradeTiming.Boost boost:job.timing.boosts){Effect effect=effect(boost.family,job.snapshotAt+boost.remainingSeconds*1000);if(effect!=null&&(out.get(effect.key)==null||out.get(effect.key).endsAt<effect.endsAt))out.put(effect.key,effect);}return new ArrayList<>(out.values());
    }
    static List<Effect> active(Village village,long now){
        List<Effect> out=new ArrayList<>();if(village==null)return out;for(Effect effect:village.effects)if(effect.endsAt>now)out.add(effect);
        if(village.townHall>=3&&FastEvents.hammerJam(now))out.add(new Effect("hammer-jam-2026","Hammer Jam","50% off",FastEvents.JAM_END,"effect_hammer_jam",false,true));
        out.sort(Comparator.comparingLong(e->e.endsAt));return out;
    }
    static int icon(android.content.Context c,Effect effect){int icon=c.getResources().getIdentifier(effect.icon,"drawable",c.getPackageName());return icon==0?R.drawable.ic_clock:icon;}
    static long nextChange(Village village,long now){long end=Long.MAX_VALUE;for(Effect effect:active(village,now))end=Math.min(end,effect.endsAt);return end;}
    private ActiveEffects(){}
}
