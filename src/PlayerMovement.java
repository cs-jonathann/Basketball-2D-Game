import java.awt.*;

public class PlayerMovement {

	// Images
	
	protected Image playerImage    = Toolkit.getDefaultToolkit().getImage("images/basketballPlayer1_walk_jog_run.png");
	protected Image upDownImage    = Toolkit.getDefaultToolkit().getImage("images/basketball1Player_walkingForward_Away.png");	
	protected Image dribblingImage = Toolkit.getDefaultToolkit().getImage("images/basketballPlayer1_dribbling.png");
	protected Image shootingImage  = Toolkit.getDefaultToolkit().getImage("images/basketballPlayer1_shooting_motion.png");
	protected Image idleImage      = Toolkit.getDefaultToolkit().getImage("images/basketballPlayer1_Idle.png");
	protected Image jumpImage      = Toolkit.getDefaultToolkit().getImage("images/basketballPlayer1_jumping_animation.png");
	protected Image reachImage     = Toolkit.getDefaultToolkit().getImage("images/basketballPlayer1_reach_animation.png");

	
	// Player Spawn point
	int x = 550;
	int y = 700;

	// Display size on screen
	final int lrPlayerWidth      	  = 250;
	final int lrPlayerHeight     	  = 250;
	final int udPlayerWidth	     	  = 120;
	final int udPlayerHeight     	  = 400;
	final int dribblingWidth     	  = 220;
	final int dribblingHeight         = 220;
	final int shootingPlayerWidth     = 305;
	final int shootingPlayerHeight    = 305;
	final int idlePlayerWidth	      = 300;
	final int idlePlayerHeight        = 250;
	final int jumpPlayerWidth		  = 280;
	final int jumpPlayerHeight        = 390;

	// Left/right sprite frame size
	final int lrFrameSize    = 362;
	final int lrTotalFrames  = 4;

	// Up/down sprite frame size — 8 frames per row, each frame is ~157px wide x 627px tall
	final int udFrameWidth      = 157;
	final int udAwayCropOffset  = 4;			// needed so any pixels from other frames dont leak into current frame
	final int udFrameHeight     = 627;
	final int udTotalFrames     = 8;
	
	
	// dribbling sprite frame size
	final int dribblingFrameWidth  = 285;
	final int dribblingFrameHeight = 280;
	final int dribblingTotalFrames = 5;
	
	
	// shooting sprite size
	final int shootingFrameWidth     = 280;
	final int shootingFrameHeight    = 280;
	final int shootingTotalFrames    = 5;
	final int shootingAnimationSpeed = 10;
	
	
	// idle sprite size
	final int idleFrameSize        = 627;
	final int idleAwayYOffset      = 80;		// away idle frame sits higher in the crop, this pushes it back down
	final int idleAwayXOffset      = -18;
	final int idleForwardXOffset   = -20;		// aligning the frames in the proper position
	final int idleForwardYOffset   = -20;
	
		
	// jumping sprite size
	final int jumpingAnimationSpeed = 10;
	final int jumpingTotalFrames    = 6;
	final int jumpingFrameWidth     = 362;
	final int jumpingFrameHeight    = 724;
	
	
	
	// reach sprite size
	final int reachingAnimationSpeed = 10;
	final int reachingTotalFrames    = 3;
	final int reachingFrameWidth     = 520;
	final int reachingFrameHeight    = 512;
	
	
	
	// Sprite sheet animation
	int spriteSheetFrame      		  = 0;
	int frameTimer             		  = 0;
	final int udAnimationSpeed 		  = 8;
	final int lrAnimationSpeed 		  = 16;
	final int dribblingAnimationSpeed = 10;


