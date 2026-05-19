import java.awt.*;
import java.awt.event.*;

public class Game extends GameBase {
	
	Image courtImage = Toolkit.getDefaultToolkit().getImage("images/basketballCourtForGame.jpg");	

	PlayerMovement player 	  = new PlayerMovement();
	CPU			   cpu    	  = new CPU();
	ScoreBoard     scoreBoard = new ScoreBoard();
	Ball		   ball       = new Ball();
	Court		   court      = new Court();
	ShotMeter      shotMeter  = new ShotMeter();
	
	int walkSpeed 	 = 8;   // 8 pixels every frame
	int runSpeed  	 = 14;
	int dribbleSpeed = 10;
	
	int savedShootingRow;
	int savedShootingX;
	
	boolean savedFacingLeft;
	boolean pWasPressed   		 = false;		// needed to detect when P was pressed to pick up the ball.
	boolean oWasPressed    		 = false;		// needed to detect when O was pressed to shoot the ball.
	boolean mWasPressed          = false;       // needed to detect when M was pressed to reach.
	boolean playerWasInFlight    = false;
	boolean cpuWasInFlight 		 = false;

	
	// players prev location
	int playerPrevX;
	int playerPrevY;
	int cpuPrevX;
	int cpuPrevY;
	
	
	// variable which controls the steal button to register once every 3 seconds
	int stealCoolDownFrames = 0;
	
	// offset for the ball to follow the hands while shooting the ball in each frame.
	int[] handOffsetX = { 160, 165, 170, 175, 180 };   // adjust these
	int[] handOffsetY = {  80,  60,  40,  20,  0 };    // adjust these
	
