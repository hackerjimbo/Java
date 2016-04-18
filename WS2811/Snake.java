public class Snake extends Mapping
{
    public Snake (int width, int height)
    {
	super (new Point (width - 1, height - 1));
    }

    public Snake (Mapping before)
    {
	super (before, before.getOutMax ());
    }
    
    public Point map (Point p)
    {
	if (before_ != null)
	    p = before_.map (p);
	
	validateIn (p);

	final Point result = ((p.getY () & 1) != 0) ? new Point (getInMax ().getX () - p.getX (), p.getY ()) : p;

	validateOut (result);
	
	return result;
    }

    public String toString ()
    {
	String result = "Snake from " + getInMax () + " to " + getOutMax ();

	if (before_ != null)
	    result = before_.toString () + ' ' + result;
	
	return result;
    }
}

