package craftbound.client;

import craftbound.client.card.CardInspectScreen;
import craftbound.component.ModComponents;
import craftbound.item.ModItems;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;

public class CraftboundClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// Right-clicking a card opens the inspect screen. Screens only exist on the client,
		// so this lives here rather than in CardItem.
		UseItemCallback.EVENT.register((player, level, hand) -> {
			ItemStack stack = player.getItemInHand(hand);
			if (level.isClientSide() && stack.is(ModItems.CARD) && stack.has(ModComponents.CARD_ID)) {
				Minecraft.getInstance().gui.setScreen(new CardInspectScreen(stack));
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.PASS;
		});
	}
}
