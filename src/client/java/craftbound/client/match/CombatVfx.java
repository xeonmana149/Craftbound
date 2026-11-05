package craftbound.client.match;

import craftbound.card.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

/** Small reusable primitives. Fixed draw budgets; no world particles or per-frame textures. */
final class CombatVfx {
    /** An explicit choice wins over artwork, name and card kind. AUTO keeps old catalogues working. */
    static CardCatalog.CastFx castFx(Identifier id) {
        var entry = id == null ? null : CardCatalog.entry(id);
        return entry == null ? CardCatalog.CastFx.AUTO : entry.castFx();
    }
    static CardCatalog.ArrivalFx arrivalFx(Identifier id) {
        var entry = id == null ? null : CardCatalog.entry(id);
        return entry == null ? CardCatalog.ArrivalFx.AUTO : entry.arrivalFx();
    }
    static boolean fox(Identifier id) {
        var fx = castFx(id);
        if (fx != CardCatalog.CastFx.AUTO) return fx == CardCatalog.CastFx.FOX;
        var c = id == null ? null : Cards.get(id);
        return c != null && c.isMob() && !c.keywords().contains(Keyword.BLOCK)
                && (id.getPath().equals("fox") || "minecraft:fox".equals(c.artItem())
                || "minecraft:fox_spawn_egg".equals(c.artItem()));
    }
    static boolean enderEye(Identifier id) {
        var fx = castFx(id);
        if (fx != CardCatalog.CastFx.AUTO) return fx == CardCatalog.CastFx.EYE_OF_ENDER;
        var card = id == null ? null : Cards.get(id);
        if (card == null || card.isMob() || card.isTool()) return false;
        // The card can reveal, discard, steal or shuffle. Its hand effect does not choose the visual.
        return id.getPath().equals("eye_of_ender") || id.getPath().equals("ender_eye")
                || "minecraft:ender_eye".equals(card.artItem()) || "ender_eye".equals(card.artItem());
    }
    static boolean egg(Identifier id) {
        var fx = castFx(id);
        if (fx != CardCatalog.CastFx.AUTO) return fx == CardCatalog.CastFx.EGG;
        var c = id == null ? null : Cards.get(id);
        if (c == null || c.isMob() || c.isTool()) return false;
        String art = c.artItem() == null ? "" : c.artItem();
        return id.getPath().equals("egg") || art.equals("egg") || art.equals("minecraft:egg");
    }
    static boolean potion(Identifier id) {
        var fx = castFx(id);
        if (fx != CardCatalog.CastFx.AUTO) return fx == CardCatalog.CastFx.POTION;
        var c = id == null ? null : Cards.get(id);
        return c != null && !c.isMob() && (id.getPath().contains("potion") || c.artItem() != null && c.artItem().contains("potion"));
    }
    static boolean arrow(Identifier id) { return castFx(id) == CardCatalog.CastFx.ARROW; }
    static boolean automaticDeath(Identifier id) {
        var entry = id == null ? null : CardCatalog.entry(id);
        return entry == null || entry.deathFx() == CardCatalog.DeathFx.AUTO;
    }
    static boolean cracks(Identifier id) {
        var entry = id == null ? null : CardCatalog.entry(id);
        if (entry == null) return false;
        if (entry.deathFx() != CardCatalog.DeathFx.AUTO) return entry.deathFx() == CardCatalog.DeathFx.CRACKS;
        var card = Cards.get(id);
        return card != null && card.keywords().contains(Keyword.BLOCK);
    }
    static boolean explodes(Identifier id) {
        var entry = id == null ? null : CardCatalog.entry(id);
        if (entry != null && entry.deathFx() != CardCatalog.DeathFx.AUTO) return entry.deathFx() == CardCatalog.DeathFx.EXPLODE;
        return entry != null && entry.effects().stream().anyMatch(e -> e.trigger() == CardCatalog.Trigger.DROP
                && e.action() == CardCatalog.Action.DAMAGE);
    }
    static boolean splits(Identifier id) {
        var entry = id == null ? null : CardCatalog.entry(id);
        if (entry != null && entry.deathFx() != CardCatalog.DeathFx.AUTO) return entry.deathFx() == CardCatalog.DeathFx.SPLIT;
        return entry != null && (id.getPath().contains("slime") || id.getPath().contains("magma_cube"))
                && entry.effects().stream().anyMatch(e -> e.trigger() == CardCatalog.Trigger.DROP && e.action() == CardCatalog.Action.SUMMON);
    }
    static void ring(GuiGraphicsExtractor g, float x, float y, float radius, int color, float t) {
        int c = MatchArt.alpha(color, (1-t)*(1-t));
        for (int i=0;i<24;i++) {
            double a=i*Math.PI*2/24;
            int px=Math.round(x+(float)Math.cos(a)*radius),py=Math.round(y+(float)Math.sin(a)*radius);
            g.fill(px-1,py-1,px+2,py+2,c);
        }
    }
    static void cracks(GuiGraphicsExtractor g, float x, float y, int w, int h, float t) {
        // Use Minecraft's actual destroy-stage texture, rather than an unrelated damage symbol.
        int stage=Math.min(9,Math.max(0,(int)(t*10)));
        g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, CRACKS[stage],
                Math.round(x),Math.round(y),0,0,w,h,16,16,16,16,0xDBFFFFFF);
    }
    private static final Identifier[] CRACKS = java.util.stream.IntStream.range(0,10)
            .mapToObj(i->Identifier.withDefaultNamespace("textures/block/destroy_stage_"+i+".png")).toArray(Identifier[]::new);
    static void streaks(GuiGraphicsExtractor g,float x,float y,int color,float t) {
        for(int i=0;i<5;i++) {
            int length=Math.round((12+i*3)*(1-t));int yy=Math.round(y+(i-2)*5);
            g.fill(Math.round(x)-length,yy,Math.round(x)+length,yy+1,MatchArt.alpha(color,1-t));
        }
    }
    private CombatVfx() {}
}
