package craftbound.game;

import com.google.gson.*;
import java.util.*;

final class TrialsRules {
    static final List<String> SECTIONS = List.of("config","act","events","rest","boons","shop");
    static final Set<String> INTERACTIVE = Set.of("ADD_CARD","REMOVE_CARD","COPY_CARD","SWAP_CARD","TRAIN_CARD","CHARM","BOON");
    static final Set<String> INSTANT = Set.of("MARKS","HEAL","MAX_HEALTH","OPENING_DRAW","START_XP","SKIP");
    static JsonObject snapshot() {
        var o = new JsonObject();
        for (String key : SECTIONS) o.add(key, TrialsContent.section(key).deepCopy());
        return o;
    }
    static List<JsonObject> list(JsonObject obj,String key) {
        return obj.getAsJsonArray(key).asList().stream().map(JsonElement::getAsJsonObject).toList();
    }
    static String s(JsonObject obj,String key,String fallback) { return obj.has(key) ? obj.get(key).getAsString() : fallback; }
    static int n(JsonObject obj,String key,int fallback) { return obj.has(key) ? obj.get(key).getAsInt() : fallback; }
    static JsonObject find(List<JsonObject> entries,String id) {
        return entries.stream().filter(o -> id.equals(s(o,"id",""))).findFirst().orElse(null);
    }
    static List<String> ids(List<JsonObject> entries) { return entries.stream().map(o -> s(o,"id","")).toList(); }

