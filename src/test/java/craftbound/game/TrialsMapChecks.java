package craftbound.game;

import com.google.gson.JsonObject;
import java.util.*;

/** Route topology, persisted decisions and mystery disclosure checks. */
final class TrialsMapChecks {
    public static void main(String[] args) {
        craftbound.test.TestCatalogue.install();
        run();
    }
    static void run() {
        seededCrossroads();
        variedCrossroads();
        authoredSizes();
        authoredCompatibility();
        upgradeOnlyOriginalDefaults();
        eventDisclosure();
        validation();
        System.out.println("PASS: Trials forced corridors, seeded crossroads, single reachable boss, save compatibility and hidden events.");
    }

    private static void seededCrossroads() {
        var rules = legacyRules(TrialsAct.BRANCHING);
        var distinctRoutes = new HashSet<String>();
        for (int seed = 0; seed < 250; seed++) {
            var act = new TrialsAct(seed, rules);
            check(act.floors == 13, "default route has thirteen floors");
            check(act.rooms.equals(new TrialsAct(seed, rules).rooms), "seed reproduces contents and geometry");
            List<TrialsAct.Room> bosses = act.rooms.stream().filter(n -> n.kind() == TrialsAct.Kind.BOSS).toList();
            check(bosses.size() == 1 && bosses.getFirst().row() == 12, "one final guardian");
            var events = act.rooms.stream().filter(n -> n.kind() == TrialsAct.Kind.EVENT).map(TrialsAct.Room::event).toList();
            check(new HashSet<>(events).size() == events.size(), "default mystery rooms do not repeat an event");
            var reachable = new HashSet<>(act.next(List.of()));
            int previousJunction = -1;
            int junctions = 0;
            StringBuilder signature = new StringBuilder();
            double previousY = -1;
            for (int row = 0; row < act.floors; row++) {
                int floor = row;
                var nodes = act.rooms.stream().filter(n -> n.row() == floor).toList();
                check(!nodes.isEmpty(), "no skipped floors");
                double y = nodes.getFirst().mapY();
                check(y > previousY && y <= 1, "floors remain vertically ordered");
                previousY = y;
                if (nodes.size() == 1) {
                    int corridorLength = row - previousJunction - 1;
                    check(corridorLength >= 2 && corridorLength <= 3, "two or three forced rooms before a junction");
                    previousJunction = row;
                    junctions++;
                }
                signature.append(nodes.size());
                double previousX = -1;
                for (var node : nodes) {
                    check(reachable.contains(node.id()), "every generated node has an incoming path");
                    check(node.mapX() > previousX && node.mapX() >= 0 && node.mapX() <= 1, "horizontal lane order");
                    previousX = node.mapX();
                    for (String exit : node.next()) {
                        var target = act.byId.get(exit);
                        check(target != null && target.row() == row + 1, "travel only to the next connected floor");
                        if (nodes.size() > 1) {
                            check(node.next().size() == 1, "corridor commits the next step");
                            long targetWidth = act.rooms.stream().filter(n -> n.row() == target.row()).count();
                            check(targetWidth == 1 || node.lane() == target.lane(), "no jumps between parallel corridors");
                        }
                    }
                    reachable.addAll(node.next());
                }
            }
            check(previousY == 1 && act.rooms.getFirst().mapY() == 0, "geometry anchors entrance and boss");
            check(junctions >= 3, "several meaningful crossroads");
            // Check every exit can eventually reach the sole boss, not just one lucky route.
            Set<String> leadsToBoss = new HashSet<>(List.of(bosses.getFirst().id()));
            for (int i = act.rooms.size() - 2; i >= 0; i--) {
                var node = act.rooms.get(i);
                check(!node.next().isEmpty() && leadsToBoss.containsAll(node.next()), "every choice reaches final boss");
                leadsToBoss.add(node.id());
            }
            var fightCounts = new HashMap<String, Integer>();
            for (int i = act.rooms.size() - 1; i >= 0; i--) {
                var node = act.rooms.get(i);
                Set<Integer> remainingFights = new HashSet<>();
                for (String next : node.next()) remainingFights.add(fightCounts.get(next));
                check(remainingFights.size() <= 1, "every route keeps the authored combat pacing");
                fightCounts.put(node.id(), (node.combat() ? 1 : 0) + (remainingFights.isEmpty() ? 0 : remainingFights.iterator().next()));
            }
            for (String entrance : act.next(List.of())) check(fightCounts.get(entrance) == 7, "each complete default route has seven fights");
            distinctRoutes.add(signature.toString());
        }
        check(distinctRoutes.size() > 8, "seeds vary corridor widths and junction floors");

        // Travel authority must reject a same-floor alternative that has no connecting edge.
        var run = TrialsRun.create(91);
        run.phase = TrialsRun.Phase.MAP;
        run.choices = new ArrayList<>(run.act().next(run.visited));
        check(run.pick(0), "enter first room");
        run.startBattle("minecraft:overworld", 0);
        check(run.finish(run.battleKey(), true, 20) && run.leaveChoice(), "complete first room");
        check(run.phase == TrialsRun.Phase.MAP, "first fight returns to map");
        var exits = run.room().next();
        check(exits.size() == 1 && run.choices.equals(exits), "only committed next room is offered");
        check(!run.pick(1), "cannot choose an unconnected lane by index");
    }

