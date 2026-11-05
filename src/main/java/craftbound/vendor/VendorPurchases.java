package craftbound.vendor;

import java.util.*;

/** Only the current day's per-player purchases survive a restock or save. */
public final class VendorPurchases {
    private long day=Long.MIN_VALUE;
    private final Map<UUID,Integer> records=new HashMap<>();
    public void advance(long current){if(day!=current){day=current;records.clear();}}
    public int bought(UUID player,long current){advance(current);return records.getOrDefault(player,0);}
    public void mark(UUID player,long current,int slot){
        if(slot<0||slot>=CardVendor.SLOTS)throw new IllegalArgumentException("Invalid single slot");
        advance(current);records.merge(player,1<<slot,(a,b)->a|b);
    }
    public Map<String,Integer> snapshot(long current){
        advance(current);var out=new HashMap<String,Integer>();records.forEach((id,mask)->out.put(id.toString(),mask));return Map.copyOf(out);
    }
    public void load(long savedDay,Map<String,Integer> saved){
        day=savedDay;records.clear();saved.forEach((id,mask)->{
            try{records.put(UUID.fromString(id),mask&((1<<CardVendor.SLOTS)-1));}catch(IllegalArgumentException ignored){}
        });
    }
    public long day(){return day;}
}