	// Player "Floor"
	int player_y_position = 810;

	
	// Physics
	public boolean physics  		= false;
	boolean walking         		= false;	// moving left or right
	boolean running         		= false;	// running left or right
	boolean movingAway      		= false;	// moving up (away from camera)
	boolean movingForward   		= false;	// moving down (toward camera)
	boolean facingLeft      		= false;
	boolean ballInHand      		= false;
	boolean isShooting              = false;
	boolean ballInFlight 			= false;
	boolean wasMovingUpDown    		= false;	// tracks if last move was up/down
	boolean isJumping				= false;    // tracks if player jumped
	boolean isReaching              = false;    // reaching for ball
	
	
	int lastUdSpriteSheetRow 	    = 0;		// remembers last up/down row frame (using this variable to hold pose)
	int lastDribblingSpriteSheetRow = 0;		// remembers last dribbling up/down row frame (to hold pose)
	
	double  gravity         		= 0.8;
	double  velocityX;
	double  velocityY;
	
	// where the ball sits in the player's hand per direction
	int ballHandOffsetLRx = 160;
	int ballHandOffsetLRy = 80;
	// (repeat for UD, away, forward)


	// getting players x and y so we can use it during collision detection
	public int getX() { return x; }
	public int getY() { return y; }


	public void setPosition(int x, int y)
	{
		this.x = x;
		this.y = y;
	}


	// player moving left
	public void moveLT(int moveAmount)
	{
		x = x - moveAmount;
		facingLeft = true;
		walking    = true;
	}

	// player moving right
	public void moveRT(int moveAmount)
	{
		x = x + moveAmount;
		facingLeft = false;
		walking    = true;
	}

	// player moving up (away from camera)
	public void moveUP(int moveAmount)
	{
		y = y - moveAmount;
		movingAway = true;
	}

	// player moving down (toward camera)
	public void moveDN(int moveAmount)
	{
		y = y + moveAmount;
		movingForward = true;
	}

	// player running up
	public void runUP(int moveAmount)
	{
		y = y - moveAmount;
		movingAway = true;
	}

	// player running down
	public void runDN(int moveAmount)
	{
		y = y + moveAmount;
		movingForward = true;
	}

	// player running right
	public void runRT(int moveAmount)
	{
		x = x + moveAmount;
		facingLeft = false;
		running    = true;
	}

	// player running left
	public void runLT(int moveAmount)
	{
		x = x - moveAmount;
		facingLeft = true;
		running    = true;
	}

	
	public void dribbleLT(int moveAmount) {
		x = x - moveAmount;
		facingLeft                  = true;
		ballInHand                  = true;
		lastDribblingSpriteSheetRow = 280;
	}


	public void dribbleRT(int moveAmount) {
		x = x + moveAmount;
		facingLeft                  = false;
		ballInHand                  = true;
		lastDribblingSpriteSheetRow = 0;
	}


	public void dribbleUP(int moveAmount) {
		y = y - moveAmount;
		movingAway                  = true;
		ballInHand                  = true;
		lastDribblingSpriteSheetRow = 840;
	}


	public void dribbleDN(int moveAmount) {
		y = y + moveAmount;
		movingForward               = true;
		ballInHand                  = true;
		lastDribblingSpriteSheetRow = 560;
	}
	
	
	public void shootingRT() {
		isShooting = true;
		facingLeft = false;
		jumpshotJump();
		spriteSheetFrame = 0;
		frameTimer       = 0;
		
	}
	
	
	public void shootingLT() {
		isShooting = true;
		facingLeft = true;
		jumpshotJump();
		spriteSheetFrame = 0;
		frameTimer       = 0;
		
	}
	
	
	public void shootingDN() {
		isShooting    = true;
		movingForward = true;
		jumpshotJump();
		spriteSheetFrame = 0;
		frameTimer       = 0;
		
	}
	
	
	public void shootingUP() {
		isShooting = true;
		movingAway = true;
		jumpshotJump();
		spriteSheetFrame = 0;
		frameTimer       = 0;
	}
	

