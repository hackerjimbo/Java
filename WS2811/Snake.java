/**
 * The snake transformation. Some boards (like the Pimoroni Unicorn HAT) wire
 * the LEDs in a snake like formation so that we have to exchange the X
 * co-ordinates on every OTHER line.
 * 
 * We always work with X-snakes. If you want (or need) a Y-snake then swap X
 * and Y, Snake transform then swap back.
 */

public class Snake extends Mapping
{
    /**
     * Create a simple case with a width and a height.
     * @param width The width.
     * @param height The height.
     */
    public Snake (int width, int height)
    {
	super (new Point (width - 1, height - 1));
    }

    /**
     * Create a mapping base on a previous one. We output the same dimensions as
     * we input.
     * @param before 
     */
    public Snake (Mapping before)
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
	final Point result = ((p.getY () & 1) != 0) ? new Point (getInMax ().getX () - p.getX (), p.getY ()) : p;

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
	String result = "Snake from " + getInMax () + " to " + getOutMax ();

	if (before_ != null)
	    result = before_.toString () + ' ' + result;
	
	return result;
    }
}
