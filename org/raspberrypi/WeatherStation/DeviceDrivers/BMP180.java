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
 * This class is used to interface to the BMP180 air pressure and temperature
 * sensor.
 * 
 * @author Jim Darby.
 */
public class BMP180 {
    /**
     * The {@code BMP180.Result} class is used to return the result from a
     * reading of the BMP180 device. It is (typically) constructed by the
     * {@code BMP180} class as a result of asking for a reading and returns the
     * values read in a convenient form.
     */
    public class Result
    {
         /**
          * This constructs the result value.
          * 
          * @param pressure The pressure (in Pa).
          * @param temperature The temperature (in degrees Celsius times 10).
          */
        public Result (int pressure, int temperature)
        {
            pressure_= pressure;
            temperature_ = temperature;
        }
        
        /**
         * This method returns the pressure reading in Pa. The value is set in
         * the constructor and cannot be modified.
         * 
         * @return The pressure in Pa. 
         */
        public int getPressure ()
        {
            return pressure_;
        }
        
        /**
         * This methods returns the temperature in degrees Celsius times 10.
         * The value is set in the constructor and cannot be modified.
         * 
         * @return The temperature in degrees Celsius times 10.
         */
        public int getTemperature ()
        {
            return temperature_;
        }
        
        /** Where we hold the pressure (in Pa). */
        private final int pressure_;
        /** Where we hold the temperature (in Celsius times 10). */
        private final int temperature_;
    }
     
    /**
     * This is the constructor for the BMP180 class. It takes the bus the
     * device is on and the address on that bus (which can be varied by setting
     * various pins on the device.
     * 
     * @param bus The {@code I2CBus} the device is on.
     * @param dev The device address on that bus (it can be changed).
     * @throws IOException If something goes amiss talking to the device.
     */
    public BMP180 (I2CBus bus, int dev) throws IOException
    {
        // Get a device object to use for communication.
        device = bus.getDevice (dev);
        
        // Verify it really is a BMP180
        if (device.read (SIGNATURE_REG) != SIGNATURE)
            throw new IOException ("BMP180: Invalid signature");
        
        // Load the device calibration data (all in one go!).
        final int got = device.read (CALIBRATION_REG, buffer, 0, BUFFER_SIZE);
        
        // Did we get it all?
        if (got != BUFFER_SIZE)
            throw new IOException ("BMP180: Failed to read calibration coefficients");
        
        // The values are all 16-bit but AC4 to AC6 are unsigned. As Java
        // doesn't have unsigned variables but bytes are signed we take great
        // care with the following shifts and masks and place the results in
        // 32-bit ints where they fit properly.
        AC1 = ((buffer[ 0]       ) << 8) | (buffer[ 1] & 0xff);
        AC2 = ((buffer[ 2]       ) << 8) | (buffer[ 3] & 0xff);
        AC3 = ((buffer[ 4]       ) << 8) | (buffer[ 5] & 0xff);
        AC4 = ((buffer[ 6] & 0xff) << 8) | (buffer[ 7] & 0xff);
        AC5 = ((buffer[ 8] & 0xff) << 8) | (buffer[ 9] & 0xff);
        AC6 = ((buffer[10] & 0xff) << 8) | (buffer[11] & 0xff);
        B1  = ((buffer[12]       ) << 8) | (buffer[13] & 0xff);
        B2  = ((buffer[14]       ) << 8) | (buffer[15] & 0xff);
        MB  = ((buffer[16]       ) << 8) | (buffer[17] & 0xff);
        MC  = ((buffer[18]       ) << 8) | (buffer[19] & 0xff);
        MD  = ((buffer[20]       ) << 8) | (buffer[21] & 0xff);
    }
    
