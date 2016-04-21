/**
 * A Mapping to flip the Y co-ordinate (a top-bottom flip).
 */

public class FlipY extends Mapping
{
    /**
     * Create a simple case with a width and a height.
     * @param width The width.
     * @param height The height.
     */
    public FlipY (int width, int height)
    {
	super (new Point (width - 1, height - 1));
    }

    /**
     * Create a mapping base on a previous one. We output the same dimensions as
     * we input just with the Y value reflected.
     * @param before 
     */
    public FlipY (Mapping before)
    {
	super (before, before.getOutMax ());
    }
    
    /**
     * Perform the mapping.
     * @param p The point to map.
     * @return The mapped point.
     */
    public Point map (Point p)
    {
        // If there is someone before us then run their mapping.
	if (before_ != null)
	    p = before_.map (p);
	
        // Validate the input AFTER any previous mapping.
	validateIn (p);

        // Perform the mapping.
	final Point result = new Point (p.getX (), getInMax ().getY () - p.getY ());

        // Check the output is valud (yes, we're paranoid).
	validateOut (result);
	
        // And return it.
	return result;
    }
    
    /**
     * Return a string description of the mapping.
     * @return The description.
     */
    public String toString ()
    {
	String result = "FlipY from " + getInMax () + " to " + getOutMax ();

	if (before_ != null)
	    result = before_.toString () + ' ' + result;
	
	return result;
    }
}