    static void validate(JsonObject root,List<String> errors) {
        try {
            var config = root.getAsJsonObject("config");
            for(String key : List.of("draftSize","minDeck","maxDeck"))
                integer(config,key,1,50,true);
            int minimum=n(config,"minDeck",8), draft=n(config,"draftSize",10), maximum=n(config,"maxDeck",24);
            if(minimum < 6 || minimum > draft || draft > maximum) throw bad("config: require 6 ≤ minDeck ≤ draftSize ≤ maxDeck");
            integer(config,"startLives",1,9,true); integer(config,"startHealth",1,500,true);
            integer(config,"startMarks",0,100,true); integer(config,"bossPacks",1,16,true);
            integer(config,"offerCount",1,6,true); integer(config,"shopStock",1,6,true); integer(config,"charmSlots",1,6,true);
            for (var key:List.of("cardPrice","charmPrice","removePrice","trainPrice")) integer(config,key,0,99,true);
        } catch(RuntimeException e) { errors.add("config: " + e.getMessage()); }
        for(String section:List.of("events","boons","rest","shop","act")) try {
            if(section.equals("act")) { validateAct(root); continue; }
            if(section.equals("shop")) {
                var shop=root.getAsJsonObject(section); var options=list(shop,"items"); labels(options);
                for(var opt:options) {
                    if(!Set.of("CARD","CHARM","REMOVE","TRAIN").contains(s(opt,"service",""))) throw bad(s(opt,"id","") + ": unknown service");
                    integer(opt,"stock",1,20,true); integer(opt,"price",0,99,false);
                }
            } else if(section.equals("events")) {
                var events=list(root,"events"); labels(events);
                for(var event:events) validateOptions(list(event,"options"),false);
            } else validateOptions(list(root,section),section.equals("boons"));
        } catch(RuntimeException e) { errors.add(section + ": " + e.getMessage()); }
    }
    private static void validateOptions(List<JsonObject> entries,boolean boon) {
        labels(entries);
        for(var e:entries) {
            integer(e,"costMarks",0,99,false); integer(e,"costHealth",0,100,false); integer(e,"limit",1,100,false);
            var ops=list(e,"operations");
            if(ops.isEmpty() || ops.size()>8) throw bad(s(e,"id","") + ": choose 1–8 operations");
            for(int i=0;i<ops.size();i++) {
                var op=ops.get(i); String type=s(op,"type","");
                if(!INTERACTIVE.contains(type) && !INSTANT.contains(type))throw bad("Unknown operation " + type);
                if(INTERACTIVE.contains(type) && i!=ops.size()-1)throw bad("Put the card, charm or boon choice last");
                if(boon && type.equals("BOON"))throw bad("A boon cannot request another boon");
                if(INSTANT.contains(type) && !type.equals("SKIP")) integer(op,"amount",1,100,true);
            }
        }
    }
    private static void labels(List<JsonObject> entries) {
        if(entries.isEmpty() || entries.size()>50)throw bad("Expected 1–50 entries");
        Set<String> seen=new HashSet<>();
        for(var e:entries) {
            String id=s(e,"id","");
            if(!id.matches("[A-Za-z0-9_-]{1,48}") || !seen.add(id))throw bad("Invalid or duplicate ID " + id);
            String icon=s(e,"iconItem","");
            if(!icon.isBlank() && !icon.matches("[a-z0-9_.-]+:[a-z0-9_/.-]+"))throw bad("Invalid iconItem for "+id);
            if(s(e,"title","").isBlank() || s(e,"title","").length()>80 || s(e,"description","").length()>320)
                throw bad("Invalid title or description for " + id);
        }
    }
    private static void validateAct(JsonObject root) {
        var act=root.getAsJsonObject("act");var floors=list(act,"floors");
        if(s(act,"name","").isBlank() || s(act,"name","").length()>80 || floors.size()<2 || floors.size()>30)throw bad("Expected named act with 2–30 floors");
        String topology=s(act,"topology","AUTHORED");
        if(!Set.of("AUTHORED",TrialsAct.BRANCHING,TrialsAct.VARIED).contains(topology))throw bad("Unknown act topology "+topology);
        boolean branching=TrialsAct.BRANCHING.equals(topology) || TrialsAct.VARIED.equals(topology);
        int templateLimit=TrialsAct.VARIED.equals(topology)?4:3;
        if(branching && (floors.size()<7 || floors.getLast().getAsJsonArray("kinds").size()!=1))
            throw bad("Branching acts need at least 7 floors and one final boss");
        var eventIds = ids(list(root,"events")); Set<Integer> reachable=new HashSet<>();
        Map<String,String> tiers=new HashMap<>();
        if(root.has("opponents"))for(var profile:list(root,"opponents"))tiers.put(s(profile,"id",""),s(profile,"tier",""));
        for(int r=0;r<floors.size();r++) {
            var f=floors.get(r);var kinds=f.getAsJsonArray("kinds");var edges=f.getAsJsonArray("edges");
            if(kinds.isEmpty() || kinds.size()>5 || edges.size()!=kinds.size())throw bad("Each floor needs 1–5 lanes with matching edge lists");
            if(branching && kinds.size()>templateLimit)throw bad("This branching version supports up to "+templateLimit+" room templates per floor");
            for(String key:List.of("titles","descriptions","events","opponents")) if(f.has(key) && f.getAsJsonArray(key).size()!=kinds.size())throw bad(key+" must match the lane count");
            if(r>0 && reachable.size()!=kinds.size())throw bad("Unreachable room on floor "+(r+1));
            reachable.clear();
            for(int k=0;k<kinds.size();k++) {
                var kind=TrialsAct.Kind.valueOf(kinds.get(k).getAsString());
                if((kind==TrialsAct.Kind.BOSS)!=(r==floors.size()-1))throw bad("Only the final floor contains bosses; every ending must be a boss");
                if(f.has("events")) {
                    String event=f.getAsJsonArray("events").get(k).getAsString();
                    if(!event.isEmpty() && (kind!=TrialsAct.Kind.EVENT || !eventIds.contains(event)))throw bad("Invalid forced event "+event);
                }
                if(f.has("opponents")) {
                    String forced=f.getAsJsonArray("opponents").get(k).getAsString();
                    String tier=switch(kind){case FIGHT->"REGULAR";case ELITE->"ELITE";case BOSS->"BOSS";default->"";};
                    if(!forced.isEmpty() && (tier.isEmpty() || !tier.equals(tiers.get(forced))))throw bad("Unknown or incompatible opponent "+forced);
                }
                if(f.has("titles") && f.getAsJsonArray("titles").get(k).getAsString().length()>80
                        || f.has("descriptions") && f.getAsJsonArray("descriptions").get(k).getAsString().length()>320)throw bad("Room title or description is too long");
                var outgoing=edges.get(k).getAsJsonArray();var unique=new HashSet<Integer>();
                if(outgoing.isEmpty()!=(r==floors.size()-1))throw bad("Every non-final room needs a next room");
                for(var edge:outgoing) {
                    int dest=edge.getAsBigDecimal().intValueExact();
                    if(dest<0 || dest>=floors.get(r+1).getAsJsonArray("kinds").size() || !unique.add(dest))throw bad("Invalid next lane "+dest);
                    reachable.add(dest);
                }
            }
        }
    }
    private static void integer(JsonObject o,String key,int min,int max,boolean required) {
        if(!o.has(key)) { if(required) throw bad("Missing "+key); return; }
        var v=o.get(key);
        if(!v.isJsonPrimitive() || !v.getAsJsonPrimitive().isNumber())throw bad(key+" needs a number");
        int n=v.getAsBigDecimal().intValueExact();
        if(n<min || n>max)throw bad(key+" must range from "+min+" to "+max);
    }
    private static IllegalArgumentException bad(String s) { return new IllegalArgumentException(s); }
    private TrialsRules() {}
}
