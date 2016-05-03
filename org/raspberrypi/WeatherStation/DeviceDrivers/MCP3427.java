/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.raspberrypi.WeatherStation.DeviceDrivers;

import java.io.IOException;

import java.util.logging.Logger;

import com.pi4j.io.i2c.I2CBus;
import com.pi4j.io.i2c.I2CDevice;
import com.pi4j.wiringpi.Gpio;
import java.util.logging.Level;

/**
 * This class is used to interface to the MCP3427 analogue to digital converter
 * (ADC).
 * 
 * @author Jim Darby
 */
public class MCP3427 {
    /** The logger we use to report information */
    private static final Logger log = Logger.getLogger("DeviceDrivers.MCP3427");
        
    /**
     * Constructor for the MCP3227 device driver. Given a bus and  a device
     * address (the device can have more than one address), the number of bits to be
     * used (12, 14 or 16) and the gain to be applied to the input (1, 2, 4 or
     * 8) this constructs the device driver.
     * 
     * @param bus The {@code I2CBus} the device is on.
     * @param dev The device address on the bus.
     * @throws IOException If communication on the I2C bus fails.
     */
    public MCP3427 (I2CBus bus, int dev) throws IOException
    {
        dev_id = dev;
        device = bus.getDevice (dev);
        device.write (CMD_SINGLE_SHOT);
        max_tries = 0 ;
    }
    
    /**
     * Read the value of an analogue input. We normalise the value returned so
     * that the actual voltage is the returned value * VREF / 32768.
     * 
     * @param channel The channel to read: 0 or 1.
     * @param bits The bits resolution required: 12, 14 or 16.
     * @param gain The gain to be used: 1, 2, 4 or 8.
     * @return The value read normalised to -32768 to 32767.
     * @throws IOException In the case of an invalid parameter or some mishap
     * on the I2C bus.
     */
    public int read (int channel, int bits, int gain) throws IOException
    {
        byte config = CMD_START;
        int delay;
        
        // Add the channel to the command.
        switch (channel) {
            case 1:
                config |= CMD_CHAN_1;
                break;
                
            case 2:
                config |= CMD_CHAN_2;
                break;
                
            default:
                throw new IOException ("MCP3427@" + Integer.toHexString(dev_id) + ": Invalid channel");
        }
        
        // Add the bits to the command and add in the current delay.
        switch (bits) {
            case 12:
                config |= CMD_BITS_12;
                delay = delay12;
                break;
                
            case 14:
                delay = delay14;
                config |= CMD_BITS_14;
                break;
                
            case 16:
                delay = delay16;
                config |= CMD_BITS_16;
                break;
                
            default:
                throw new IOException ("MCP3427@" + Integer.toHexString(dev_id) + ": Invalid resolution");
        }
        
        // Add in the gain to the command.
        switch (gain) {
            case 1:
                config |= CMD_GAIN_1;
                break;
                
            case 2:
                config |= CMD_GAIN_2;
                break;
                
            case 4:
                config |= CMD_GAIN_4;
                break;
                
            case 8:
                config |= CMD_GAIN_8;
                break;

            default:
                throw new IOException ("MCP3427@" + Integer.toHexString(dev_id) + ": Invalid gain");
        }
        
        // Run the command
        device.write (config);
        
        // Wait for the response
        Gpio.delay (delay);
            
        boolean ok = false;
        boolean on_first = false;
        
        for (int tries = 0; tries < 10; ++tries) {
            if (device.read (buffer, 0, 3) != 3)
                throw new IOException ("MCP3427@" + Integer.toHexString(dev_id) + ": failed to read data");
                   
            if ((buffer[2] & 0x80) == 0) {
                ok = true;
                
                if (tries == 0)
                    on_first = true;
                
                if (tries > max_tries) {
                    max_tries = tries;
                    
                    log.log (Level.FINE, "MCP3427@{0}: max tries now {1}", new Object[]{Integer.toHexString(dev_id), max_tries});
                }
                
                break;
            }
            
            Gpio.delay (delay / 10 + 1);
        }
        
        if (!ok)
            throw new IOException ("MCP3427@" + Integer.toHexString(dev_id) + ": Conversion failed");
        
        // If we didn't get it on the first time, bump up the delay
        if (!on_first) {
            switch (bits) {
                case 12:
                    delay12 += 1;
                    log.log (Level.FINE, "MCP3427@{0}: delay 12 now {1}", new Object[]{Integer.toHexString(dev_id), delay12});
                    break;
                    
                case 14:
                    delay14 += 1;
                    log.log (Level.FINE, "MCP3427@{0}: delay 14 now {1}", new Object[]{Integer.toHexString(dev_id), delay14});
                    break;
                    
                case 16:
                    delay16 += 1;
                    log.log (Level.FINE, "MCP3427@{0}: delay 16 now {1}", new Object[]{Integer.toHexString(dev_id), delay16});
                    break;
            }
        }
        
        // Note that we keep the sign extension of buffer[0] as the result is
        // a signed value.
        return ((buffer [0]  << 8) | (buffer[1] & 0xff)) << (16 - bits);
    }
    
