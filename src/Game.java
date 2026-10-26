import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.event.KeyEvent;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 2D Basketball - 1 on 1 against the CPU, first to 21.
 *
 * Run this class (it has a main method). Controls are shown on the main menu.
 */
@SuppressWarnings("serial")
public class Game extends GameBase {

	private static final long serialVersionUID = 1L;

	static final int WALK_SPEED    = 8;
	static final int RUN_SPEED     = 14;
	static final int DRIBBLE_SPEED = 10;
	static final int RIM_RADIUS    = 30;

	// game screens
	static final int LOADING = 0, MENU = 1, PLAYING = 2, PAUSED = 3, GAME_OVER = 4;
	int screen = LOADING;
	String loadError;

	BufferedImage courtImage;

	final PlayerMovement player = new PlayerMovement("YOU", SpriteData.P1_WALK, SpriteData.P1_UPDOWN,
			SpriteData.P1_DRIBBLE, SpriteData.P1_SHOOT, SpriteData.P1_IDLE, SpriteData.P1_JUMP, SpriteData.P1_REACH);
	final CPU        cpu        = new CPU();
	final Ball       ball       = new Ball();
	final ShotMeter  shotMeter  = new ShotMeter();
	final ScoreBoard scoreBoard = new ScoreBoard();

	final List<Toast> toasts = new ArrayList<Toast>();

	// after a basket: short celebration, fade out, other team takes the ball
	int     resetTimer = -1;
	boolean resetToPlayer;
	double  fade;
	boolean playerWon;
	int     hintTimer;
	int     stealGraceFrames;     // frames the CPU gives you space after you inbound the ball
	long    frameCount;

	// main menu / game over button
	final double buttonX = WIDTH / 2.0 - 150, buttonY = 880, buttonW = 300, buttonH = 78;

	// pause button (top-left corner during a game) and the pause screen's buttons
	final double pauseX = 28, pauseY = 22, pauseW = 214, pauseH = 58;
	final String[] pauseMenuIds    = { "resume", "restart", "menu" };
	final String[] pauseMenuLabels = { "RESUME", "RESTART", "MAIN MENU" };
	final double pauseMenuX = WIDTH / 2.0 - 190, pauseMenuW = 380, pauseMenuH = 78;
	final double[] pauseMenuY = { 480, 580, 680 };


	public static void main(String[] args) {
		GameBase.launch(new GameBase.Factory() {
			@Override
			public GameBase create() {
				return new Game();
			}
		}, "2D Basketball");
	}


	/** Runs on a background thread while the window shows "Loading...". */
	@Override
	public void loadAssets() {
		try {
			courtImage = SpriteSheet.resize(SpriteSheet.loadImage("basketballCourtForGame.jpg"), WIDTH, HEIGHT);
			SpriteSheet[] sheets = {
				SpriteData.P1_WALK, SpriteData.P1_UPDOWN, SpriteData.P1_DRIBBLE, SpriteData.P1_SHOOT,
				SpriteData.P1_IDLE, SpriteData.P1_JUMP, SpriteData.P1_REACH,
				SpriteData.P2_WALK, SpriteData.P2_UPDOWN, SpriteData.P2_DRIBBLE, SpriteData.P2_SHOOT,
				SpriteData.P2_IDLE, SpriteData.P2_JUMP, SpriteData.P2_REACH,
			};
			for (SpriteSheet s : sheets) s.load();
			ball.load();
		} catch (Exception e) {
			e.printStackTrace();
			loadError = String.valueOf(e.getMessage());
		}
	}

	/** Back on the Swing thread once loading has finished. */
	@Override
	public void onLoaded() {
		if (loadError == null) screen = MENU;
	}


	void newGame() {
		scoreBoard.reset();
		toasts.clear();
		resetTimer = -1;
		fade = 0;
		shotMeter.reset();
		// opening tip: ball at center court, first one to grab it gets it
		player.reset(560, 900, 'R');
		cpu.reset(1150, 700, 'L');
		ball.setLoose();
		ball.shot = null;
		ball.x = 855;  ball.y = 800;  ball.z = 120;
		ball.vx = 0;   ball.vy = 0;   ball.vz = 0;
		cpu.state = "GET_BALL";
		cpu.contestAt = -1;
		hintTimer = 360;
		stealGraceFrames = 0;
		screen = PLAYING;
		toast("GRAB THE BALL!", 855, 560, new Color(255, 210, 63), 90, 46);
	}


