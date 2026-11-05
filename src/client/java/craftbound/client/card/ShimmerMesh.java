package craftbound.client.card;

import java.util.ArrayList;
import java.util.List;

// Two clipped, linearly shaded polygons. Work is bounded by the rectangle's corners,
// never its pixel dimensions. Kept independent of Minecraft for geometry regression checks.
final class ShimmerMesh {
    record Point(float x, float y) {}
    record Vertex(float x, float y, int alpha) {}

    static List<Vertex> build(int width, int height, float diagonal, int halfWidth, int maxAlpha) {
        if (width <= 0 || height <= 0 || halfWidth <= 0 || maxAlpha <= 0
                || diagonal + halfWidth <= 0 || diagonal - halfWidth >= width + height) {
            return List.of();
        }
        List<Point> rectangle = List.of(new Point(0, 0), new Point(0, height),
                new Point(width, height), new Point(width, 0));
        List<Vertex> vertices = new ArrayList<>(32);
        for (int half = 0; half < 2; half++) {
            float low = half == 0 ? diagonal - halfWidth : diagonal;
            float high = half == 0 ? diagonal : diagonal + halfWidth;
            List<Point> polygon = clip(clip(rectangle, low, true), high, false);
            for (int i = 1; i + 1 < polygon.size(); i++) {
                Point a = polygon.getFirst(), b = polygon.get(i), c = polygon.get(i + 1);
                float cross = (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x);
                if (Math.abs(cross) < 0.0001F) continue;
                // GUI uses quads: repeat the final vertex to encode a triangle.
                vertices.add(vertex(a, diagonal, halfWidth, maxAlpha));
                vertices.add(vertex(b, diagonal, halfWidth, maxAlpha));
                Vertex last = vertex(c, diagonal, halfWidth, maxAlpha);
                vertices.add(last);
                vertices.add(last);
            }
        }
        return vertices;
    }

    private static Vertex vertex(Point p, float diagonal, int halfWidth, int maxAlpha) {
        float strength = Math.max(0, 1 - Math.abs(p.x + p.y - diagonal) / halfWidth);
        return new Vertex(p.x, p.y, Math.round(Math.min(255, maxAlpha) * strength));
    }

    // Sutherland-Hodgman clipping against a diagonal x+y boundary.
    private static List<Point> clip(List<Point> input, float boundary, boolean keepAbove) {
        if (input.isEmpty()) return input;
        List<Point> output = new ArrayList<>(6);
        Point previous = input.getLast();
        float previousDistance = previous.x + previous.y - boundary;
        boolean previousInside = keepAbove ? previousDistance >= 0 : previousDistance <= 0;
        for (Point current : input) {
            float distance = current.x + current.y - boundary;
            boolean inside = keepAbove ? distance >= 0 : distance <= 0;
            if (inside != previousInside) {
                float fraction = previousDistance / (previousDistance - distance);
                output.add(new Point(previous.x + fraction * (current.x - previous.x),
                        previous.y + fraction * (current.y - previous.y)));
            }
            if (inside) output.add(current);
            previous = current;
            previousDistance = distance;
            previousInside = inside;
        }
        return output;
    }
}
