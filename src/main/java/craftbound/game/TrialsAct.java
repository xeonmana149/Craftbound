package craftbound.game;

import com.google.gson.*;
import java.util.*;

/** Rooms and exits are deterministic from the rules snapshot stored in each run. */
final class TrialsAct {
    static final String BRANCHING = "BRANCHING_V1";
    static final String VARIED = "BRANCHING_V2";
    enum Kind { FIGHT, ELITE, BOSS, SHOP, REST, EVENT }
    record Room(int row, int lane, Kind kind, String event, String opponent, String title,
                String description, List<String> next, double mapX, double mapY) {
        String id() { return row + ":" + lane; }
        boolean combat() { return kind == Kind.FIGHT || kind == Kind.ELITE || kind == Kind.BOSS; }
    }
    private record RouteFloor(int width, List<List<Integer>> exits, List<Double> x) {}
    private record RouteNode(int row, int lane, List<String> exits, double x, double y) {
        String id() { return row + ":" + lane; }
    }

    final String name;
    final List<Room> rooms;
    final Map<String, Room> byId;
    final int floors, battles;

    TrialsAct(long seed, JsonObject content) {
        var definition = content.getAsJsonObject("act");
        var stages = TrialsRules.list(definition, "floors");
        var shop = content.getAsJsonObject("shop");
        name = definition.get("name").getAsString();
        floors = stages.size();
        String topology = TrialsRules.s(definition, "topology", "AUTHORED");
        boolean branching = BRANCHING.equals(topology), varied = VARIED.equals(topology);
        List<RouteFloor> route = branching ? branchingRoute(seed, stages) : List.of();
        double[] heights = floorHeights(seed, floors);
        Map<Integer, List<RouteNode>> variedRoute = varied ? variedRoute(seed, stages, heights) : Map.of();
        var events = new ArrayList<>(TrialsRules.list(content, "events"));
        // Keep this random sequence unchanged for pre-branching rules snapshots.
        var random = new Random(seed ^ 0x71F2DA4B586C19E3L);
        Collections.shuffle(events, random);
        var generated = new ArrayList<Room>();
        int eventCount = 0, combatFloors = 0;
        for (int row = 0; row < floors; row++) {
            var floor = stages.get(row);
            var kinds = floor.getAsJsonArray("kinds");
            var templates = new ArrayList<Integer>();
            for (int i = 0; i < kinds.size(); i++) templates.add(i);
            if (floor.has("shuffle") && floor.get("shuffle").getAsBoolean()) Collections.shuffle(templates, random);
            RouteFloor path = branching ? route.get(row) : null;
            List<RouteNode> nodes = varied ? variedRoute.getOrDefault(row, List.of()) : List.of();
            int width = varied ? nodes.size() : branching ? path.width() : templates.size();
            boolean hasCombat = false;
            for (int index = 0; index < width; index++) {
                RouteNode node = varied ? nodes.get(index) : null;
                int lane = varied ? node.lane() : index;
                int template = templates.get(lane % templates.size());
                var kind = Kind.valueOf(kinds.get(template).getAsString());
                String event = override(floor, "events", template, "");
                String title, description;
                switch (kind) {
                    case EVENT -> {
                        var entry = event.isEmpty() ? events.get(eventCount++ % events.size()) : TrialsRules.find(events, event);
                        event = entry.get("id").getAsString();
                        title = entry.get("title").getAsString();
                        description = TrialsRules.s(entry, "description", "");
                    }
                    case REST -> {
                        title = "Lantern Camp";
                        description = "Rest, train a card or take a boon.";
                    }
                    case SHOP -> {
                        title = TrialsRules.s(shop, "title", "Village Outfitter");
                        description = TrialsRules.s(shop, "description", "Trade your Emeralds.");
                    }
                    default -> {
                        hasCombat = true;
                        title = kind == Kind.BOSS ? "Chamber guardian" : kind == Kind.ELITE ? "Elite challenger" : "Challenger";
                        description = kind == Kind.BOSS ? "Defeat the guardian to clear the act."
                                : kind == Kind.ELITE ? "Fight for Emeralds, a charm and a card choice." : "Fight for Emeralds and a card choice.";
                    }
                }
                var links = new ArrayList<String>();
                if (varied) links.addAll(node.exits());
                else if (branching) {
                    for (int target : path.exits().get(lane)) links.add((row + 1) + ":" + target);
                } else {
                    for (var target : floor.getAsJsonArray("edges").get(lane).getAsJsonArray())
                        links.add((row + 1) + ":" + target.getAsInt());
                }
                double x = varied ? node.x() : branching ? path.x().get(lane) : width == 1 ? 0.5 : 0.14 + 0.72 * lane / (width - 1);
                double y = varied ? node.y() : heights[row];
                generated.add(new Room(row, lane, kind, event, override(floor, "opponents", template, ""),
                        override(floor, "titles", template, title), override(floor, "descriptions", template, description),
                        List.copyOf(links), x, y));
            }
            if (hasCombat) combatFloors++;
        }
        battles = combatFloors;
        rooms = List.copyOf(generated);
        var index = new LinkedHashMap<String, Room>();
        for (var room : rooms) index.put(room.id(), room);
        byId = Map.copyOf(index);
    }

