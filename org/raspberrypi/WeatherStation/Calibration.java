/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.raspberrypi.WeatherStation;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.json.JSONTokener;
import org.json.JSONObject;
import org.json.JSONException;

/**
 * The calibration class is used to hold information to convert pulses to the
 * actual values of wind speed and rain fall. It parses the JSON file containing
 * the information and makes it available.
 * 
 * @author Jim Darby
 */
public class Calibration {
    private static final Logger LOG = Logger.getLogger("WeatherStation.Calibration");
    
    /**
     * Constructor for Calibration class. It needs the name of the configuration
     * file to parse.
     * @param config_file The file to read to obtain the configuration
     * information from.
     */
    public Calibration (String config_file)
    {
        // Set up some default values to signify configuration failure.
        pulsesToWindSpeed_ = -1000;
        pulsesToMM_ = -1000;
        
        try (FileInputStream config_stream = new FileInputStream (config_file)) {
            try {
                JSONTokener tokener = new JSONTokener (config_stream);
                JSONObject config = new JSONObject (tokener);

                try {
                    pulsesToWindSpeed_ = config.getDouble ("pulsesToWindSpeed");
                    LOG.log (Level.CONFIG, "pulses to wind speed: {0}", pulsesToWindSpeed_);
                }

                catch (JSONException e) {
                    LOG.log (Level.SEVERE, "Invalid or missing pulsesToWindSpeed: {0}", e.getMessage ());
                }

                try {
                    pulsesToMM_ = config.getDouble("pulsesToMM");
                    LOG.log (Level.CONFIG, "pulses to mm: {0}", pulsesToMM_);
                }

                catch (JSONException e) {
                    LOG.log (Level.SEVERE, "Invalid or missing pulsesToMM: {0}", e.getMessage ());
                }
            }
            
            catch (JSONException e) {
                LOG.log(Level.SEVERE, "JSON parsing error in calibration file: {0}", e.getMessage ());
            }
        }
       
        catch (FileNotFoundException e) {
            LOG.log (Level.SEVERE, "Unable to open calibration file: {0}, {1}", new Object[]{config_file, e.getMessage()});
        }
        
        catch (IOException e) {
            LOG.log (Level.SEVERE, "Error reading calibration file: {0}, {1}", new Object[]{config_file, e.getMessage()});
        }
    }
    
    /**
     * Method to obtain the mapping from pulses to wind speed. The value is
     * how many km/h result is one pulse per second from the sensor.
     * @return The multiplication factor.
     */
    public double getPulsesToWindSpeed () {
        return pulsesToWindSpeed_;
    }
    
    /**
     * Method to return the mapping from pulses to mm of rain. The value is mm
     * of rain per single pulse received from the sensor.
     * @return  The multiplication factor 
     */
    public double getPulsesToMM () {
        return pulsesToMM_;
    }
    
    /** Private variable holding the wind speed conversion factor */
    private double pulsesToWindSpeed_;
    /** Private variable holding the mm per pulse conversion factor */
    private double pulsesToMM_;
}