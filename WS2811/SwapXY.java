public class SwapXY extends Mapping
{
    public SwapXY (int width, int height)
    {
	super (new Point (width - 1, height - 1), new Point (height - 1, width - 1));
    }

    public SwapXY (Mapping before)
    {
	super (before, new Point (before.getOutMax ().getY (), before.getOutMax ().getX ()));
    }
    
    public Point map (Point p)
    {
	if (before_ != null)
	    p = before_.map (p);

	validateIn (p);

	final Point result = new Point (p.getY (), p.getX ());

	validateOut (result);
	
	return result; 
    }

    public String toString ()
    {
	String result = "SwapXY in " + getInMax () + " out " + getOutMax ();

	if (before_ != null)
	    result = before_.toString () + ' ' + result;
	
	return result;
    }
}
