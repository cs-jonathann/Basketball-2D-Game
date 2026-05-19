import java.awt.*;

public class Ball {
    
    Image image = Toolkit.getDefaultToolkit().getImage("images/ball.png");
    
    int x = 815;
    int y = 776;
    
    double velocityX = 0;
    double velocityY = 0;
    double gravity   = 0.4;
    
    // walls so the ball doesn't go flying off screen
    final int floorY    = 900;
    final int leftWall  = 80;
    final int rightWall = 1630;
    
    Rect rect = new Rect(x, y, 50, 50);
    
    boolean blocked = false;
    
    
    // Applies gravity, moves the ball, and rebuilds its rectangle, gets called once per frame during flight
    public void update() {
        x = x + (int)velocityX;
        y = y + (int)velocityY;
        velocityY = velocityY + gravity;
        rebuildRect();
    }
    
    
    // Rebuilds the rect after a manual position change (scoring teleport, knock-loose scatter, etc.)
    public void rebuildRect() {
        rect = new Rect(x, y, 50, 50);
    }
    
    
    // Convenience for scoring and rim checks
    public int getCenterX() { return x + 25; }
    public int getCenterY() { return y + 25; }
    
    
    // Stops the ball  used when scoring, hitting a wall, or any flight-ending event
    public void stop() {
        velocityX = 0;
        velocityY = 0;
    }


    // Clamps the ball to a "safe" reachable area on the court — used when a loose ball
    // ends up out of bounds and needs to be brought back inside so players can grab it
    public void clampToCourt() {
        int safeLeftX  = 230;
        int safeRightX = 1430;
        int safeTopY   = 410;
        int safeBotY   = 940;

        // checking where it is at, if less than the safe zone, bring it back in
        if(x < safeLeftX)  x = safeLeftX;
        if(x > safeRightX) x = safeRightX;
        if(y < safeTopY)   y = safeTopY;
        if(y > safeBotY)   y = safeBotY;
    }
}