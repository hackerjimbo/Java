public class Point
{
    public Point (int x, int y)
    {
	if (x < 0 || y < 0)
	    throw new IllegalArgumentException ("Negative co-ordinate in (" + x + ',' + y + ')');
	
	x_ = x;
	y_ = y;
    }
    
    public int getX ()
    {
	return x_;
    }
    
    public int getY ()
    {
	return y_;
    }
    
    public String toString ()
    {
	return "(" + x_ + ',' + y_ + ')';
    }

    public boolean inside (Point p)
    {
	return x_ <= p.x_ && y_ <= p.y_;
    }
    
    private final int x_;
    private final int y_;
}
