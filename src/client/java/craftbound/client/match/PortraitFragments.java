package craftbound.client.match;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** A small pixel-art Voronoi fracture. Built once per defeat, never once per frame. */
public final class PortraitFragments {
    public static final int GRID = 12;
    public record Patch(int x, int y, int width, int height) {}
    public record Shard(List<Patch> patches, float x, float y, float dx, float dy, float lifetime) {}

    public static List<Shard> create(long seed) {
        Random random = new Random(seed);
        int count = 14;
        int[] sx = new int[count], sy = new int[count];
        boolean[] occupied = new boolean[GRID * GRID];
        for (int i = 0; i < count; i++) {
            int cell;
            do { cell = random.nextInt(occupied.length); } while (occupied[cell]);
            occupied[cell] = true; sx[i] = cell % GRID; sy[i] = cell / GRID;
        }
        int[][] owner = new int[GRID][GRID];
        float[] cx = new float[count], cy = new float[count];
        int[] area = new int[count];
        for (int y = 0; y < GRID; y++) for (int x = 0; x < GRID; x++) {
            int best = 0, distance = Integer.MAX_VALUE;
            for (int i = 0; i < count; i++) {
                int d = (x-sx[i])*(x-sx[i]) + (y-sy[i])*(y-sy[i]);
                if (d < distance) { best = i; distance = d; }
            }
            owner[y][x] = best; cx[best] += x + .5F; cy[best] += y + .5F; area[best]++;
        }
        List<Shard> result = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            List<Patch> patches = new ArrayList<>();
            // Combine identical horizontal runs vertically, limiting repeated portrait submissions.
            for (int y = 0; y < GRID; y++) for (int x = 0; x < GRID;) {
                if (owner[y][x] != i) { x++; continue; }
                int start = x;
                while (x < GRID && owner[y][x] == i) x++;
                int width = x-start, merge = -1;
                for (int p = 0; p < patches.size(); p++) {
                    Patch old = patches.get(p);
                    if (old.x == start && old.width == width && old.y + old.height == y) { merge = p; break; }
                }
                if (merge < 0) patches.add(new Patch(start,y,width,1));
                else { Patch old = patches.get(merge); patches.set(merge,new Patch(start,old.y,width,old.height+1)); }
            }
            float x = cx[i]/area[i]/GRID, y = cy[i]/area[i]/GRID;
            double angle = Math.atan2(y-.5F,x-.5F) + (random.nextFloat()-.5F)*1.1F;
            float distance = .45F + random.nextFloat()*.55F;
            result.add(new Shard(List.copyOf(patches),x,y,(float)Math.cos(angle)*distance,
                    (float)Math.sin(angle)*distance,.72F+random.nextFloat()*.28F));
        }
        return List.copyOf(result);
    }
    private PortraitFragments() {}
}
