package craftbound.client;

import craftbound.block.CardDisplayBlock;
import craftbound.block.ModBlocks;
import craftbound.client.binder.BinderScreen;
import craftbound.client.block.CardDisplayRenderer;
import craftbound.client.card.CardInspectScreen;
import craftbound.client.card.PackOpeningScreen;
import craftbound.client.deck.DeckCaseScreen;
import craftbound.client.match.MatchScreen;
import craftbound.client.match.TableScreen;
import craftbound.network.MatchStatePayload;
import craftbound.network.TableLobbyPayload;
import craftbound.entity.ModEntities;
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
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;

public class CraftboundClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
        craftbound.client.match.RelayClient.initialize();
        ClientPlayNetworking.registerGlobalReceiver(craftbound.network.TrialsStatePayload.TYPE,
                (packet, ctx) -> craftbound.client.match.TrialsScreen.receive(ctx.client(), packet));
        ClientPlayNetworking.registerGlobalReceiver(craftbound.network.CommanderViewPayload.TYPE,(p,ctx)->{
            var client=ctx.client();var current=client.gui.screen();
            if(p.json().isEmpty()){if(current instanceof craftbound.client.match.CommanderScreen)client.gui.setScreen(null);return;}
            var data=com.google.gson.JsonParser.parseString(p.json()).getAsJsonObject();
            if(current instanceof craftbound.client.match.CommanderScreen screen && screen.same(data)) screen.update(data);
            else if(p.open())client.gui.setScreen(new craftbound.client.match.CommanderScreen(data));
        });
        ClientPlayNetworking.registerGlobalReceiver(craftbound.network.CommanderHistoryPayload.TYPE,(p,ctx)->{
            if(ctx.client().gui.screen() instanceof craftbound.client.match.CommanderScreen screen) screen.receiveHistory(p);
        });
        ClientPlayNetworking.registerGlobalReceiver(craftbound.network.HandConsentPayload.TYPE,(payload,context)->craftbound.client.match.HandConsentOverlay.update(payload));
        ClientPlayNetworking.registerGlobalReceiver(craftbound.network.SpectatorStatePayload.TYPE,(payload,context)->{
            var current=context.client().gui.screen();
            if(current instanceof craftbound.client.match.SpectatorScreen screen&&screen.table().equals(payload.table())){
                if(payload.json().isEmpty())context.client().gui.setScreen(null);else screen.update(payload);
            }else if(payload.open()&&!payload.json().isEmpty())context.client().gui.setScreen(new craftbound.client.match.SpectatorScreen(payload));
        });
        ClientPlayNetworking.registerGlobalReceiver(craftbound.network.TurnClockPayload.TYPE,(payload,context)->{
            var screen=context.client().gui.screen();
            if(screen instanceof MatchScreen match)match.clock(payload);
            else if(screen instanceof craftbound.client.match.MultiplayerBoardScreen match)match.clock(payload);
            else if(screen instanceof craftbound.client.match.MatchPauseScreen prompt)prompt.match.clock(payload);
        });
        ClientPlayNetworking.registerGlobalReceiver(craftbound.network.VendorStatePayload.TYPE, (payload, context) -> {
            var current=context.client().gui.screen();
            if(current instanceof craftbound.client.vendor.CardVendorScreen vendor && vendor.pos().equals(payload.pos()))
                vendor.update(payload);
            else if(payload.open()) context.client().gui.setScreen(new craftbound.client.vendor.CardVendorScreen(payload));
        });
        net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
            // Creative search indexes tooltips on a worker. Font wrapping can bake/upload glyphs,
            // so only wrap for actual on-screen tooltips; indexing needs the original text.
            if (com.mojang.blaze3d.systems.RenderSystem.isOnRenderThread()
                    && stack.is(ModItems.CARD) && stack.has(ModComponents.CARD_ID)) {
                Minecraft client = Minecraft.getInstance();
                Tooltips.wrapCardTooltip(client.font, lines, client.getWindow().getGuiScaledWidth() - 24);
            }
        });
        ClientPlayNetworking.registerGlobalReceiver(craftbound.network.CardCatalogPayload.TYPE, (payload, context) -> {
            var next = craftbound.card.CardCatalog.parse(payload.json());
            craftbound.card.CardCatalog.validateReplacement(craftbound.card.CardCatalog.builtin(), next);
            craftbound.card.CardCatalog.install(next);
            craftbound.client.match.LocalLayoutPreview.catalogueChanged(context.client());
        });
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> {
            craftbound.client.block.TableBoardTextures.clear();
            craftbound.client.match.HandConsentOverlay.clear();
            craftbound.card.CardCatalog.install(craftbound.card.CardCatalog.builtin());
            try { craftbound.card.CardCatalogService.loadLocal(); }
            catch (Exception e) { craftbound.Craftbound.LOGGER.error("Could not restore local cards", e); }
        }));
		// The Card Master looks like a wandering trader; the display stand draws its card.
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STOPPING.register(client->craftbound.client.block.TableBoardTextures.clear());
		EntityRendererRegistry.register(ModEntities.CARD_MASTER, craftbound.client.block.CardMasterRenderer::new);
        EntityRendererRegistry.register(ModEntities.CARD_SEAT, craftbound.client.block.CardSeatRenderer::new);
		BlockEntityRendererRegistry.register(ModBlocks.CARD_DISPLAY_ENTITY, CardDisplayRenderer::new);
        BlockEntityRendererRegistry.register(ModBlocks.CARD_TABLE_ENTITY, craftbound.client.block.CardTableRenderer::new);
		CardDisplayBlock.openInspect = card -> Minecraft.getInstance().gui.setScreen(new CardInspectScreen(card));

		// Match updates: open the match screen, or refresh it if it's already open.
		ClientPlayNetworking.registerGlobalReceiver(MatchStatePayload.TYPE, (payload, context) -> {
            var root=com.google.gson.JsonParser.parseString(payload.json()).getAsJsonObject();
            var current=context.client().gui.screen();
            if(root.has("paused") && root.get("paused").getAsBoolean()) {
                if(current instanceof MatchScreen || current instanceof craftbound.client.match.MatchPauseScreen)
                    context.client().gui.setScreen(null);
                return;
            }
            MatchScreen screen=current instanceof MatchScreen existing?existing
                    :current instanceof craftbound.client.match.MatchPauseScreen prompt?prompt.match:null;
            if(screen==null)screen=new MatchScreen(payload.json());else screen.update(payload.json());
            int request=root.has("pauseRequest")?root.get("pauseRequest").getAsInt():-1;
            if(request>=0 && request!=root.get("you").getAsInt()) {
                if(!(current instanceof craftbound.client.match.MatchPauseScreen))
                    context.client().gui.setScreen(new craftbound.client.match.MatchPauseScreen(screen));
            } else if(current!=screen) context.client().gui.setScreen(screen);
		});

		// The card table's lobby: open it, update it, or close it once you're waiting for an opponent.
		ClientPlayNetworking.registerGlobalReceiver(craftbound.network.ResumeMatchPayload.TYPE, (payload, context) ->
				context.client().gui.setScreen(new craftbound.client.match.MatchResumeScreen(payload)));
		ClientPlayNetworking.registerGlobalReceiver(TableLobbyPayload.TYPE, (payload, context) -> {
			Screen current = context.client().gui.screen();
			if (payload.close()) {
				if (current instanceof TableScreen) {
					context.client().gui.setScreen(null);
				}
			} else if (current instanceof TableScreen screen && screen.table().equals(payload.table()) && screen.tableMode()==payload.tableMode()) {
				screen.update(payload.waiting(), payload.error(), payload.developerAccess());
			} else {
				context.client().gui.setScreen(new TableScreen(payload.table(), payload.waiting(), payload.error(), payload.developerAccess(), payload.tableMode()));
			}
		});

		// The server tells us when we've opened a booster pack, so we can play the reveal.
		ClientPlayNetworking.registerGlobalReceiver(PackOpenedPayload.TYPE, (payload, context) ->
				context.client().gui.setScreen(new PackOpeningScreen(payload.pulls(), payload.jackpot(), payload.fresh())));

		// Right-clicking a card, binder or deck case opens its screen. Sleeves use their server-side unlock action.
		// Screens only exist on the client,
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
				screen = new DeckCaseScreen(hand);
			}
			if (screen == null) {
				return InteractionResult.PASS;
			}
			Minecraft.getInstance().gui.setScreen(screen);
			return InteractionResult.SUCCESS;
		});
	}
}
