public class Identity extends Mapping
{
    public Identity (int width, int height)
    {
	super (new Point (width - 1, height - 1));
    }

    public Identity (Mapping before)
    {
	super (before, before.getOutMax ());
    }
    
    public Point map (Point p)
    {
	if (before_ != null)
	    p = before_.map (p);
	
	validateIn (p);

	final Point result = p;

	validateOut (result);
	
	return result;
    }

    public String toString ()
    {
	String result = "Identity from " + getInMax () + " to " + getOutMax ();

	if (before_ != null)
	    result = before_.toString () + ' ' + result;
	
	return result;
    }
}

