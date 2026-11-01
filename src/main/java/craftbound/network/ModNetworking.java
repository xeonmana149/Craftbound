package craftbound.network;

import craftbound.binder.BinderContents;
import craftbound.component.ModComponents;
import craftbound.item.BinderItem;
import craftbound.item.CardItem;
import craftbound.item.ModItems;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

// Packets from the binder screen. The client only asks; the server checks the request and moves the cards,
// so a modified client can't create cards out of nothing.
public class ModNetworking {
    public static void initialize() {
        PayloadTypeRegistry.serverboundPlay().register(TakeFromBinderPayload.TYPE, TakeFromBinderPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(FileAllCardsPayload.TYPE, FileAllCardsPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(PackOpenedPayload.TYPE, PackOpenedPayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(TakeFromBinderPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            ItemStack binder = player.getItemInHand(payload.mainHand() ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND);
            if (!binder.is(ModItems.BINDER)) {
                return;
            }
            BinderContents contents = binder.getOrDefault(ModComponents.BINDER_CONTENTS, BinderContents.EMPTY);
            int have = contents.count(payload.card(), payload.enchanted());
            if (have <= 0) {
                return;
            }
            int amount = payload.all() ? Math.min(have, 64) : 1;
            binder.set(ModComponents.BINDER_CONTENTS, contents.add(payload.card(), payload.enchanted(), -amount));
            ItemStack cards = CardItem.of(payload.card(), payload.enchanted());
            cards.setCount(amount);
            player.getInventory().placeItemBackInInventory(cards, Prediction.SERVER_ONLY);
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.BUNDLE_REMOVE_ONE, SoundSource.PLAYERS, 0.8F, 1.0F);
        });

        ServerPlayNetworking.registerGlobalReceiver(FileAllCardsPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            ItemStack binder = player.getItemInHand(payload.mainHand() ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND);
            if (!binder.is(ModItems.BINDER)) {
                return;
            }
            Inventory inventory = player.getInventory();
            boolean filedAny = false;
            for (int i = 0; i < inventory.getContainerSize(); i++) {
                ItemStack stack = inventory.getItem(i);
                if (BinderItem.canFile(stack)) {
                    BinderItem.file(binder, stack);
                    filedAny = true;
                }
            }
            if (filedAny) {
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.BUNDLE_INSERT, SoundSource.PLAYERS, 0.8F, 1.0F);
            }
        });
    }
}
