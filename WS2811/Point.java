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
 * A class to describe a point. It is composed of an integer x co-ordinate and 
 * an integer y co-ordinate.
 * @author Jim Darby
 */

public class Point
{
    /**
     * Construct from a given x and y value.
     * @param x
     * @param y 
     */
    public Point (int x, int y)
    {
	if (x < 0 || y < 0)
	    throw new IllegalArgumentException ("Negative co-ordinate in (" + x + ',' + y + ')');
	
	x_ = x;
	y_ = y;
    }
    
    /**
     * Obtain the x value.
     * @return The x value.
     */
    public int getX ()
    {
	return x_;
    }
    
    /**
     * Obtain the y value.
     * @return The y value.
     */
    public int getY ()
    {
	return y_;
    }
    
    /**
     * Convert the object to a string.
     * @return A string representing the point.
     */
    public String toString ()
    {
	return "(" + x_ + ',' + y_ + ')';
    }

    /**
     * Given another point p, is this point within the area of the square of
     * values created between the origin and the values of p.
     * @param p
     * @return True if we've inside the region.
     */
    public boolean inside (Point p)
    {
	return x_ <= p.x_ && y_ <= p.y_;
    }
    
    /** The value of x. */
    private final int x_;
    /** The value of y. */
    private final int y_;
}
