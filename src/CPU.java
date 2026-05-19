import java.awt.Toolkit;
import java.awt.*;

public class CPU extends PlayerMovement  {

	// State: essentially tells the CPU what to do next.
	
	String state  = "GET_BALL";
	
	
	// Hoop Target (direction where the CPU dribbles towards)
	
	int targetHoopX = 1000;
	int targetHoopY = 450;
	
	
	int roamTargetX 		= 0;
	int roamTargetY 		= 0;
	int roamSpotsTotal 		= 3 + (int)(Math.random() * 4);		// 3-6 random spots per possession
																// math.random * 4 = 0.0 - 3.99 -> 0 - 3 then + 3
	int roamSpotsVisited    = 0;
	
	// flips true when we've picked a random spot
	boolean roamTargetSet 	= false;
	
	double timeToRoam = 900 + Math.random() * 600;	// Min. 900 frames (15 seconds), Max 1500 frames (25 seconds)
	
	int stuckFrames = 0;
	int lastFrameX  = -1;
	int lastFrameY  = -1;

	// cooldown for the CPU's steal attempts
	int stealCoolDownFrames = 0;

	// cooldown for the CPU's block (jump) attempts
	int blockCoolDownFrames = 0;
													
	
	
	public CPU() {
		
		// Images
		
		playerImage 	=  Toolkit.getDefaultToolkit().getImage("images/basketballPlayer2_walk_jog_run.png");
		upDownImage 	=  Toolkit.getDefaultToolkit().getImage("images/basketballPlayer2_walkingForward_Away.png");
		dribblingImage 	=  Toolkit.getDefaultToolkit().getImage("images/basketballPlayer2_dribbling.png");
		shootingImage 	=  Toolkit.getDefaultToolkit().getImage("images/basketballPlayer2_shooting_motion.png");
		idleImage 		=  Toolkit.getDefaultToolkit().getImage("images/basketballPlayer2_Idle.png");
		reachImage      =  Toolkit.getDefaultToolkit().getImage("images/basketballPlayer2_reach_animation.png");
		jumpImage       =  Toolkit.getDefaultToolkit().getImage("images/basketballPlayer2_jumping_animation.png");

	
		
		// Spawn Point
		x = 1200;
		y = 400;
			
	}
	
	
	// asked ai for hints on how to get the cpu moving using states
	public void position(int ballX, int ballY, int playerX, int playerY) {
		
	    int walkSpeed    = 8;
	    int dribbleSpeed = 10;

	    // putting in  +/- walkSpeed helps it no longer overshoot and stop making it glitch. called a "tolerance"
	    // the size of the step.
	    
	    // State to go get the ball
	    if(state.equals("GET_BALL")) {	 					// move toward the ball in GET_BALL state   
		    if      (ballX < x - walkSpeed) moveLT(walkSpeed);
		    else if (ballX > x + walkSpeed) moveRT(walkSpeed);
	
		    if      (ballY < y - walkSpeed) moveUP(walkSpeed);
		    else if (ballY > y + walkSpeed) moveDN(walkSpeed);
	    
	    }
	    
	   // Dribbling State
	    
	    else if(state.equals("DRIBBLE")) {
	    	
	    	// if dribbling and close enough to shoot, switch state to shoot.
	    	if(Math.abs(x - targetHoopX) < 200 && Math.abs(y - targetHoopY) < 250) {
	    		state = "SHOOT";
	    		return;
	    	}
	    	 	
	    	// + and - cause of tolerance
	    	if(targetHoopX < x - dribbleSpeed) 		dribbleLT(dribbleSpeed);
	    	else if(targetHoopX > x + dribbleSpeed) dribbleRT(dribbleSpeed);
	    	
	    	if(targetHoopY < y - dribbleSpeed)		dribbleUP(dribbleSpeed);
	    	else if(targetHoopY > y + dribbleSpeed) dribbleDN(dribbleSpeed);
	    	
	    }
	    
	    // Roaming State

	    
	    else if(state.equals("ROAM")) {
	        
	        // pick a random target if we dont have one 
	        if(!roamTargetSet) {
	            roamTargetX   = (int)(300 + Math.random() * 1100);			// x range: 300 - 1400
	            roamTargetY   = (int)(500 + Math.random() * 400);			// y range: 500 - 900
	            roamTargetSet = true;										// picked a spot so go to it
	            stuckFrames   = 0;          								// reset stuck counter for new target
	        }
	        
	        // STUCK DETECTION — if position didn't change from last frame, CPU is blocked
	        if(x == lastFrameX && y == lastFrameY) {
	            stuckFrames++;
	        } else {
	            stuckFrames = 0;
	        }
	        
	        // saving position for next frames comparison
	        lastFrameX = x;
	        lastFrameY = y;
	        
	        // Set reachedTarget to true if the CPU is within 10 pixels of the target in BOTH 
	        // the horizontal AND vertical directions. Otherwise, set it to false
	        // is the CPU close enough (10 pixel range) of the target location
	        boolean reachedTarget;
	        	if(Math.abs(roamTargetX - x) <= dribbleSpeed && Math.abs(roamTargetY - y) <= dribbleSpeed) reachedTarget = true;
	        	else																					   reachedTarget = false;
	        
	       
	        // if it has been stuck for 30 frames (half a second)
	        boolean isStuck;	        
		        if(stuckFrames >= 30) isStuck = true;
		        else 				  isStuck = false;
	        
		    
		    // forcing to find new position if reached or is stuck
	        if(reachedTarget || isStuck) {
	            roamSpotsVisited++;
	            
	            // shoot if met the spots minimum
	            if(roamSpotsVisited >= roamSpotsTotal) {
	                state = "SHOOT";
	            } 
	            
	            // otherwise, keep going to new spots
	            else {
	                roamTargetSet = false;
	            }
	        }
	        
	        // fallback timer, if everything else fails force a shot
	        if(timeToRoam > 0.0) timeToRoam--;
	        if(timeToRoam <= 0.0) {
	            state = "SHOOT";
	        }
	        
	        // Movement toward target
	        if(roamTargetX < x - dribbleSpeed)         dribbleLT(dribbleSpeed);
	        else if(roamTargetX > x + dribbleSpeed)    dribbleRT(dribbleSpeed);
	        
	        if(roamTargetY < y - dribbleSpeed)         dribbleUP(dribbleSpeed);
	        else if(roamTargetY > y + dribbleSpeed)    dribbleDN(dribbleSpeed);
	    }
	 
	    
	    
	    // Shooting State
	    
	    else if(state.equals("SHOOT")) {
	    	shootingLT();
	    	state        = "WAIT";			// temporary state, CPU just stands until ball flight ends and we tell it to get the ball.
	    }
	    
	    
	    else if (state.equals("RELEASING")) {
	        if (!isShooting) {     // animation finished naturally
	            state = "WAIT";    // ball is now in flight thanks to draw()
	        }
	    }
	    
	    
	    // Defensive State

	    else if(state.equals("DEFEND")) {
	    	// this variable is for the CPU to keep a distance and not clog the player so much
	    	// to the point where ai wants to go.
	    	int defensiveDistance = 160;
	    	int defensiveSpeed    = 10 ;

	    	 // Movement toward target
	        if(playerX < x - defensiveDistance)         moveLT(defensiveSpeed);
	        else if(playerX > x + defensiveDistance)    moveRT(defensiveSpeed);

	        if(playerY < y - defensiveDistance)         moveUP(defensiveSpeed);
	        else if(playerY > y + defensiveDistance)    moveDN(defensiveSpeed);
	    }
	}



