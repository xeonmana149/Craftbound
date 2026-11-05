package craftbound.client.card;

import java.util.List;

// Standalone: javac ShimmerMesh.java ShimmerMeshChecks.java; java craftbound.client.card.ShimmerMeshChecks
public final class ShimmerMeshChecks {
    private static int checks;
    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        int[][] sizes = {{120,168,20,112}, {30,37,7,69}, {58,80,6,112}, {360,504,60,112}, {1,1,1,112}};
        int peakVertices = 0;
        for (int[] size : sizes) {
            int w = size[0], h = size[1], band = size[2], alpha = size[3];
            for (int step = 0; step <= 1600; step++) {
                float diagonal = -band + (int)(step / 1600.0F * (w + h + band * 2));
                List<ShimmerMesh.Vertex> mesh = ShimmerMesh.build(w, h, diagonal, band, alpha);
                peakVertices = Math.max(peakVertices, mesh.size());
                check(mesh.size() <= 32 && mesh.size() % 4 == 0, "constant vertex budget");
                double area = 0;
                for (int i = 0; i < mesh.size(); i += 4) {
                    var a = mesh.get(i); var b = mesh.get(i+1); var c = mesh.get(i+2);
                    check(c.equals(mesh.get(i+3)), "triangle encoded as a GUI quad");
                    double cross = (b.x()-a.x())*(c.y()-a.y())-(b.y()-a.y())*(c.x()-a.x());
                    check(cross < 0, "same winding as vanilla GUI rectangles");
                    area -= cross / 2;
                    float middle = (a.x()+a.y()+b.x()+b.y()+c.x()+c.y())/3;
                    double expectedAlpha = alpha * Math.max(0, 1 - Math.abs(middle-diagonal)/band);
                    check(Math.abs((a.alpha()+b.alpha()+c.alpha())/3.0 - expectedAlpha) < 0.6,
                            "gradient must interpolate correctly without crossing the peak");
                    for (int j = 0; j < 4; j++) {
                        var v = mesh.get(i+j);
                        check(v.x() >= -0.001 && v.x() <= w+0.001 && v.y() >= -0.001 && v.y() <= h+0.001,
                                "shimmer escaped the card");
                        check(v.alpha() >= 0 && v.alpha() <= alpha, "opacity range");
                    }
                }
                double expected = below(diagonal+band,w,h)-below(diagonal-band,w,h);
                check(Math.abs(area-expected) < 0.1, "clipped coverage has gaps/overlap: "+area+" vs "+expected);
            }
        }
        check(ShimmerMesh.build(120,168,100,0,112).isEmpty(), "zero width band");
        check(ShimmerMesh.build(0,168,100,20,112).isEmpty(), "empty card");
        check(ShimmerMesh.build(120,168,100,20,0).isEmpty(), "transparent band");
        int peakTiles = 0;
        for (int d = -20; d <= 308; d++) {
            int tiles = 0;
            for (int row = 0; row < 168; row += 2) {
                for (int offset = -20; offset <= 20; offset += 2) {
                    int column = d-row+offset;
                    if (Math.max(column,0) < Math.min(column+2,120)) tiles++;
                }
            }
            peakTiles = Math.max(peakTiles, tiles);
        }
        System.out.println(checks+" shimmer geometry checks passed; peak "+peakVertices
                +" vertices/card; old full-card peak "+peakTiles+" GUI submissions -> 1.");
    }

    private static double below(double t, int width, int height) {
        return (square(t)-square(t-width)-square(t-height)+square(t-width-height))/2;
    }
    private static double square(double value) { return value <= 0 ? 0 : value*value; }
}
