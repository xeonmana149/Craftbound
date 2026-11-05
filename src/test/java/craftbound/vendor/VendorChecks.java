package craftbound.vendor;

import craftbound.card.*;
import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import java.nio.file.*;
import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.resources.Identifier;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.storage.loot.LootTable;

public final class VendorChecks {
    static int checks;
    static void ok(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception{
  craftbound.test.TestCatalogue.install();
        var one=CardVendor.lineup(42,12345,10);
        ok(one.equals(CardVendor.lineup(42,12345,10)),"Same lineup for every viewer");
        ok(!one.equals(CardVendor.lineup(42,12346,10)),"Different machine");
        ok(!one.equals(CardVendor.lineup(42,12345,11)),"Next day");
        for(int day=0;day<2000;day++){
            var offers=CardVendor.lineup(100,2345,day);ok(offers.size()==6,"Six daily slots");
            ok(offers.stream().map(CardVendor.Offer::card).distinct().count()==6,"Unique IDs");
            for(int i=0;i<6;i++){
                var offer=offers.get(i);var card=Cards.get(offer.card());ok(!card.token(),"No tokens");
                ok(i<3?card.rarity()==CardRarity.COMMON:i<5?card.rarity()==CardRarity.RARE:
                        card.rarity()==CardRarity.EPIC||card.rarity()==CardRarity.LEGENDARY,"Rarity slots");
                ok(offer.price()==CardVendor.price(card.rarity(),offer.enchanted()),"Server price");
            }
        }
        for(CardRarity rarity:CardRarity.values()){
            var pool=new ArrayList<CardVendor.Candidate>();
            for(int i=0;i<8;i++)pool.add(new CardVendor.Candidate(Identifier.parse("craftbound:vendor_test_"+i),rarity));
            var offers=CardVendor.lineup(1,2,3,pool);
            ok(offers.size()==6&&offers.stream().map(CardVendor.Offer::card).distinct().count()==6,"Empty rarity fallback");
            ok(CardVendor.price(rarity,true)==3*CardVendor.price(rarity,false),"Enchanted pricing");
            ok(CardVendor.lineup(1,2,3,pool.subList(0,2)).size()==2,"Tiny catalogue cannot duplicate singles");
        }
        ok(CardVendor.lineup(1,2,3,List.of()).isEmpty(),"Empty catalogue");
        ok(CardVendor.day(23999)==0&&CardVendor.day(24000)==1,"Dawn");
        ok(CardVendor.untilRestock(23999)==1&&CardVendor.untilRestock(24000)==24000,"Restock countdown");
        var offer=one.get(0);
        ok(CardVendor.canBuy(0,10,10,0,offer.price(),one,offer.card().toString(),offer.enchanted()),"Affordable current offer");
        ok(!CardVendor.canBuy(0,9,10,0,1000,one,offer.card().toString(),offer.enchanted()),"Reject stale day");
        ok(!CardVendor.canBuy(0,10,10,1,1000,one,offer.card().toString(),offer.enchanted()),"Reject sold slot");
        ok(!CardVendor.canBuy(0,10,10,0,offer.price()-1,one,offer.card().toString(),offer.enchanted()),"Reject insufficient funds");
        ok(!CardVendor.canBuy(0,10,10,0,1000,one,"craftbound:wrong",offer.enchanted()),"Reject changed catalogue offer");
        ok(!CardVendor.canBuy(0,10,10,0,1000,one,offer.card().toString(),!offer.enchanted()),"Reject changed finish");
        for(int bad:new int[]{-2,6,Integer.MAX_VALUE})ok(!CardVendor.canBuy(bad,10,10,0,1000,one,"",false),"Reject invalid slot");
        ok(CardVendor.canBuy(-1,9,10,63,5,one,"",false),"Pack independent of single stock/day");
        ok(!CardVendor.canBuy(-1,10,10,0,4,one,"",false),"Pack costs five");
        var packs=new VendorPurchases();var buyer=UUID.randomUUID();packs.mark(buyer,10,2);
        for(int i=0;i<CardVendor.PACK_LIMIT;i++){
            ok(CardVendor.canBuy(-1,10,10,packs.bought(buyer,10),5,one,"",false),"Pack "+(i+1)+" of the daily limit");
            packs.markPack(buyer,10);
        }
        ok(CardVendor.packsBought(packs.bought(buyer,10))==CardVendor.PACK_LIMIT&&(packs.bought(buyer,10)&(1<<2))!=0,"Pack count and single marks share the record");
        ok(!CardVendor.canBuy(-1,10,10,packs.bought(buyer,10),1000,one,"",false),"Packs sell out per player per day");
        var reloaded=new VendorPurchases();reloaded.load(10,packs.snapshot(10));
        ok(CardVendor.packsLeft(reloaded.bought(buyer,10))==0,"Pack count survives saving");
        ok(CardVendor.packsLeft(reloaded.bought(buyer,11))==CardVendor.PACK_LIMIT,"Packs restock the next day");
        ok(CardVendor.packsLeft(reloaded.bought(UUID.randomUUID(),10))==CardVendor.PACK_LIMIT,"Another player's stock is untouched");
        var ledger=new VendorPurchases();var a=UUID.randomUUID();var b=UUID.randomUUID();
        ledger.mark(a,10,0);ledger.mark(a,10,5);
        ok(ledger.bought(a,10)==33&&ledger.bought(b,10)==0,"Purchases are per-player and per-slot");
        var restored=new VendorPurchases();restored.load(10,ledger.snapshot(10));
        ok(restored.bought(a,10)==33,"Persist purchases");
        ok(restored.snapshot(11).isEmpty()&&restored.bought(a,11)==0,"Prune prior day and restock");
        SharedConstants.tryDetectVersion();Bootstrap.bootStrap();
        BuiltInRegistries.ITEM.listElements().forEach(holder->{if(!holder.areComponentsBound())holder.bindComponents(DataComponentMap.EMPTY);});
        var inventory=new Inventory(null,new EntityEquipment());
        inventory.setItem(0,new ItemStack(Items.EMERALD,2));inventory.setItem(1,new ItemStack(Items.EMERALD,4));
        inventory.setItem(2,new ItemStack(Items.DIAMOND,7));
        ok(VendorTrading.pay(inventory,5)&&VendorTrading.emeralds(inventory)==1,"Charge split stacks");
        ok(!VendorTrading.pay(inventory,2)&&VendorTrading.emeralds(inventory)==1,"Failed payment changes nothing");
        ok(inventory.getItem(2).getCount()==7,"Other items untouched");
        // Validate schemas with the equivalent vanilla two-half block, without Fabric registry injection.
        String root=args.length==0?".":args[0];
        String recipe=Files.readString(Path.of(root,"src/main/resources/data/craftbound/recipe/card_vendor.json")).replace("craftbound:card_vendor","minecraft:oak_door");
        String loot=Files.readString(Path.of(root,"src/main/resources/data/craftbound/loot_table/blocks/card_vendor.json")).replace("craftbound:card_vendor","minecraft:oak_door");
        Recipe.DIRECT_CODEC.parse(JsonOps.INSTANCE,JsonParser.parseString(recipe)).getOrThrow();
        var ops=net.minecraft.resources.RegistryOps.create(JsonOps.INSTANCE,net.minecraft.core.RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
        LootTable.DIRECT_CODEC.parse(ops,JsonParser.parseString(loot)).getOrThrow();
        System.out.println("PASS: "+checks+" vendor assertions; determinism, rarities, pricing, fallback, dawn, per-player purchases, stale/invalid requests, split-stack payment and 26.3 recipe/loot schemas.");
    }
}
