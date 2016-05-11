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

public class Point
{
    public Point (int x, int y)
    {
	if (x < 0 || y < 0)
	    throw new IllegalArgumentException ("Negative co-ordinate in (" + x + ',' + y + ')');
	
	x_ = x;
	y_ = y;
    }
    
    public int getX ()
    {
	return x_;
    }
    
    public int getY ()
    {
	return y_;
    }
    
    public String toString ()
    {
	return "(" + x_ + ',' + y_ + ')';
    }

    public boolean inside (Point p)
    {
	return x_ <= p.x_ && y_ <= p.y_;
    }
    
    private final int x_;
    private final int y_;
}
