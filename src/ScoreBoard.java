import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;

/** Scores, drawn onto the jumbotron that is part of the court art. */
public class ScoreBoard {

	public static final int WIN_SCORE = 21;

	int playerScore = 0;
	int cpuScore    = 0;

	private final Font scoreFont = new Font("SansSerif", Font.BOLD, 44);
	private final Font labelFont = new Font("SansSerif", Font.BOLD, 14);
	private final Font infoFont  = new Font("SansSerif", Font.BOLD, 17);


	public void reset() {
		playerScore = 0;
		cpuScore    = 0;
	}

	public void addPlayerPoints(int points) { playerScore += points; }
	public void addCpuPoints(int points)    { cpuScore += points; }


	public void draw(Graphics2D g) {
		g.setFont(scoreFont);
		drawCentered(g, String.valueOf(playerScore), 795, 214, new Color(255, 225, 77));
		drawCentered(g, String.valueOf(cpuScore),    918, 214, new Color(255, 107, 240));

		g.setFont(labelFont);
		drawCentered(g, "YOU", 795, 169, new Color(27, 21, 48));
		drawCentered(g, "CPU", 918, 169, new Color(27, 21, 48));

		g.setFont(infoFont);
		drawCentered(g, "FIRST TO " + WIN_SCORE, 856, 302, new Color(159, 179, 255));
	}


	/** Draws text centered horizontally and vertically on (cx, cy). */
	static void drawCentered(Graphics2D g, String text, double cx, double cy, Color color) {
		FontMetrics fm = g.getFontMetrics();
		int x = (int) Math.round(cx - fm.stringWidth(text) / 2.0);
		int y = (int) Math.round(cy + (fm.getAscent() - fm.getDescent()) / 2.0);
		g.setColor(color);
		g.drawString(text, x, y);
	}
}