    private record Edge(TrialsAct.Room from, TrialsAct.Room to) {}
    private record Range(int shortest, int longest, int leastFights, int mostFights) {}
    private static void variedCrossroads() {
        var rules = TrialsRules.snapshot();
        boolean fourBranches = false, staggered = false, skipsRoom = false;
        var layouts = new HashSet<String>();
        for (int seed = 0; seed < 250; seed++) {
            var act = new TrialsAct(seed, rules);
            check(act.rooms.equals(new TrialsAct(seed, rules).rooms), "V2 seed reproduces rooms, geometry and choices");
            check(act.floors == 13 && act.rooms.getLast().kind() == TrialsAct.Kind.BOSS
                    && act.rooms.stream().filter(n -> n.kind() == TrialsAct.Kind.BOSS).count() == 1,
                    "V2 converges on a single thirteenth-floor boss");
            var reached = new HashSet<>(act.next(List.of()));
            var incoming = new HashMap<String, Integer>();
            var edges = new ArrayList<Edge>();
            for (var node : act.rooms) for (String id : node.next()) incoming.merge(id, 1, Integer::sum);
            for (var node : act.rooms) {
                check(reached.contains(node.id()), "every V2 room is reachable from an entrance");
                check(node.lane() >= 0 && node.lane() < 4 && node.next().size() <= 4, "up to four separate branches");
                check(node.mapX() >= 0 && node.mapX() <= 1 && node.mapY() >= 0 && node.mapY() <= 1, "V2 positions stay in map");
                fourBranches |= node.next().size() == 4 || node.row() == 0 && act.next(List.of()).size() == 4;
                for (String id : node.next()) {
                    var next = act.byId.get(id);
                    check(next != null && next.row() > node.row() && next.mapY() > node.mapY(), "routes always progress toward boss");
                    check(node.lane() == next.lane() || node.next().size() > 1 || incoming.get(id) > 1,
                            "lane changes occur only at a crossroads or merge");
                    skipsRoom |= next.row() > node.row() + 1;
                    edges.add(new Edge(node, next));
                }
                reached.addAll(node.next());
            }
            for (int a = 0; a < act.rooms.size(); a++) for (int b = a + 1; b < act.rooms.size(); b++) {
                var left = act.rooms.get(a); var right = act.rooms.get(b);
                if (left.row() == right.row()) {
                    check(Math.abs(left.mapX() - right.mapX()) > 0.15, "parallel rooms have clear horizontal separation");
                    staggered |= Math.abs(left.mapY() - right.mapY()) > 0.001;
                }
            }
            for (int a = 0; a < edges.size(); a++) for (int b = a + 1; b < edges.size(); b++) {
                var x = edges.get(a); var y = edges.get(b);
                if (x.from.id().equals(y.from.id()) || x.to.id().equals(y.to.id())
                        || x.from.id().equals(y.to.id()) || x.to.id().equals(y.from.id())) continue;
                check(!crosses(x, y), "route lines never cross unrelated branches");
            }
            var remaining = new HashMap<String, Range>();
            for (int i = act.rooms.size() - 1; i >= 0; i--) {
                var node = act.rooms.get(i);
                if (node.next().isEmpty()) {
                    check(node.kind() == TrialsAct.Kind.BOSS, "no dead ends except final boss");
                    remaining.put(node.id(), new Range(1, 1, 1, 1));
                } else {
                    int shortPath = Integer.MAX_VALUE, longPath = 0, fewFights = Integer.MAX_VALUE, manyFights = 0;
                    for (String id : node.next()) {
                        Range tail = remaining.get(id);
                        check(tail != null, "every branch leads to the boss");
                        shortPath = Math.min(shortPath, tail.shortest); longPath = Math.max(longPath, tail.longest);
                        fewFights = Math.min(fewFights, tail.leastFights); manyFights = Math.max(manyFights, tail.mostFights);
                    }
                    int fight = node.combat() ? 1 : 0;
                    remaining.put(node.id(), new Range(shortPath + 1, longPath + 1, fewFights + fight, manyFights + fight));
                }
            }
            int shortest = 99, longest = 0;
            for (String start : act.next(List.of())) {
                Range r = remaining.get(start);
                shortest = Math.min(shortest, r.shortest); longest = Math.max(longest, r.longest);
                check(r.leastFights >= 4 && r.mostFights <= 7, "all short and long routes keep meaningful combat pacing");
            }
            check(shortest >= 9 && longest == 13 && shortest < longest, "players can choose speed or extra preparation");
            var eventIds = act.rooms.stream().filter(n -> n.kind() == TrialsAct.Kind.EVENT).map(TrialsAct.Room::event).toList();
            check(new HashSet<>(eventIds).size() == eventIds.size(), "bundled mystery events do not repeat");
            layouts.add(act.rooms.stream().map(n -> n.id() + n.next()).toList().toString());
        }
        check(fourBranches && staggered && skipsRoom && layouts.size() > 100,
                "seeds include four-way choices, staggered nodes and genuinely different route lengths");
    }

