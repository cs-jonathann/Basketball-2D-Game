import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import javax.imageio.ImageIO;

/**
 * One sprite sheet plus the measured data for every frame in it (see SpriteData).
 *
 * The sheets were hand drawn, so the character sits in a different spot in each frame.
 * Instead of cutting fixed-size squares (which made the players pop around and cut
 * empty space on the CPU's differently-sized sheets), every frame is drawn so that
 * its feet land exactly on the actor's position.
 *
 * Frames are cut and pre-scaled once when the game starts, so drawing each frame is
 * a single fast drawImage call.
 */
public class SpriteSheet {

	final String file;
	final double scale;          // draw scale so every animation is the same body size
	final String faces;          // per row: R right, L left, F toward camera, A away
	final double[][][] frames;   // [row][col] = {x, y, w, h, anchorX, anchorY, handX, handY}

	private BufferedImage[][] scaled;
	private BufferedImage[][] mirrored;


	public SpriteSheet(String file, double scale, String faces, double[][][] frames) {
		this.file   = file;
		this.scale  = scale;
		this.faces  = faces;
		this.frames = frames;
	}


	/** Faces direction ('R' / 'L') of the side-view frames in a row. */
	public char facing(int row) {
		return faces.charAt(row);
	}


	public void load() throws IOException {
		BufferedImage sheet = loadImage(file);
		scaled   = new BufferedImage[frames.length][];
		mirrored = new BufferedImage[frames.length][];

		for (int r = 0; r < frames.length; r++) {
			scaled[r]   = new BufferedImage[frames[r].length];
			mirrored[r] = new BufferedImage[frames[r].length];

			for (int c = 0; c < frames[r].length; c++) {
				double[] f = frames[r][c];
				BufferedImage crop = sheet.getSubimage((int) f[0], (int) f[1], (int) f[2], (int) f[3]);
				int w = Math.max(1, (int) Math.round(f[2] * scale));
				int h = Math.max(1, (int) Math.round(f[3] * scale));
				scaled[r][c]   = resize(crop, w, h);
				mirrored[r][c] = mirror(scaled[r][c]);
			}
		}
	}


	/** Draws a frame with its feet anchor on (feetX, feetY). */
	public void draw(Graphics2D g, int row, int col, boolean flip, double feetX, double feetY) {
		double[] f = frames[row][col];
		BufferedImage img = flip ? mirrored[row][col] : scaled[row][col];
		double fromLeft = flip ? (f[0] + f[2] - f[4]) : (f[4] - f[0]);
		int dx = (int) Math.round(feetX - fromLeft * scale);
		int dy = (int) Math.round(feetY - (f[5] - f[1]) * scale);
		g.drawImage(img, dx, dy, null);
	}


	/** Where the ball should sit in the hands on a shooting frame (screen coordinates). */
	public double[] handPoint(int row, int col, boolean flip, double feetX, double feetY) {
		double[] f = frames[row][col];
		double dx = (f[6] - f[4]) * scale;
		return new double[] { feetX + (flip ? -dx : dx), feetY + (f[7] - f[5]) * scale };
	}


	// ------------------------------------------------------------ image helpers

	/** Loads from the classpath (images/ copied into bin/ by Eclipse) or from disk. */
	static BufferedImage loadImage(String name) throws IOException {
		InputStream in = SpriteSheet.class.getResourceAsStream("/images/" + name);
		if (in != null) {
			try {
				return ImageIO.read(in);
			} finally {
				in.close();
			}
		}
		String[] paths = { "images/" + name, "src/images/" + name, "../src/images/" + name };
		for (String p : paths) {
			File file = new File(p);
			if (file.exists()) return ImageIO.read(file);
		}
		throw new IOException("Could not find image: " + name);
	}


	/** High quality downscale (halves in steps so it doesn't look jagged). */
	static BufferedImage resize(BufferedImage src, int w, int h) {
		BufferedImage cur = toArgb(src);
		int cw = cur.getWidth();
		int ch = cur.getHeight();
		do {
			if (cw > w) cw = Math.max(w, cw / 2);
			if (ch > h) ch = Math.max(h, ch / 2);
			if (cw < w) cw = w;
			if (ch < h) ch = h;
			BufferedImage next = new BufferedImage(cw, ch, BufferedImage.TYPE_INT_ARGB);
			Graphics2D g = next.createGraphics();
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
			g.drawImage(cur, 0, 0, cw, ch, null);
			g.dispose();
			cur = next;
		} while (cw != w || ch != h);
		return cur;
	}


	static BufferedImage mirror(BufferedImage src) {
		int w = src.getWidth();
		int h = src.getHeight();
		BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = out.createGraphics();
		g.drawImage(src, w, 0, -w, h, null);
		g.dispose();
		return out;
	}


	private static BufferedImage toArgb(BufferedImage src) {
		if (src.getType() == BufferedImage.TYPE_INT_ARGB) return src;
		BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = out.createGraphics();
		g.drawImage(src, 0, 0, null);
		g.dispose();
		return out;
	}
}
