import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/**
 * Window + game loop + keyboard input.
 *
 * Originally this was an Applet. Applets no longer run in browsers and were removed from
 * newer JDKs, so the game now runs in a normal window (run Game.main from Eclipse).
 *
 * Fixes compared to the applet version:
 *  - Everything (updating and drawing) happens on the Swing thread, so the game state can
 *    never change halfway through a frame being drawn (that caused flicker and glitches).
 *  - Fixed 60 updates per second using real elapsed time instead of Thread.sleep(16).
 *  - Swing double-buffers the panel, and the game is drawn to a 1710x1080 back buffer
 *    that is scaled to fit any window size.
 */
@SuppressWarnings("serial")
public abstract class GameBase extends JPanel implements KeyListener, ActionListener {

	private static final long serialVersionUID = 1L;

	public static final int WIDTH  = 1710;
	public static final int HEIGHT = 1080;

	private static final double STEP_NANOS = 1_000_000_000.0 / 60.0;

	// keyboard state
	private final boolean[] pressing      = new boolean[1024];
	private final boolean[] pressedFrame  = new boolean[1024];

	private final BufferedImage backBuffer = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
	private Timer timer;
	private long lastTime;
	private double accumulator;


	public GameBase() {
		super(true);                              // double buffered
		super.setBackground(Color.BLACK);
		super.setFocusable(true);
		super.addKeyListener(this);
		MouseAdapter mouse = new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				requestFocusInWindow();
				Rectangle v = viewport();
				onClick((e.getX() - v.x) * (double) WIDTH / v.width, (e.getY() - v.y) * (double) HEIGHT / v.height);
				mouseMoved(e);      // the screen just changed under the mouse
			}

			// hand cursor over anything clickable
			@Override
			public void mouseMoved(MouseEvent e) {
				Rectangle v = viewport();
				boolean hot = isClickable((e.getX() - v.x) * (double) WIDTH / v.width, (e.getY() - v.y) * (double) HEIGHT / v.height);
				setCursor(Cursor.getPredefinedCursor(hot ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
			}
		};
		super.addMouseListener(mouse);
		super.addMouseMotionListener(mouse);
		// clicking away from the window would otherwise leave keys stuck down
		super.addFocusListener(new FocusAdapter() {
			@Override
			public void focusLost(FocusEvent e) {
				java.util.Arrays.fill(pressing, false);
				onFocusLost();
			}
		});
	}


	// ---------------------------------------------------------------- hooks for Game
	/** Creates the game panel (called on the Swing thread). */
	public interface Factory {
		GameBase create();
	}

	/** Load images etc. Runs on a background thread so the window appears right away. */
	public void loadAssets() {}
	/** Called on the Swing thread after loadAssets() finishes. */
	public void onLoaded() {}

	public abstract void update();
	public abstract void render(Graphics2D g);
	public void onClick(double x, double y) {}
	public boolean isClickable(double x, double y) { return false; }
	public void onFocusLost() {}


	// ---------------------------------------------------------------- input helpers
	public boolean isDown(int... codes) {
		for (int c : codes) if (c < pressing.length && pressing[c]) return true;
		return false;
	}

	/** True only on the update right after the key went down. */
	public boolean wasPressed(int... codes) {
		for (int c : codes) if (c < pressedFrame.length && pressedFrame[c]) return true;
		return false;
	}


	// ---------------------------------------------------------------- loop
	public void start() {
		lastTime = System.nanoTime();
		if (timer == null) timer = new Timer(4, this);
		timer.start();
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		long now = System.nanoTime();
		accumulator += Math.min(250_000_000L, now - lastTime);     // don't spiral after a pause
		lastTime = now;

		boolean stepped = false;
		while (accumulator >= STEP_NANOS) {
			update();
			java.util.Arrays.fill(pressedFrame, false);
			accumulator -= STEP_NANOS;
			stepped = true;
		}
		if (stepped) repaint();
	}


	// ---------------------------------------------------------------- drawing
	@Override
	protected void paintComponent(Graphics g) {
		super.paintComponent(g);

		Graphics2D bg = backBuffer.createGraphics();
		bg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		bg.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		bg.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		render(bg);
		bg.dispose();

		Graphics2D g2 = (Graphics2D) g;
		g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		Rectangle v = viewport();
		g2.drawImage(backBuffer, v.x, v.y, v.width, v.height, null);
	}

	/** The area of the panel the game is drawn in (keeps the 1710x1080 shape). */
	private Rectangle viewport() {
		double s = Math.min(getWidth() / (double) WIDTH, getHeight() / (double) HEIGHT);
		int w = (int) Math.round(WIDTH * s);
		int h = (int) Math.round(HEIGHT * s);
		return new Rectangle((getWidth() - w) / 2, (getHeight() - h) / 2, w, h);
	}


	// ---------------------------------------------------------------- keys
	@Override
	public void keyPressed(KeyEvent e) {
		int c = e.getKeyCode();
		if (c < 0 || c >= pressing.length) return;
		if (!pressing[c]) pressedFrame[c] = true;
		pressing[c] = true;
	}

	@Override
	public void keyReleased(KeyEvent e) {
		int c = e.getKeyCode();
		if (c < 0 || c >= pressing.length) return;
		pressing[c] = false;
	}

	@Override
	public void keyTyped(KeyEvent e) {}


	// ---------------------------------------------------------------- window
	/** Opens the game in a window sized to fit the screen, then loads assets in the background. */
	public static void launch(final Factory factory, final String title) {
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				final GameBase game = factory.create();
				JFrame frame = new JFrame(title);
				frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
				frame.setContentPane(game);

				Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
				double s = Math.min(1.0, Math.min(screen.width * 0.92 / WIDTH, screen.height * 0.88 / HEIGHT));
				game.setPreferredSize(new Dimension((int) (WIDTH * s), (int) (HEIGHT * s)));
				frame.pack();
				frame.setLocationRelativeTo(null);
				frame.setVisible(true);
				game.requestFocusInWindow();
				game.start();

				Thread loader = new Thread(new Runnable() {
					@Override
					public void run() {
						game.loadAssets();
						SwingUtilities.invokeLater(new Runnable() {
							@Override
							public void run() {
								game.onLoaded();
							}
						});
					}
				}, "asset-loader");
				loader.setDaemon(true);
				loader.start();
			}
		});
	}
}