    private static double side(TrialsAct.Room a, TrialsAct.Room b, TrialsAct.Room c) {
        return (b.mapX() - a.mapX()) * (c.mapY() - a.mapY())
                - (b.mapY() - a.mapY()) * (c.mapX() - a.mapX());
    }
    private static boolean crosses(Edge a, Edge b) {
        return side(a.from, a.to, b.from) * side(a.from, a.to, b.to) < -1e-12
                && side(b.from, b.to, a.from) * side(b.from, b.to, a.to) < -1e-12;
    }

    private static JsonObject legacyRules(String topology) {
        var root = TrialsRules.snapshot();
        var act = new JsonObject();
        act.addProperty("name", "The Copper Depths");
        if (topology != null) act.addProperty("topology", topology);
        var floors = new com.google.gson.JsonArray();
        for (int row = 0; row < 13; row++) {
            var floor = new JsonObject();
            floor.add("kinds", com.google.gson.JsonParser.parseString(row == 12 ? "[\"BOSS\"]"
                    : row % 2 == 1 ? "[\"SHOP\",\"REST\",\"EVENT\"]"
                    : row == 0 ? "[\"FIGHT\",\"FIGHT\",\"FIGHT\"]" : "[\"FIGHT\",\"FIGHT\",\"ELITE\"]"));
            floor.addProperty("shuffle", row != 12);
            floor.add("edges", com.google.gson.JsonParser.parseString(row == 12 ? "[[]]"
                    : row == 11 ? "[[0],[0],[0]]" : "[[0,1],[0,1,2],[1,2]]"));
            floors.add(floor);
        }
        act.add("floors", floors);
        root.add("act", act);
        return root;
    }