    /** The highest value we can return */
    public static final int MAX = 32767;
    /** The lowest value we can return */
    public static final int MIN = -32768;
    /** The reference voltage used */
    public static final double VREF = 2.048;
    
    /** The pi4j I2CDevice object we use to communicate with */
    private final I2CDevice device;
    /** The numeric device on the bus */
    private final int dev_id;
    /** The buffer area we use */
    private final byte buffer[] = new byte[3];
    /** The maximum number of tries we have taken to get a reading */
    private int max_tries;
    
    /** The incomplete command to start a single-shot conversion */
    private static final byte CMD_START       = (byte) 0x80;
    /** Run the conversion on channel 1 */
    private static final byte CMD_CHAN_1      = (byte) 0x00;
    /** Run the conversion on channel 2 */
    private static final byte CMD_CHAN_2      = (byte) 0x20;
    /** Perform a single shot conversion */
    private static final byte CMD_SINGLE_SHOT = (byte) 0x00;
    /** Convert with 12-bit accuracy */
    private static final byte CMD_BITS_12     = (byte) 0x00;
    /** Convert with 14-bit accuracy */
    private static final byte CMD_BITS_14     = (byte) 0x04;
    /** Convert with 16-bit accuracy */
    private static final byte CMD_BITS_16     = (byte) 0x08;
    /** Set the gain to 1 (no gain) */
    private static final byte CMD_GAIN_1      = (byte) 0x00;
    /** Set the gain to 2 */
    private static final byte CMD_GAIN_2      = (byte) 0x01;
    /** Set the gain to 4 */
    private static final byte CMD_GAIN_4      = (byte) 0x02;
    /** Set the gain to 8 */
    private static final byte CMD_GAIN_8      = (byte) 0x03;
    
    // These are the delays we use for each resolution. They start off at the
    // device minimum and then we increase them to the device maximum. We always
    // round towards increased time.
    
    /** Minimum delay for a 12-bit sample. */
    private static final int DELAY12_MIN = 1000 / 328 + 1;
    /** Maximum delay for a 12-bit sample. */
    private static final int DELAY12_MAX = 1000 / 176 + 1;
    /** Minimum delay for a 14-bit sample. */
    private static final int DELAY14_MIN = 1000 /  82 + 1;
    /** Maximum delay for a 14-bit sample. */
    private static final int DELAY14_MAX = 1000 /  44 + 1;
    /** Minimum delay for a 16-bit sample. */
    private static final int DELAY16_MIN = 1000 /  21 + 1;
    /** Maximum delay for a 16-bit sample. */
    private static final int DELAY16_MAX = 1000 /  11 + 1;
    
    /** The delay we currently use for 12-bit readings, starts at the minimum */
    private int delay12 = DELAY12_MIN;
    /** The delay we currently use for 14-bit readings, starts at the minimum */
    private int delay14 = DELAY14_MIN;
    /** The delay we currently use for 16-bit readings, starts at the minimum */
    private int delay16 = DELAY16_MIN;
}