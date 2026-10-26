import java.awt.Color;
import java.awt.Graphics2D;

/**
 * A basketball player (the user's player, and the base class for the CPU).
 *
 * Position is the player's FEET on the court (x, y) plus a jump height z. The old
 * version moved the sprite's top-left corner up and down to jump, which mixed jumping
 * with walking up the court and caused players to "teleport" after landing.
 *
 * Animations advance in update(), never while drawing.
 */
public class PlayerMovement {

	public static final double GRAVITY = 0.8;

	final String name;

	// sprite sheets
	final SpriteSheet walkSheet, upDownSheet, dribbleSheet, shootSheet, idleSheet, jumpSheet, reachSheet;

	// position (feet) and jump
	double x, y;
	double z, vz;
	double prevX, prevY;

	char facing = 'R';      // R right, L left, F toward camera, A away from camera
	char side   = 'R';      // last left/right facing (used by jump / reach)

	boolean hasBall;
	String  action;         // null, "shoot", "jump" or "reach"
	int     actionT;
	boolean moving, running;
	int     stealCoolDownFrames;

	// animation
	private String animKey = "";
	private int animFrame, animTimer;

	// current pose (chosen in updatePose, used by draw)
	SpriteSheet poseSheet;
	int poseRow, poseCol;
	boolean poseFlip;


	public PlayerMovement(String name, SpriteSheet walk, SpriteSheet upDown, SpriteSheet dribble,
			SpriteSheet shoot, SpriteSheet idle, SpriteSheet jump, SpriteSheet reach) {
		this.name         = name;
		this.walkSheet    = walk;
		this.upDownSheet  = upDown;
		this.dribbleSheet = dribble;
		this.shootSheet   = shoot;
		this.idleSheet    = idle;
		this.jumpSheet    = jump;
		this.reachSheet   = reach;
		reset(0, 0, 'R');
	}


	public final void reset(double x, double y, char facing) {
		this.x = x;
		this.y = y;
		this.prevX = x;
		this.prevY = y;
		this.z = 0;
		this.vz = 0;
		this.facing = facing;
		this.side = facing == 'L' ? 'L' : 'R';
		this.hasBall = false;
		this.action = null;
		this.actionT = 0;
		this.moving = false;
		this.running = false;
		this.stealCoolDownFrames = 0;
		this.animKey = "";
		this.poseSheet = idleSheet;
		this.poseRow = 0;
		this.poseCol = 0;
		this.poseFlip = facing == 'R';
	}


	public boolean isAirborne() { return z > 0 || vz > 0; }
	public boolean canAct()     { return action == null && !isAirborne(); }
	public boolean isShooting() { return "shoot".equals(action); }


	/** dx, dy are -1, 0 or 1. */
	public void move(int dx, int dy, double speed, boolean run) {
		if ("shoot".equals(action) || "reach".equals(action)) return;
		if (dx == 0 && dy == 0) return;
		double len = Math.sqrt(dx * dx + dy * dy);          // diagonal isn't faster
		x += dx / len * speed;
		y += dy / len * speed;
		if (dx != 0) {
			facing = dx < 0 ? 'L' : 'R';
			side = facing;
		} else {
			facing = dy < 0 ? 'A' : 'F';
		}
		moving = true;
		running = run;
	}

	public void faceToward(double targetX) {
		facing = targetX < x ? 'L' : 'R';
		side = facing;
	}

	public void clampToFloor() {
		y = Court.clampY(y, 0);
		x = Court.clampX(x, y, 0);
	}


	// ---------------------------------------------------------------- actions
	public boolean startShot(Court.Hoop hoop) {
		if (!canAct() || !hasBall) return false;
		if (facing == 'L' || facing == 'R') faceToward(hoop.x);    // turn to the basket
		action = "shoot";
		actionT = 0;
		return true;
	}

	public boolean startJump() {
		if (!canAct() || hasBall) return false;
		action = "jump";
		actionT = 0;
		return true;
	}

	public boolean startReach(double towardX) {
		if (!canAct() || hasBall || stealCoolDownFrames > 0) return false;
		faceToward(towardX);
		action = "reach";
		actionT = 0;
		stealCoolDownFrames = 180;            // 180 / 60fps = 3 seconds
		return true;
	}

	public void cancelAction() {
		action = null;
		actionT = 0;
	}


