package craftbound.game;

import com.google.gson.*;
import craftbound.board.BoardDesign;
import craftbound.card.*;
import java.util.*;
import net.minecraft.resources.Identifier;

/** Parsed, immutable opponent profiles. A started match carries its own profile in its save. */
public final class TrialsOpponents {
    public enum Tier { REGULAR, ELITE, BOSS }
    public enum Tag { MOB, BLOCK, ACTION, TOOL, ENCHANTMENT, RANGED, SPRINT, SHIELD, DROP, DAMAGE, HEAL, SUMMON, BUFF, FREEZE }
    public enum Kind { NONE, MOB_HEALTH, PACK_ATTACK, MOB_SPRINT, BLOCK_HEALTH, TOOL_DURABILITY, HEAL_WOUNDED, HEAVY_DRAW, WAR_HORN, DEATH_BLAST, DEATH_SPLIT }
    public record Ability(String name,Kind kind,int amount,int every,String card) {}
    public record Profile(String id,String name,Tier tier,CardClass heroClass,BoardDesign board,String style,
                          int health,int healthStep,int deckSize,int deckStep,int bonusXp,int extraCards,int bounty,
                          List<String> anchors,List<MobType> tribes,List<Tag> tags,Ability ability) {
        public Profile { anchors=List.copyOf(anchors);tribes=List.copyOf(tribes);tags=List.copyOf(tags); }
    }

    public static List<Profile> parse(JsonArray data) {
        if(data==null || data.size()>128)throw new IllegalArgumentException("opponents: expected at most 128 profiles.");
        List<Profile> result=new ArrayList<>();
        Set<String> ids=new HashSet<>(); EnumSet<Tier> tiers=EnumSet.noneOf(Tier.class);
        for(int i=0;i<data.size();i++) {
            try {
                JsonObject o=data.get(i).getAsJsonObject();
                String id=string(o,"id",48);
                if(!id.matches("[a-z0-9_]{1,48}")||!ids.add(id))throw new IllegalArgumentException("id must be unique lowercase letters, digits or underscores.");
                var spec=o.getAsJsonObject("ability");
                if(spec==null)throw new IllegalArgumentException("ability is required.");
                var abilityKind=enumOf(spec,"kind",Kind.class);
                var ability=new Ability(string(spec,"name",48),abilityKind,number(spec,"amount",1,5),number(spec,"every",1,8),
                    idOrEmpty(string(spec,"card",180,true)));
                if(ability.kind()!=Kind.DEATH_SPLIT && !ability.card.isEmpty())throw new IllegalArgumentException("ability.card is only used by DEATH_SPLIT.");
                if(!ability.card.isEmpty()){
                    var def=Cards.get(Identifier.parse(ability.card));
                    if(def!=null && (!def.isMob()||def.keywords().contains(Keyword.BLOCK)))throw new IllegalArgumentException("ability.card must name a mob.");
                }
                var profile=new Profile(id,string(o,"name",64),enumOf(o,"tier",Tier.class),enumOf(o,"heroClass",CardClass.class),enumOf(o,"board",BoardDesign.class),
                    string(o,"style",200),number(o,"health",1,100),number(o,"healthStep",0,5),number(o,"deckSize",6,40),number(o,"deckStep",0,5),
                    number(o,"bonusXp",0,3),number(o,"extraCards",0,3),number(o,"bounty",0,20),
                    strings(o,"anchors",80).stream().map(TrialsOpponents::requiredId).toList(),
                    enumList(o,"tribes",MobType.class),enumList(o,"tags",Tag.class),ability);
                if(profile.heroClass==CardClass.NEUTRAL)throw new IllegalArgumentException("heroClass must have a hero power.");
                result.add(profile);tiers.add(profile.tier);
            }catch(RuntimeException e){throw new IllegalArgumentException("opponents["+i+"]: "+e.getMessage(),e);}
        }
        if(tiers.size()!=Tier.values().length)throw new IllegalArgumentException("opponents: include at least one REGULAR, ELITE and BOSS.");
        return List.copyOf(result);
    }
    private static String requiredId(String value) {
        if(value.isEmpty() || !value.contains(":") || Identifier.tryParse(value)==null)throw new IllegalArgumentException("Invalid card ID: "+value);
        return value;
    }
    private static String idOrEmpty(String value){return value.isEmpty()?"":requiredId(value);}
    private static String string(JsonObject o,String key,int max){return string(o,key,max,false);}
    private static String string(JsonObject o,String key,int max,boolean allowEmpty) {
        var v=o.get(key);
        if(v==null || !v.isJsonPrimitive() || !v.getAsJsonPrimitive().isString())throw new IllegalArgumentException(key+" must be text.");
        String s=v.getAsString().strip();
        if(s.length()>max || !allowEmpty&&s.isBlank())throw new IllegalArgumentException("Invalid "+key+".");
        return s;
    }
    private static int number(JsonObject o,String key,int min,int max){
        var v=o.get(key);
        if(v==null || !v.isJsonPrimitive() || !v.getAsJsonPrimitive().isNumber())throw new IllegalArgumentException(key+" must be a number.");
        int n;
        try{n=v.getAsBigDecimal().intValueExact();}catch(ArithmeticException e){throw new IllegalArgumentException(key+" must be a whole number.");}
        if(n<min || n>max)throw new IllegalArgumentException(key+" must be "+min+"–"+max+".");
        return n;
    }
    private static <T extends Enum<T>> T enumOf(JsonObject o,String key,Class<T> cls) {
        try{return Enum.valueOf(cls,string(o,key,64));}catch(IllegalArgumentException ex){throw new IllegalArgumentException("Invalid "+key+".");}
    }
    private static List<String> strings(JsonObject o,String key,int limit) {
        if(!o.has(key)||!o.get(key).isJsonArray()||o.getAsJsonArray(key).size()>limit)throw new IllegalArgumentException("Invalid "+key+" list.");
        Set<String> values=new LinkedHashSet<>();
        for(var e:o.getAsJsonArray(key)){
            if(!e.isJsonPrimitive()||!e.getAsJsonPrimitive().isString()||!values.add(e.getAsString()))throw new IllegalArgumentException("Invalid or duplicate "+key+" entry.");
        }
        return List.copyOf(values);
    }
    private static <T extends Enum<T>> List<T> enumList(JsonObject o,String key,Class<T> cls) {
        try{return strings(o,key,cls.getEnumConstants().length).stream().map(s->Enum.valueOf(cls,s)).toList();}
        catch(IllegalArgumentException e){throw new IllegalArgumentException("Invalid "+key+" entry.");}
    }
    private TrialsOpponents() {}
}