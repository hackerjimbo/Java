/*
 * Copyright (C) 2016 Jim Darby and the Raspberry Pi Foundation.
 *
 * This software is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this software.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.raspberrypi.WeatherStation;

import org.raspberrypi.WeatherStation.DeviceDrivers.*;

import java.io.IOException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.io.FileNotFoundException;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import com.pi4j.io.gpio.GpioFactory;
import com.pi4j.io.gpio.GpioController;
import com.pi4j.io.gpio.RaspiPin;
import com.pi4j.io.i2c.I2CBus;
import com.pi4j.io.i2c.I2CFactory;
import com.pi4j.wiringpi.Gpio;
import com.pi4j.system.SystemInfo;
import com.pi4j.system.SystemInfo.BoardType;

import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author pi
 */
public class WeatherStation {
    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        final Logger root_logger = Logger.getLogger ("");
        final Logger log = Logger.getLogger ("WeatherStation");
        final Reporter reporter = new Reporter ();
        
        root_logger.setLevel (Level.FINE);
        root_logger.addHandler (reporter);
        
        database = new Database ("/home/pi/credentials.json");
        reporter.addDB (database);

        log.info ("Weather Station code starting.");
        
        // Determine board nad bus used
        boolean plus_board = true;
        int use_i2cbus = I2CBus.BUS_1;
        
        try {
            final BoardType type = SystemInfo.getBoardType ();
            
            switch (type)
            {
                // What do we work on?
                case ModelA_Plus_Rev1:
                case ModelB_Plus_Rev1:
                case Model2B_Rev1:
                    break;
                    
                // What do we *not* work on? This is the most useful as we
                // know about all the boards that aren't supported.
                case ModelA_Rev1:
                case ModelB_Rev1:
                    // Use the original I2C bus
                    use_i2cbus = I2CBus.BUS_0;
                    
                    // ...and fall through...
                    
                case ModelB_Rev2:
                    plus_board = false;
                    log.log (Level.SEVERE, "Unsupported board, using alternative pins: {0}", type);
                    break;
                    
                // The clear ommission here is the Pi 3. However that should
                // work (but hasn't been formally tested). The Pi Zero has been
                // tested and works well.
                case UNKNOWN:
                    log.log (Level.WARNING, "Unknown board type, hoping for the best");
                    break;
                    
                default:
                    log.log (Level.WARNING, "getBoardType gave an unrecognised result, hoping for the best");
                    break;
            }
        }
        
        catch (IOException | InterruptedException e) {
            log.log (Level.WARNING, "Unable to determine board type: {0}", e.getLocalizedMessage ());
        }

        // Load wind direction and rain/wind speed calibration file
        final WindDirection direction = new WindDirection ("/home/pi/wind_direction.json");
        final Calibration calibration = new Calibration ("/home/pi/calibration.json");
        
        // Start the uploaded thread
        final Thread uploader = new CloudUploader ();
        
        uploader.start ();

        // Start the twitter thread
        final Thread tweeter = new Tweeter ();
        
        tweeter.start ();
        
        GpioController gpio = GpioFactory.getInstance ();