	// moving with actual physics
	public void move()
	{
		x = x + (int)velocityX;
		y = y + (int)velocityY;

		velocityY = velocityY + gravity;

		if(physics == true) {
			
			if(y >= player_y_position) {
				y         = player_y_position;
				velocityY = 0;
				physics   = false;
				isJumping = false;
			}
		}
		else if(physics == false)
		{
			velocityY = 0;
		}
	}

	
	// player jumping
	public void jump(){
		if(physics == false){
			
			velocityY         = velocityY - 20;
			player_y_position = y;
			//walking           = true;
			physics           = true;
			isJumping         = true;
		}
	}
	
	
	public void reach() {
		isReaching = true;
	}
	
	
	// resets the state of a player/cpu who just lost the ball
	public void resetAfterLosingBall() {
	    ballInHand       = false;
	    isShooting       = false;
	    ballInFlight     = false;
	    physics          = false;
	    velocityY        = 0;
	    spriteSheetFrame = 0;
	    frameTimer       = 0;
	}
	
	
	public void jumpshotJump() {
		if(physics == false){
			
			velocityY         = velocityY - 10;
			player_y_position = y;
			walking           = true;
			physics           = true;
		}
	}

	

	// checks to see if player or cpu are stuck anywhere
	public void checkBoundsAndSlide(OutOfBoundsLine[] bounds, int prevX, int prevY) {
	    
	    // Build the rectangle at the new position
	    Rect myRect = new Rect(x, y, lrPlayerWidth, lrPlayerHeight);

	    // Check if moving (x, y) causes any overlap
	    boolean overlapsAny = false;
	    for (int i = 0; i < bounds.length; i++) {
	        if (i == 8 && physics == true) continue;		// allows the players to jump, turns off top wall
	        if (myRect.overlaps(bounds[i])) { overlapsAny = true; break; }
	    }

	    // saving coordinates if any overlap happened
	    if (overlapsAny) {
	        
	        // where the player is trying to go
	        int newX = x;
	        int newY = y;
	        
	        // now this checks if X is the problem in the movement
	        myRect = new Rect(newX, prevY, lrPlayerWidth, lrPlayerHeight);
	        
	        boolean xOnlyOverlaps = false;
	        for (int i = 0; i < bounds.length; i++) {
	            if (i == 8 && physics == true) continue;
	            if (myRect.overlaps(bounds[i])) { xOnlyOverlaps = true; break; }
	        }
	        
	        // if X movement did not cause any bugs, save it
	        if (!xOnlyOverlaps) {
	            setPosition(newX, prevY);	
	        } 
	        
	        else {
	            
	            // now check if Y is the problem in the movement
	            myRect = new Rect(prevX, newY, lrPlayerWidth, lrPlayerHeight);
	            
	            boolean yOnlyOverlaps = false;
	            for (int i = 0; i < bounds.length; i++) {
	                if (i == 8 && physics == true) continue;
	                if (myRect.overlaps(bounds[i])) { yOnlyOverlaps = true; break; }
	            }
	            
	            // if Y movement did not cause any bugs, save it
	            if (!yOnlyOverlaps) {
	                setPosition(prevX, newY);
	            } 
	            
	            // if both caused problems, then set the position to its previous one. (mostly when stuck in a corner)
	            else {
	                setPosition(prevX, prevY);
	            }
	        }
	    }
	}
	
	
	
	
	
	
	
	
	
	
	

	public void draw(Graphics g) {

		if(isShooting == true)                        				drawShootingState(g);
		else if(isJumping == true)                    				drawJumpingState(g);
		else if(isReaching == true)                   				drawReachingState(g);
		else if(ballInHand == true)                   				drawDribblingState(g);
		else if(movingAway == true || movingForward == true)  		drawUpDownState(g);
		else if(!walking && !running)         						drawIdleState(g);
		else                                  						drawWalkingLRState(g);

		
		walking       = false;
		running       = false;
		movingAway    = false;
		movingForward = false;
		// ballInHand does not go here since it is a
		// persistent state and not a per-frame flag like walking or running
	}


