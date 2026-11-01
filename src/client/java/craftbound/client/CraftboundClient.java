package craftbound.client;

import craftbound.block.CardDisplayBlock;
import craftbound.block.ModBlocks;
import craftbound.client.binder.BinderScreen;
import craftbound.client.block.CardDisplayRenderer;
import craftbound.client.card.CardInspectScreen;
import craftbound.client.card.PackOpeningScreen;
import craftbound.client.card.SleevePreviewScreen;
import craftbound.client.deck.DeckCaseScreen;
import craftbound.client.match.MatchScreen;
import craftbound.network.MatchStatePayload;
import craftbound.component.ModComponents;
import craftbound.item.ModItems;
import craftbound.network.PackOpenedPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;

public class CraftboundClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// The seat at a card table is invisible; the display stand draws its card.
		EntityRendererRegistry.register(ModBlocks.SEAT, NoopRenderer::new);
		BlockEntityRendererRegistry.register(ModBlocks.CARD_DISPLAY_ENTITY, CardDisplayRenderer::new);
		CardDisplayBlock.openInspect = card -> Minecraft.getInstance().gui.setScreen(new CardInspectScreen(card));

		// Match updates: open the match screen, or refresh it if it's already open.
		ClientPlayNetworking.registerGlobalReceiver(MatchStatePayload.TYPE, (payload, context) -> {
			if (context.client().gui.screen() instanceof MatchScreen screen) {
				screen.update(payload.json());
			} else {
				context.client().gui.setScreen(new MatchScreen(payload.json()));
			}
		});

		// The server tells us when we've opened a booster pack, so we can play the reveal.
		ClientPlayNetworking.registerGlobalReceiver(PackOpenedPayload.TYPE, (payload, context) ->
				context.client().gui.setScreen(new PackOpeningScreen(payload.pulls(), payload.jackpot())));

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
