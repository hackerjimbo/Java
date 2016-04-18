public class FlipY extends Mapping
{
    public FlipY (int width, int height)
    {
	super (new Point (width - 1, height - 1));
    }

    public FlipY (Mapping before)
    {
	super (before, before.getOutMax ());
    }
    
    public Point map (Point p)
    {
	if (before_ != null)
	    p = before_.map (p);
	
	validateIn (p);

	final Point result = new Point (p.getX (), getInMax ().getY () - p.getY ());

	validateOut (result);
	
	return result;
    }

    public String toString ()
    {
	String result = "FlipY from " + getInMax () + " to " + getOutMax ();

	if (before_ != null)
	    result = before_.toString () + ' ' + result;
	
	return result;
    }
}
