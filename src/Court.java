public class Court {

	// *If I Have time, find a more efficient way to create the out of bounds line*
	OutOfBoundsLine[] bounds = {// x, y,   w,   h
			new OutOfBoundsLine(209, 600, 1, 100),			    // leftWall1
			new OutOfBoundsLine(170, 700, 1, 100),				// leftWall2
			new OutOfBoundsLine(120, 800, 1, 100),				// leftWall3
			new OutOfBoundsLine(80, 900, 1, 100),				// leftWall4
			new OutOfBoundsLine(1500, 600, 1, 100),			    // rightWall1
			new OutOfBoundsLine(1540, 700, 1, 100),				// rightWall2
			new OutOfBoundsLine(1590, 800, 1, 100),				// rightWall3
			new OutOfBoundsLine(1630, 900, 1, 100), 			// rightWall4
			new OutOfBoundsLine(222, 400, 1200, 1),				// topWall
			new OutOfBoundsLine(75, 1000, 1570, 1),			    // bottomWall
		};


	Rim[] rims = {
			new Rim(1461, 465, 1, 27),	 // front right rim
			new Rim(250, 463, 1, 27)	 // front left rim
	};


	Line[] backboardLines = {
		    new Line(1596, 525, 1550, 480), // right backboard lower side
		    new Line(1596, 375, 1596, 525),	// right backboard right side
		    new Line(1495, 335, 1596, 375), // right backboard top side
		    new Line(117, 520, 163, 485)  , // left  backboard lower side (shortened to match right)
		    new Line(117, 381, 117, 520)  , // left  backboard left side
		    new Line(214, 338, 117, 381)  , // left  backboard top side
		};


	Line[] threePointLines = {
			new Line(612, 642, 612, 987)  ,   // three point line on left side of the court
			new Line(1098, 642, 1098, 987),   // three point line on rights side of the court
	};


	Rect rightBackboardInterior = new Rect(1537, 400, 45, 50);
	Rect leftBackboardInterior  = new Rect(131,  400, 45, 50);



	// backboard interior rectangles always active so bank shots work correctly
	public void handleBackboardCollisions(Ball ball) {

		if(ball.rect.overlaps(rightBackboardInterior)) {
			ball.x         = (int)rightBackboardInterior.x - 51;
		    ball.velocityX = -ball.velocityX * 0.2;
		    ball.velocityY =  ball.velocityY * 0.7;
		}

		if(ball.rect.overlaps(leftBackboardInterior)) {
			ball.x         = (int)(leftBackboardInterior.x + leftBackboardInterior.w) + 1;
		    ball.velocityX = -ball.velocityX * 0.2;
		    ball.velocityY =  ball.velocityY * 0.7;
		}
	}



	// Handles ball bouncing off rims (deflects ball when it overlaps a rim)
	public void handleRimCollisions(Ball ball, int ballCenterY) {

	    for(int i = 0; i < rims.length; i++) {
	        if(ball.rect.overlaps(rims[i])) {

	            // horizontal rims only bounce if ball CENTER hasn't crossed the rim yet.
	            // this allows the ball to pass through once the center is past the rim level.
	            if(rims[i].w > rims[i].h) {

	                if(ball.velocityY > 0 && ballCenterY < rims[i].y) {

	                    // approaching from above, bounce off top of rim
	                    ball.y = (int)rims[i].y - 50;
	                    ball.velocityY = -ball.velocityY * 0.7;
	                    ball.velocityX =  ball.velocityX * 0.7;
	                }
	                else if(ball.velocityY < 0 && ballCenterY > rims[i].y) {

	                    // approaching from below, bounce off bottom of rim
	                    ball.y = (int)(rims[i].y + rims[i].h);
	                    ball.velocityY = -ball.velocityY * 0.7;
	                    ball.velocityX =  ball.velocityX * 0.7;
	                }
	            }

	            // front rims block from either side
	            else {
	                boolean isRightRim = rims[i].x > 1000;
	                boolean isLeftRim  = rims[i].x < 300;

	                if(isRightRim) {

	                    if(ball.velocityX > 0)
	                        ball.x = (int)rims[i].x - 50;           // push ball left of rim

	                    else
	                        ball.x = (int)(rims[i].x + rims[i].w);  // push ball right of rim
	                        ball.velocityX = -ball.velocityX * 0.7;
	                        ball.velocityY =  ball.velocityY * 0.7;
	                }

	                else if(isLeftRim) {

	                    if(ball.velocityX < 0)
	                        ball.x = (int)(rims[i].x + rims[i].w);  // push ball right of rim

	                    else
	                        ball.x = (int)rims[i].x - 50;           // push ball left of rim
	                        ball.velocityX = -ball.velocityX * 0.7;
	                        ball.velocityY =  ball.velocityY * 0.7;
	                }
	            }
	        }
	    }
	}
}