	// ================================================================= update
	@Override
	public void update() {
		frameCount++;
		switch (screen) {
			case MENU:
				if (wasPressed(KeyEvent.VK_ENTER, KeyEvent.VK_SPACE)) newGame();
				break;
			case PAUSED:
				if (wasPressed(KeyEvent.VK_ESCAPE, KeyEvent.VK_ENTER)) screen = PLAYING;
				else if (wasPressed(KeyEvent.VK_R)) newGame();
				else if (wasPressed(KeyEvent.VK_Q)) screen = MENU;
				break;
			case GAME_OVER:
				if (wasPressed(KeyEvent.VK_ENTER, KeyEvent.VK_SPACE)) newGame();
				else if (wasPressed(KeyEvent.VK_ESCAPE, KeyEvent.VK_Q)) screen = MENU;
				else updateWorld(false);
				break;
			case PLAYING:
				if (wasPressed(KeyEvent.VK_ESCAPE)) { screen = PAUSED; break; }
				updateWorld(true);
				break;
			default:
				break;
		}
	}

	@Override
	public void onFocusLost() {
		if (screen == PLAYING) screen = PAUSED;
	}

	private static boolean hit(double x, double y, double bx, double by, double bw, double bh) {
		return x >= bx && x <= bx + bw && y >= by && y <= by + bh;
	}

	/** Which clickable thing (if any) is under (x, y) on the current screen. */
	String buttonAt(double x, double y) {
		if ((screen == MENU || screen == GAME_OVER) && hit(x, y, buttonX, buttonY, buttonW, buttonH)) return "play";
		if (screen == PLAYING && hit(x, y, pauseX, pauseY, pauseW, pauseH)) return "pause";
		if (screen == PAUSED) {
			for (int i = 0; i < pauseMenuIds.length; i++) {
				if (hit(x, y, pauseMenuX, pauseMenuY[i], pauseMenuW, pauseMenuH)) return pauseMenuIds[i];
			}
		}
		return null;
	}

	@Override
	public boolean isClickable(double x, double y) {
		return buttonAt(x, y) != null;
	}

	@Override
	public void onClick(double x, double y) {
		String id = buttonAt(x, y);
		if (id == null) return;
		if (id.equals("play") || id.equals("restart")) newGame();
		else if (id.equals("pause"))  screen = PAUSED;
		else if (id.equals("resume")) screen = PLAYING;
		else if (id.equals("menu"))   screen = MENU;
	}


	void updateWorld(boolean allowInput) {
		boolean inputLocked = !allowInput || resetTimer >= 0;
		player.prevX = player.x;  player.prevY = player.y;
		cpu.prevX = cpu.x;        cpu.prevY = cpu.y;

		if (!inputLocked) updatePlayerInput();
		else shotMeter.update(false);
		if (resetTimer < 0 && screen == PLAYING) cpu.think(this);

		if (player.update()) launchShot(player);
		if (cpu.update())    launchShot(cpu);

		separatePlayers();
		updateBall();
		player.updatePose();
		cpu.updatePose();

		if (hintTimer > 0) hintTimer--;
		if (stealGraceFrames > 0) stealGraceFrames--;
		for (Iterator<Toast> it = toasts.iterator(); it.hasNext();) {
			Toast t = it.next();
			if (++t.t >= t.life) it.remove();
		}
		updatePossessionReset();
	}


