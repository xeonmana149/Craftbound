package craftbound.game;

import craftbound.card.*;
import java.util.*;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import static craftbound.game.WorkshopChecks.ok;

/** Use the actual four-seat battle to separate owner, ally, enemy and team-Health targeting. */
final class TeamTargetingChecks {
    private static MatchPlayer pilot(String name) {
        List<CardRef> cards = Cards.collectible().stream().filter(id -> DeckRules.allowed(id, CardClass.FIGHTER))
                .flatMap(id -> java.util.stream.Stream.generate(() -> new CardRef(id, false))
                        .limit(DeckRules.maxCopies(Cards.get(id).rarity())))
                .limit(30).toList();
        return new MatchPlayer(name, UUID.randomUUID(), CardClass.FIGHTER, null, cards);
    }
    private static CardCatalog.Effect aim(CardCatalog.Aim aim, CardCatalog.Action action) {
        return new CardCatalog.Effect(CardCatalog.Trigger.PLAY, action, aim, CardCatalog.BoardKind.BOTH,
                null, 1, 0, 0, null, false);
    }
    private static CardRef card(String name) { return new CardRef(Identifier.parse("craftbound:" + name), false); }

    static void run() {
        var m = new RelayMatch(new MatchPlayer[][]{{pilot("A"), pilot("C")},{pilot("B"), pilot("D")}}, RandomSource.create(155));
        int own = m.current, partner = own ^ 2, near = own ^ 1, far = near ^ 2;
        Minion a = m.summon(own, card("zombie")), b = m.summon(partner, card("zombie"));
        Minion c = m.summon(near, card("zombie")), d = m.summon(far, card("zombie"));
        Target ally = Target.mob(partner, b.id), self = Target.mob(own, a.id);
        var friendly = m.validTargets(own, Target.Kind.FRIENDLY_MOB, Cards.TOTEM_OF_UNDYING);
        ok(friendly.equals(List.of(self)), "your friendly target only sees your board");
        var enemies = m.validTargets(own, Target.Kind.ENEMY_MOB);
        ok(enemies.containsAll(List.of(Target.mob(near,c.id), Target.mob(far,d.id))) && !enemies.contains(ally), "enemy choice across opposing seats");
        var openTargets = m.validTargets(own, Target.Kind.ANY_CHARACTER);
        ok(openTargets.contains(ally) && openTargets.contains(Target.hero(partner)), "any target can choose an ally explicitly");
        var random = CardEffects.possibleTargets(m, own, null, aim(CardCatalog.Aim.RANDOM_ENEMY,CardCatalog.Action.DAMAGE), Target.NONE);
        ok(random.stream().filter(Target::hero).count() == 1 && random.size() == 3 && random.stream().allMatch(t->m.isEnemy(own,t.side())),
                "random damage chooses one shared enemy life pool or either opposing board");
        var freeze = CardEffects.possibleTargets(m, own, null, aim(CardCatalog.Aim.RANDOM_ENEMY,CardCatalog.Action.FREEZE), Target.NONE);
        ok(freeze.stream().filter(Target::hero).count()==2, "random Freeze may hit either distinct enemy hero");
        int before = a.attack;
        CardEffects.onItem(m,own,card("drowned"),Target.NONE);
        ok(a.attack==before+1 && b.attack==before, "friendly Undead buff does not spill onto ally's board");
        CardEffects.onItem(m,own,card("splash_potion_of_harming"),Target.NONE);
        ok(a.health==a.maxHealth && b.health==b.maxHealth && c.health==c.maxHealth-2 && d.health==d.maxHealth-2, "explicit enemy AOE hits both foes only");
        m.damageHero(near,7);
        ok(m.player(near).health==23 && m.player(far).health==23,"one hit mirrored, not doubled");
        m.healHero(far,40);
        ok(m.player(near).health==30 && m.player(far).health==30,"shared healing caps at max health");
        m.heal(Target.hero(near),2,true);
        ok(m.player(near).health==32 && m.player(far).maxHealth==32,"Well Fed increases both displayed caps once");
        m.player(near).frozen=m.player(far).frozen=true;
        m.cleanseHero(far);
        ok(!m.player(far).frozen && m.player(near).frozen && m.player(near).health==30 && m.player(far).maxHealth==30,
                "Cleanse resets shared max Health but only removes the chosen captain's personal Freeze");
        var shield=m.summon(near,card("turtle"));
        var wall=m.summon(far,card("cobblestone_wall"));
        a.ready=true;a.summonedThisTurn=false;
        ok(m.whyCantAttack(own,a.id,Target.hero(near))!=null && m.whyCantAttack(own,a.id,Target.mob(near,shield.id))==null
                && m.whyCantAttack(own,a.id,Target.hero(far))==null && m.whyCantAttack(own,a.id,Target.mob(far,wall.id))!=null
                && m.whyCantAttack(own,a.id,ally)!=null, "Shield and front row protect their owner; both enemies can be selected");

        var count = new CardCatalog.Effect(CardCatalog.Trigger.PLAY, CardCatalog.Action.DAMAGE, CardCatalog.Aim.ENEMY_HERO,
                CardCatalog.BoardKind.BOTH, null, 1, 0, 0, null, false, null, List.of(), CardCatalog.CountScope.ENEMY,
                CardCatalog.BoardKind.BOTH, null, false, CardCatalog.Scope.FRIENDLY, null, CardCatalog.Gate.ALWAYS);
        ok(CardEffects.multiplier(m, own, null, count)==4, "for each enemy counts across both rival boards only");
        m.equip(near, card("iron_sword"));
        m.equip(far, card("iron_sword"));
        m.equip(partner, card("iron_sword"));
        m.player(own).xp=5;
        m.player(own).hand.add(card("lava_bucket"));
        ok(m.act(m.player(own).playerId,craftbound.network.MatchActionPayload.PLAY,m.player(own).hand.size()-1,far,Target.NONE)==null
                && m.player(far).tool==null && m.player(near).tool!=null && m.player(partner).tool!=null,
                "singular enemy tool uses the explicitly chosen rival");
        ok(CardEffects.possibleTargets(m,own,null,aim(CardCatalog.Aim.ENEMY_HERO,CardCatalog.Action.DAMAGE),Target.NONE)
                .equals(List.of(Target.hero(far))), "singular enemy hero damage has one target");

        eachHeroCards(m,own,partner,near,far);
        var normal = WorkshopChecks.match();
        normal.player(1).health = 18; normal.damageHero(1, 4); normal.healHero(1, 3);
        ok(normal.player(0).health == 30 && normal.player(1).health == 17, "duel hero Health remains personal");
    }
    /** Resolve actual authored cards (including validation and automatic text) against the shared pools. */
    private static void eachHeroCards(RelayMatch m, int own, int partner, int near, int far) {
        var initial = CardCatalog.current();
        var json = com.google.gson.JsonParser.parseString(initial.json()).getAsJsonObject();
        var spell = card("splash_potion_of_harming");
        var entry = json.getAsJsonArray("cards").asList().stream().map(com.google.gson.JsonElement::getAsJsonObject)
                .filter(c -> c.get("id").getAsString().equals(spell.id().toString())).findFirst().orElseThrow();
        var effect = entry.getAsJsonArray("effects").get(0).getAsJsonObject();
        try {
            effect.addProperty("target","EACH_ENEMY_HERO");
            CardCatalog.install(CardCatalog.parse(json.toString()));
            ok(CardCatalog.description(CardCatalog.entry(spell.id())).contains("each enemy hero"), "each-enemy wording");
            CardEffects.onItem(m,own,spell,Target.NONE);
            ok(m.player(near).health == 26 && m.player(far).health == 26 && m.player(own).health == 30,
                    "each opposing seated hero takes 2, sharing four total damage");
            effect.addProperty("target","EACH_HERO");
            CardCatalog.install(CardCatalog.parse(json.toString()));
            CardEffects.onItem(m,own,spell,Target.NONE);
            ok(m.player(own).health == 26 && m.player(partner).health == 26 && m.player(near).health == 22,
                    "each hero hits four distinct heads, including the caster");
            effect.addProperty("target","EACH_ENEMY_HERO");
            effect.addProperty("action","FREEZE");
            CardCatalog.install(CardCatalog.parse(json.toString()));
            m.player(near).frozen = false;
            CardEffects.onItem(m,own,spell,Target.NONE);
            ok(m.player(near).frozen && m.player(far).frozen && !m.player(partner).frozen, "each rival gets their own status");
            effect.addProperty("action","WITHER");
            boolean rejected=false;
            try { CardCatalog.parse(json.toString()); } catch (IllegalArgumentException expected) { rejected=true; }
            ok(rejected,"mob-only effect cannot target every hero");
        } finally { CardCatalog.install(initial); }
    }
    private TeamTargetingChecks() {}
}
