package craftbound.client;

import craftbound.client.binder.BinderScreen;
import craftbound.client.card.CardInspectScreen;
import craftbound.client.card.PackOpeningScreen;
import craftbound.client.card.SleevePreviewScreen;
import craftbound.client.deck.DeckCaseScreen;
import craftbound.component.ModComponents;
import craftbound.item.ModItems;
import craftbound.network.PackOpenedPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;

public class CraftboundClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// The server tells us when we've opened a booster pack, so we can play the reveal.
		ClientPlayNetworking.registerGlobalReceiver(PackOpenedPayload.TYPE, (payload, context) ->
				context.client().gui.setScreen(new PackOpeningScreen(payload.pulls())));

		// Right-clicking a card, binder or sleeve opens its screen. Screens only exist on the client,
		// so this lives here rather than in the item classes.
		UseItemCallback.EVENT.register((player, level, hand) -> {
			if (!level.isClientSide()) {
				return InteractionResult.PASS;
			}
			ItemStack stack = player.getItemInHand(hand);
			Screen screen = null;
			if (stack.is(ModItems.CARD) && stack.has(ModComponents.CARD_ID)) {
				screen = new CardInspectScreen(stack);
			} else if (stack.is(ModItems.BINDER)) {
				screen = new BinderScreen(hand);
			} else if (stack.is(ModItems.DECK_CASE)) {
				screen = new DeckCaseScreen(hand);
			} else if (stack.is(ModItems.SLEEVE)) {
				screen = new SleevePreviewScreen(stack);
			}
			if (screen == null) {
				return InteractionResult.PASS;
			}
			Minecraft.getInstance().gui.setScreen(screen);
			return InteractionResult.SUCCESS;
		});
	}
}
