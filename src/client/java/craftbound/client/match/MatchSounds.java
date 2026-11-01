package craftbound.client.match;

import craftbound.card.Cards;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

// Minecraft sounds for the match screen. Mobs use their own sounds (a Zombie groans when it arrives, a Creeper
// hisses), items use the sound of using that item, and the game itself uses UI and note block sounds.
// Sounds are looked up by name, so a missing one is just silent.
final class MatchSounds {
    // Mobs whose arrival has a better sound than their idle noise.
    private static final Map<String, String> SUMMON = Map.ofEntries(
            Map.entry("creeper", "entity.creeper.primed"),
            Map.entry("charged_creeper", "entity.creeper.primed"),
            Map.entry("warden", "entity.warden.emerge"),
            Map.entry("wither", "entity.wither.spawn"),
            Map.entry("ender_dragon", "entity.ender_dragon.growl"),
            Map.entry("evoker", "entity.evoker.cast_spell"),
            Map.entry("ravager", "entity.ravager.roar"),
            Map.entry("piglin_brute", "entity.piglin_brute.angry"),
            Map.entry("vindicator", "entity.vindicator.celebrate"),
            Map.entry("bee", "entity.bee.pollinate"),
            Map.entry("sniffer", "entity.sniffer.digging"),
            Map.entry("wheat", "item.crop.plant"),
            Map.entry("sweet_berry_bush", "block.sweet_berry_bush.place"),
            Map.entry("cobblestone_wall", "block.stone.place"),
            Map.entry("furnace", "block.stone.place"),
            Map.entry("dispenser", "block.stone.place"),
            Map.entry("beacon", "block.beacon.activate"),
            Map.entry("composter", "block.composter.fill_success"));
    // Cards that aren't mobs in Minecraft, or share another mob's sounds.
    private static final Map<String, String> MOB_SOUND = Map.of(
            "charged_creeper", "creeper",
            "vex", "vex");
    // What playing each item sounds like.
    private static final Map<String, String> ITEM = Map.ofEntries(
            Map.entry("rotten_flesh", "entity.generic.eat"),
            Map.entry("enchanted_golden_apple", "entity.generic.eat"),
            Map.entry("arrow", "entity.arrow.shoot"),
            Map.entry("totem_of_undying", "item.totem.use"),
            Map.entry("nether_star", "block.beacon.activate"),
            Map.entry("dragon_egg", "entity.experience_orb.pickup"),
            Map.entry("experience_bottle", "entity.experience_bottle.throw"),
            Map.entry("potion_of_strength", "entity.generic.drink"),
            Map.entry("sweeping_edge", "entity.player.attack.sweep"),
            Map.entry("piston", "block.piston.extend"),
            Map.entry("bone_meal", "item.bone_meal.use"),
            Map.entry("bountiful_harvest", "item.crop.plant"));

    static void play(String name, float pitch, float volume) {
        SoundEvent sound = BuiltInRegistries.SOUND_EVENT.getValue(Identifier.withDefaultNamespace(name));
        if (sound != null) {
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
        }
    }

    static void play(String name) {
        play(name, 1.0F, 0.7F);
    }

    // A mob's own sound: kind is "ambient", "hurt" or "death". Blocks and crops sound like stone and plants.
    static void mob(Identifier card, String kind) {
        String path = card.getPath();
        if (Cards.get(card) != null && Cards.get(card).keywords().contains(craftbound.card.Keyword.BLOCK)) {
            play(kind.equals("death") ? (path.equals("wheat") || path.equals("sweet_berry_bush") ? "block.crop.break" : "block.stone.break")
                    : "block.stone.hit", 1.0F, 0.7F);
            return;
        }
        String mob = MOB_SOUND.getOrDefault(path, path);
        play("entity." + mob + "." + kind, 1.0F, 0.7F);
    }

    static void summon(Identifier card) {
        String special = SUMMON.get(card.getPath());
        if (special != null) {
            play(special, 1.0F, 0.75F);
        } else {
            mob(card, "ambient");
        }
    }

    static void item(Identifier card) {
        play(ITEM.getOrDefault(card.getPath(), "item.book.page_turn"), 1.0F, 0.75F);
    }

    // Equipping a tool: swords clink like iron armour, the bow like leather.
    static void equip(Identifier card) {
        play(card.getPath().equals("bow") ? "item.armor.equip_leather" : "item.armor.equip_iron", 1.0F, 0.8F);
    }

    private MatchSounds() {
    }
}