	void updatePlayerInput() {
		boolean left  = isDown(KeyEvent.VK_A, KeyEvent.VK_LEFT);
		boolean right = isDown(KeyEvent.VK_D, KeyEvent.VK_RIGHT);
		boolean up    = isDown(KeyEvent.VK_W, KeyEvent.VK_UP);
		boolean down  = isDown(KeyEvent.VK_S, KeyEvent.VK_DOWN);
		int dx = (right ? 1 : 0) - (left ? 1 : 0);
		int dy = (down ? 1 : 0) - (up ? 1 : 0);
		boolean run = isDown(KeyEvent.VK_C, KeyEvent.VK_SHIFT);

		double speed = player.hasBall ? DRIBBLE_SPEED : (run ? RUN_SPEED : WALK_SPEED);
		player.move(dx, dy, speed, run && !player.hasBall);

		// shoot: hold O, release in the green
		if (wasPressed(KeyEvent.VK_O) && player.hasBall && player.startShot(Court.RIGHT)) shotMeter.start();
		shotMeter.update(isDown(KeyEvent.VK_O));

		// jump / block
		if (wasPressed(KeyEvent.VK_SPACE)) player.startJump();

		// reach / steal
		if (wasPressed(KeyEvent.VK_M) && player.startReach(cpu.x)) {
			boolean close = Math.abs(player.x - cpu.x) < 140 && Math.abs(player.y - cpu.y) < 50;
			if (close && cpu.hasBall && !cpu.isShooting()) resolveSteal(player, cpu);
		}

		// pick up / drop
		if (wasPressed(KeyEvent.VK_P)) {
			if (player.hasBall && player.action == null) {
				player.hasBall = false;
				ball.setLoose();
				ball.x = player.x + (player.side == 'L' ? -45 : 45);
				ball.y = player.y + 4;
				ball.z = 40;
				ball.vx = ball.vy = ball.vz = 0;
				cpu.state = "GET_BALL";
			}
			else if (canGrab(player)) {
				giveBall(player);
			}
		}
	}


	boolean canGrab(PlayerMovement p) {
		return ball.state == Ball.LOOSE && !p.hasBall && !p.isShooting()
				&& Math.abs(ball.x - p.x) < 80 && Math.abs(ball.y - p.y) < 45
				&& ball.z < 170 + p.z;
	}

	void giveBall(PlayerMovement p) {
		PlayerMovement other = (p == player) ? cpu : player;
		other.hasBall = false;
		p.hasBall = true;
		ball.setHeld(p);
		if (p == cpu) {
			cpu.newPossession();
			stealGraceFrames = 0;      // grace only covers your inbound
		}
		else hintTimer = 0;
	}

	void resolveSteal(PlayerMovement thief, PlayerMovement victim) {
		double roll = Math.random();
		if (roll < 0.2) {                      // clean steal
			giveBall(thief);
			toast("STOLEN!", thief.x, thief.y - 260, new Color(255, 210, 63), 70, 40);
		}
		else if (roll < 0.5) {                 // knocked loose: the ball bounces away
			victim.hasBall = false;
			ball.setLoose();
			ball.x = victim.x;
			ball.y = victim.y;
			ball.z = 60;
			int dir = thief.x < victim.x ? 1 : -1;
			ball.vx = dir * Court.rand(4, 8);
			ball.vy = Court.rand(-2.5, 2.5);
			ball.vz = Court.rand(2, 4);
			toast("KNOCKED LOOSE!", victim.x, victim.y - 260, Color.WHITE, 60, 32);
		}
		// otherwise the reach missed
	}


	// ================================================================= shooting
	void launchShot(PlayerMovement shooter) {
		if (!shooter.hasBall) {
			if (shooter == player) shotMeter.reset();
			return;
		}
		Court.Hoop hoop = (shooter == player) ? Court.RIGHT : Court.LEFT;
		double[] hand = shooter.handPoint();
		shooter.hasBall = false;

		// the ball starts exactly where it was drawn in the hands
		ball.state  = Ball.SHOT;
		ball.holder = null;
		ball.x = hand[0];
		ball.y = shooter.y;
		ball.z = shooter.y - hand[1];

		double dx = hoop.x - ball.x;
		double T  = Court.clamp(30 + Math.abs(dx) / 18, 36, 90);     // frames in the air
		double vx = dx / T;
		double vy = (hoop.floorY - ball.y) / T;
		double vz = (hoop.rimZ - ball.z + 0.5 * Ball.GRAVITY * T * T) / T;

		boolean three = hoop.isThree(shooter.x);
		double pf = 1, jx = 0, jz = 0;

		if (shooter == player) {
			shotMeter.forceRelease();
			if (shotMeter.isInGreenWindow()) {
				toast("GREEN!", shooter.x, shooter.y - 300, new Color(33, 210, 74), 50, 34);
			} else {
				double off = shotMeter.releaseOffset();          // early < 0 < late
				// the green window is near the end of the bar, so late misses ramp up faster;
				// holding until the meter maxes out is a really bad shot
				boolean late = off > 0;
				pf = 1 + off * (late ? 0.6 : 0.22);
				jx = Court.rand(-1, 1) * (0.35 + Math.abs(off) * (late ? 2.2 : 0.8));
				jz = Court.rand(-0.3, 0.3);
				if (shotMeter.shotReleaseValue >= shotMeter.shotMeterMax) {   // never let go: forced brick
					pf = Court.rand(0.72, 0.82);                    // comes up short
					jx = Court.rand(-0.5, 0.5);
				}
			}
			shotMeter.reset();
		} else {
			double makeChance = three ? 0.38 : 0.5;
			if (Math.random() >= makeChance) {
				jx = Court.sign(Math.random() - 0.5) * Court.rand(0.45, 1.3);
				jz = Court.rand(-0.4, 0.4);
			}
		}

		ball.vx = vx * pf + jx;
		ball.vy = vy;
		ball.vz = vz * pf + jz;

		Ball.Shot s = new Ball.Shot();
		s.shooter = shooter;
		s.hoop = hoop;
		s.three = three;
		ball.shot = s;
	}


