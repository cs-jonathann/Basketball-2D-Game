/**
 * Court geometry in screen coordinates (the game is laid out on a 1710 x 1080 screen).
 *
 * The old version used ten 1-pixel "walls" checked against the full 250x250 sprite box,
 * which let players get stuck in corners, slip past walls and end up off screen.
 * Now each player's feet are simply kept inside a trapezoid that follows the floor,
 * so they slide smoothly along the edges and can never leave the court.
 */
public class Court {

	public static final double FLOOR_TOP    = 650;
	public static final double FLOOR_BOTTOM = 1000;

	// Player attacks the RIGHT hoop, CPU attacks the LEFT hoop.
	//                                       rimX  rimY floorY boardX dir threeX
	public static final Hoop RIGHT = new Hoop(1492, 472, 800, 1550,  1, 1098);
	public static final Hoop LEFT  = new Hoop( 221, 472, 800,  164, -1,  612);


	/** Left edge of the floor at depth y. */
	public static double leftAt(double y) {
		return lerp(240, 75, (y - FLOOR_TOP) / (FLOOR_BOTTOM - FLOOR_TOP));
	}

	/** Right edge of the floor at depth y. */
	public static double rightAt(double y) {
		return lerp(1470, 1635, (y - FLOOR_TOP) / (FLOOR_BOTTOM - FLOOR_TOP));
	}

	public static double clampY(double y, double margin) {
		return clamp(y, FLOOR_TOP + margin, FLOOR_BOTTOM - margin);
	}

	/** Call after clampY. */
	public static double clampX(double x, double y, double margin) {
		return clamp(x, leftAt(y) + margin, rightAt(y) - margin);
	}


	// ------------------------------------------------------------ small math helpers
	public static double lerp(double a, double b, double t) {
		return a + (b - a) * t;
	}

	public static double clamp(double v, double lo, double hi) {
		return v < lo ? lo : (v > hi ? hi : v);
	}

	public static double rand(double lo, double hi) {
		return lo + Math.random() * (hi - lo);
	}

	public static int sign(double v) {
		return v < 0 ? -1 : 1;
	}


	/** One basket: rim position, backboard and the three point line for that end. */
	public static class Hoop {
		final double x;        // rim center
		final double rimY;     // rim height on screen
		final double floorY;   // depth of the floor under the rim
		final double boardX;   // backboard plane
		final int    dir;      // +1 right hoop, -1 left hoop
		final double threeX;   // shots from beyond this x are worth 3
		final double rimZ;     // rim height above the floor

		Hoop(double x, double rimY, double floorY, double boardX, int dir, double threeX) {
			this.x      = x;
			this.rimY   = rimY;
			this.floorY = floorY;
			this.boardX = boardX;
			this.dir    = dir;
			this.threeX = threeX;
			this.rimZ   = floorY - rimY;
		}

		boolean isThree(double shooterX) {
			return dir > 0 ? shooterX < threeX : shooterX > threeX;
		}
	}
}
