import java.awt.*;

// This class is to create jagged / slanted lines to trace the
// baselines of the basketball court and the backboard.
// This also allows us to add collision detection to these lines.

public class Line {
	
	int Ax;
	int Ay;
	
	int Bx;
	int By;
				
	double Nx;
	double Ny;
	
	double c;
	
	
	public Line(int Ax, int Ay, int Bx, int By) {
		
		this.Ax = Ax;
		this.Ay = Ay;
		
		this.Bx = Bx;
		this.By = By;
		
		int Vx = Ax - Bx;
		int Vy = Ay - By;
		
		double mag = Math.sqrt(Vx*Vx + Vy*Vy);
		
		Nx = -Vy / mag;
		Ny =  Vx / mag;
		
		c = - Nx * Ax - Ny * Ay;
	}
	
	
	public double distanceTo(double Px, double Py) {
		return Nx * Px + Ny * Py + c;
	}
	
	
	
	
	public void draw(Graphics g) {
		g.drawLine(Ax,  Ay, Bx, By);
	}

}