	// if shooting button pressed (game.java) it calls one of the shooting
	// methods here (shootingLT), then iShooting = true, gets called in
	// draw method, then draw method calls this method to in fact draw the image
	private void drawShootingState(Graphics g) {

		// advance animation frame
		frameTimer++;

		if(frameTimer >= shootingAnimationSpeed) {
			spriteSheetFrame = spriteSheetFrame + 1;				// move frame along

			if(spriteSheetFrame == shootingTotalFrames) {
				isShooting       = false;
				ballInHand       = false;
				ballInFlight     = true;
				spriteSheetFrame = 0;
				frameTimer       = 0;
			}

			frameTimer = 0;
		}

		int shootingSpriteSheetRow;
		if(movingAway) 		   shootingSpriteSheetRow = 840;
		else if(movingForward) shootingSpriteSheetRow = 560;
		else if (facingLeft)   shootingSpriteSheetRow = 0;
		else 				   shootingSpriteSheetRow = 0;


		// calc. where to start cropping the sprite sheet to grab the correct frame
		int cropLeft = spriteSheetFrame * shootingFrameWidth;


		if(facingLeft) { 		// (flipping the x variables places to mirror the image to seem left)
			g.drawImage(shootingImage,
						x + shootingPlayerWidth, y, x, y + shootingPlayerHeight,
						cropLeft, shootingSpriteSheetRow, cropLeft + shootingFrameWidth,
						shootingSpriteSheetRow + shootingFrameHeight, null);
		}
		else {
			g.drawImage(shootingImage,
						x, y, x + shootingPlayerWidth, y + shootingPlayerHeight,
						cropLeft, shootingSpriteSheetRow, cropLeft + shootingFrameWidth,
						shootingSpriteSheetRow + shootingFrameHeight, null);
		}
	}



	private void drawJumpingState(Graphics g) {

		frameTimer++;

		if(frameTimer >= jumpingAnimationSpeed) {
			spriteSheetFrame = spriteSheetFrame + 1;

			if(spriteSheetFrame == jumpingTotalFrames) {
				isJumping 			= false;
				spriteSheetFrame 	= 0;
				frameTimer 			= 0;
			}

			frameTimer = 0;
		}

		int cropLeft = spriteSheetFrame * jumpingFrameWidth;

		g.drawImage(jumpImage,
				x, y, x + lrPlayerWidth + 28, y + lrPlayerHeight + 140,
				cropLeft, 0, cropLeft + jumpingFrameWidth , jumpingFrameHeight, null);
	}



	private void drawReachingState(Graphics g) {

		frameTimer++;

		if(frameTimer >= reachingAnimationSpeed) {
			spriteSheetFrame = spriteSheetFrame + 1;

			if(spriteSheetFrame == reachingTotalFrames) {
				isReaching 			= false;
				spriteSheetFrame 	= 0;
				frameTimer 			= 0;
			}

			frameTimer = 0;
		}

		int cropLeft = spriteSheetFrame * reachingFrameWidth;

		g.drawImage(reachImage,
				x, y, x + lrPlayerWidth + 10, y + lrPlayerHeight + 10,
				cropLeft, 0, cropLeft + reachingFrameWidth , reachingFrameHeight, null);
	}



	private void drawDribblingState(Graphics g) {

		frameTimer++;

		if(frameTimer >= dribblingAnimationSpeed) {
			spriteSheetFrame = (spriteSheetFrame + 1) % dribblingTotalFrames;		// goes thru each frame until module == 0 to restart from first frame
			frameTimer = 0;
		}

		int dribblingSpriteSheetRow;

		// setting which dribbling row to use from the pixels (each one is 280 pixels)
		if(movingAway)		   dribblingSpriteSheetRow = 840;
		else if(movingForward) dribblingSpriteSheetRow = 560;

		// remembers the last direction the user was facing, left and right falls in the same else
		else                   dribblingSpriteSheetRow = lastDribblingSpriteSheetRow;

		int cropLeft = spriteSheetFrame * dribblingFrameWidth;

		// up-downs dimensions
		g.drawImage(dribblingImage,
					x, y, x + dribblingWidth, y + dribblingHeight,
					cropLeft, dribblingSpriteSheetRow, cropLeft + dribblingFrameWidth,
					dribblingSpriteSheetRow + dribblingFrameHeight, null);
	}



