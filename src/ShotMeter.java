import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;

/**
 * Shot meter: hold O to fill the bar, let go inside the green window for a perfect shot.
 * Releasing early makes the shot fall short, late makes it go long.
 */
public class ShotMeter {

	boolean   isChargingShot   = false;  // player is "loading" up a jump shot
	boolean   shotReleased     = false;  // player let go of O
	int       shotMeterValue   = 0;      // how full the bar is
	int       shotReleaseValue = -1;     // value of the bar when O was released
	final int shotMeterMax     = 100;    // reaching this forces a (bad) release
	final int greenWindowStart = 80;
	final int greenWindowEnd   = 92;


	public void start() {
		isChargingShot   = true;
		shotReleased     = false;
		shotMeterValue   = 0;
		shotReleaseValue = -1;
	}

	public void reset() {
		isChargingShot   = false;
		shotReleased     = false;
		shotMeterValue   = 0;
		shotReleaseValue = -1;
	}


	/** Called every frame with whether O is held. */
	public void update(boolean holdingShoot) {
		if (!isChargingShot || shotReleased) return;

		if (!holdingShoot) {
			shotReleased     = true;
			shotReleaseValue = shotMeterValue;
			return;
		}

		// fills 2 units per frame (~50 frames to full)
		shotMeterValue = Math.min(shotMeterMax, shotMeterValue + 2);
		if (shotMeterValue >= shotMeterMax) {
			shotReleased     = true;
			shotReleaseValue = shotMeterMax;
		}
	}

	/** Makes sure a release value exists (used at the moment the ball leaves the hands). */
	public void forceRelease() {
		if (!shotReleased) {
			shotReleased     = true;
			shotReleaseValue = shotMeterValue;
		}
	}

	public boolean isInGreenWindow() {
		return shotReleaseValue >= greenWindowStart && shotReleaseValue <= greenWindowEnd;
	}

	/** -1 (very early) .. 0 (perfect) .. +0.3 (very late). */
	public double releaseOffset() {
		double center = (greenWindowStart + greenWindowEnd) / 2.0;
		return (shotReleaseValue - center) / (shotMeterMax / 2.0);
	}


	public void draw(Graphics2D g, double centerX, double topY) {
		if (!isChargingShot) return;

		int meterW = 110, meterH = 14;
		int meterX = (int) Math.round(centerX - meterW / 2.0);
		int meterY = (int) Math.round(topY);
		int greenStartPx = meterX + (int) ((double) greenWindowStart / shotMeterMax * meterW);
		int greenEndPx   = meterX + (int) ((double) greenWindowEnd / shotMeterMax * meterW);

		g.setColor(new Color(0, 0, 0, 140));
		g.fillRect(meterX - 4, meterY - 7, meterW + 8, meterH + 14);

		g.setColor(Color.WHITE);
		g.fillRect(meterX, meterY, greenStartPx - meterX, meterH);
		g.setColor(new Color(33, 210, 74));
		g.fillRect(greenStartPx, meterY, greenEndPx - greenStartPx, meterH);
		g.setColor(new Color(255, 140, 0));
		g.fillRect(greenEndPx, meterY, meterX + meterW - greenEndPx, meterH);

		// moving yellow line
		int fillX = meterX + (int) ((double) shotMeterValue / shotMeterMax * meterW);
		g.setColor(new Color(255, 230, 0));
		g.fillRect(fillX - 2, meterY - 4, 5, meterH + 8);

		// cyan marker where the player let go
		if (shotReleased && shotReleaseValue >= 0) {
			int releaseX = meterX + (int) ((double) shotReleaseValue / shotMeterMax * meterW);
			g.setColor(Color.CYAN);
			g.fillRect(releaseX - 2, meterY - 4, 5, meterH + 8);
		}

		g.setColor(Color.BLACK);
		g.setStroke(new BasicStroke(2));
		g.drawRect(meterX, meterY, meterW, meterH);
	}
}