    /**
     * Read the temperature and pressure from the device.
     * 
     * @param mode Sets the precision. 0 is ultra low power with one sample, 1
     * is standard with two samples, 2 is high resolution with four samples and
     * 3 is ultra high precision with 8 samples. Values outside the range 0 to
     * 3 will result in an {@code IOException} being thrown.
     * @return A {@code BMP180.Result} object containing the values read.
     * @throws IOException In the case of invalid mode selection or I2C bus
     * problems.
     */
    public Result read (int mode) throws IOException
    {
        // Validate the mode
        if (mode < 0 || mode > 3)
            throw new IOException ("BMP180: Invalid mode");
        
        // Request the temperature and get the result as per the data sheet.
        device.write (CMD_REG, CMD_READ_TEMP);
        Gpio.delay (5);
        
        if (device.read (RESULT_REG, buffer, 0, TEMP_SIZE) != TEMP_SIZE)
            throw new IOException ("BMP180: Short temperature read");
        
        // Perform rituals as per the data sheet.
        final int UT = (buffer[0] << 8) | (buffer[1] & 0xff);
        
        long X1 = ((UT - AC6) * AC5) / 32768;
        long X2 = (MC * 2048) / (X1 + MD);
        final long B5 = X1 + X2;
        
        // Obtain the temperature as degrees Celsius times 10.
        final long T = (B5 + 8) / 16;
        
        // How long it takes to read the pressure for each mode.
        final int delays[] = {5, 8, 14, 26};
        
        // Request the pressure and get the result as per the data sheet.
        device.write (CMD_REG, (byte) (CMD_READ_PRESSURE + (mode << 6)));
        Gpio.delay (delays[mode]);
        
        if (device.read (RESULT_REG, buffer, 0, PRESSURE_SIZE) != PRESSURE_SIZE)
            throw new IOException ("BMP180: Short pressure read");
        
        // Perform more magic as per the data sheet.
        final int UP = (((buffer[0] & 0xff) << 16) | ((buffer[1] & 0xff) << 8) | (buffer[2] & 0xff)) >> (8 - mode);
        
        final long B6 = B5 - 4000;
        X1 = (B2 * ((B6 * B6) / 4096)) / 2048;
        X2 = (AC2 * B6) / 2048;
        long X3 = X1 + X2;
        final long B3 = (((AC1 * 4 + X3) << mode) + 2) / 4;
        X1 = (AC3 * B6) / 8192;
        X2 = (B1 * ((B6 * B6) / 8192)) / 32768;
        X3 = ((X1 + X2) + 2) / 4;
        final long B4 = (AC4 * (X3 + 32768)) / 32768;
        final long B7 = (UP - B3) * (50000 >> mode);
        
        long p;
        
        if (B7 < 0x80000000L)
            p = (B7 * 2) / B4;
        else
            p = (B7 / B4) * 2;
        
        X1 = (p / 256) * (p / 256);
        X1 = (X1 * 3038) / 65536;
        X2 = (-7357 * p) / 65536;
        p = p + (X1 + X2 + 3791) / 16;
        
        // And we have a result
        return new Result ((int) p, (int) T);
    }
 
    /** Largest read data sized used (in fact calibration data) */
    private static final int BUFFER_SIZE = 22;
    /** Location of calibration data */
    private static final int CALIBRATION_REG = 0xaa;
    /** Location of command register */
    private static final int CMD_REG = 0xf4;
    /** Locate of result register */
    private static final int RESULT_REG = 0xf6;
    /** Locate of signature register */
    private static final int SIGNATURE_REG = 0xd0;
    /** Signature value */
    private static final int SIGNATURE = 0x55;
    /** Size of temperature data */
    private static final int TEMP_SIZE = 2;
    /** Size of pressure data */
    private static final int PRESSURE_SIZE = 3;
    /** Command to request a temperature read */
    private static final byte CMD_READ_TEMP = 0x2e;
    /** Command to request a pressure read */
    private static final byte CMD_READ_PRESSURE = 0x34;
 
    /** The I2C device */
    private final I2CDevice device;
    /** Buffer used for reading results */
    private final byte[] buffer = new byte[BUFFER_SIZE];
    
    // Device specific calibration constants. These can't really be commented
    // on as they're just magic from the data sheet.
    private final int AC1, AC2, AC3, AC4, AC5, AC6;
    private final int B1, B2;
    private final int MB, MC, MD;
}