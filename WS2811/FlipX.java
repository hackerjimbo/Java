public class FlipX extends Mapping
{
    public FlipX (int width, int height)
    {
	super (new Point (width - 1, height - 1));
    }

    public FlipX (Mapping before)
    {
	super (before, before.getOutMax ());
    }
    
    public Point map (Point p)
    {
	if (before_ != null)
	    p = before_.map (p);
	
	validateIn (p);

	final Point result = new Point (getInMax ().getX () - p.getX (), p.getY ());

	validateOut (result);
	
	return result;
    }

    public String toString ()
    {
	String result = "FlipX from " + getInMax () + " to " + getOutMax ();

	if (before_ != null)
	    result = before_.toString () + ' ' + result;
	
	return result;
    }
}

