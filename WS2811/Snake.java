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
 * <http://www.gnu.org/licenses/>..
 */

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
