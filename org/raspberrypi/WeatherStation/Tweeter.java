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
import twitter4j.RateLimitStatus;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.time.DateTimeException;
import java.time.format.DateTimeFormatter;

/**
 * This class sends tweets (if the credentials are supplied). It runs as a
 * distinct thread to avoid blocking the main processing.
 * 
 * @author Jim Darby
 */
public class Tweeter extends Thread
{
    /** Logger for the tweeting thread */
    private static final Logger LOG = Logger.getLogger ("WeatherStation.Tweeter");
    /** Dates and times the way MySQL likes them. */
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    /**
     * Construct the class.
     */
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
     * The thread's main run. This just loops tweeting just after midnight in a
     * timezone that can be specified in a configuration file..
     */
    @Override
    public void run ()
    {
        // Any chance?
        if (!any_chance)
            return;
        
        LOG.log (Level.INFO, "Tweeter thread running");
        
        // We use a non-secure random number generator to tweet a random time
        // after midnight to avoid flooding twitter.
        ThreadLocalRandom rng = ThreadLocalRandom.current ();
                
        while (true) {
            try {
                // Get the latest configuration information
                Database.DatabaseConfig config = WeatherStation.getDatabase ().getConfig ();
        
                // Check we have enough, if we don't it's not a problem, we just
                // come back in a bit (10 minutes).
                if (!config.getMysqlGood() || !config.getTwitterGood()) {
                    Thread.sleep (1000 * 600);
                    continue;
                }
        
                // Let's look at timezones
                
                ZoneId local = null;
                
                // Did they configure one?
                if (config.getTwitterTimezone () != null) {
                    // Yes? Try to get the information on it,
                    try {
                        local = ZoneId.of (config.getTwitterTimezone ());
                    }
                    
                    catch (DateTimeException e) {
                        LOG.log (Level.WARNING, "Failed to get timezone: {0}", e.getMessage ());
                    }
                }
                
                // Just default to the system one if we can't do any better.
                if (local == null)
                    local = ZoneId.systemDefault ();
                
                // The rest of the system works in UTC
                final ZoneId utc = ZoneId.of ("UTC");
                
                // Get our local time. We base the day of the daily summary on
                // this
                final ZonedDateTime now = ZonedDateTime.now (local);

                // Get start and end of the local day, then convert to UTC then to text.
                // We need to tweak that as we log at the END of the ten minute period.
                // So add nine miunutes to the time so that we avoid the last
                // part of the day before yesterday's results and include the
                // last part of yesterdays.
                final ZonedDateTime start = now.truncatedTo (ChronoUnit.DAYS).withZoneSameInstant (utc).plusMinutes (9);
                final ZonedDateTime end   = start.plusDays (1);
                final String start_text   = start.format (FORMATTER);
                final String end_text     = end.format (FORMATTER);
                final String date_text    = now.format (DateTimeFormatter.ISO_LOCAL_DATE);
                
                // Calculate how long we need to wait and add a random offset
                // to avoid flooding twitter
                final long wait           = now.until (end, (ChronoUnit.SECONDS));
                final long offset         = rng.nextInt (MILLIS_LOWEST, MILLIS_HIGHEST);
                
                LOG.log (Level.FINE, "Queued tweet for {0} from {1} to {2} in {3}", new Object[]{date_text, start_text, end_text, wait * 1000 + offset});
        
                Thread.sleep (wait * 1000 + offset);
                
                // Send the tweet
                tweet (config, date_text, start_text, end_text);
            }
            
            catch (InterruptedException e) {
                LOG.log (Level.SEVERE, "Thread interrupted: {0}", e.getMessage ());
            }
        }
    }
    
    /**
     * Send a tweet.
     * @param config The confiuration file
     * @param date The date the summary refers to
     * @param start Start time of that day in UTC
     * @param end End time of that day in UTC
     */
    private void tweet (Database.DatabaseConfig config, String date, String start, String end)
    {
        // Connect to the database and create the prepared statement
        try (Connection con = DriverManager.getConnection ("jdbc:mysql://" + config.getMysqlHost() + ":3306/" + config.getMysqlDb (), 
                    config.getMysqlUser (), config.getMysqlPass());
                PreparedStatement command = con.prepareStatement("SELECT " +
                    "MAX(AMBIENT_TEMPERATURE) AS \"Max\", MIN(AMBIENT_TEMPERATURE) AS \"Min\", " +
                    "SUM(RAINFALL) AS \"Rain\", MAX(HUMIDITY) AS \"Max RH\", MIN(HUMIDITY) AS \"Min RH\", " +
                    "AVG(WIND_SPEED) AS \"Wind\", MAX(WIND_GUST_SPEED) AS \"Gust\", " +
                    "MAX(AIR_PRESSURE) AS \"P Max\", MIN(AIR_PRESSURE) AS \"P Min\", COUNT(*) AS \"Samples\" " +
                    "FROM WEATHER_MEASUREMENT WHERE CREATED >= ? AND CREATED < ?;",
                    ResultSet.TYPE_FORWARD_ONLY,
                    ResultSet.CONCUR_READ_ONLY)) {
            
            // Fill in the paramters
            command.setString (1, start);
            command.setString (2, end);
 
            // Keep track of the result
            String text = null;

            // Run the query
            try (ResultSet r = command.executeQuery ()) {
                // Loop over the results
                while (r.next ()) {
                    // Multiple results?
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
            
            // No results?
            if (text == null) {
                LOG.log (Level.WARNING, "No results for twitter feed?");
                return;
            }
            
            // OK, we have something. Fire it at twitter.
            try {
                Twitter twitter = new TwitterFactory ().getInstance ();

                twitter.setOAuthConsumer (config.getTwitterConsumerKey (), config.getTwitterConsumerSecret ());
                twitter.setOAuthAccessToken (new AccessToken (config.getTwitterAccessToken (), config.getTwitterAccessTokenSecret ()));

                twitter.updateStatus (text);

                LOG.log (Level.INFO, "tweeted {0}", text);
            }
             
            catch (TwitterException e) {
                String message = "Failed to tweet";
                
                if (e.exceededRateLimitation ()) {
                    message += " rate limited";

                    RateLimitStatus s = e.getRateLimitStatus ();
                    
                    if (s != null) {
                        message += " " + s.getLimit ();
                        message += " " + s.getRemaining ();
                        message += " " + s.getResetTimeInSeconds ();
                        message += " " + s.getSecondsUntilReset ();
                    }
                }
                
                if (e.isCausedByNetworkIssue ())
                    message += " network issue";
                
                if (e.resourceNotFound ())
                    message += " no resource";
                
                message += ": ";
                
                if (e.isErrorMessageAvailable ()) {
                    String m = e.getErrorMessage ();
                    
                    if (m != null)
                        message += "message \"" + e.getErrorMessage() + "\" ";
                }
                    
                
                message += e.getMessage ();
                            
                LOG.log (Level.WARNING, "Failed to tweet: {0}", message);
            }
        }
        
        catch (SQLException e) {
                LOG.log (Level.SEVERE, "Database error read failed: {0}", e.getMessage());
        }
    }
    
    /** Lowest number of milliseconds after midnight we tweet */ 
    private final int MILLIS_LOWEST = 60000;
    /** Highest number of milliseconds after midnight we tweet */
    private final int MILLIS_HIGHEST = 600000;
    /** Any chance of this working? */
    private boolean any_chance;
}