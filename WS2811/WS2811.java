/*
 * Copyright (C) 2016 Jim Darby.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */

/**
 * Provide a sensible interface to the WS2811 library.
 * 
 * @author Jim Darby
 */
public class WS2811 {
    public WS2811 (int width, int height,
            boolean flip_x,
            boolean flip_y,
            boolean snake,
            int type, double brightness)
    {
        if (width < 0 || height < 0 ||
                (type != WS2811Raw.WS2811_STRIP_RGB) &&
                (type != WS2811Raw.WS2811_STRIP_RBG) &&
                (type != WS2811Raw.WS2811_STRIP_GRB) &&
                (type != WS2811Raw.WS2811_STRIP_GBR) &&
                (type != WS2811Raw.WS2811_STRIP_BRG) &&
                (type != WS2811Raw.WS2811_STRIP_BGR) ||
                brightness < 0 || brightness > 1)
            throw new IllegalArgumentException ("Invalid parameter to WS2811");
        
        width_ = width;
        height_ = height;
        flip_x_ = flip_x;
        flip_y_ = flip_y;
        snake_ = snake;
        leds_ = width_ * height_;
        data_ = new int[leds_];
        
        for (int i = 0; i < leds_; ++i)
            data_[i] = 0;
        
        WS2811Raw.ws2811_init (type, leds_);
        WS2811Raw.ws2811_brightness ((int) (brightness * 255));
        WS2811Raw.ws2811_update (data_);
    }
    
    public void setPixel (int x, int y, int r, int g, int b)
    {       
         if (x < 0 || x>= width_ || y < 0 || y >= height_ ||
                r < 0 || r > 255 || g < 0 || g > 255 || b < 0 || b >= 255)
            throw new IllegalArgumentException ("Invalid parameter to WS2811.setPixel");
        
        if (flip_x_)
            x = (width_ - 1) - x;
        
        if (flip_y_)
            y = (height_ - 1) - y;
        
        if (snake_ && (y & 1) != 0)
            x = (width_ - 1) - x;
        
        data_[x + width_ * y] = (r << 16) | (g << 8) | b;
    }
    
    public void show ()
    {
        WS2811Raw.ws2811_update (data_);
    }
   
    void close ()
    {
        WS2811Raw.ws2811_close ();
    }
    
    final private int width_;
    final private int height_;
    final private int leds_;
    final private boolean flip_x_;
    private final boolean flip_y_;
    final private boolean snake_;
    final private int[] data_;
}
