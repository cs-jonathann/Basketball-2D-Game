import java.awt.*;

public class ScoreBoard {
    
    int playerScore = 0;
    int cpuScore    = 0;
    
    
    // Add points to the player's score
    public void addPlayerPoints(int points) {
        playerScore += points;
    }
    
    
    // Add points to the CPU's score
    public void addCpuPoints(int points) {
        cpuScore += points;
    }
    
    
    // Draws both scores at the top of the screen
    public void draw(Graphics g) {
        
        g.setColor(Color.WHITE);
        g.setFont(new Font("Arial", Font.BOLD, 40));
        g.drawString("Player Score: " + playerScore, 300, 50);
        
        g.setColor(Color.WHITE);
        g.setFont(new Font("Arial", Font.BOLD, 40));
        g.drawString("CPU Score: " + cpuScore, 700, 50);
    }
}