	// CPU reaching / stealing ball from player
	public void attemptSteal(PlayerMovement player, Ball ball) {

	    Rect playerBodyRect = new Rect(player.getX(), player.getY(), player.lrPlayerWidth, player.lrPlayerHeight);
	    Rect myRect         = new Rect(getX(), getY(), lrPlayerWidth, lrPlayerHeight);

	    // countdown the CPU's steal cooldown
	    if(stealCoolDownFrames > 0) stealCoolDownFrames--;

	    // CPU attempts when ball is in players hand and it is close enough.
	    if(player.ballInHand && physics == false && stealCoolDownFrames == 0
	            && myRect.overlaps(playerBodyRect) && Math.random() < 0.05) {		// the math.random makes the cpu only reach 5% of the time

	        reach();
	        stealCoolDownFrames = 180;     // 3 second cooldown for when the cpu can reach again

	        double roll = Math.random();

	        // CPU successfully steals the ball
	        if(roll < 0.2) {
	            player.resetAfterLosingBall();
	            ballInHand        = true;
	            state             = "ROAM";
	            timeToRoam        = 900 + Math.random() * 600;
	            roamTargetSet     = false;
	            roamSpotsTotal    = 3 + (int)(Math.random() * 4);
	            roamSpotsVisited  = 0;
	        }

	        // CPU knocks the ball loose
	        else if(roll < 0.5) {
	            player.resetAfterLosingBall();
	            state = "GET_BALL";

	            ball.x = (int)(player.getX() + (Math.random() - 0.5) * 800);
	            ball.y = (int)(player.getY() + (Math.random() - 0.5) * 200);

	            ball.rebuildRect();
	        }

	        // otherwise CPU misses, player keeps dribbling
	        else {

	        }
	    }
	}



	// CPU blocking decision — jump when the player has shot the ball and it's close
	public void attemptBlock(PlayerMovement player, Ball ball) {

	    if(blockCoolDownFrames > 0) blockCoolDownFrames--;

	    int ballToCpuDistanceX = Math.abs(ball.x - getX());
	    int ballToCpuDistanceY = Math.abs(ball.y - getY());

	    if(player.ballInFlight && physics == false && blockCoolDownFrames == 0
	            && ballToCpuDistanceX < 300 && ballToCpuDistanceY < 300 && Math.random() < 0.15) {
	        // jump when distance is less than 300px and all other conditions met. jumps <0.15 of the frames

	        jump();
	        blockCoolDownFrames = 240;									// only allowed to jump every 4 seconds
	    }
	}
}
