import java.awt.*;

public class Rect
{
	double x;
	double y;
	
	int w;
	int h;

	
	boolean selected = false;
	
	public Rect(int x, int y, int w, int h)
	{
		this.x = x;
		this.y = y;
		
		this.w = w;
		this.h = h;
	}
	
	public boolean isSelected()
	{
		return selected;
	}
	
	public void setSelected()
	{
		selected = true;
	}
	
	public void clearSelected()
	{
		selected = false;
	}
	
	public void toggle()
	{
		selected = ! selected;
	}
	
	public void pushLeft(Rect r)
	{
		double penetration = r.x + r.w - x;
		
		if(penetration < r.w/2)
			
			r.x -= penetration + 1;
	}
	
	public void pushRight(Rect r)
	{
		double penetration = x + w - r.x;
		
		if(penetration < r.w/2)
			
			r.x += penetration + 1;
	}
	
	public void pushUp(Rect r)
	{
		double penetration = r.y + r.h - y ;
		
		if(penetration < r.h/2)
			
			r.y -= penetration + 1;
	}
	
	public void pushDown(Rect r)
	{
		double penetration = y + h - r.y;
		
		if(penetration < r.h/2)
			
			r.y += penetration + 1;
	}
	
	
	public void pushes(Rect r)
	{
		pushDown(r);
		pushUp(r);
		pushRight(r);
		pushLeft(r);		
	}
	
	public boolean overlaps(Rect r)
	{
		return (x <= r.x + r.w) &&
			   (y <= r.y + r.h) &&
			   
			   (r.x <= x + w)   &&
			   (r.y <= y + h);	
	}
	
	public boolean contains(int mx, int my)
	{
		return (mx > x)   && 
			   (mx < x+w) && 
			   (my > y)   && 
			   (my < y+h);
	}
	
	public void moveBy(int dx, int dy)
	{
		x += dx;
		y += dy;
	}
	
	
	public void draw(Graphics g)
	{
		g.drawRect((int)x, (int)y, w, h);
	}
	
}	
	