	void updateBall() {
		if (ball.state == Ball.HELD) {
			ball.x = ball.holder.x;
			ball.y = ball.holder.y;
			ball.z = 0;
			return;
		}
		double prevZ = ball.z;
		ball.integrate();

		if (ball.state == Ball.SHOT) updateShotFlight(prevZ);

		if (ball.state == Ball.NET) {             // drops through the net
			ball.vx *= 0.85;
			ball.vy *= 0.85;
			if (ball.z <= 0) ball.state = Ball.LOOSE;
		}
		if (ball.state == Ball.SHOT && ball.z <= 0) ball.state = Ball.LOOSE;   // missed, now a rebound
		ball.bounceAndRoll();
	}


	void updateShotFlight(double prevZ) {
		Ball.Shot s = ball.shot;
		Court.Hoop h = s.hoop;
		s.t++;
		boolean nearDepth = Math.abs(ball.y - h.floorY) < 70;

		// blocks: early in the flight, by an airborne defender
		if (s.t < 28 && s.rimHits == 0) {
			PlayerMovement[] defenders = { player, cpu };
			for (PlayerMovement d : defenders) {
				if (d == s.shooter || d.z < 15 || s.blockChecked.contains(d)) continue;
				double handsX = d.x + (d.side == 'L' ? -15 : 15);
				double top    = d.y - d.z - 250;
				double bottom = d.y - d.z - 105;
				double sy     = ball.y - ball.z;
				if (Math.abs(ball.x - handsX) < 60 && sy > top && sy < bottom && Math.abs(ball.y - d.y) < 70) {
					s.blockChecked.add(d);
					if (d == cpu && Math.random() > 0.7) continue;        // CPU doesn't block everything
					ball.setLoose();
					ball.vx = -Court.sign(ball.vx) * Court.rand(3, 6);
					ball.vy = Court.rand(-2, 2);
					ball.vz = Court.rand(0, 2.5);
					toast("BLOCKED!", d.x, d.y - d.z - 290, new Color(255, 77, 77), 70, 44);
					return;
				}
			}
		}

		// rim: checked when the ball comes down through the rim's height
		if (!s.scored && prevZ >= h.rimZ && ball.z < h.rimZ && nearDepth) {
			double d = ball.x - h.x;
			if (Math.abs(d) <= RIM_RADIUS - 6) {
				scoreBasket(s);
			}
			else if (Math.abs(d) <= RIM_RADIUS + Ball.RADIUS && s.rimHits < 4) {
				s.rimHits++;
				ball.z  = h.rimZ;
				ball.vz = Math.abs(ball.vz) * 0.45 + 1.4;
				ball.vx = ball.vx * 0.35 + Court.sign(d) * Court.rand(1.2, 2.2);
			}
		}

		// backboard (and the padding / stanchion behind it): the ball never goes behind the hoop
		boolean pastBoard = h.dir > 0 ? ball.x + Ball.RADIUS > h.boardX : ball.x - Ball.RADIUS < h.boardX;
		if (pastBoard && ball.vx * h.dir > 0) {
			boolean hitBoard = nearDepth && ball.z > h.rimZ - 20 && ball.z < h.rimZ + 160;
			ball.x  = h.boardX - h.dir * Ball.RADIUS;
			ball.vx = -ball.vx * (hitBoard ? 0.45 : 0.3);
		}
	}


