package com.villageclock.app;

import java.util.*;

/** Base-work accounting with finite boost windows and an optional known repeating helper. */
public final class UpgradeTiming {
    public static final long HELPER_WORK_SECONDS = 3600;
    public static final long HELPER_DAY_SECONDS = 23 * 3600;
    public static final class Boost {
        public final String family, group, label;
        public final long extraSpeed, remainingSeconds;
        public Boost(String family, long extraSpeed, long remainingSeconds) {
            this(family, extraSpeed, remainingSeconds, family, family);
        }
        public Boost(String family, long extraSpeed, long remainingSeconds, String group, String label) {
            this.family=family; this.extraSpeed=extraSpeed; this.remainingSeconds=remainingSeconds;
            this.group=group; this.label=label;
        }
    }
    /** Current session plus explicit repeat assignment at the exported unified cooldown. */
    public static final class Helper {
        public final long level, currentSeconds, nextStartSeconds;
        public final String label;
        public Helper(long level, long currentSeconds, long nextStartSeconds, String label) {
            this.level=Math.max(0,level); this.currentSeconds=Math.max(0,currentSeconds);
            this.nextStartSeconds=nextStartSeconds < 0 ? -1 : Math.max(currentSeconds,nextStartSeconds);
            this.label=label;
        }
        long activeSeconds(long elapsed) {
            long active=Math.min(elapsed,currentSeconds);
            if(nextStartSeconds>=0 && elapsed>nextStartSeconds) {
                long since=elapsed-nextStartSeconds;
                active=add(active, add(multiply(since/HELPER_DAY_SECONDS,HELPER_WORK_SECONDS),
                    Math.min(since%HELPER_DAY_SECONDS,HELPER_WORK_SECONDS)));
            }
            return active;
        }
        boolean active(long elapsed) {
            return level>0 && (elapsed<currentSeconds || nextStartSeconds>=0 && elapsed>=nextStartSeconds
                && (elapsed-nextStartSeconds)%HELPER_DAY_SECONDS<HELPER_WORK_SECONDS);
        }
        long nextChange(long elapsed) {
            if(elapsed<currentSeconds)return currentSeconds;
            if(nextStartSeconds<0)return Long.MAX_VALUE;
            if(elapsed<nextStartSeconds)return nextStartSeconds;
            long cycle=(elapsed-nextStartSeconds)/HELPER_DAY_SECONDS;
            long start=add(nextStartSeconds,multiply(cycle,HELPER_DAY_SECONDS));
            return elapsed-start<HELPER_WORK_SECONDS ? add(start,HELPER_WORK_SECONDS) : add(start,HELPER_DAY_SECONDS);
        }
    }
    public static final class Plan {
        public final long workSeconds;
        public final List<Boost> boosts;
        public final Helper helper;
        private final Map<String,List<Boost>> groups=new LinkedHashMap<>();
        public Plan(long workSeconds,List<Boost> incoming,Helper helper) {
            if(workSeconds<0)throw new IllegalArgumentException("Negative work");
            this.workSeconds=workSeconds;this.helper=helper;
            Map<String,Boost> families=new LinkedHashMap<>();
            for(Boost b:incoming) {
                if(b.extraSpeed<=0||b.remainingSeconds<=0)continue;
                Boost prev=families.get(b.family);
                if(prev==null||prev.remainingSeconds<b.remainingSeconds)families.put(b.family,b);
            }
            boosts=Collections.unmodifiableList(new ArrayList<>(families.values()));
            for(Boost b:boosts)groups.computeIfAbsent(b.group,k->new ArrayList<>()).add(b);
        }
        public boolean boosted() {return !boosts.isEmpty() || helper!=null && helper.level>0
            && (helper.currentSeconds>0||helper.nextStartSeconds>=0);}
        /** Integer seconds of normal-speed upgrade work completed by this elapsed real second. */
        public long workDone(long elapsed) {
            elapsed=Math.max(0,elapsed);long work=elapsed;
            for(List<Boost> group:groups.values()) {
                if(group.size()==1){Boost b=group.get(0);work=add(work,multiply(Math.min(elapsed,b.remainingSeconds),b.extraSpeed));continue;}
                TreeSet<Long> ends=new TreeSet<>();for(Boost b:group)ends.add(Math.min(elapsed,b.remainingSeconds));
                long previous=0;
                for(long end:ends) {
                    long speed=0;for(Boost b:group)if(b.remainingSeconds>=end)speed=Math.max(speed,b.extraSpeed);
                    work=add(work,multiply(end-previous,speed));previous=end;
                }
            }
            if(helper!=null)work=add(work,multiply(helper.activeSeconds(elapsed),helper.level));
            return work;
        }
        public long remainingWork(long elapsed) {return Math.max(0,workSeconds-Math.min(workSeconds,workDone(elapsed)));}
        public long speed(long elapsed) {
            elapsed=Math.max(0,elapsed);long speed=1;
            for(List<Boost> group:groups.values()){long extra=0;for(Boost b:group)if(elapsed<b.remainingSeconds)extra=Math.max(extra,b.extraSpeed);speed=add(speed,extra);}
            if(helper!=null&&helper.active(elapsed))speed=add(speed,helper.level);
            return speed;
        }
        public long nextChange(long elapsed) {
            long change=Long.MAX_VALUE;
            for(Boost b:boosts)if(b.remainingSeconds>elapsed)change=Math.min(change,b.remainingSeconds);
            if(helper!=null)change=Math.min(change,helper.nextChange(elapsed));
            return change;
        }
        public List<String> sources(long elapsed) {
            List<String> result=new ArrayList<>();
            for(List<Boost> group:groups.values()) {
                Boost strongest=null;
                for(Boost b:group)if(elapsed<b.remainingSeconds&&(strongest==null||b.extraSpeed>strongest.extraSpeed))strongest=b;
                if(strongest!=null)result.add(strongest.label);
            }
            if(helper!=null) {
                if(helper.active(elapsed))result.add(helper.label);
                else if(helper.nextStartSeconds>=0)result.add(helper.label+" scheduled");
            }
            return result;
        }
        /** Earliest real second when all exported work is complete, including boost expiry. */
        public long secondsToFinish() {
            long low=0,high=workSeconds;
            while(low<high){long middle=low+(high-low)/2;if(workDone(middle)>=workSeconds)high=middle;else low=middle+1;}
            return low;
        }
    }
    public static long secondsToFinish(long exportedWorkSeconds,List<Boost> incoming) {
        return new Plan(exportedWorkSeconds,incoming,null).secondsToFinish();
    }
    private static long add(long a,long b){return a>Long.MAX_VALUE-b?Long.MAX_VALUE:a+b;}
    private static long multiply(long a,long b){return a!=0&&b>Long.MAX_VALUE/a?Long.MAX_VALUE:a*b;}
    private UpgradeTiming() { }
}