	Rect playerRect;
	Rect cpuRect;
	

	
	public void inGameLoop() {
			
		// getting players previous x and y from the methods in playerMovement
		// need to save before any movement is done
		
		playerPrevX = player.getX();
		playerPrevY = player.getY();	
		
		
		
		
		// using a rectangle for the ball to check if a player is near it to be able to pick it up
		ball.rect   = new Rect(ball.x, ball.y, 50, 50);


		// Player Movements
		
		if (pressing[_A] && player.ballInHand)	player.dribbleLT(dribbleSpeed);
		else if (pressing[_A] && pressing[_C])  player.runLT(runSpeed);
		else if (pressing[_A])             		player.moveLT(walkSpeed);

		if (pressing[_D] && player.ballInHand)	player.dribbleRT(dribbleSpeed);
		else if (pressing[_D] && pressing[_C])  player.runRT(runSpeed);
		else if (pressing[_D])             		player.moveRT(walkSpeed);

		if (pressing[_W] && player.ballInHand)	player.dribbleUP(dribbleSpeed);
		else if (pressing[_W] && pressing[_C])  player.runUP(runSpeed);
		else if (pressing[_W])             		player.moveUP(walkSpeed);
		
		if (pressing[_S] && player.ballInHand)	player.dribbleDN(dribbleSpeed);
		else if (pressing[_S] && pressing[_C])  player.runDN(runSpeed);
		else if (pressing[_S])             		player.moveDN(walkSpeed);
		
		
		// calling the shot meter and passing the shot button with the player object
		shotMeter.update(pressing[_O], player);

		// when player presses O and shot starts, fire the shooting animation
		if(pressing[_O] && !shotMeter.oWasPressed && player.ballInHand && !shotMeter.isChargingShot) {
		    savedShootingRow = player.lastDribblingSpriteSheetRow;
		    savedFacingLeft  = player.facingLeft;
		    savedShootingX   = player.getX();
		    
		    // trigger animation
		    if     (savedShootingRow == 840)  player.shootingUP();
		    else if(savedShootingRow == 560)  player.shootingDN();
		    else if(player.facingLeft)        player.shootingLT();
		    else                              player.shootingRT();
		}
		
					
		// Jumping + Physics

		if (pressing[SPACE])  player.jump();
							  player.move();
								 cpu.move();
			    
		
		// Build playerRect with current position AFTER all player movement this frame
		playerRect = new Rect(player.getX(), player.getY(), player.lrPlayerWidth, player.lrPlayerHeight);
		
		


	    // Using this to close the applet
		if(pressing[ESC])  System.exit(0);
			
		
		
		if(player.ballInFlight || cpu.ballInFlight) {
			
			if(!playerWasInFlight && !cpuWasInFlight) {
				setShotVelocity();
			}
				
							
			// player blocking cpu logic
			if(cpu.ballInFlight && player.physics == true && !ball.blocked && playerRect.overlaps(ball.rect)) {
					ball.blocked   = true;
					ball.velocityX = -ball.velocityX * 0.7;
	                ball.velocityY =  ball.velocityY * 0.7;
			}

			
			// move the ball every frame so it aligns with the hands
			ball.update();

			
			// update ball.rect to current position so all collision checks below are accurate
			ball.rect = new Rect(ball.x, ball.y, 50, 50);

			// ball center is used for rim zone check and scoring detection
			int ballCenterX = ball.x + 25;
			int ballCenterY = ball.y + 25;


			court.handleBackboardCollisions(ball);

			checkScoring(ballCenterX, ballCenterY);

			court.handleRimCollisions(ball, ballCenterY);
		}
		
		
		// calls the method to see if the ball hit any of the out of bound invisible walls
		endFlightOnBoundary();

		playerWasInFlight    = player.ballInFlight;
		cpuWasInFlight       = cpu.ballInFlight;
			
				  

		// always keeps the ball inbounds at a reachable position if missed shot or knocked loose out of bounds
		if(!player.ballInFlight && !cpu.ballInFlight && !player.ballInHand && !cpu.ballInHand) {
		    ball.clampToCourt();	// clampToCourt pushes the ball back in bounds
		}
		
		
		
		

		
		
			
			handleBallPickupDrop();
			playerAttemptSteal();
			
			// if the cpu is in defend mode and the ball becomes loose on the ground, make the cpu chase and pick up the ball
			if(cpu.state.equals("DEFEND") && !player.ballInHand && !player.ballInFlight && !cpu.ballInHand) {
				cpu.state = "GET_BALL";
			}			
			
		

			player.checkBoundsAndSlide(court.bounds, playerPrevX, playerPrevY);
		
			
		
										// CPU LOGIC (has to be saved after movements)

		// 1. Save where the CPU IS, before moving
		cpuPrevX = cpu.getX();
		cpuPrevY = cpu.getY();

		// 2. Move the CPU
		cpu.position(ball.x, ball.y, player.getX(), player.getY());
		

		// 3. Check collision against bounds (axis-separated, lets CPU slide along walls)
		cpu.checkBoundsAndSlide(court.bounds, cpuPrevX, cpuPrevY);

		// Rebuild final cpuRect for downstream checks
		cpuRect = new Rect(cpu.getX(), cpu.getY(), cpu.lrPlayerWidth, cpu.lrPlayerHeight);

		
		
		cpu.attemptSteal(player, ball);
		cpu.attemptBlock(player, ball);
		    
		

		// 4. Pickup logic — don't pick up the ball while it's in flight
		if (!cpu.ballInHand && !player.ballInHand && !cpu.ballInFlight && !player.ballInFlight && cpuRect.overlaps(ball.rect)) {
			    cpu.ballInHand       = true;
			    cpu.state            = "ROAM";
			    cpu.timeToRoam 		 = 900 + Math.random() * 600;				// Min. 900 frames (15 seconds), Max 1500 frames (25 seconds)	
				cpu.roamTargetSet	 = false;									// forces a new random spot next possession
				cpu.roamSpotsTotal   = 3 + (int)(Math.random() * 4);
				cpu.roamSpotsVisited = 0;
		}
		
		
		
		// getting the x and y for the players hands and then adding offsets to make sure it aligns with the hands.
		if (cpu.ballInHand) {
		    ball.x = cpu.getX() + 150;
		    ball.y = cpu.getY() + 90;
		}	
		
		
		
		/* Player and CPU collision detection with one another */
		handlePlayerCpuCollision();
		
		
		
	}

	
	
	
	
	
	
	private void playerAttemptSteal() {
	    
	    // player reaching / stealing ball from cpu	
	    Rect cpuBodyRect = new Rect(cpu.getX(), cpu.getY(), cpu.lrPlayerWidth, cpu.lrPlayerHeight);
	    
	    // our countdown timer for being able to register the steal button every 3 seconds so we 
	    // wont be able to spam the steal button.
	    if(stealCoolDownFrames > 0) stealCoolDownFrames--;
	    
	    // player reaching / stealing ball from cpu	
	    if(pressing[_M] && !mWasPressed && cpu.ballInHand && player.physics == false && stealCoolDownFrames == 0
	            && playerRect.overlaps(cpuBodyRect)) {
	        
	        player.reach();
	        stealCoolDownFrames = 180;				// 180 / 60fps = 3 seconds
	        
	        double roll = Math.random();
	        
	        // if player steals the ball
	        if(roll < 0.2) {
	            cpu.resetAfterLosingBall();
	            player.ballInHand = true;
	            cpu.state         = "DEFEND";
	        }
	        
	        // if player knocks the ball loose
	        else if(roll < 0.5) {
	            cpu.resetAfterLosingBall();
	            cpu.state = "GET_BALL";
	            
	            ball.x = (int)(cpu.getX() + (Math.random() - 0.5) * 800);  // ±400px
	            ball.y = (int)(cpu.getY() + (Math.random() - 0.5) * 200);
	            
	            ball.rect = new Rect(ball.x, ball.y, 50, 50);
	        }
	        
	        // if none of the above happens, opposing player keeps shooting or dribbling
	        else {
	            
	        }
	    }
	    
	    mWasPressed = pressing[_M];						// saving the state of M to check for future frames
	}
	
	
	
	
	
	
	
	
	