	void scoreBasket(Ball.Shot s) {
		s.scored = true;
		ball.state = Ball.NET;
		ball.vx = (s.hoop.x - ball.x) * 0.2;
		ball.vy = 0;
		ball.vz = -1.5;

		int points = s.three ? 3 : 2;
		boolean byPlayer = s.shooter == player;
		if (byPlayer) scoreBoard.addPlayerPoints(points);
		else          scoreBoard.addCpuPoints(points);
		toast("+" + points, s.hoop.x, s.hoop.rimY - 70,
				byPlayer ? new Color(255, 210, 63) : new Color(255, 122, 217), 80, 64);

		if (scoreBoard.playerScore >= ScoreBoard.WIN_SCORE || scoreBoard.cpuScore >= ScoreBoard.WIN_SCORE) {
			playerWon = scoreBoard.playerScore >= ScoreBoard.WIN_SCORE;
			screen = GAME_OVER;
			resetTimer = -1;
			player.cancelAction();
			cpu.cancelAction();
			shotMeter.reset();
			return;
		}
		// the other side takes the ball out
		resetTimer = 0;
		resetToPlayer = !byPlayer;
	}


	void updatePossessionReset() {
		if (resetTimer < 0) {
			fade = Math.max(0, fade - 0.08);
			return;
		}
		resetTimer++;
		if (resetTimer > 55) fade = Math.min(1, fade + 0.08);
		if (resetTimer == 70) {
			shotMeter.reset();
			if (resetToPlayer) {
				player.reset(420, 840, 'R');
				cpu.reset(820, 840, 'L');        // backed off so you aren't pressured right away
				giveBall(player);
				stealGraceFrames = 120;          // 2 seconds
			} else {
				cpu.reset(1290, 840, 'L');
				player.reset(1010, 840, 'R');
				giveBall(cpu);
			}
			player.updatePose();
			cpu.updatePose();
			resetTimer = -1;
		}
	}


	/**
	 * Keeps the two players from walking through each other. Whoever walked into the
	 * other one gets stopped (no more snapping both back to last frame, which jittered).
	 */
	void separatePlayers() {
		boolean movedP = Math.hypot(player.x - player.prevX, player.y - player.prevY) > 0.1;
		boolean movedC = Math.hypot(cpu.x - cpu.prevX, cpu.y - cpu.prevY) > 0.1;
		double wp = 0.5;
		if (movedP && !movedC) wp = 1;
		else if (movedC && !movedP) wp = 0;

		for (int pass = 0; pass < 2; pass++) {
			double dx = cpu.x - player.x;
			double dy = cpu.y - player.y;
			double rx = 70, ry = 26;
			double px = rx - Math.abs(dx);
			double py = ry - Math.abs(dy);
			if (px > 0 && py > 0) {
				if (px / rx < py / ry) {
					player.x -= Court.sign(dx) * px * wp;
					cpu.x    += Court.sign(dx) * px * (1 - wp);
				} else {
					player.y -= Court.sign(dy) * py * wp;
					cpu.y    += Court.sign(dy) * py * (1 - wp);
				}
			}
			player.clampToFloor();
			cpu.clampToFloor();
			wp = 0.5;      // if a wall stopped the push, share what's left
		}
	}


	void toast(String text, double x, double y, Color color, int life, int size) {
		toasts.add(new Toast(text, x, y, color, life, size));
	}


