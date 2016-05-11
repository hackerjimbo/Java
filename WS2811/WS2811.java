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
 * Provide a sensible interface to the WS2811 library.
 * 
 * @author Jim Darby
 */
public class WS2811 {
    public WS2811 (int width, int height,
		   Mapping map,
		   int type,
		   double brightness)
    {
        if (width <= 0 || height <= 0 ||
                (type != WS2811Raw.WS2811_STRIP_RGB) &&
                (type != WS2811Raw.WS2811_STRIP_RBG) &&
                (type != WS2811Raw.WS2811_STRIP_GRB) &&
                (type != WS2811Raw.WS2811_STRIP_GBR) &&
                (type != WS2811Raw.WS2811_STRIP_BRG) &&
                (type != WS2811Raw.WS2811_STRIP_BGR) ||
                brightness < 0 || brightness > 1)
            throw new IllegalArgumentException ("Invalid parameter to WS2811");

	i_width_ = width;
	i_height_ = height;
	max_ = new Point (i_width_ - 1, i_height_ - 1);

	// System.out.println ("Input width " + i_width_ + " height " + i_height_ + " limit " + max_);
	
	final Point out = map.getOutMax ();
	
        o_width_ = out.getX () + 1;
        o_height_ = out.getY () + 1;

	// System.out.println ("Output width " + o_width_ + " height " + o_height_ + " limit " + out);
		
        leds_ = o_width_ * o_height_;
	map_ = new int[leds_];
        data_ = new int[leds_];

	for (int y = 0; y < i_height_; ++y)
	    for (int x = 0; x < i_width_; ++x) {
		final Point p = map.map (new Point (x, y));

		// System.out.println ("Mapped " + x + ',' + y + " to " + p);
		
		final int value = p.getX () + o_width_ * p.getY ();

		// System.out.println ("Map " + x + ',' + y + " to " + value + " at " + (x + i_width_ * y));
		
		map_[x + i_width_ * y] = value;
	    }

	/* System.out.println ();

	for (int i = 0; i < leds_; ++i) {
	    if (i != 0)
		System.out.print (' ');
	    
	    System.out.print (map_[i]);
	}

	System.out.println ();
	*/
	
        for (int i = 0; i < leds_; ++i)
            data_[i] = 0;
        
        WS2811Raw.ws2811_init (type, leds_);
        WS2811Raw.ws2811_brightness ((int) (brightness * 255));
        WS2811Raw.ws2811_update (data_);
    }
    
    public void setPixel (Point p, int r, int g, int b)
    {       
	if (!p.inside (max_) ||
                r < 0 || r > 255 || g < 0 || g > 255 || b < 0 || b >= 255)
            throw new IllegalArgumentException ("Invalid parameter to WS2811.setPixel");

	final int x = p.getX ();
	final int y = p.getY ();
	
	// System.out.println ("pos " + x + ',' + y + " -> " + map_[x + i_width_ * y]);
	 
        data_[map_[x + i_width_ * y]] = (r << 16) | (g << 8) | b;
    }
    
    public void show ()
    {
        WS2811Raw.ws2811_update (data_);
    }
   
    void close ()
    {
        WS2811Raw.ws2811_close ();
    }
    
    final private int i_width_;
    final private int i_height_;
    final private Point max_;
    
    final private int o_width_;
    final private int o_height_;
    
    final private int leds_;

    final private int[] map_;
    final private int[] data_;
}
