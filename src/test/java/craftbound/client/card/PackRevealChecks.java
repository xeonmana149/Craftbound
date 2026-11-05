package craftbound.client.card;

import craftbound.card.CardRarity;

public final class PackRevealChecks {
    public static void run() {
        var rarities = new CardRarity[]{CardRarity.COMMON, CardRarity.RARE, CardRarity.EPIC, CardRarity.LEGENDARY, CardRarity.LEGENDARY};
        var reveal = new PackReveal(rarities, new boolean[]{false, false, false, false, true});
        require(reveal.active(0) == -1 && reveal.anyUnqueued() && !reveal.allSettled(0), "Fresh pack must stay face down");
        reveal.queue(3, 0); reveal.queue(3, 0.1F); reveal.queueAll(0.1F);
        require(reveal.active(0.1F) == 3, "Reveal all must not interrupt the clicked Legendary");
        require(reveal.flip(3, reveal.charge(3) - 0.01F) == 0, "Keep face hidden during anticipation");
        require(Math.abs(reveal.burstAge(3, reveal.charge(3) + PackReveal.FLIP / 2)) < 0.00001F, "Burst must coincide with face reveal");
        int previous = -1, transitions = 0;
        for (int tick = 0; tick < 3000; tick++) {
            float now = tick / 100F; int simultaneous = 0, active = reveal.active(now);
            for (int i = 0; i < rarities.length; i++) {
                if (reveal.age(i, now) >= 0 && !reveal.settled(i, now)) simultaneous++;
                require(reveal.focus(i, now) >= 0 && reveal.focus(i, now) <= 1, "Focus bounds");
            }
            require(simultaneous <= 1, "Celebrations must never overlap");
            if (active >= 0 && active != previous) { transitions++; previous = active; }
        }
        require(transitions == 5 && reveal.allSettled(30), "Every queued pull must finish once");
        require(reveal.charge(4) > reveal.charge(3) && reveal.charge(3) > reveal.charge(2), "Rarity and Enchanted anticipation");
        var skipped = new PackReveal(rarities, new boolean[5]);
        skipped.queue(0,0); skipped.finishActive(.05F); skipped.queue(1,.05F);
        require(skipped.settled(0,.05F)&&skipped.active(.05F)==1&&!skipped.queued(3),"Rapid clicks must advance ordinary pulls without opening untouched cards");
        var legendarySkip = new PackReveal(rarities,new boolean[5]);
        legendarySkip.queue(3,0);legendarySkip.queue(2,0);legendarySkip.finishActive(.1F);
        require(legendarySkip.settled(3,.1F)&&legendarySkip.age(2,.11F)>=0&&!legendarySkip.queued(0),"Second-click skip brings queued reveal forward without revealing the entire pack");
        skipped.queueAll(0); skipped.skip(0);
        require(skipped.allSettled(0) && skipped.active(0) == -1 && !skipped.anyUnqueued(), "Skip must finish safely even at time zero");
        System.out.println("PASS: pack reveal queue, anticipation, face/burst timing, no overlapping celebrations and immediate skip.");
    }
    private static void require(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
}