	private void checkScoring(int ballCenterX, int ballCenterY) {
    
    // Scoring detection — runs BEFORE rim collision so the rim bounce doesn't cancel it
    // savedShootingX holds where the player was standing when they shot to determine 2 or 3
    
    // Save who shot BEFORE we reset ballInFlight in the scoring branches
    boolean playerShot = player.ballInFlight;
    
    // right basket
    if(ball.velocityY > 0 && ballCenterY > 462 && ballCenterY < 487		// if ball goes through basket
            && ballCenterX > 1461 && ballCenterX < 1520) {
        
        // if behind the 3 point line (right side line is at x=1098)
        int points = (savedShootingX < 1098) ? 3 : 2;
        
        // Award the shooter
        if(playerShot) scoreBoard.addPlayerPoints(points);
        else           scoreBoard.addCpuPoints(points);
        
        player.ballInFlight  = false;
        cpu.ballInFlight     = false;
        if (cpu.state.equals("WAIT")) cpu.state = "GET_BALL";
        ball.velocityX        = 0;
        ball.velocityY        = 0;
        cpu.ballInHand 		 = true;
        cpu.setPosition(1250, 600);
        cpu.state 			 = "ROAM";
        cpu.timeToRoam 		 = 900 + Math.random() * 600;			    // Min. 900 frames (15 seconds), Max 1500 frames (25 seconds)
        cpu.roamTargetSet	 = false;									// forces a new random spot next possession
        cpu.roamSpotsTotal   = 3 + (int)(Math.random() * 4);			// 3 - 6 spots to be visited
        cpu.roamSpotsVisited = 0;										// reset back to 0 visited spots for next possession
    }
    
    // left basket
    if(ball.velocityY > 0 && ballCenterY > 462 && ballCenterY < 487
            && ballCenterX > 191 && ballCenterX < 250) {
        
        // if behind the 3 point line (left side line is at x=612)
        int points = (savedShootingX > 612) ? 3 : 2;
        
        // Award the shooter
        if(playerShot) scoreBoard.addPlayerPoints(points);
        else           scoreBoard.addCpuPoints(points);

        player.ballInFlight = false;
        cpu.ballInFlight    = false;				
        ball.velocityX       = 0;
        ball.velocityY       = 0;
        player.ballInHand 	= true;
        cpu.state			= "DEFEND";
        player.setPosition(290, 600);
        playerPrevX = 300;
        playerPrevY = 480;
    }
}
	
	
	
	
	
	
	private void setShotVelocity() {
	    
	    ball.blocked = false;			// resets at the start of a new shot
	    
	    
	    // player shooting values 
	    if (player.ballInFlight) {

	        // player's shot — ball starts at hand position
	        ball.x = player.getX() + 150;
	        ball.y = player.getY() + 90;


	        double greenWindowCenter = (shotMeter.greenWindowStart + shotMeter.greenWindowEnd) / 2.0;   // 86.0
	        
	        // basically determines the velocities of the shot
	        double powerFactor;

	        // this is the green window (between the start and end of bar)
	        boolean inGreenWindow = (shotMeter.shotReleaseValue >= shotMeter.greenWindowStart && 
	        							shotMeter.shotReleaseValue <= shotMeter.greenWindowEnd);

	        if(inGreenWindow) {
	            // velocity values stay the same every time, no randomness
	            powerFactor = 1.0;
	        } 
	        
	        else {
	            // Outside green window, powerFactor velocity values are now based on the release of the shot
	            // so it can change a lot
	            double deviation = (shotMeter.shotReleaseValue - greenWindowCenter) / (shotMeter.shotMeterMax / 2.0);
	                 powerFactor = 1.0 + deviation * 0.35;
	        }

	        // Shooting away from camera
	        if(savedShootingRow == 840) {
	            ball.velocityX = 0;						// is 0 since not shooting horizontally in this case
	            ball.velocityY = -18 * powerFactor;

	        } 
	        
	        // shooting toward camera
	        else if(savedShootingRow == 560) {
	            ball.velocityX = 0;					   // is 0 since not shooting horizontally in this case
	            ball.velocityY = -10 * powerFactor;

	        } 
	        
	        // shooting left or right
	        else {
	            
	            int    hoopTargetX;
	            int    hoopTargetY = 450;
	            
	                if(savedFacingLeft) {
	                    hoopTargetX = 195;
	                }
	                
	                else {
	                    hoopTargetX = 1465;
	                }
	                
	            // how far in distance x and y need to travel to the hoop based on the location of the player.
	            double distanceX      = hoopTargetX - (player.getX() + 150);
	            double distanceY      = hoopTargetY - (player.getY() + 150);

	            // how many frames the ball should be in the air, based on X distance at a reference speed of 12px a frame
	            double time    		  = Math.abs(distanceX) / 12.0;
	            
	            // needed to help the ball go downward when approaching the hoop
	            double minFlightTime  = Math.sqrt(2.0 * Math.abs(distanceY) / ball.gravity) * 1.05;
	            
	            // if the time is too short, gets replaced by the minimum flight time needed
	            if(time < minFlightTime) time = minFlightTime;

	            // Ideal velocities that arc the ball exactly to the hoop center
	            // ball needs to travel a X amount of distance in this amount of time
	            double idealVX = distanceX / time;
	            
	            // helps pull the ball downward over time into the basket
	            double idealVY = (distanceY - 0.5 * ball.gravity * time * time) / time;

	            
	            // jitter makes bad releases feel punishing, and random velocities added or subtracted by 75
	            double jitter;
	            
	            if(inGreenWindow) {
	                jitter = 0;
	            }
	            
	            else {
	                jitter = (Math.random() - 0.5) + 1.5;
	            }

	            // applying everything all together to get the velocities on how the ball is going to move
	            ball.velocityX = idealVX * powerFactor + jitter;
	            ball.velocityY = idealVY * powerFactor + jitter;
	        }

	        // resetting shot meter values
	        shotMeter.reset();
	    }
	    
	    
	    
	    // CPU shooting values and where to shoot
	    else {
	        int hoopTargetX = 195;
	        int hoopTargetY = 450;
	        
	        double distanceX      = hoopTargetX - (cpu.getX() + 150);
	        double distanceY      = hoopTargetY - (cpu.getY() + 150);

	        double time    		  = Math.abs(distanceX) / 12.0;
	        double minFlightTime  = Math.sqrt(2.0 * Math.abs(distanceY) / ball.gravity) * 1.05;
	                
	        if(time < minFlightTime) time = minFlightTime;

	        double idealVX = distanceX / time;
	        double idealVY = (distanceY - 0.5 * ball.gravity * time * time) / time;
	        
	        double powerFactor = 1.0;
	        double jitter;
	        
	        
	        // number from 0.0 to 1.0. CPU hits 40% of shots
	        if(Math.random() > 0.6) {
	            jitter = 0;
	        }
	        
	        // 60% when it does miss, miss time it randomly.
	        else {
	            jitter = (Math.random() - 0.5) * 1.5;
	        }
	        
	        // applying everything all together to get the velocities on how the ball is going to move
	        ball.velocityX = idealVX * powerFactor + jitter;
	        ball.velocityY = idealVY * powerFactor + jitter;
	        
	        savedShootingX = cpu.getX();    // used to detect 2pt and 3pt positions
	    }
	}
	
	
	
