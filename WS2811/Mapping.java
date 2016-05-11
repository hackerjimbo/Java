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
 * An abstract class the defines a mapping.
 */

abstract class Mapping
{
    /**
     * Construct from a single maximum point. Both input and output are within
     * this point and the origin.
     * @param inOutMax The maximum point.
     */
    public Mapping (Point inOutMax)
    {
	inMax_ = inOutMax;
	outMax_ = inOutMax;
	before_ = null;
    }

    /**
     * Construct from two maximum points. The first gives the input size and
     * the second the output size.
     * @param inMax The maximum input point.
     * @param outMax The maximum output point.
     */
    public Mapping (Point inMax, Point outMax)
    {
	inMax_ = inMax;
	outMax_ = outMax;
	before_ = null;
    }

    /**
     * Construct from a previous mapping and provide our own output mapping.
     * @param before The previous mapping.
     * @param outMax Our new output maxima.
     */
    public Mapping (Mapping before, Point outMax)
    {
	inMax_ = before.getOutMax ();
	outMax_ = outMax;
	before_ = before;
    }

    /**
     * Validate an input value. It must lie between the origin and the point
     * given INCLUSIVE.
     * @param p The point.
     */
    public void validateIn (Point p)
    {
        // Are we good?
	if (p.inside (inMax_))
	    return;

        // No, fire an exception
	throw new IllegalArgumentException ("Input co-ordinate " + p + " outside " + inMax_);
    }

    /**
     * Validate an output value. It must lie between the origin and the point
     * given INCLUSIVE.
     * @param p The point.
     */
    public void validateOut (Point p)
    {
        // Are we good?
	if (p.inside (outMax_))
	    return;

        // No, first an exception.
	throw new IllegalArgumentException ("Output co-ordinate " + p + " outside " + outMax_);
    }

    /**
     * Obtain this filter's input maxima.
     * @return A point describing the maxima.
     */
    public Point getInMax ()
    {
	return inMax_;
    }

    /**
     * Get the maxima of the input to this entire chain.
     * @return The maxima.
     */
    public Point getOriginalMax ()
    {
        // If nothing before, it's us. Otherwise pass it on.
	return (before_ == null) ? inMax_ : before_.getOriginalMax ();
    }

    /**
     * Get this filter's output maxima.
     * @return A point describing the maxima.
     */
    public Point getOutMax ()
    {
	return outMax_;
    }

    /**
     * Abstract method to perform the mapping.
     * @param p The input.
     * @return The mapped value..
     */
    abstract public Point map (Point p);
    
    /**
     * Abstract method to turn the mapping into a string form.
     * @return The string form.
     */
    abstract public String toString ();

    /** The input maxima. */
    protected final Point inMax_;
    /** The output maxima. */
    protected final Point outMax_;
    /** Any previous mapping, null if none. */
    protected Mapping before_;
}