    private static void authoredSizes() {
        for (int count = 7; count <= 30; count++) {
            var rules = TrialsRules.snapshot();
            var stages = TrialsRules.list(rules.getAsJsonObject("act"), "floors");
            var floors = new com.google.gson.JsonArray();
            for (int row = 0; row < count - 1; row++) {
                var floor = stages.get(row % 2).deepCopy();
                if (row == count - 2) {
                    var edges = new com.google.gson.JsonArray();
                    for (int lane = 0; lane < floor.getAsJsonArray("kinds").size(); lane++)
                        edges.add(com.google.gson.JsonParser.parseString("[0]"));
                    floor.add("edges", edges);
                }
                floors.add(floor);
            }
            floors.add(stages.getLast().deepCopy());
            rules.getAsJsonObject("act").add("floors", floors);
            var errors = new ArrayList<String>();
            TrialsRules.validate(rules, errors);
            check(errors.isEmpty(), "custom floor count validates " + count);
            for (int seed = 0; seed < 10; seed++) {
                var act = new TrialsAct(seed, rules);
                check(act.floors == count && act.rooms.getLast().kind() == TrialsAct.Kind.BOSS, "custom size generates a boss route");
            }
        }
    }

    private static void authoredCompatibility() {
        var rules = legacyRules(null);
        var stages = TrialsRules.list(rules.getAsJsonObject("act"), "floors");
        var before = new TrialsAct(464, rules);
        for (var node : before.rooms) {
            var expected = stages.get(node.row()).getAsJsonArray("edges").get(node.lane()).getAsJsonArray()
                    .asList().stream().map(e -> (node.row() + 1) + ":" + e.getAsInt()).toList();
            check(node.next().equals(expected), "legacy snapshots keep all authored exits");
        }
        var run = TrialsRun.create(464);
        run.rules = rules;
        run.visited = new ArrayList<>(List.of("0:2"));
        run.phase = TrialsRun.Phase.MAP;
        run.choices = new ArrayList<>(run.act().next(run.visited));
        var restored = TrialsRun.JSON.fromJson(TrialsRun.JSON.toJson(run), TrialsRun.class);
        check(!restored.migrate(), "existing act snapshot does not migrate to new default");
        check(restored.act().rooms.equals(before.rooms) && restored.choices.equals(run.choices), "saved paths and offers survive reload");

        var explicit = rules.deepCopy();
        explicit.getAsJsonObject("act").addProperty("topology", "AUTHORED");
        check(before.rooms.equals(new TrialsAct(464, explicit).rooms), "explicit authored mode matches previous format");
        var generated = TrialsRun.create(17);
        generated.phase = TrialsRun.Phase.MAP;
        generated.choices = new ArrayList<>(generated.act().next(generated.visited));
        generated.pick(0);
        var copy = TrialsRun.JSON.fromJson(TrialsRun.JSON.toJson(generated), TrialsRun.class);
        check(copy.act().rooms.equals(generated.act().rooms) && copy.visited.equals(generated.visited), "branching graph and selected room persist");
    }