	private void handleBallPickupDrop() {
	    
		// if pressing P and pWasPressed becomes true
	    if(pressing[_P] && !pWasPressed){
	        
	        // if the ball is NOT in anyone's hand and not in flight, and they're near it
	        if(!player.ballInHand && !cpu.ballInHand && !player.ballInFlight && !cpu.ballInFlight
	            && playerRect.overlaps(ball.rect)){
		            player.ballInHand = true;				// pick up is only allowed when near the ball
		            cpu.state         = "DEFEND";
	        }
	        else if(player.ballInHand) {
	            player.ballInHand = false;						// drop ball is allowed anywhere
	            ball.x = player.getX() + 150;					// ball lands at player's feet
	            ball.y = player.getY() + 210;
	        }
	    }
	    
	    pWasPressed = pressing[_P];						// saving the state of P to check for future frames
	}
	
	
	
	private void endFlightOnBoundary() {
	    
	    // stop flight when ball hits the floor
	    if(ball.y >= ball.floorY) {
	        ball.y = ball.floorY;
	        endFlight();
	    }

	    // stop flight when ball hits the left wall
	    if(ball.x <= ball.leftWall) {
	        ball.x = ball.leftWall;
	        endFlight();
	    }

	    // stop flight when ball hits the right wall
	    else if(ball.x >= ball.rightWall) {
	        ball.x = ball.rightWall;
	        endFlight();
	    }
	}