	// ================================================================= render
	@Override
	public void render(Graphics2D g) {
		if (screen == LOADING) {
			g.setColor(new Color(11, 16, 32));
			g.fillRect(0, 0, WIDTH, HEIGHT);
			g.setFont(new Font("SansSerif", Font.BOLD, 40));
			ScoreBoard.drawCentered(g, loadError == null ? "Loading..." : "Could not load images: " + loadError,
					WIDTH / 2.0, HEIGHT / 2.0, Color.WHITE);
			return;
		}

		g.drawImage(courtImage, 0, 0, null);
		scoreBoard.draw(g);

		if (screen == MENU) {
			drawMenu(g);
			return;
		}

		player.drawShadow(g);
		cpu.drawShadow(g);
		ball.drawShadow(g);

		// depth sorting: whoever is closer to the camera (lower on screen) is drawn last
		boolean ballOnFloor = ball.state == Ball.LOOSE && ball.z < 60;
		PlayerMovement back  = player.y <= cpu.y ? player : cpu;
		PlayerMovement front = back == player ? cpu : player;
		if (ballOnFloor && ball.y < back.y) ball.draw(g);
		back.draw(g, ball);
		if (ballOnFloor && ball.y >= back.y && ball.y < front.y) ball.draw(g);
		front.draw(g, ball);
		if (ballOnFloor && ball.y >= front.y) ball.draw(g);

		// a ball in the air is always in front
		if (ball.state == Ball.SHOT || ball.state == Ball.NET || (ball.state == Ball.LOOSE && !ballOnFloor)) {
			ball.draw(g);
		}

		shotMeter.draw(g, player.x, player.y - player.z - 275);
		drawToasts(g);
		drawHint(g);

		if (fade > 0) {
			g.setColor(new Color(5, 8, 18, (int) (255 * fade)));
			g.fillRect(0, 0, WIDTH, HEIGHT);
		}
		if (screen == PLAYING)   drawPauseButton(g);
		if (screen == PAUSED)    drawPause(g);
		if (screen == GAME_OVER) drawGameOver(g);
	}


	private void drawPauseButton(Graphics2D g) {
		RoundRectangle2D pill = new RoundRectangle2D.Double(pauseX, pauseY, pauseW, pauseH, pauseH, pauseH);
		g.setColor(new Color(8, 12, 28, 200));
		g.fill(pill);
		g.setColor(new Color(255, 255, 255, 64));
		g.setStroke(new BasicStroke(2));
		g.draw(pill);
		// pause icon (two bars)
		g.setColor(Color.WHITE);
		int iy = (int) (pauseY + pauseH / 2 - 12);
		g.fillRect((int) pauseX + 24, iy, 7, 24);
		g.fillRect((int) pauseX + 37, iy, 7, 24);
		g.setFont(new Font("SansSerif", Font.BOLD, 24));
		int ty = (int) Math.round(pauseY + pauseH / 2 + (g.getFontMetrics().getAscent() - g.getFontMetrics().getDescent()) / 2.0);
		g.drawString("PAUSE", (int) pauseX + 58, ty);
		g.setFont(new Font("SansSerif", Font.BOLD, 14));
		g.setColor(new Color(143, 154, 181));
		ty = (int) Math.round(pauseY + pauseH / 2 + (g.getFontMetrics().getAscent() - g.getFontMetrics().getDescent()) / 2.0);
		int escW = g.getFontMetrics().stringWidth("ESC");
		g.drawString("ESC", (int) (pauseX + pauseW - 24 - escW), ty);      // right-aligned
	}


	private void drawToasts(Graphics2D g) {
		for (Toast t : toasts) {
			double p = (double) t.t / t.life;
			float alpha = (float) (p < 0.75 ? 1 : 1 - (p - 0.75) / 0.25);
			g.setFont(new Font("SansSerif", Font.BOLD, t.size));
			double y = t.y - t.t * 0.8;
			Color shadow = new Color(0, 0, 0, (int) (190 * alpha));
			ScoreBoard.drawCentered(g, t.text, t.x + 3, y + 3, shadow);
			ScoreBoard.drawCentered(g, t.text, t.x, y,
					new Color(t.color.getRed(), t.color.getGreen(), t.color.getBlue(), (int) (255 * alpha)));
		}
	}


	private void drawHint(Graphics2D g) {
		if (screen != PLAYING || resetTimer >= 0) return;
		String text = null;
		if (canGrab(player)) text = "Press P to pick up the ball";
		else if (hintTimer > 0 && !player.hasBall) text = "Run to the ball with WASD (hold C to sprint)";
		else if (player.hasBall && scoreBoard.playerScore == 0 && scoreBoard.cpuScore == 0 && !shotMeter.isChargingShot)
			text = "Hold O to shoot, release in the green. You attack the right hoop.";
		if (text == null) return;

		g.setFont(new Font("SansSerif", Font.BOLD, 26));
		int w = g.getFontMetrics().stringWidth(text) + 40;
		g.setColor(new Color(8, 12, 28, 200));
		g.fill(new RoundRectangle2D.Double(WIDTH / 2.0 - w / 2.0, 1022, w, 44, 44, 44));
		ScoreBoard.drawCentered(g, text, WIDTH / 2.0, 1044, Color.WHITE);
	}


