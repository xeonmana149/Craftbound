package craftbound.vendor;

import craftbound.card.CardRarity;
import craftbound.card.Cards;
import java.util.*;
import net.minecraft.resources.Identifier;

/** All economy/restock tuning lives here. The lineup is a pure function of catalogue, seed, position and day. */
public final class CardVendor {
    public static final int PACK_PRICE=5, COMMON_PRICE=2, RARE_PRICE=5, EPIC_PRICE=12, LEGENDARY_PRICE=30;
    public static final int ENCHANTED_MULTIPLIER=3, SLOTS=6, COMMON_SLOTS=3, RARE_SLOTS=2;
    public static final double LEGENDARY_CHANCE=0.10, ENCHANTED_CHANCE=0.05, BUY_RANGE=8;
    public static final long DAY_TICKS=24000;
    public static final int REFRESH_TICKS=20;
    // Each player can buy PACK_LIMIT packs per machine per day. A player's daily record is one int: bits 0-5 mark
    // the singles they bought, bits 8-11 count their packs, so old saves load unchanged.
    public static final int PACK_LIMIT=3, PACK_SHIFT=8, RECORD_BITS=((1<<SLOTS)-1)|(0xF<<PACK_SHIFT);
    public static int packsBought(int record){return (record>>>PACK_SHIFT)&0xF;}
    public static int packsLeft(int record){return Math.max(0,PACK_LIMIT-packsBought(record));}
    public record Offer(Identifier card,boolean enchanted,int price) {}
    public record Candidate(Identifier card,CardRarity rarity) {}
    public static long day(long ticks){return Math.floorDiv(ticks,DAY_TICKS);}
    public static long untilRestock(long ticks){return DAY_TICKS-Math.floorMod(ticks,DAY_TICKS);}
    public static int price(CardRarity rarity,boolean enchanted) {
        int base=switch(rarity){case COMMON->COMMON_PRICE;case RARE->RARE_PRICE;case EPIC->EPIC_PRICE;case LEGENDARY->LEGENDARY_PRICE;};
        return base*(enchanted?ENCHANTED_MULTIPLIER:1);
    }
    public static List<Offer> lineup(long seed,long position,long day) {
        var candidates=new ArrayList<Candidate>();
        for(var id:Cards.collectible()) if(!Cards.get(id).token()) candidates.add(new Candidate(id,Cards.get(id).rarity()));
        return lineup(seed,position,day,candidates);
    }
    private static long mix(long x) {
        x=(x^(x>>>30))*0xbf58476d1ce4e5b9L;x=(x^(x>>>27))*0x94d049bb133111ebL;return x^(x>>>31);
    }
    public static List<Offer> lineup(long seed,long position,long day,List<Candidate> candidates) {
        var random=new Random(mix(seed)^mix(position+0x9e3779b97f4a7c15L)^mix(day+0x632be59bd9b4e019L));
        var offers=new ArrayList<Offer>();var used=new HashSet<Identifier>();
        for(int slot=0;slot<SLOTS;slot++) {
            CardRarity wanted=slot<COMMON_SLOTS?CardRarity.COMMON:slot<COMMON_SLOTS+RARE_SLOTS?CardRarity.RARE
                    :random.nextDouble()<LEGENDARY_CHANCE?CardRarity.LEGENDARY:CardRarity.EPIC;
            int nearest=Integer.MAX_VALUE;var pool=new ArrayList<Candidate>();
            for(var c:candidates) {
                if(used.contains(c.card()))continue;
                int distance=Math.abs(c.rarity().ordinal()-wanted.ordinal());
                if(distance<nearest){nearest=distance;pool.clear();}
                if(distance==nearest)pool.add(c);
            }
            // A user-edited catalogue with fewer than six unique cards leaves unfillable slots unavailable.
            if(pool.isEmpty())break;
            var chosen=pool.get(random.nextInt(pool.size()));
            boolean enchanted=random.nextDouble()<ENCHANTED_CHANCE;
            offers.add(new Offer(chosen.card(),enchanted,price(chosen.rarity(),enchanted)));
            used.add(chosen.card());
        }
        return List.copyOf(offers);
    }
    public static boolean canBuy(int slot,long requestedDay,long day,int boughtMask,int emeralds,List<Offer> offers,
                                 String expectedCard,boolean expectedEnchanted) {
        if(slot==-1)return emeralds>=PACK_PRICE&&packsLeft(boughtMask)>0;
        if(slot<0||slot>=offers.size()||slot>=SLOTS||requestedDay!=day||(boughtMask&(1<<slot))!=0)return false;
        var offer=offers.get(slot);
        return emeralds>=offer.price()&&offer.card().toString().equals(expectedCard)&&offer.enchanted()==expectedEnchanted;
    }
    private CardVendor(){}
}
