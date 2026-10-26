/**
 * The CPU opponent.
 *
 * Uses the same states as before (GET_BALL, ROAM, SHOOT, DEFEND, WAIT), but the state is
 * re-decided from the situation every frame, so the CPU can't get stuck in a stale state
 * (which used to leave it standing still or wandering off).
 */
public class CPU extends PlayerMovement {

	String state = "GET_BALL";

	// roaming with the ball
	double  roamTargetX, roamTargetY;
	boolean roamTargetSet;
	int     roamSpotsTotal, roamSpotsVisited;
	double  timeToRoam;

	// player's shot tick at which the CPU jumps to contest (-1 = not contesting)
	int contestAt = -1;


	public CPU() {
		super("CPU", SpriteData.P2_WALK, SpriteData.P2_UPDOWN, SpriteData.P2_DRIBBLE, SpriteData.P2_SHOOT,
				SpriteData.P2_IDLE, SpriteData.P2_JUMP, SpriteData.P2_REACH);
	}


	/** Called whenever the CPU gets the ball. */
	public void newPossession() {
		state            = "ROAM";
		roamTargetSet    = false;
		roamSpotsTotal   = 2 + (int) (Math.random() * 3);     // 2-4 spots before shooting
		roamSpotsVisited = 0;
		timeToRoam       = 480 + Math.random() * 240;        // 8-12 second fallback
	}


	public void think(Game game) {
		PlayerMovement player = game.player;
		Ball ball = game.ball;

		if (hasBall) {
			if (!state.equals("ROAM") && !state.equals("SHOOT") && !state.equals("SHOOTING")) state = "ROAM";
		}
		else if (player.hasBall)                                  state = "DEFEND";
		else if (ball.state == Ball.SHOT || ball.state == Ball.NET) state = "WAIT";
		else                                                      state = "GET_BALL";

		if (state.equals("GET_BALL")) {
			go(player, ball.x, ball.y, 9, true, 6);
			if (game.canGrab(this) && Math.abs(ball.x - x) < 60) game.giveBall(this);
		}
		else if (state.equals("WAIT")) {
			// get in position for the rebound
			Court.Hoop h = ball.shot != null ? ball.shot.hoop : Court.LEFT;
			go(player, h.x - h.dir * 170, h.floorY + 60, 6, false, 6);
		}
		else if (state.equals("ROAM")) {
			if (!roamTargetSet) {
				roamTargetX   = Court.rand(280, 1050);
				roamTargetY   = Court.rand(690, 980);
				roamTargetSet = true;
			}
			boolean reached = go(player, roamTargetX, roamTargetY, Game.DRIBBLE_SPEED, false, Game.DRIBBLE_SPEED);
			if (reached) {
				roamSpotsVisited++;
				roamTargetSet = false;
				if (roamSpotsVisited >= roamSpotsTotal) state = "SHOOT";
			}
			timeToRoam--;
			if (timeToRoam <= 0) state = "SHOOT";
		}
		else if (state.equals("SHOOT")) {
			if (startShot(Court.LEFT)) state = "SHOOTING";
		}
		else if (state.equals("DEFEND")) {
			// stay between the player and the right hoop, a step off him
			// (right after you inbound, it gives you extra room and doesn't reach)
			boolean grace = game.stealGraceFrames > 0;
			go(player, player.x + (grace ? 300 : 120), player.y, 8, false, 10);
			faceToward(player.x);
			boolean close = Math.abs(player.x - x) < 130 && Math.abs(player.y - y) < 45;
			if (close && !grace && !player.isShooting() && Math.random() < 0.02 && startReach(player.x)) {
				game.resolveSteal(this, player);
			}
		}

		// contest the player's jump shot
		if (player.isShooting()) {
			if (player.actionT == 1) {
				boolean near = Math.abs(player.x - x) < 230 && Math.abs(player.y - y) < 60 && x > player.x - 40;
				contestAt = (near && Math.random() < 0.4) ? 37 + (int) (Math.random() * 4) : -1;
			}
			if (player.actionT == contestAt) {
				faceToward(player.x);
				startJump();
			}
		}
	}


	/** Steps toward a target (walking around the player). Returns true once it's there. */
	private boolean go(PlayerMovement player, double tx, double ty, double speed, boolean run, double tolerance) {
		int dx = Math.abs(tx - x) > tolerance ? Court.sign(tx - x) : 0;
		int dy = Math.abs(ty - y) > tolerance ? Court.sign(ty - y) : 0;

		double ahead = (player.x - x) * dx;
		if (dx != 0 && ahead > 0 && ahead < 110 && Math.abs(player.y - y) < 34 && Math.abs(tx - x) > ahead) {
			dy = y < player.y ? -1 : 1;
			if (y - 40 < Court.FLOOR_TOP)    dy = 1;
			if (y + 40 > Court.FLOOR_BOTTOM) dy = -1;
		}
		move(dx, dy, speed, run);
		return dx == 0 && dy == 0;
	}
}
