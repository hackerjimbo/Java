/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.raspberrypi.WeatherStation;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.FileNotFoundException;

import java.util.Arrays;
import java.util.logging.Logger;

import org.json.JSONTokener;
import org.json.JSONObject;
import org.json.JSONException;
import org.json.JSONArray;

import org.raspberrypi.WeatherStation.DeviceDrivers.MCP3427;
import java.util.logging.Level;

/**
 * This class handles turning the ADC reading into a wind direction. There is an
 * ingenious encoder on the sensor that encodes the direction using a series of
 * resistor values. Here we load a configuration file and, based on the
 * information there, turn the value into a direction.
 * 
 * @author Jim Darby
 */
public class WindDirection {
    private static final Logger LOG = Logger.getLogger("WeatherStation.WindDirection");
    
    /**
     * This class holds configuration data about how to calculate the wind
     * direction. The key items are the name, the angle and the minimum and
     * maximum values from the ADC that indicate that specific direction.
     */
    private class Direction implements Comparable <Direction>
    {
        public Direction (String name, double angle, int adc)
        {
            name_ = name;
            angle_ = angle;
            adc_ = adc;
        }
        
        /**
         * Get the name of the wind direction.
         * @return The name.
         */
        public String getName ()
        {
            return name_;
        }
        
        /**
         * Get the nominal angle of the direction.
         * @return The angle (in degrees).
         */
        public double getAngle ()
        {
            return angle_;
        }
        
        /**
         * Get the ADC value corresponding to this exact direction.
         * @return The value.
         */
        public int getAdc ()
        {
            return adc_;
        }
        
        /**
         * Get the minimum ADC value that we count as being this angle.
         * @return The minimum ADC value.
         */
        public int getAdc_min ()
        {
            return adc_min_;
        }
        
        /**
         * Get the maximum value that we count as being this angle.
         * @return The maximum ADC value.
         */
        public int getAdc_max ()
        {
            return adc_max_;
        }
        
        /**
         * Set the minimum ADC value that we count as being this angle.
         * @param min The minimum ADC value.
         */
        public void setAdc_min (int min)
        {
            adc_min_ = min;
        }
        
        /**
         * Set the maximum ADC value that we count as being this angle.
         * @param max The maximum ADC value.
         */
        public void setAdc_max (int max)
        {
            adc_max_ = max;
        }
        
        /**
         * Allow sorting of {@code Direction}s.
         * @param other The one to compare with.
         * @return Negative if we're less, zero if the same and positive if
         * we're greater.
         */
        @Override
        public int compareTo (Direction other)
        {
            return adc_ - other.adc_;
        }
        
        private final String name_;
        private final double angle_;
        private final int adc_;
        private int adc_min_;
        private int adc_max_;
    }
    
    /**
     * Load the wind configuration file and create the information to decode
     * the direction.
     * @param config_file The location of the configuration file.
     */
    public WindDirection (String config_file)
    {
        // Ensure variables are null if no resources allocated
        directions_ = null;

        try (FileInputStream config_stream = new FileInputStream (config_file)) {
            JSONTokener tokener = new JSONTokener (config_stream);
            JSONObject config = new JSONObject (tokener);
            
            final double vin = config.getDouble ("vin");
            final int vdivider = config.getInt ("vdivider");
            final JSONArray directions = config.getJSONArray("directions");
            
            directions_ = new Direction[directions.length ()];
            
            for (int i = 0; i < directions.length (); ++i) {
                final JSONObject direction = directions.getJSONObject(i);
                final String name = direction.getString ("dir");
                final double angle = direction.getDouble("angle");
                final int ohms = direction.getInt ("ohms");
                final double vout = vin * ohms / (vdivider + ohms);
                final int adc = (int) (vout * MCP3427.MAX / MCP3427.VREF);
                
                directions_[i] = new Direction (name, angle, adc);
            }
            
            // Sort the directions into ascending ADC values
            Arrays.sort (directions_);
            
            // Fill in the lower and upper bounds for each entry EXCLUDING
            // the lower of the lowest and upper of the highest.
            for (int i = 1; i < directions.length (); ++i) {
                Direction lower = directions_[i-1];
                Direction upper = directions_[i];
                
                final int half_way = (lower.getAdc () + upper.getAdc ()) / 2;
                
                lower.setAdc_max (half_way);
                upper.setAdc_min (half_way + 1);
            }
            
            // Fill in the end values
            directions_[0].setAdc_min (0);
            directions_[directions.length () - 1].setAdc_max (MCP3427.MAX);
            
            LOG.log (Level.CONFIG, "Wind direction data loaded OK");
        }
        
        catch (FileNotFoundException e) {
            LOG.log (Level.SEVERE, "Can't open wind direction file: {0}", config_file);
        }
        
        catch (IOException e) {
            LOG.log (Level.SEVERE, "I/O error on wind direction file: {0}, {1}", new Object[]{config_file, e.getMessage ()});
        } 
        
        catch (JSONException e) {
            LOG.log (Level.SEVERE, "JSON parsing error on wind direction file: {0}, {1}", new Object[]{config_file, e.getMessage ()});
        }
    }
    
    /**
     * Given an ADC value return the direction.
     * @param adc The ADC value.
     * @return The angle.
     */
    public final double angleFromADC (int adc)
    {
        // We search the directions using a binary chop. At any point we're
        // looking between min and max. Initialise them to the start and end
        // of the data
        int min = 0;
        int max = directions_.length - 1;
        
        while (min <= max) {
            // Check the one in the middle
            int check = (max + min) / 2;
            Direction dir = directions_[check];
            
            // Is it in range, if so return the angle
            if (adc >= dir.getAdc_min() && adc <= dir.getAdc_max())
                return dir.getAngle ();
            
            // Are we too high in the date (i.e. is the ADC value too low)?
            // If so, then the highest entry must be the one below us.
            // If not the lowest entry must be the one above us.
            if (adc < dir.getAdc_min())
                max = check - 1;
            else
                min = check + 1;
        }
        
        // If we get this far then we're out of range. Return a dummy value
        return -1000;
    }
    
    private Direction directions_[];
}