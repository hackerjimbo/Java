/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.raspberrypi.WeatherStation.DeviceDrivers;

import java.util.logging.Logger;

import com.pi4j.io.gpio.GpioController;
import com.pi4j.io.gpio.Pin;
import com.pi4j.io.gpio.PinPullResistance;
import com.pi4j.io.gpio.GpioPinDigitalInput;
import com.pi4j.io.gpio.event.GpioPinListenerDigital;
import com.pi4j.io.gpio.event.GpioPinDigitalStateChangeEvent;
import com.pi4j.io.gpio.PinState;

/**
 * This class is used to implement pulse counting on a GPIO input.
 * @author Jim Darby
 */
public class PulseCounter implements GpioPinListenerDigital {
    private static final Logger LOG = Logger.getLogger("DeviceDrivers.PulseCounter");
    
    /**
     * This class is used to hold the result of a pulse count reading.
     */
    public class Result {
        /**
         * COnstruct a result.
         * @param count How many pulses were counted.
         * @param nanoseconds How many nanoseconds they were counted over.
         */
        public Result (int count, long nanoseconds)
        {
            count_ = count;
            nanoseconds_ = nanoseconds;
        }
        
        /**
         * Return the pulse count.
         * @return The pulse count.
         */
        public int getCount () {
            return count_;
        }
        
        /**
         * Return the time that the pulse count was counted over.
         * @return The time in nanoseconds.
         */
        public long getNanoseconds () {
            return nanoseconds_;
        }
        
        /** Where we store the count */
        private final int count_;
        /** Where we store the nanoseconds */
        private final long nanoseconds_;
    }
    
    /**
     * Construct a PulseCounter. This counts the HIGH to LOW transitions of the
     * input on a specific pin and with a specific debounce time.
     * @param gpio The pi4j GPIO controller to use.
     * @param pin The pin we want to count pulses on.
     * @param debounce The debounce time in milliseconds.
     */
    public PulseCounter (GpioController gpio, Pin pin, int debounce)
    {
        // Locate and provision the pin.
        in_ = gpio.provisionDigitalInputPin (pin, PinPullResistance.PULL_UP);
        
        // Take note of its initial state.
        lastState_ = in_.getState();
        
        // Set debounce time
        in_.setDebounce (debounce);

        // Initialise readings
        count_ = 0;
        readAt_ = System.nanoTime ();

        // And use ourselves as the listener
        in_.addListener (this);
    }
    
    /**
     * Stop listening and generally shut down.
     */
    public void close ()
    {
        in_.removeListener(this);
    }
    
    /**
     * Override finalize to make sure we're shut down. 
     * @throws Throwable If it all goes pear shaped.
     */
    @Override
    protected void finalize () throws Throwable
    {
        close ();
        super.finalize ();
    }
    
    /**
     * Handle a pin change interrupt. Note that this is synchronized to avoid
     * multiple access the to the count_ and readAt_ object variables.
     * @param e The event we're looking at.
     */
    @Override
    public synchronized void handleGpioPinDigitalStateChangeEvent (GpioPinDigitalStateChangeEvent e)
    {
        // What has happened?
        switch (e.getState ()) {
            case HIGH:
                // We should come to HIGH from LOW.
                if (lastState_ != PinState.LOW) {
                    LOG.warning ("PulseCounter: Potentially missed a pulse (double HIGH)");
                    
                    // Add 1 to the count as we (presumably) missed a LOW.
                    count_ += 1;
                }
                
                // Make a note that we're now in the HIGH state.
                lastState_ = PinState.HIGH;
                
                break;
                
            case LOW:
                // Increment the pulse count.
                count_ += 1;
                
                // We should have come to LOW from HIGH.                
                if (lastState_ != PinState.HIGH)
                    LOG.warning ("PulseCounter: Potentially missed a pulse (double LOW)");
                
                // Make a note that we're now in the LOW state.
                lastState_ = PinState.LOW;
                
                break;
                
            default:
                // How did we get here?
                LOG.warning ("Pin goes WHUT!");
                break;
        }
    }
    
    /**
     * Obtain the result of the pulse counting. Note that this is a synchrnoized
     * method to avoid multiple access to the count_ and readAt_ object
     * variables.
     * @return The result. 
     */
    public synchronized Result getResult () {
        final long now = System.nanoTime ();
        
        Result r = new Result (count_, now - readAt_);
        count_ = 0;
        readAt_ = now;
        
        return r;
    }
    
    /** The Pin we're working with. */
    private final GpioPinDigitalInput in_;
    /** The counts since the last read. */
    private int count_;
    /** When we last read the data. */
    private long readAt_;
    /** The pin state we last saw */
    private PinState lastState_;
}