        try {
            final PulseCounter wind_counter = new PulseCounter (gpio, plus_board ? RaspiPin.GPIO_21 : RaspiPin.GPIO_00, 1);
            final PulseCounter rain_counter = new PulseCounter (gpio, plus_board ? RaspiPin.GPIO_22 : RaspiPin.GPIO_02, 300);
            final DS18B20 ds18b20 = new DS18B20 ();
            
            final I2CBus bus = I2CFactory.getInstance (use_i2cbus);
            
            MCP3427 adc1   = null;
            MCP3427 adc2   = null;
            BMP180  bmp180 = null;
            HTU21D  hdu21d = null;
            
            long rain_total = 0;
            
            // Air quality readings
            int air_readings = 0;
            double air_total = 0;
            
            // Wind speed.
            int wind_readings = 0;
            double wind_time = 0;
            int wind_total = 0;
            double wind_peak = 0;
            ArrayList <Double> wind_directions = new ArrayList <> ();
            
            // BMP180 readings
            int bmp180_readings = 0;
            double bmp180_total = 0;
            double pressure_total = 0;
 
            // HDR21D readings
            int htu21d_readings = 0;
            double htu21d_total = 0;
            double humidity_total = 0;
            
            // DS18B20 readings
            int temp3_readings = 0;
            double temp3_total = 0;
            
            // Tens of minutes past the hour we last reported, negative for not
            int last_tens_past = -100;
            boolean sample_complete = false;
            
            try {
                adc1 = new MCP3427 (bus, 0x69);
                log.info ("MCP3427 at 0x69 available");
            }
            
            catch (IOException e) {
                log.log (Level.WARNING, "MCP3427 at 0x69 not available: {0}", e.getMessage ());
            }
            
            try {
                adc2 = new MCP3427 (bus, 0x6a);
                log.info ("MCP3427 at 0x6a available");
            }
            
            catch (IOException e) {
                log.log (Level.WARNING, "MCP3427 at 0x6a not available: {0}", e.getMessage ());
            }
            
            try {
                bmp180 = new BMP180 (bus, 0x77);
                log.info ("BMP180 available");
            }
            
            catch (IOException e) {
                log.log (Level.WARNING, "BMP180 not available: {0}", e.getMessage ());
            }
            
            try {
                hdu21d = new HTU21D (bus);
                log.info ("HTU21D available");
            }
            
            catch (IOException e) {
                log.log (Level.WARNING, "HTU21D not available: {0}", e.getMessage ());
            }
            
            while (true) {                
                database.updateConfig ();
                
                if (rain_counter != null) {
                    PulseCounter.Result rain = rain_counter.getResult ();

                    rain_total += rain.getCount ();
                }
                
                if (wind_counter != null) {
                    PulseCounter.Result wind = wind_counter.getResult ();
                
                    final double wind_elapsed = wind.getNanoseconds () / 1e9;
                    final double wind_rate = wind.getCount () / wind_elapsed;
                
                    wind_readings += 1;
                    wind_total += wind.getCount();
                    wind_time += wind_elapsed;
                
                    if (wind_rate > wind_peak)
                        wind_peak = wind_rate;
                }
                
                if (adc1 != null) {
                    try {
                        final int wind_adc = adc1.read (1, 16, 1);
                        final double wind_dir = direction.angleFromADC (wind_adc);
                    
                        if (wind_dir >= 0)
                            wind_directions.add (wind_dir);
                    }
                    
                    catch (IOException e) {
                        log.log (Level.WARNING, "Wind direction read failed: {0}", e.getMessage ());
                    }
                }
                
                if (adc2 != null) {
                    try {
                        final int air_quality = adc2.read (1, 16, 1);
                        
                        air_readings += 1;
                        air_total += air_quality;
                    }
                    
                    catch (IOException e) {
                        log.log (Level.WARNING, "Air quality read failed: {0}", e.getMessage ());
                    }
                }
                
                if (bmp180 != null) {
                    try {
                        BMP180.Result tp = bmp180.read (3);

                        bmp180_readings += 1;
                        bmp180_total += tp.getTemperature () / 10.0;
                        pressure_total += tp.getPressure () / 100.0;
                    }
                    
                    catch (IOException e) {
                        log.log (Level.WARNING, "BMP180 read failed: {0}", e.getMessage());
                    }
                }
                
                if (hdu21d != null) {
                    try {
                        HTU21D.Result th = hdu21d.read ();

                        htu21d_readings += 1;
                        htu21d_total += th.getTemperature ();
                        humidity_total += th.getHumidity ();
                    }
                    
                    catch (IOException e) {
                        log.log(Level.WARNING, "HDU21D read failed: {0}", e.getMessage());
                    }
                }
                
                if (ds18b20.ok ()) {
                    try {
                        final double temp3 = ds18b20.read ();
                       
                        temp3_readings += 1;
                        temp3_total += temp3;
                    }
                    
                    catch (IOException e) {
                        log.log (Level.WARNING, "DS18B20 read failed: {0}", e.getMessage());
                    }
                }
                
                ZonedDateTime now = ZonedDateTime.now ();
                final int tens_past = now.getMinute () / 10;
                
                if (last_tens_past < 0)
                    last_tens_past = tens_past;
                
                if (tens_past != last_tens_past) {
                    boolean reset_data = false;
                    
                    // Only report if we completed a full ten minute run.
                    // Otherwise data (e.g. rain rate) may be falsly low.
                    if (sample_complete) {
                        final double wind_scale = calibration.getPulsesToWindSpeed();
                        final double wind_speed = (wind_readings > 0 && wind_scale > 0) ? (wind_scale * wind_total / wind_time) : -1000;
                        final double air_quality = (air_readings   > 0) ? 100 * (1 - ((((double) air_total) / air_readings) / MCP3427.MAX)) : -1000;
                           
                        double wind_direction = -1000;
                        
                        final int wind_samples = wind_directions.size ();
                        
                        if (wind_samples > 0) {
                            double sin_sum = 0;
                            double cos_sum = 0;
                            
                            for (double d : wind_directions) {
                                final double radians = Math.toRadians (d);
                                sin_sum += Math.sin (radians);
                                cos_sum += Math.cos (radians);
                            }
                            
                            wind_direction = Math.toDegrees (Math.atan2 (sin_sum, cos_sum));
                            
                            // If it's negative then make it positive
                            if (wind_direction < 0)
                                wind_direction += 360;
                            
                            wind_directions.clear ();
                        }
                        
                        try (PrintStream out = new PrintStream (new FileOutputStream ("/home/pi/weather.data", true))) {
                            out.printf ("%s,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f",
                                    now.format (DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                                    rain_total * calibration.getPulsesToMM (),
                                    wind_speed,
                                    (wind_scale     > 0) ? (wind_peak * wind_scale)          : -1000,
                                    air_quality,
                                    (bmp180_readings > 0) ? (bmp180_total / bmp180_readings)    : -1000,
                                    (bmp180_readings > 0) ? (pressure_total / bmp180_readings) : -1000,
                                    (htu21d_readings > 0) ? (htu21d_total / htu21d_readings)    : -1000,
                                    (htu21d_readings > 0) ? (humidity_total / htu21d_readings) : -1000,
                                    (temp3_readings > 0) ? (temp3_total / temp3_readings)    : -1000,
                                    wind_direction);
                            out.println ();
                            out.close ();
                            
                            reset_data = true;
                        }
                        
                        catch (FileNotFoundException e) {
                            log.log (Level.WARNING, "Failed to open output file: {0}", e.getMessage ());
                        }
                        
                        if (database.log (now.withNano (0).format (DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                                rain_total * calibration.getPulsesToMM (),
                                wind_speed,
                                (wind_scale     > 0) ? (wind_peak * wind_scale)          : -1000,
                                air_quality,
                                (bmp180_readings > 0) ? (bmp180_total / bmp180_readings)    : -1000,
                                (bmp180_readings > 0) ? (pressure_total / bmp180_readings) : -1000,
                                (htu21d_readings > 0) ? (htu21d_total / htu21d_readings)    : -1000,
                                (htu21d_readings > 0) ? (humidity_total / htu21d_readings) : -1000,
                                wind_direction,
                                (temp3_readings > 0) ? (temp3_total / temp3_readings)    : -1000))
                            reset_data = true;
                    }
                    else
                    {
                        // We're startin a new interval, the data will be complete
                        sample_complete = true;
                    }
                    
                    if (reset_data) {
                        // Reset everything if it all worked
                        rain_total = 0;

                        air_readings = 0;
                        air_total = 0;

                        wind_readings = 0;
                        wind_time = 0;
                        wind_total = 0;
                        wind_peak = 0;

                        bmp180_readings = 0;
                        bmp180_total = 0;
                        pressure_total = 0;

                        htu21d_readings = 0;
                        htu21d_total = 0;
                        humidity_total = 0;

                        temp3_readings = 0;
                        temp3_total = 0;
                    }
                                
                    last_tens_past = tens_past;
                }
                
                Gpio.delay (10000 - (now.getSecond() % 10) * 1000 - now.getNano() / 1000000);
            }
        }
        
        catch (IOException e)
        {
            log.log (Level.SEVERE, "IOException: {0}", e.getMessage());
        }
        
        gpio.shutdown ();
    }
    
    public static Database getDatabase ()
    {
        return database;
    }
    
    private static Database database;
}