	private void drawMenu(Graphics2D g) {
		g.setPaint(new GradientPaint(0, 0, new Color(6, 9, 22, 184), 0, HEIGHT, new Color(6, 9, 22, 230)));
		g.fillRect(0, 0, WIDTH, HEIGHT);

		Font title = new Font("SansSerif", Font.BOLD, 104);
		g.setFont(title);
		ScoreBoard.drawCentered(g, "2D BASKETBALL", WIDTH / 2.0 + 5, 160, new Color(18, 10, 4));
		ScoreBoard.drawCentered(g, "2D BASKETBALL", WIDTH / 2.0, 155, new Color(255, 170, 40));
		g.setFont(new Font("SansSerif", Font.BOLD, 30));
		ScoreBoard.drawCentered(g, "1-on-1 against the CPU  ·  first to " + ScoreBoard.WIN_SCORE + " wins",
				WIDTH / 2.0, 238, new Color(207, 216, 255));

		// the two players on either side
		double bob = Math.sin(frameCount / 30.0) * 3;
		SpriteData.P1_IDLE.draw(g, 0, 2, false, 300, 760 + bob);
		SpriteData.P2_IDLE.draw(g, 0, 2, false, WIDTH - 300, 760 - bob);
		g.setFont(new Font("SansSerif", Font.BOLD, 26));
		ScoreBoard.drawCentered(g, "YOU", 300, 795, new Color(255, 179, 107));
		ScoreBoard.drawCentered(g, "CPU", WIDTH - 300, 795, new Color(255, 225, 77));

		// controls card
		double cx = WIDTH / 2.0 - 400, cy = 300, cw = 800, ch = 530;
		g.setColor(new Color(14, 20, 42, 235));
		g.fill(new RoundRectangle2D.Double(cx, cy, cw, ch, 52, 52));
		g.setColor(new Color(255, 255, 255, 30));
		g.setStroke(new BasicStroke(2));
		g.draw(new RoundRectangle2D.Double(cx, cy, cw, ch, 52, 52));

		g.setFont(new Font("SansSerif", Font.BOLD, 30));
		ScoreBoard.drawCentered(g, "CONTROLS", WIDTH / 2.0, cy + 55, Color.WHITE);

		String[][] rows = {
			{ "W A S D",  "Move  (arrow keys work too)" },
			{ "C + move", "Sprint (without the ball)" },
			{ "Hold O",   "Shoot - release in the green" },
			{ "Space",    "Jump to block a shot" },
			{ "M",        "Reach for a steal" },
			{ "P",        "Pick up / drop the ball" },
			{ "Esc",      "Pause (or the button in the top-left)" },
		};
		Font keyFont  = new Font("SansSerif", Font.BOLD, 24);
		Font descFont = new Font("SansSerif", Font.PLAIN, 25);
		for (int i = 0; i < rows.length; i++) {
			double y = cy + 120 + i * 56;
			g.setFont(keyFont);
			int kw = Math.max(64, g.getFontMetrics().stringWidth(rows[i][0]) + 30);
			double kx = cx + 250 - kw;
			g.setColor(new Color(38, 49, 79));
			g.fill(new RoundRectangle2D.Double(kx, y - 21, kw, 42, 20, 20));
			ScoreBoard.drawCentered(g, rows[i][0], kx + kw / 2.0, y, new Color(255, 210, 63));
			g.setFont(descFont);
			g.setColor(new Color(232, 236, 246));
			int ty = (int) Math.round(y + (g.getFontMetrics().getAscent() - g.getFontMetrics().getDescent()) / 2.0);
			g.drawString(rows[i][1], (int) (cx + 280), ty);
		}
		g.setFont(new Font("SansSerif", Font.PLAIN, 21));
		ScoreBoard.drawCentered(g, "You attack the right hoop, the CPU attacks the left.",
				WIDTH / 2.0, cy + ch - 28, new Color(143, 154, 181));

		drawButton(g, "PLAY");
		g.setFont(new Font("SansSerif", Font.BOLD, 22));
		ScoreBoard.drawCentered(g, "or press ENTER", WIDTH / 2.0, 990, new Color(207, 216, 255));
	}