    private static void upgradeOnlyOriginalDefaults() {
        var original = legacyRules(null);
        var preserved = original.deepCopy();
        preserved.remove("act");
        check(TrialsContent.upgradeDefaultAct(original), "untouched old bundled act upgrades");
        check(original.getAsJsonObject("act").get("topology").getAsString().equals(TrialsAct.VARIED), "migration selects branching graph");
        check(!TrialsContent.upgradeDefaultAct(original), "migration is idempotent");
        original.remove("act");
        check(original.equals(preserved), "migration changes no run settings or other sections");
        var v1 = legacyRules(TrialsAct.BRANCHING);
        check(TrialsContent.upgradeDefaultAct(v1)
                && v1.getAsJsonObject("act").get("topology").getAsString().equals(TrialsAct.VARIED),
                "untouched V1 default upgrades to varied paths");
        for (String modification : List.of("title", "edge", "mode")) {
            var edited = legacyRules(null);
            var act = edited.getAsJsonObject("act");
            act.remove("topology");
            switch (modification) {
                case "title" -> act.addProperty("name", "My custom dungeon");
                case "edge" -> act.getAsJsonArray("floors").get(0).getAsJsonObject()
                        .getAsJsonArray("edges").get(0).getAsJsonArray().remove(1);
                case "mode" -> act.addProperty("topology", "AUTHORED");
            }
            var before = edited.deepCopy();
            check(!TrialsContent.upgradeDefaultAct(edited) && edited.equals(before), "preserve user-edited " + modification);
        }
    }

    private static void eventDisclosure() {
        TrialsRun run = null;
        for (int seed = 0; seed < 64; seed++) {
            var candidate = TrialsRun.create(seed);
            if (candidate.act().rooms.stream().anyMatch(n -> n.kind() == TrialsAct.Kind.EVENT)) { run = candidate; break; }
        }
        check(run != null, "a mystery can occur on generated routes");
        var hidden = run.act().rooms.stream().filter(n -> n.kind() == TrialsAct.Kind.EVENT).findFirst().orElseThrow();
        var map = run.view("").getAsJsonArray("map");
        for (var entry : map) {
            var node = entry.getAsJsonObject();
            if (!node.get("kind").getAsString().equals("EVENT")) continue;
            check(node.get("title").getAsString().equals("Unknown encounter")
                    && node.get("description").getAsString().equals("Enter this room to discover what awaits.")
                    && !node.get("revealed").getAsBoolean() && !node.has("event"), "payload never discloses unseen event identity");
        }
        // Reveal uses a committed visit, independently of whether the event screen is still open.
        run.visited.add(hidden.id());
        run.phase = TrialsRun.Phase.EVENT;
        run.choices = new ArrayList<>(TrialsRules.ids(run.options(run.phase)));
        check(event(run, hidden.id()).get("title").getAsString().equals(hidden.title()), "entered event has its authored title");
        run.leaveChoice();
        var shown = event(run, hidden.id());
        check(shown.get("revealed").getAsBoolean() && shown.get("description").getAsString().equals(hidden.description()),
                "visited event stays visible after leaving");
    }

    private static JsonObject event(TrialsRun run, String id) {
        return run.view("").getAsJsonArray("map").asList().stream().map(e -> e.getAsJsonObject())
                .filter(n -> n.get("id").getAsString().equals(id)).findFirst().orElseThrow();
    }

    private static void validation() {
        var root = TrialsRules.snapshot();
        var errors = new ArrayList<String>();
        TrialsRules.validate(root, errors);
        check(errors.isEmpty(), "bundled branching act validates");
        root.getAsJsonObject("act").addProperty("topology", "unknown");
        TrialsRules.validate(root, errors);
        check(errors.stream().anyMatch(e -> e.contains("topology")), "unknown topology rejected");
        root = TrialsRules.snapshot();
        var finalFloor = TrialsRules.list(root.getAsJsonObject("act"), "floors").getLast();
        finalFloor.getAsJsonArray("kinds").add("BOSS");
        finalFloor.getAsJsonArray("edges").add(new com.google.gson.JsonArray());
        errors.clear();
        TrialsRules.validate(root, errors);
        check(errors.stream().anyMatch(e -> e.contains("one final boss")), "generated act cannot end in several bosses");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError("Trials map: " + message);
    }
    private TrialsMapChecks() {}
}
