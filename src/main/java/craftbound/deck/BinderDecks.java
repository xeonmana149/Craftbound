package craftbound.deck;

import craftbound.binder.BinderContents;
import craftbound.card.CardClass;
import craftbound.component.ModComponents;
import craftbound.item.ModItems;
import craftbound.network.BinderDeckPayload;
import java.util.UUID;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

public final class BinderDecks {
    public static SavedDecks get(ItemStack binder) {
        return binder.getOrDefault(ModComponents.SAVED_DECKS, SavedDecks.EMPTY);
    }

    // Selects a saved deck in the binder at that slot. Decks always live in a binder now, so an empty id finds nothing.
    public static ItemStack resolve(Inventory inventory, int slot, String id) {
        if (slot < 0 || slot >= inventory.getContainerSize()) return ItemStack.EMPTY;
        ItemStack holder = inventory.getItem(slot);
        if (id.isEmpty()) return ItemStack.EMPTY;
        if (!holder.is(ModItems.BINDER)) return ItemStack.EMPTY;
        SavedDecks.Entry entry = get(holder).find(id);
        return entry == null ? ItemStack.EMPTY : entry.asStack();
    }

    // 0 and 1 retain the original offhand/main hand wire values. Table screens bind to an inventory slot.
    public static int locationForSlot(int slot) { return slot + 2; }

    public static ItemStack holder(net.minecraft.world.entity.player.Player player, int location) {
        if (location == 0) return player.getOffhandItem();
        if (location == 1) return player.getMainHandItem();
        int slot = location - 2;
        var inventory = player.getInventory();
        return location >= 2 && slot < inventory.getContainerSize() ? inventory.getItem(slot) : ItemStack.EMPTY;
    }

    // Creation encodes format, class and optional leader together so half-created Commander lists cannot be saved.
    public static String creation(DeckFormat format,CardClass cls,Identifier leader) {
        return format.name()+"|"+cls.name()+"|"+(leader==null?"":leader.toString());
    }
    public static DeckContents create(String value,boolean foil,java.util.function.BiPredicate<Identifier,Boolean> owned) {
        String[] parts=value.split("\\|",-1);
        DeckFormat format=parts.length==1 ? DeckFormat.DUEL : DeckFormat.valueOf(parts[0]);
        if (parts.length!=1 && parts.length!=3) return null;
        CardClass cls=CardClass.valueOf(parts.length==1?parts[0]:parts[1]);
        if (cls==CardClass.NEUTRAL) return null;
        DeckContents deck=new DeckContents(cls,BinderContents.EMPTY,true,format,java.util.Optional.empty());
        if (format!=DeckFormat.COMMANDER) return parts.length==1 || parts[2].isEmpty()?deck:null;
        Identifier leader=Identifier.tryParse(parts[2]);
        if (!craftbound.game.CommanderMatch.eligible(leader) || !craftbound.card.DeckRules.allowed(leader,cls) || !owned.test(leader,foil)) return null;
        return deck.withCommander(leader,foil);
    }

    public static DeckContents copyCards(DeckContents target, DeckContents source,
            java.util.function.ToIntBiFunction<Identifier,Boolean> owned) {
        if (!target.equals(target.clearCards())) return target;
        for (Identifier id : craftbound.card.Cards.collectible()) {
            if (target.leads(id) || source.copies(id)==0) continue;
            for (boolean foil : new boolean[]{false,true}) {
                int copies=Math.min(source.cards().count(id,foil),owned.applyAsInt(id,foil));
                for (int i=0;i<copies && DeckBuilding.whyNot(target,id)==null;i++) target=target.add(id,foil,1);
            }
        }
        return target;
    }

    public static void handle(ServerPlayer player, BinderDeckPayload packet) {
        ItemStack binder = holder(player, packet.location());
        if (!binder.is(ModItems.BINDER)) return;
        try {
            // Creation IDs are random UUIDs supplied by the UI; never accept unbounded arbitrary keys.
            UUID.fromString(packet.deckId());
            SavedDecks saved = get(binder);
            SavedDecks.Entry entry = saved.find(packet.deckId());
            int action = packet.action();
            if (action == BinderDeckPayload.CREATE) {
                if (entry != null) return;
                DeckContents deck=create(packet.value(),packet.enchanted(),(id,foil)->DeckBuilding.owned(player.getInventory(),id,foil)>0);
                if(deck==null || saved.count(deck.format())>=SavedDecks.LIMIT)return;
                ItemStack stack=DeckBuilding.deckStack();
                stack.set(ModComponents.DECK_CONTENTS,deck);
                binder.set(ModComponents.SAVED_DECKS, saved.put(SavedDecks.Entry.fromStack(packet.deckId(), stack)));
                return;
            }
            if (entry == null) return;
            if (action == BinderDeckPayload.DELETE) {
                binder.set(ModComponents.SAVED_DECKS, saved.remove(entry.id()));
                return;
            }
            ItemStack stack = entry.asStack();
            switch (action) {
                case BinderDeckPayload.COPY_CARDS -> {
                    var source=saved.find(packet.value());
                    if(source==null || source.id().equals(entry.id()))return;
                    stack.set(ModComponents.DECK_CONTENTS,copyCards(entry.deck(),source.deck(),
                            (id,foil)->DeckBuilding.owned(player.getInventory(),id,foil)));
                }
                case BinderDeckPayload.CHOOSE_COMMANDER -> {
                    Identifier leader=Identifier.parse(packet.value());
                    if(DeckBuilding.owned(player.getInventory(),leader,packet.enchanted())==0)return;
                    stack.set(ModComponents.DECK_CONTENTS,entry.deck().withCommander(leader,packet.enchanted()));
                }
                case BinderDeckPayload.ADD -> DeckBuilding.add(player, stack, Identifier.parse(packet.value()), packet.enchanted());
                case BinderDeckPayload.REMOVE -> DeckBuilding.remove(stack, Identifier.parse(packet.value()), packet.enchanted());
                case BinderDeckPayload.RENAME -> DeckBuilding.rename(stack, packet.value());
                case BinderDeckPayload.CLASS -> DeckBuilding.setClass(stack, CardClass.valueOf(packet.value()));
                case BinderDeckPayload.SLEEVE -> DeckBuilding.setSleeve(player, stack, Identifier.parse(packet.value()));
                case BinderDeckPayload.CLEAR -> stack.set(ModComponents.DECK_CONTENTS,
                        entry.deck().clearCards());
                default -> { return; }
            }
            binder.set(ModComponents.SAVED_DECKS, saved.put(SavedDecks.Entry.fromStack(entry.id(), stack)));
        } catch (IllegalArgumentException ignored) {
            // Invalid network input leaves both the collection and all lists unchanged.
        }
    }

    private BinderDecks() {}
}
