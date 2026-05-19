import java.awt.*;

public class ShotMeter {
	
	
	// variables needed for shot meter implementation. *Used help from ai to create a shot meter*
	boolean   isChargingShot   = false;  // if player is "loading" up into a jumpshot
	boolean   shotReleased     = false;  // keeping track of if player let go of ball
	int       shotMeterValue   = 0;		 // tracks how full the bar is every frame
	int       shotReleaseValue = -1; 	 // is the final value of the shot meter upon release.
	final int shotMeterMax     = 100;	 // max value of shot meter, if it surpasses this max, then its a very later, and automatically shoots
	final int greenWindowStart = 80;     // starts at 80% of bar
	final int greenWindowEnd   = 92;     // ends at 92% of bar
	
	boolean oWasPressed = false;
	
	
	// Called every frame from Game's inGameLoop — handles all the per-frame meter state
	public void update(boolean pressingO, PlayerMovement player) {
		
		// Shooting mechanics

		// if the player was going for a shot and lost the ball, reset everything
		if(isChargingShot && !player.ballInHand && !player.isShooting && !player.ballInFlight) {
			isChargingShot   = false;
			shotReleased     = false;
			shotReleaseValue = -1;
			shotMeterValue   = 0;
		}

		// Detect O key RELEASE — must happen before updating oWasPressed
		if(isChargingShot && !pressingO && oWasPressed && !shotReleased) {
			shotReleased     = true;
			shotReleaseValue = shotMeterValue;
		}

		// Start shot on first press of O (hold to charge)
		if(pressingO && !oWasPressed && player.ballInHand && !isChargingShot) {

			if(player.lastDribblingSpriteSheetRow       == 840)      player.shootingUP();
			else if (player.lastDribblingSpriteSheetRow == 560)		 player.shootingDN();
			else if (player.facingLeft) 							 player.shootingLT();
			else 													 player.shootingRT();

			isChargingShot   = true;
			shotMeterValue   = 0;
			shotReleased     = false;
			shotReleaseValue = -1;
		}
		oWasPressed = pressingO;

		// Fills meter 2 units per frame while charging (~50 frames to full at 60fps)
		if(isChargingShot && !shotReleased && shotMeterValue < shotMeterMax) {
			shotMeterValue += 2;
			
			// if held on for too long (end of meter), it can no longer surpass it, so meterValue = meterMax
			if(shotMeterValue > shotMeterMax) shotMeterValue = shotMeterMax;
		}

		// if the player held on for too long and never released and shotMeterValue >= shotMeterMax
		// automatically shoots the ball and its a horrible release.
		if(isChargingShot && shotMeterValue >= shotMeterMax && !shotReleased) {
			shotReleased     = true;
			shotReleaseValue = shotMeterMax;
		}
	}
	
	
	
	
	// Returns true if the player released the meter in the green window
	// this is the green window (between the start and end of bar)
	public boolean isInGreenWindow() {
		return shotReleaseValue >= greenWindowStart && shotReleaseValue <= greenWindowEnd;
	}
	
	public boolean wasReleased() {
        return shotReleased;
    }
    
    public int getReleaseValue() {
        return shotReleaseValue;
    }
    
    public boolean isCharging() {
        return isChargingShot;
    }
    
    public void reset() {
        isChargingShot   = false;
        shotReleased     = false;
        shotReleaseValue = -1;
        shotMeterValue   = 0;
    }
    
    
    public void draw(Graphics g, int playerX, int playerY) {
        
    			if(isChargingShot) {
    				int meterX = playerX;
    				int meterY = playerY - 55;
    				int meterW = 100;
    				int meterH = 14;

    				// calculations for where the green window is
    				int greenWindowStarttPx = meterX + (int)((double)greenWindowStart / shotMeterMax * meterW);
    				int greenWindowEndPx    = meterX + (int)((double)greenWindowEnd / shotMeterMax * meterW);
    				
    				// filling in the white part of the meter
    				g.setColor(Color.WHITE);
    				g.fillRect(meterX, meterY, greenWindowStarttPx - meterX, meterH);

    				// Green window zone
    				g.setColor(Color.GREEN);
    				g.fillRect(greenWindowStarttPx, meterY, greenWindowEndPx - greenWindowStarttPx, meterH);  	// calc for green window

    				// orange part of the meter
    				g.setColor(new Color(255, 140, 0));
    				g.fillRect(greenWindowEndPx, meterY, (meterX + meterW) - greenWindowEndPx, meterH);			// calc for orange part

    				// Moving yellow line in shot meter
    				// starts at the meters beginning, clamps the value from 0 to 100, then converts it to 0.0 to 1.0, 
    				// then meterW(100) sets it to a int, which will be the pixels
    				int fillX = meterX + (int)((double)Math.min(shotMeterValue, shotMeterMax) / shotMeterMax * meterW);
    				g.setColor(Color.YELLOW);
    				
    				// shifts the rectangle 2px left so its centered, meterY draws it 3px above the bars top edge,
    				// 5 is for the line to be 5px wide, and meter + 6 is for the line to be 3px out on each side to be able
    				// to see it poke out.
    				g.fillRect(fillX - 2, meterY - 3, 5, meterH + 6);

    				// Cyan marker is to show exactly where the player let go after release
    				if(shotReleased && shotReleaseValue >= 0) {
    					int releaseX = meterX + (int)((double)shotReleaseValue / shotMeterMax * meterW);
    					g.setColor(Color.CYAN);
    					g.fillRect(releaseX - 2, meterY - 3, 5, meterH + 6);
    				}

    				// Outline
    				g.setColor(Color.BLACK);
    				g.drawRect(meterX, meterY, meterW, meterH);
    			}
    }
	
}