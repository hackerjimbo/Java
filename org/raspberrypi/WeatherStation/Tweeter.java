/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.raspberrypi.WeatherStation;

import java.util.logging.Level;
import java.util.logging.Logger;

import java.util.concurrent.ThreadLocalRandom; 

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.TwitterFactory;
import twitter4j.auth.AccessToken;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.time.DateTimeException;
import java.time.format.DateTimeFormatter;

/**
 * This class sends tweets (if the credentials are supplied).
 * @author jim
 */
public class Tweeter extends Thread
{
    private static final Logger LOG = Logger.getLogger ("WeatherStation.Tweeter");
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    public Tweeter ()
    {
                // Load MySQL drivers
        try {
            Class.forName ("com.mysql.jdbc.Driver");  
            any_chance = true;
            LOG.log (Level.INFO, "Tweeter thread loaded");
        }
        
        catch (ClassNotFoundException e) {
            LOG.log (Level.SEVERE, "Unable to get MySQL driver: {0}", e.getMessage ());
            any_chance = false;
        }
    }
    
    /**
     * The thread's main run. This just loops tweeting just after midnight.
     */
    @Override
    public void run ()
    {
        if (!any_chance)
            return;
        
        LOG.log (Level.INFO, "Tweeter thread running");
        ThreadLocalRandom rng = ThreadLocalRandom.current ();
        
        while (true) {
            try {
                Database.DatabaseConfig config = WeatherStation.getDatabase ().getConfig ();
        
                // Check we have enough, if we don't it's not a problem, we just
                // come back in a bit (10 minutes).
                if (!config.getMysqlGood() || !config.getTwitterGood()) {
                    Thread.sleep (1000 * 600);
                    continue;
                }
        
                // Let's look at timezones
                
                ZoneId local = null;
                
                if (config.getTwitterTimezone () != null) {
                    try {
                        local = ZoneId.of (config.getTwitterTimezone ());
                    }
                    
                    catch (DateTimeException e) {
                        LOG.log (Level.WARNING, "Failed to get timezone: {0}", e.getMessage ());
                    }
                }
                
                if (local == null)
                    local = ZoneId.systemDefault ();
                
                final ZoneId utc = ZoneId.of ("UTC");
                final ZonedDateTime now = ZonedDateTime.now (local);

                final ZonedDateTime start = now.truncatedTo (ChronoUnit.DAYS).withZoneSameInstant (utc);
                final ZonedDateTime end   = start.plusDays (1);
                final String start_text   = start.format (formatter);
                final String end_text     = end.format (formatter);
                final String date_text    = now.format (DateTimeFormatter.ISO_LOCAL_DATE);
                final long wait           = now.until (end, (ChronoUnit.SECONDS));
                final long offset         = rng.nextInt (MILLIS_LOWEST, MILLIS_HIGHEST);
                
                LOG.log (Level.FINE, "Queued tweet for {0} from {1} to {2} in {3}", new Object[]{date_text, start, end, wait * 1000 + offset});
                
                // Wait a little time until after midnight. Random to avoid
                // flooding twitter.
                Thread.sleep (wait * 1000 + offset);
                tweet (config, date_text, start_text, end_text);
            }
            
            catch (InterruptedException e) {
                LOG.log (Level.SEVERE, "Thread interrupted: {0}", e.getMessage ());
            }
        }
    }
    
    private void tweet (Database.DatabaseConfig config, String date, String start, String end)
    {
        try (Connection con = DriverManager.getConnection ("jdbc:mysql://" + config.getMysqlHost() + ":3306/" + config.getMysqlDb (), 
                    config.getMysqlUser (), config.getMysqlPass());
                PreparedStatement command = con.prepareStatement("SELECT " +
                    "MAX(AMBIENT_TEMPERATURE) AS \"Max\", MIN(AMBIENT_TEMPERATURE) AS \"Min\", " +
                    "SUM(RAINFALL) AS \"Rain\", MAX(HUMIDITY) AS \"Max RH\", MIN(HUMIDITY) AS \"Min RH\", " +
                    "AVG(WIND_SPEED) AS \"Wind\", MAX(WIND_GUST_SPEED) AS \"Gust\", " +
                    "MAX(AIR_PRESSURE) AS \"P Max\", MIN(AIR_PRESSURE) AS \"P Min\", COUNT(*) AS \"Samples\" " +
                    "FROM WEATHER_MEASUREMENT WHERE CREATED >= ? AND CREATED < ?);",
                    ResultSet.TYPE_FORWARD_ONLY,
                    ResultSet.CONCUR_READ_ONLY))
                 {
            command.setString (1, start);
            command.setString (2, end);
 
            String text = null;

            try (ResultSet r = command.executeQuery ()) {
                while (r.next ()) {
                    if (text != null)
                        LOG.log (Level.WARNING, "Mutliple results for twitter feed?");

                    text = "Report for " + date;
                    text += ": temp " + r.getDouble ("Min") + '/' + r.getDouble ("Max");
                    text += " humidity " + r.getDouble ("Min RH") + '/' + r.getDouble ("Max RH");
                    text += " pressure " + r.getDouble ("P Min") + '/' + r.getDouble ("P Max");
                    text += " gust " + r.getDouble ("Gust");
                    text += " rain " + r.getDouble ("Rain");
                    text += " #piweather";
                }
            }
            
            if (text == null) {
                LOG.log (Level.WARNING, "No results for twitter feed?");
                return;
            }
            
            try {
                Twitter twitter = new TwitterFactory ().getInstance ();

                twitter.setOAuthConsumer (config.getTwitterConsumerKey (), config.getTwitterConsumerSecret ());
                twitter.setOAuthAccessToken (new AccessToken (config.getTwitterAccessToken (), config.getTwitterAccessTokenSecret ()));

                twitter.updateStatus (text);

                LOG.log (Level.INFO, "tweeted {0}", text);
            }
             
            catch (TwitterException e) {
                LOG.log(Level.WARNING, "Failed to tweet: {0}", e.getErrorMessage ());
            }
        }
        
        catch (SQLException e) {
                LOG.log (Level.SEVERE, "Database error read failed: {0}", e.getMessage());
        }
    }
    
    private final long MILLIS_IN_1_DAY = 1000 * 60 * 60 * 24;
    private final int MILLIS_LOWEST = 60000;
    private final int MILLIS_HIGHEST = 600000;
    
    private boolean any_chance;
}