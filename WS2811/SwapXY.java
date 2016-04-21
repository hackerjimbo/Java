/**
 * A Mapping to swap the X and Y co-ordinates.
 */

public class SwapXY extends Mapping
{
    /**
     * Create a simple case with a width and a height. Note that this has input
     * size width by height but output size height by width.
     * @param width The width.
     * @param height The height.
     */
    public SwapXY (int width, int height)
    {
	super (new Point (width - 1, height - 1), new Point (height - 1, width - 1));
    }
    
    /**
     * Create a mapping base on a previous one. We output the same dimensions
     * except that they are rotated. So input of size x by y becomes output of
     * size y by x.
     * @param before 
     */
    public SwapXY (Mapping before)
    {
	super (before, new Point (before.getOutMax ().getY (), before.getOutMax ().getX ()));
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
	final Point result = new Point (p.getY (), p.getX ());

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
	String result = "SwapXY in " + getInMax () + " out " + getOutMax ();

	if (before_ != null)
	    result = before_.toString () + ' ' + result;
	
	return result;
    }
}