	private void drawUpDownState(Graphics g) {

		wasMovingUpDown = true;

		frameTimer++;
		if(frameTimer >= udAnimationSpeed) {

			if(movingAway) {

				// toggle between frames 1 and 2
				if(spriteSheetFrame == 1)  spriteSheetFrame = 2;
				else                       spriteSheetFrame = 1;
			}
			
			// movingForward
			else {
				// toggle between frames 0 and 1
				if(spriteSheetFrame == 0)  spriteSheetFrame = 1;
				else                       spriteSheetFrame = 0;
			}

			frameTimer = 0;
		}

		// row 0 = walking away (up), row 1 = walking forward (down)
		int udSpriteSheetRow;

		if(movingAway)   udSpriteSheetRow = 0;				// top row — walking away from camera
		else             udSpriteSheetRow = udFrameHeight;	// bottom row starts at pixel 627


		lastUdSpriteSheetRow = udSpriteSheetRow;	// save for standing still pose

		int cropLeft;
		if(movingAway)
			cropLeft = spriteSheetFrame * udFrameWidth + udAwayCropOffset;		// gotta add the udAwayCrop if we are moving away so no pixels leak into our code.
		else
			cropLeft = spriteSheetFrame * udFrameWidth;

		g.drawImage(upDownImage,
					x, y, x + udPlayerWidth, y + udPlayerHeight,
					cropLeft, udSpriteSheetRow, cropLeft + udFrameWidth,
					udSpriteSheetRow + udFrameHeight, null);
	}



	private void drawIdleState(Graphics g) {

		if(wasMovingUpDown) {
			if(lastUdSpriteSheetRow == 0) {
				// was moving away is frame 3 so 627 * 3
				int cropLeft = 3 * idleFrameSize;
				g.drawImage(idleImage,
							x + idleAwayXOffset, y + idleAwayYOffset, x + idleAwayXOffset + idlePlayerWidth, y + idleAwayYOffset + idlePlayerHeight,
							cropLeft, 0, cropLeft + idleFrameSize, idleFrameSize, null);
			}
			else {
				// was moving forward is frame 2 627 * 2
				int cropLeft = 2 * idleFrameSize;
				g.drawImage(idleImage,
							x + idleForwardXOffset, y + idleForwardYOffset, x + idleForwardXOffset + idlePlayerWidth, y + idleForwardYOffset + idlePlayerHeight,
							cropLeft, 0, cropLeft + idleFrameSize, idleFrameSize, null);
			}
		}
		else {
			// left/right idle — frame 0
			if(facingLeft) {
				g.drawImage(idleImage,
							x, y, x + idlePlayerWidth, y + idlePlayerHeight,
							0, 0, idleFrameSize, idleFrameSize, null);
			}
			else {
				// right image
				g.drawImage(idleImage,
							x + idlePlayerWidth, y, x, y + idlePlayerHeight,
							0, 0, idleFrameSize, idleFrameSize, null);
			}
		}
	}



	private void drawWalkingLRState(Graphics g) {

		// reset frame when switching from up/down to left/right walking
		if(wasMovingUpDown) {
			spriteSheetFrame = 0;
			frameTimer       = 0;
			wasMovingUpDown  = false;
		}

		// walking or running left/right spritesheet (run and walk share the same sprite)
		int lrSpriteSheetRow;

		if(running)   lrSpriteSheetRow = 724;	// run row  starts at pixel 724 (362 * 2)
		else          lrSpriteSheetRow = 0;		// walk row starts at pixel 0

		frameTimer++;
		if(frameTimer >= lrAnimationSpeed)
		{
			spriteSheetFrame = (spriteSheetFrame + 1) % lrTotalFrames;
			frameTimer = 0;
		}

		// cropping the frame from the left and right
		int cropLeftSS  = spriteSheetFrame * lrFrameSize;
		int cropRightSS = cropLeftSS + lrFrameSize;

		if(facingLeft) {
			g.drawImage(playerImage,
						x + lrPlayerWidth, y, x, y + lrPlayerHeight,
						cropLeftSS, lrSpriteSheetRow, cropRightSS,
						lrSpriteSheetRow + lrFrameSize, null);
		}
		else {
			g.drawImage(playerImage,
						x, y, x + lrPlayerWidth, y + lrPlayerHeight,
						cropLeftSS, lrSpriteSheetRow, cropRightSS,
						lrSpriteSheetRow + lrFrameSize, null);
		}
	}
}