	/** Timers + jump physics. Returns true on the frame the ball leaves the hands. */
	public boolean update() {
		boolean release = false;
		if (stealCoolDownFrames > 0) stealCoolDownFrames--;

		if ("shoot".equals(action)) {
			actionT++;
			if (actionT == 32) vz = 8;                 // small hop into the jump shot
			if (actionT >= 50) {                       // 5 frames x 10 ticks, then release
				action = null;
				release = true;
			}
		}
		else if ("jump".equals(action)) {
			actionT++;
			if (actionT == 6) vz = 14;                 // take off after a short crouch
			if (actionT > 6 && !isAirborne()) action = null;
		}
		else if ("reach".equals(action)) {
			actionT++;
			if (actionT >= 24) action = null;
		}

		if (z > 0 || vz != 0) {
			z += vz;
			vz -= GRAVITY;
			if (z <= 0) {
				z = 0;
				vz = 0;
			}
		}
		return release;
	}


	private static int rowFor(char facing) {
		switch (facing) {
			case 'L': return 1;
			case 'F': return 2;
			case 'A': return 3;
			default:  return 0;
		}
	}

	/** Chooses this tick's sprite frame. */
	public void updatePose() {
		SpriteSheet sheet;
		int row = 0, frames = 1, speed = 1, col = -1;
		boolean flip = false;

		if ("shoot".equals(action)) {
			sheet = shootSheet;
			row = rowFor(facing);
			col = Math.min(4, actionT / 10);
		}
		else if ("jump".equals(action)) {
			sheet = jumpSheet;
			if (actionT < 3)       col = 0;
			else if (actionT < 6)  col = 1;
			else if (vz > 7)       col = 2;
			else if (vz > 0)       col = 3;
			else if (vz > -6)      col = 4;
			else                   col = 5;
			flip = sheet.facing(0) != side;
		}
		else if ("reach".equals(action)) {
			sheet = reachSheet;
			col = Math.min(2, actionT / 8);
			flip = sheet.facing(0) != side;
		}
		else if (hasBall) {
			sheet = dribbleSheet;
			row = rowFor(facing);
			frames = 5;
			speed = moving ? 6 : 9;
		}
		else if (moving && (facing == 'L' || facing == 'R')) {
			sheet = walkSheet;
			row = running ? 2 : 0;          // walk row / run row
			frames = 4;
			speed = running ? 5 : 8;
			flip = facing == 'L';
		}
		else if (moving) {
			sheet = upDownSheet;
			row = facing == 'A' ? 0 : 1;
			frames = 8;
			speed = 5;
		}
		else {
			sheet = idleSheet;
			if (facing == 'F')      col = 2;
			else if (facing == 'A') col = 3;
			else { col = 0; flip = facing == 'R'; }
		}

		String key = sheet.file + ":" + row;
		if (!key.equals(animKey)) {
			animKey = key;
			animFrame = 0;
			animTimer = 0;
		}
		if (col < 0) {
			animTimer++;
			if (animTimer >= speed) {
				animTimer = 0;
				animFrame = (animFrame + 1) % frames;
			}
			col = animFrame;
		}

		poseSheet = sheet;
		poseRow = row;
		poseCol = col;
		poseFlip = flip;
		moving = false;
		running = false;
	}


	/** Where the ball is held (only exact on shooting frames, which have no ball drawn). */
	public double[] handPoint() {
		if (poseSheet == shootSheet) {
			return poseSheet.handPoint(poseRow, poseCol, poseFlip, x, y - z);
		}
		return new double[] { x + (side == 'L' ? -40 : 40), y - z - 120 };
	}


	public void drawShadow(Graphics2D g) {
		double s = Math.max(0.45, 1 - z / 300);
		g.setColor(new Color(0, 0, 0, (int) (255 * 0.28 * s)));
		int w = (int) (104 * s), h = (int) (28 * s);
		g.fillOval((int) (x - w / 2), (int) (y - 2 - h / 2), w, h);
	}

	public void draw(Graphics2D g, Ball ball) {
		poseSheet.draw(g, poseRow, poseCol, poseFlip, x, y - z);
		// the shooting / jumping sprites have no ball in them, so draw it in the hands
		if ((isShooting() || "jump".equals(action)) && hasBall) {
			double[] h = handPoint();
			ball.drawAt(g, h[0], h[1], 0);
		}
	}
}
