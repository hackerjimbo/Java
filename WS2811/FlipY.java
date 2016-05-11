/*
 * Copyright (C) 2016 Jim Darby.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see
 * <http://www.gnu.org/licenses/>.
 */

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
