package craftbound.client.card;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.joml.Matrix3x2f;

// A light card following the cursor: a little positional lag, a springy roll, and a small paper-like flex.
// Shared by full card views and vanilla inventory/chest cursors; gameplay still uses the real mouse position.
public final class CardDragMotion {
    private long lastTime;
    private float x, y, velocityX, velocityY, lastMouseX, lastMouseY;
    private float roll, rollVelocity, bend, bendVelocity;

    public void reset(float mouseX, float mouseY) {
        reset(mouseX, mouseY, System.nanoTime());
    }

    private void reset(float mouseX, float mouseY, long time) {
        this.x = this.lastMouseX = mouseX;
        this.y = this.lastMouseY = mouseY;
        this.velocityX = this.velocityY = this.roll = this.rollVelocity = this.bend = this.bendVelocity = 0;
        this.lastTime = time;
    }

    void update(float mouseX, float mouseY, float width, long time) {
        float dt = (time - this.lastTime) / 1_000_000_000.0F;
        if (this.lastTime == 0 || dt > 0.18F || dt < 0) {
            reset(mouseX, mouseY, time);
            return;
        }
        if (dt < 0.0001F) return;
        float pointerX = clamp((mouseX - this.lastMouseX) / dt, -2500, 2500);
        float pointerY = clamp((mouseY - this.lastMouseY) / dt, -2500, 2500);
        float targetRoll = clamp(pointerX * 0.00010F, -0.18F, 0.18F);
        float targetBend = clamp(pointerX * 0.000025F + pointerY * 0.000015F, -0.07F, 0.07F);
        int steps = Math.max(1, (int) Math.ceil(dt * 120));
        float step = dt / steps;
        for (int i = 0; i < steps; i++) {
            this.velocityX += ((mouseX - this.x) * 420 - this.velocityX * 32) * step;
            this.velocityY += ((mouseY - this.y) * 420 - this.velocityY * 32) * step;
            this.x += this.velocityX * step;
            this.y += this.velocityY * step;
            this.rollVelocity += ((targetRoll - this.roll) * 220 - this.rollVelocity * 18) * step;
            this.bendVelocity += ((targetBend - this.bend) * 180 - this.bendVelocity * 17) * step;
            this.roll += this.rollVelocity * step;
            this.bend += this.bendVelocity * step;
        }
        float lag = Math.max(2, Math.min(10, width * 0.08F));
        this.x = mouseX + clamp(this.x - mouseX, -lag, lag);
        this.y = mouseY + clamp(this.y - mouseY, -lag, lag);
        this.roll = clamp(this.roll, -0.24F, 0.24F);
        this.bend = clamp(this.bend, -0.10F, 0.10F);
        this.lastMouseX = mouseX;
        this.lastMouseY = mouseY;
        this.lastTime = time;
    }

    // Draw centred at (0, 0), then pop the pose. This keeps all parts of the card on the same moving plane.
    public void begin(GuiGraphicsExtractor g, float mouseX, float mouseY, float width) {
        update(mouseX, mouseY, width, System.nanoTime());
        g.pose().pushMatrix();
        g.pose().translate(this.x, this.y);
        g.pose().rotate(this.roll);
        g.pose().mul(new Matrix3x2f(1, this.bend * 0.35F, this.bend, 1, 0, 0));
        g.pose().scale(1 - Math.abs(this.roll) * 0.15F, 1 - Math.abs(this.bend) * 0.3F);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