	private void endFlight() {
	    player.ballInFlight = false;
	    cpu.ballInFlight    = false;
	    if (cpu.state.equals("WAIT")) cpu.state = "GET_BALL";
	    ball.stop();
	}
	
	
	
	
	// Resolves overlap between the player and CPU
	// First tries to snap both back to their last positions; if still stuck, forces a push apart
	private void handlePlayerCpuCollision() {
	    
	    Rect playerRectDefense = new Rect(player.getX(), player.getY(), player.lrPlayerWidth - 150, player.lrPlayerHeight - 150);
	    Rect cpuRectDefense    = new Rect(cpu.getX(), cpu.getY(), cpu.lrPlayerWidth - 150, cpu.lrPlayerHeight - 150);

	    if(playerRectDefense.overlaps(cpuRectDefense)) {
	        
	        // First try setting them back to previous position
	        cpu.setPosition(cpuPrevX, cpuPrevY);
	        player.setPosition(playerPrevX, playerPrevY);
	        
	        // Rebuild rectangles to check if setting them back to previous positions
	        playerRectDefense = new Rect(player.getX(), player.getY(), player.lrPlayerWidth - 150, player.lrPlayerHeight - 150);
	        cpuRectDefense    = new Rect(cpu.getX(), cpu.getY(), cpu.lrPlayerWidth - 150, cpu.lrPlayerHeight - 150);
	        
	        // If still stuck after setting them back, force a separation push
	        if(playerRectDefense.overlaps(cpuRectDefense)) {
	            int push = 5; // 5px
	            
	            // Now checking to see which direction are cpu and player stuck with.  
	            // Are they to the left or right of one another
	            if(player.getX() < cpu.getX()) {
	                player.setPosition(player.getX() - push, player.getY());
	                cpu.setPosition(cpu.getX() + push, cpu.getY());
	            } else {
	                player.setPosition(player.getX() + push, player.getY());
	                cpu.setPosition(cpu.getX() - push, cpu.getY());
	            }
	        }
	    }
	}
	
	
	
	
	
	// Draws the player and CPU sprites
	private void drawPlayers(Graphics g) {
	    player.draw(g);
	       cpu.draw(g);
	}


	// Draws the ball based on current state — in-hand, in-flight, shooting, or loose
	private void drawBall(Graphics g) {
	    
	    // when shooting the ball draw it at the hands
	    if(player.isShooting) {
	        g.drawImage(ball.image,
	                player.getX() + handOffsetX[player.spriteSheetFrame],
	                player.getY() + handOffsetY[player.spriteSheetFrame],
	                70, 70, null);
	    }
	    
	    else if (cpu.isShooting) {
	        g.drawImage(ball.image,
	            cpu.getX() + handOffsetX[cpu.spriteSheetFrame],
	            cpu.getY() + handOffsetY[cpu.spriteSheetFrame],
	            70, 70, null);
	    }
	    
	    // when the player or cpu is moving to release the ball. (updating ball.x and ball.y happens inGameLoop()
	    else if(player.ballInFlight || cpu.ballInFlight) {
	        g.drawImage(ball.image, ball.x, ball.y, 70, 70, null);
	    }
	    
	    // if ball is in players hand draw nothing since the sprite takes care of it
	    else if(player.ballInHand || cpu.ballInHand) {
	        
	    }
	    
	    // if ball is not in shooting motion or in the players hand, draw it at the x and y where it gets dropped
	    else {
	        g.drawImage(ball.image, ball.x, ball.y, 70, 70, null);
	    }
	}


	// Debug visuals — out of bounds rectangles for testing the boundaries
	private void drawDebug(Graphics g) {
	    
	    for(int i = 0; i < court.bounds.length; i++) {
	        g.setColor(Color.CYAN);
	        court.bounds[i].draw(g);
	    }
	}
	
	
	
	
	
	
	
	
	public void paint(Graphics g) {
	    
	    g.drawImage(courtImage, 0, 0, getWidth(), getHeight(), null);
	    
	    drawPlayers(g);
	    drawBall(g);
	    drawDebug(g);
	    
	    shotMeter.draw(g, player.getX(), player.getY());
	    scoreBoard.draw(g);
	}
	

}
 