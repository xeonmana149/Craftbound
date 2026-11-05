package craftbound.client.match;

import craftbound.board.BoardDesign;
import craftbound.client.card.CardCanvas;

/** Pixel inlays shared by the binder sample, match mat and physical tabletop. */
public final class BoardTrim {
    private static final String[] SPRUCE = {"...#...", "..###..", "...#...", ".#####.", "..###..", "#######", "...#..."};
    private static final String[] AMETHYST = {"...#...", "..#.#..", ".#...#.", "#..#..#", ".#...#.", "..#.#..", "...#..."};
    private static final String[] OCEAN = {"##...##", ".#...#.", ".#.#.#.", ".#####.", "...#...", "...#...", "..###.."};
    public static void draw(CardCanvas canvas, BoardDesign design, int x, int y, int w, int h) {
        if (design.trialReward()) {
            var art = craftbound.Craftbound.id("textures/gui/trials/boards/" + design.id() + ".png");
            if (craftbound.client.card.CardFrames.hasTrialArt(art)) {
                canvas.frameRegion(art,0,0,120,168,x,y,w,h);
                return;
            }
        }
        String[] motif = switch(design) {
            case SPRUCE -> SPRUCE;
            case AMETHYST -> AMETHYST;
            case PRISMARINE -> OCEAN;
            default -> design.trialReward() ? AMETHYST : null;
        };
        if (motif == null || w < 22 || h < 22) return;
        int pixel = Math.clamp(Math.min(w,h)/95, 1, 3);
        for (int side=0; side<2; side++) {
            for (int end=0; end<2; end++) {
                int left = side==0 ? x+3 : x+w-3-7*pixel;
                int top = end==0 ? y+3 : y+h-3-7*pixel;
                for (int row=0; row<7; row++) for (int col=0; col<7; col++) {
                    if (motif[row].charAt(col)=='#') {
                        int px=left+col*pixel, py=top+row*pixel;
                        canvas.fill(px,py,px+pixel,py+pixel,design.accent);
                    }
                }
            }
        }
    }
    private BoardTrim() {}
}