	private void drawButton(Graphics2D g, String label) {
		double pulse = 1 + Math.sin(frameCount / 15.0) * 0.02;
		double w = buttonW * pulse, h = buttonH * pulse;
		double x = buttonX + buttonW / 2 - w / 2, y = buttonY + buttonH / 2 - h / 2;
		g.setColor(new Color(255, 122, 26, 90));
		g.fill(new RoundRectangle2D.Double(x - 6, y - 6, w + 12, h + 12, h + 12, h + 12));
		g.setColor(new Color(255, 122, 26));
		g.fill(new RoundRectangle2D.Double(x, y, w, h, h, h));
		g.setFont(new Font("SansSerif", Font.BOLD, 38));
		ScoreBoard.drawCentered(g, label, buttonX + buttonW / 2, buttonY + buttonH / 2, new Color(26, 13, 2));
	}


	private void drawPause(Graphics2D g) {
		g.setColor(new Color(6, 9, 22, 180));
		g.fillRect(0, 0, WIDTH, HEIGHT);
		g.setFont(new Font("SansSerif", Font.BOLD, 90));
		ScoreBoard.drawCentered(g, "PAUSED", WIDTH / 2.0, 380, Color.WHITE);

		for (int i = 0; i < pauseMenuIds.length; i++) {
			boolean primary = i == 0;
			RoundRectangle2D pill = new RoundRectangle2D.Double(pauseMenuX, pauseMenuY[i], pauseMenuW, pauseMenuH, pauseMenuH, pauseMenuH);
			g.setColor(primary ? new Color(255, 122, 26) : new Color(255, 255, 255, 26));
			g.fill(pill);
			if (!primary) {
				g.setColor(new Color(255, 255, 255, 77));
				g.setStroke(new BasicStroke(2));
				g.draw(pill);
			}
			g.setFont(new Font("SansSerif", Font.BOLD, 34));
			ScoreBoard.drawCentered(g, pauseMenuLabels[i], pauseMenuX + pauseMenuW / 2, pauseMenuY[i] + pauseMenuH / 2,
					primary ? new Color(26, 13, 2) : Color.WHITE);
		}
		g.setFont(new Font("SansSerif", Font.BOLD, 22));
		ScoreBoard.drawCentered(g, "Keys:  Esc resume  \u00b7  R restart  \u00b7  Q main menu", WIDTH / 2.0, 820,
				new Color(207, 216, 255));
	}


	private void drawGameOver(Graphics2D g) {
		g.setColor(new Color(6, 9, 22, 184));
		g.fillRect(0, 0, WIDTH, HEIGHT);
		g.setFont(new Font("SansSerif", Font.BOLD, 110));
		String text = playerWon ? "YOU WIN!" : "CPU WINS";
		ScoreBoard.drawCentered(g, text, WIDTH / 2.0 + 5, 405, Color.BLACK);
		ScoreBoard.drawCentered(g, text, WIDTH / 2.0, 400, playerWon ? new Color(255, 210, 63) : new Color(255, 107, 240));
		g.setFont(new Font("SansSerif", Font.BOLD, 56));
		ScoreBoard.drawCentered(g, scoreBoard.playerScore + " - " + scoreBoard.cpuScore, WIDTH / 2.0, 530, Color.WHITE);
		drawButton(g, "PLAY AGAIN");
		g.setFont(new Font("SansSerif", Font.BOLD, 22));
		ScoreBoard.drawCentered(g, "ENTER to play again  ·  Q for menu", WIDTH / 2.0, 990, new Color(207, 216, 255));
	}


	/** Floating text like "+3" or "BLOCKED!". */
	static class Toast {
		final String text;
		final double x, y;
		final Color color;
		final int life, size;
		int t;

		Toast(String text, double x, double y, Color color, int life, int size) {
			this.text = text;
			this.x = x;
			this.y = y;
			this.color = color;
			this.life = life;
			this.size = size;
		}
	}
}
