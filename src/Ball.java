import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/**
 * The basketball. Like the players, it has a floor position (x, y) and a height z,
 * so it can arc toward the rim, bounce off the rim / backboard / floor and roll.
 */
public class Ball {

	public static final double GRAVITY = 0.4;
	public static final int    RADIUS  = 22;

	// states
	public static final int HELD  = 0;
	public static final int LOOSE = 1;
	public static final int SHOT  = 2;
	public static final int NET   = 3;     // dropping through the net after a make

	double x = 855, y = 800, z = 0;
	double vx, vy, vz;
	double spin;
	int state = LOOSE;
	PlayerMovement holder;
	Shot shot;

	private BufferedImage image;


	/** Info about the shot currently in the air. */
	static class Shot {
		PlayerMovement shooter;
		Court.Hoop hoop;
		boolean three;
		boolean scored;
		int t;
		int rimHits;
		Set<PlayerMovement> blockChecked = new HashSet<PlayerMovement>();
	}


	public void load() throws IOException {
		BufferedImage raw = SpriteSheet.loadImage("ball.png");
		// crop the empty border so the ball fills its 44px circle
		int minX = raw.getWidth(), minY = raw.getHeight(), maxX = 0, maxY = 0;
		for (int py = 0; py < raw.getHeight(); py += 2) {
			for (int px = 0; px < raw.getWidth(); px += 2) {
				if ((raw.getRGB(px, py) >>> 24) > 40) {
					if (px < minX) minX = px;
					if (py < minY) minY = py;
					if (px > maxX) maxX = px;
					if (py > maxY) maxY = py;
				}
			}
		}
		BufferedImage cropped = raw.getSubimage(minX, minY, maxX - minX + 1, maxY - minY + 1);
		image = SpriteSheet.resize(cropped, RADIUS * 2, RADIUS * 2);
	}


	public void setHeld(PlayerMovement p) {
		state = HELD;
		holder = p;
		shot = null;
		vx = vy = vz = 0;
	}

	public void setLoose() {
		state = LOOSE;
		holder = null;
	}


	/** Moves a loose / bouncing ball. Shot flight is handled by Game (needs the players). */
	public void integrate() {
		x += vx;
		y += vy;
		z += vz;
		vz -= GRAVITY;
		spin += vx * 0.03;
	}

	public void bounceAndRoll() {
		if (z <= 0) {
			z = 0;
			if (vz < -1.6) {          // bounce
				vz = -vz * 0.55;
				vx *= 0.85;
				vy *= 0.85;
			} else {
				vz = 0;
			}
		}
		if (state == LOOSE) {
			if (z == 0) {             // rolling friction
				vx *= 0.93;
				vy *= 0.93;
			}
			if (Math.abs(vx) < 0.05) vx = 0;
			if (Math.abs(vy) < 0.05) vy = 0;

			// keep loose balls on the floor where the players can reach them
			double ny = Court.clampY(y, 10);
			double nx = Court.clampX(x, ny, 10);
			if (nx != x) vx = -vx * 0.5;
			if (ny != y) vy = -vy * 0.5;
			x = nx;
			y = ny;
		}
	}


	public void drawShadow(Graphics2D g) {
		if (state == HELD) return;
		double s = Math.max(0.35, 1 - z / 400);
		g.setColor(new Color(0, 0, 0, (int) (255 * 0.3 * s)));
		int w = (int) (40 * s), h = (int) (14 * s);
		g.fillOval((int) (x - w / 2), (int) (y - h / 2), w, h);
	}

	/** Draws the ball centered on (cx, cy). */
	public void drawAt(Graphics2D g, double cx, double cy, double rotation) {
		AffineTransform old = g.getTransform();
		g.translate(cx, cy);
		if (rotation != 0) g.rotate(rotation);
		g.drawImage(image, -RADIUS, -RADIUS, null);
		g.setTransform(old);
	}

	public void draw(Graphics2D g) {
		drawAt(g, x, y - z - RADIUS, spin);
	}
}