    /**
     * A span has two or three independent corridor rooms followed by one shared junction.
     * Every fork and merge happens at a junction, so parallel corridors never cross.
     * This version is part of saved run rules; a future topology change needs a new name.
     */
    private static List<RouteFloor> branchingRoute(long seed, List<JsonObject> stages) {
        var random = new Random(seed ^ 0x46BA1C591E082F3DL);
        int total = stages.size();
        int[] widths = new int[total];
        double[] centers = new double[total];
        int row = 0;
        while (row < total) {
            int remaining = total - row;
            var spans = new ArrayList<Integer>();
            for (int span : List.of(3, 4)) {
                int rest = remaining - span;
                if (rest == 0 || rest >= 3 && rest != 5) spans.add(span);
            }
            int span = spans.get(random.nextInt(spans.size()));
            int corridorWidth = 2 + random.nextInt(2);
            for (int offset = 0; offset < span - 1; offset++)
                corridorWidth = Math.min(corridorWidth, stages.get(row + offset).getAsJsonArray("kinds").size());
            double center = 0.46 + random.nextDouble() * 0.08;
            for (int offset = 0; offset < span - 1; offset++) {
                widths[row + offset] = corridorWidth;
                centers[row + offset] = center + (random.nextDouble() - 0.5) * 0.06;
            }
            widths[row + span - 1] = 1;
            centers[row + span - 1] = row + span == total ? 0.5 : 0.34 + random.nextDouble() * 0.32;
            row += span;
        }
        var route = new ArrayList<RouteFloor>();
        for (int r = 0; r < total; r++) {
            var exits = new ArrayList<List<Integer>>();
            var positions = new ArrayList<Double>();
            for (int lane = 0; lane < widths[r]; lane++) {
                var next = new ArrayList<Integer>();
                if (r + 1 < total) {
                    if (widths[r + 1] == 1) next.add(0);
                    else if (widths[r] == 1) {
                        for (int target = 0; target < widths[r + 1]; target++) next.add(target);
                    } else next.add(lane);
                }
                exits.add(List.copyOf(next));
                double spread = widths[r] == 3 ? 0.34 : 0.22;
                double x = centers[r];
                if (widths[r] > 1) x += (2.0 * lane / (widths[r] - 1) - 1) * spread + (random.nextDouble() - 0.5) * 0.035;
                positions.add(x);
            }
            route.add(new RouteFloor(widths[r], List.copyOf(exits), List.copyOf(positions)));
        }
        return List.copyOf(route);
    }

