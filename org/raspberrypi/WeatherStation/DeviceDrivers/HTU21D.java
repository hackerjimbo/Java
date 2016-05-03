/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.raspberrypi.WeatherStation.DeviceDrivers;

import java.io.IOException;

import com.pi4j.io.i2c.I2CBus;
import com.pi4j.io.i2c.I2CDevice;

import com.pi4j.wiringpi.Gpio;

/**
 * This class is used to interface to the HTU21D humidity and temperature
 * sensor.
 * 
 * @author Jim Darby.
 */
public class HTU21D {
    /**
     * The {@code HTU21D.Result} class is used to return the result from a
     * reading of the HTU21D device. It is (typically) constructed by the
     * {@code HTU21D} class as a result of asking for a reading and returns the
     * values read in a convenient form.
     */
    public class Result
    {
        /**
         * This constructs the result value.
         * @param humidity The relative humidity.
         * @param temperature The temperature (in degrees Celsius).
         */
        public Result (double humidity, double temperature)
        {
            humidity_= humidity;
            temperature_ = temperature;
        }
        
        /**
         * This methods returns the relative humidity. The value is set in the
         * constructor and cannot be modified.
         * @return The relative humidity as a percentage.
         */
        public double getHumidity ()
        {
            return humidity_;
        }
        
        /**
         * This method returns the temperature in degrees Celsius. The value
         * is set in the constructor and cannot be modified.
         * @return The temperature in degrees Celsius.
         */
        public double getTemperature ()
        {
            return temperature_;
        }
        
        /** Where we store the humidity. */
        private final double humidity_;
        
        /** Where we store the temperature. */
        private final double temperature_;
    }
    
    /**
     * This is the constructor for the HTU21D class. It takes the bus the
     * device is on (the address is fixed at 0x40).
     * @param bus The {@code I2CBus} the device is on.
     * @throws IOException If something goes amiss talking to the device.
     */
    public HTU21D (I2CBus bus) throws IOException
    {
        // Get a device object to use for communication.
        device = bus.getDevice (DEVICE_ID);
        
        // Perform a soft reset
        device.write (CMD_SOFT_RESET);
        
        // Set device mode to be sure.
        device.write (INIT, 0, INIT.length);
    }
    
    /**
     * Reads both temperature and pressure from the device.
     * @return A {@code HTU21D.Result} object containing the values read.
     * @throws IOException If something goes amiss talking to the device.
     */
    public Result read () throws IOException
    {    
        // Start devive reading the temperature.
        device.write (CMD_READ_TEMP_NOHOLD);
        
        // The data sheet states that 50 mS is the maximum time to read the temperature.
        Gpio.delay (50);
        
        // Grab data from the device
        if (device.read (buffer, 0, 3) != 3)
            throw new IOException ("HTU21D: Failed to read temperature");
        
        // Validate the checksum (CRC).
        verify_crc (buffer);
        
        // Calculate the temperature as per the data sheet.
        final double temperature = -46.85 + 175.72 * (((buffer[0] & 0xff) << 8) | (buffer[1] & 0xfc)) / 65536.0;
        
        // Start device reading the temperature
        device.write (CMD_READ_HUMID_NOHOLD);
        
        // The data sheet states that 16 mS is the maximum time to read the humidity
        Gpio.delay (16);
        
        // Grab the readings from the device.
        if (device.read (buffer, 0, 3) != 3)
            throw new IOException ("HTU21D: Failed to read humidity");
        
        // Validate the checksum (CRC).
        verify_crc (buffer);
        
        // Calculate the basic humidity as per the datasheet.
        final double humidity = -6 + 125 * (((buffer[0] & 0xff) << 8) | (buffer[1] & 0xfc)) / 65536.0;
        
        // Return the result including the temperature compensation to the
        // humidity as per the datasheet.
        return new Result (humidity + (25 - temperature) * -0.15, temperature);
    }
    
    /**
     * Verify the data's checksum. The algorithm is based on the code from
     * Sparkfun's Arduino breakout found at
     * {@code https://github.com/sparkfun/HTU21D_Breakout/blob/master/Libraries/Arduino/src/SparkFunHTU21D.cpp}
     * which is licensed as ``This code is public domain but you buy me a beer
     * if you use this and we meet someday (Beerware license).''
     * @param buffer The buffer to verify.
     * @throws IOException If verification fails.
     */
    private static void verify_crc (byte buffer[]) throws IOException
    {
        int remainder = ((buffer[0] & 0xff) << 16) | ((buffer[1] & 0xff) << 8) | (buffer[2] & 0xff);
        int divisor = 0x988000;

        /*
         * POLYNOMIAL = 0x0131 = x^8 + x^5 + x^4 + 1
         * divisor = 0x988000 is the 0x0131 polynomial shifted to farthest left of three bytes
         */
        
        for (int i = 0; i < 16; ++i) {
            if ((remainder & (1 << (23 - i))) != 0)
                remainder ^= divisor;
            
            divisor >>= 1;
        }
        
        if (remainder != 0)
            throw new IOException ("HTU21D: CRC failure");
    }
    
    // Class constatants
    
    /** The device on the bus */
    private static final byte DEVICE_ID = (byte) 0x40;
    /** Reset under software control */
    private static final byte CMD_SOFT_RESET = (byte) 0xfe;
    /** Write the user register */
    private static final byte CMD_WRITE_USER_REG = (byte) 0xe6;
    /** Request a temperature read without holding the bus */
    private static final byte CMD_READ_TEMP_NOHOLD = (byte) 0xf3;
    /** Request a humidity read without holding the bus */
    private static final byte CMD_READ_HUMID_NOHOLD = (byte) 0xf5;
    
    // This is the power-on default but be safe (highest precision).
    private static final byte INIT[] = { CMD_WRITE_USER_REG, 0 };
    
    // Object varaibles
    
    /** The pi4j I2CDevice object we use to communicate with */
    private final I2CDevice device;
    /** The buffer area we use */
    private final byte buffer[] = new byte[3];
 }