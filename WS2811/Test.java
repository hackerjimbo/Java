/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 *
 * @author jim
 */
public class Test {
    
    public static void main(String[] args) throws InterruptedException
    {
        System.load ("/home/pi/libws2811.so");
        
        WS2811 w = new WS2811 (8, 8, true, false, true, WS2811Raw.WS2811_STRIP_GRB, 0.2);
        
        
        for (int x = 0; x < 8; ++x)
            for (int y = 0; y < 8; ++y) {
                w.setPixel(x, y, 128, 0, 0);
                w.show();
                Thread.sleep (50);
            }
        
        for (int x = 0; x < 8; ++x)
            for (int y = 0; y < 8; ++y) {
                w.setPixel(x, y, 0, 128, 0);
                w.show();
                Thread.sleep (50);
            }
        
        for (int x = 0; x < 8; ++x)
            for (int y = 0; y < 8; ++y) {
                w.setPixel(x, y, 0, 0, 128);
                w.show();
                Thread.sleep (50);
            }
        
        w.close();
    }
    
    /*public Test () throws InterruptedException
    {
        System.load ("/home/pi/libws2811.so");
        
        data = new int[64];
        
        System.out.println ("Hello from Java!");
        
        ws2811_init (data.length);
        
        for (int i = 0; i < data.length; ++i)
            data[i] = 0;
        
        ws2811_update (data);
        
        for (int i = 0; i <= 200; ++i) {
            final int r = clip (i, 50);
            final int g = clip (i - 50, 50);
            final int b = clip (i - 100, 50);
            
            for (int j = data.length - 1; j > 0; --j)
                data[j] = data[j-1];
            
            data[0] = (r << 16) | (g << 8) | b;
            
            ws2811_update (data);
            Thread.sleep (50);
        }
        
        for (int i = 0; i < data.length; ++i)
            data[i] = 0;
        
        ws2811_update (data);
        
        ws2811_wait ();
        
        run ();
        
        ws2811_close ();
    }
    
    private void run () throws InterruptedException
    {
        int step = 0;
        int effect = 0;
        
        while (true) {
            for (int i = 0; i < 400; ++i) {
                for (int y = 0; y < 8; ++y) {
                    for (int x = 0; x < 8; ++x) {
                        double[] rgb = (effect == 0) ? swirl (x, y, step) :
                                rainbow_search (x, y, step);
                        
                        int r = clip ((int) rgb[0], 255);
                        int g = clip ((int) rgb[1], 255);
                        int b = clip ((int) rgb[2], 255);
                        
                        int x1 = x;
                        int y1 = y;
                        
                        if ((y1 & 1) != 0)
                            x1 = 7 - x1;
                        
                        data[x1 + y1 * 8] = (r << 16) | (g << 8) | b;
                    }
                }
                
                ws2811_update (data);
                
                Thread.sleep (10);
                step += 1;
            }
            
            effect += 1;
            effect %= 2;
        }
    }
      */     
    /**
     * twisty swirly goodness
     *
     */
    private double[] swirl (int x, int y, int step)
    {
        x -= 4;
        y -= 4;

        final double dist = Math.sqrt(x * x + y * y) / 2.0;
        final double angle = (step / 10.0) + (dist * 1.5);
        final double s = Math.sin(angle);
        final double c = Math.cos(angle);    
    
        final double xs = x * c - y * s;
        final double ys = x * s + y * c;

        final double r = Math.abs(xs + ys) * 64 - 20;

        final double[] result = new double[3];
        
        result[0] = r;
        result[1] = r + (s * 130);
        result[2] = r + (c * 130);
        
        return result;
    }
    
    private double[] rainbow_search (int x, int y, int step)
    {
        final double xs = Math.sin((step) / 100.0) * 20.0;
        final double ys = Math.cos((step) / 100.0) * 20.0;

        final double scale = ((Math.sin(step / 60.0) + 1.0) / 5.0) + 0.2;
        
        final double r = Math.sin((x + xs) * scale) + Math.cos((y + xs) * scale);
        final double g = Math.sin((x + xs) * scale) + Math.cos((y + ys) * scale);
        final double b = Math.sin((x + ys) * scale) + Math.cos((y + ys) * scale);
        
        final double[] result = new double[3];
        
        result[0] = r * 255;
        result[1] = g * 255;
        result[2] = b * 255;
        
        return result;
    }
    
    private static int clip (int a, int b)
    {
        if (a < 0)
            return 0;
        
        return (a > b) ? b : a;
    }
        
    private int[] data;
}