    /**
     * V2 keeps first/last corridor nodes in separate lanes while optional middle rooms vary
     * route length. Shared endpoints keep the fan-out and merge lines from crossing paths.
     */
    private static Map<Integer, List<RouteNode>> variedRoute(long seed, List<JsonObject> stages, double[] heights) {
        var random = new Random(seed ^ 0x4A3C7D9F1258B6E0L);
        var route = new TreeMap<Integer, List<RouteNode>>();
        int total = stages.size(), start = 0;
        List<String> fromJunction = null;
        while (start < total) {
            int remaining = total - start;
            var spans = new ArrayList<Integer>();
            for (int span : List.of(4, 5)) {
                int rest = remaining - span;
                if (rest == 0 || rest >= 3) spans.add(span);
            }
            int span = spans.isEmpty() ? 3 : spans.get(random.nextInt(spans.size()));
            int end = start + span - 1;
            int width = 2 + random.nextInt(3);
            for (int row = start; row < end; row++) width = Math.min(width, stages.get(row).getAsJsonArray("kinds").size());
            var lengths = new ArrayList<Integer>();
            for (int lane = 0; lane < width; lane++) lengths.add(2 + random.nextInt(span - 2));
            lengths.set(0, 2);
            if (width > 1) lengths.set(width - 1, span - 1);
            Collections.shuffle(lengths, random);
            double center = 0.46 + random.nextDouble() * 0.08;
            double spread = width == 4 ? 0.38 : width == 3 ? 0.33 : 0.23;
            for (int lane = 0; lane < width; lane++) {
                var chosenRows = new TreeSet<Integer>();
                chosenRows.add(start);
                chosenRows.add(end - 1);
                var optional = new ArrayList<Integer>();
                for (int row = start + 1; row < end - 1; row++) optional.add(row);
                Collections.shuffle(optional, random);
                for (int row : optional) {
                    if (chosenRows.size() >= lengths.get(lane)) break;
                    chosenRows.add(row);
                }
                var rows = new ArrayList<>(chosenRows);
                if (fromJunction != null) fromJunction.add(start + ":" + lane);
                for (int i = 0; i < rows.size(); i++) {
                    int row = rows.get(i);
                    String next = i + 1 < rows.size() ? rows.get(i + 1) + ":" + lane : end + ":0";
                    double x = width == 1 ? center : center + (2.0 * lane / (width - 1) - 1) * spread;
                    x += (random.nextDouble() - 0.5) * 0.035;
                    double gap = row == 0 ? heights[1] : heights[row] - heights[row - 1];
                    double offset = (random.nextDouble() - 0.5) * 0.4 * gap;
                    double y = row == 0 ? (lane == 0 ? 0 : Math.abs(offset)) : heights[row] + offset;
                    route.computeIfAbsent(row, key -> new ArrayList<>()).add(new RouteNode(row, lane, List.of(next), x, y));
                }
            }
            var exits = new ArrayList<String>();
            double junctionX = end == total - 1 ? 0.5 : 0.34 + random.nextDouble() * 0.32;
            route.computeIfAbsent(end, key -> new ArrayList<>()).add(new RouteNode(end, 0, exits, junctionX, heights[end]));
            fromJunction = exits;
            start += span;
        }
        for (var nodes : route.values()) nodes.sort(Comparator.comparingInt(RouteNode::lane));
        return route;
    }

    private static double[] floorHeights(long seed, int total) {
        var random = new Random(seed ^ 0x19F0A83652CB47E1L);
        double[] heights = new double[total];
        for (int row = 1; row < total; row++) heights[row] = heights[row - 1] + 0.85 + random.nextDouble() * 0.3;
        double full = heights[total - 1];
        for (int row = 1; row < total; row++) heights[row] /= full;
        return heights;
    }

    private String override(JsonObject obj, String key, int i, String fallback) {
        if (!obj.has(key)) return fallback;
        String v = obj.getAsJsonArray(key).get(i).getAsString();
        return v.isBlank() ? fallback : v;
    }
    List<String> next(List<String> visited) {
        return visited.isEmpty() ? rooms.stream().filter(r -> r.row == 0).map(Room::id).toList() : byId.get(visited.getLast()).next();
    }
}
