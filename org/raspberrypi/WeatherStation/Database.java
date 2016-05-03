/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.raspberrypi.WeatherStation;

import java.io.InputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.LinkOption;
import java.nio.file.Files;
import java.nio.file.attribute.FileTime;

import java.text.MessageFormat;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.ZoneOffset;

import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.LinkedList;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.PreparedStatement;

import org.json.JSONTokener;
import org.json.JSONObject;
import org.json.JSONException;

/**
 *
 * @author jim
 */
public class Database
{
    private static final Logger LOG = Logger.getLogger ("WeatherStation.Database");
    
    public Database (String config_file)
    {
        config_file_name = config_file;
        config = new DatabaseConfig (config_file);
        
        // Try to load the MySQL driver module
        try {
            Class.forName ("com.mysql.jdbc.Driver");        
        }
        
        catch (ClassNotFoundException e) {
            LOG.log (Level.SEVERE, "Unable to get MySQL driver: {0}", e.getMessage ());
        }
        
        // Get us started
        MysqlConnect ();
    }
    
    /**
     * Check to see if a new and improved (hopefully) configuration file is
     * present.
     * @return true if the new one was loaded and is later and no worse.
     */
    public boolean updateConfig ()
    {
        // Check for a newer (and better!) configuration file
        DatabaseConfig maybe_newer = config.newer (config_file_name);
        
        // If so, swap it in
        if (maybe_newer != null) {
            config = maybe_newer;
            LOG.log (Level.CONFIG, "Configuration updated");
            
            return true;
        }
        
        return false;
    }
    
    /** Connect (or reconnect) to the local MySQL database. */
    private boolean MysqlConnect ()
    {
        boolean result = false;
        
        // Do we have any chance?
        if (config.getMysqlGood ()) {
            try {
                // Could we make this more database agnostic? Almost certainly!
                Connection new_connection = DriverManager.getConnection ("jdbc:mysql://" + config.getMysqlHost () + ":3306/" + config.getMysqlDb (),
                    config.getMysqlUser (),
                    config.getMysqlPass());
                
                // Close any old connections (just in case)
                close ();
                
                // Now use our new connection
                connection = new_connection;
                result = true;
                mysql_failing = false;
       
                LOG.log (Level.INFO, "MySQL connection established");
            }
            
            catch (SQLException e) {
                // If we're already failing don't add more messages!
                if (!mysql_failing) {
                    // Set this *before* logging so if we get called back we
                    // won't recurse infinitely.
                    mysql_failing = true;
                    LOG.log (Level.WARNING, "Unable to connect to MySQL: {0}", e.getMessage ());
                }
            }
        }
        
        return result;
    }
           
    /**
     * Insert a log record into the MySQL database for later retrieval. If it
     * fails then it'll be appended to the end of a limited internal queue
     * waiting for it to start working again.
     * @param r The record to log,
     * @return Whether it was logged successfully.
     */
    public boolean log (LogRecord r)
    {
        // Jump the queue if it is empty (most common case)
        if (log_queue.isEmpty () && log_single (r))
            return true;
        
        // Add it to the end of the queue, make a note if it worked.
        final boolean queued = queue_log (r);

        // Clear the backlog
        LogRecord x;
        
        while ((x = log_queue.poll()) != null) {
            if (!log_single (x)) {
                log_queue.addFirst (x);
                return false;
            }
        }

        // Last chance, if the queue was full but not it's empty....
        // Rare but possible, especially with a small queue limit.
        if (!queued) {
            // Try again
            if (log_single (r))
                return true;
            
            // Very bad luck, try queuing it
            return queue_log (r);
        }
        
        // All cleared!
        return true; 
    }

    /**
     * Internal version logs a single record or fails. 
     * @param r The record to log
     * @return if it was successful.
     */
    private boolean log_single (LogRecord r)
    {
        // Extract raw message from the record
        String text = r.getMessage ();
        
        // The message can be null, if so ignore it.
        if (text == null)
            return true;

        // Construct a name from the class and method
        final String source = r.getSourceClassName () + ' ' + r.getSourceMethodName ();  
        
        // The sun logger talks too much for my liking....
        if (source.startsWith ("sun.net.www.") && r.getLevel ().intValue () < Level.INFO.intValue ())
            return true;

        final Object[] parameters = r.getParameters();
        
        // Do we need to format the message? Thanks to Brenton for finding this
        // algorithm. For full details see:
        // https://docs.oracle.com/javase/8/docs/api/java/util/logging/Formatter.html#formatMessage-java.util.logging.LogRecord-
        if (parameters != null && parameters.length != 0 && text.contains ("{0"))
            text = new MessageFormat (text).format (parameters);
        
        // Extra key data from the record
        final int seconds = (int) ((r.getMillis () + 500) / 1000);
        final ZonedDateTime zoned = ZonedDateTime.of (LocalDateTime.ofEpochSecond (seconds, 0, ZoneOffset.UTC), ZoneOffset.UTC) ;
        final String when = zoned.format (DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        final String level = r.getLevel ().getLocalizedName ();

        // Handle boundary case of an empty string. This would otherwise return
        // false because nothing is ever written!
        if (text.isEmpty ())
            return true;
    
        // How are we doing?
        boolean result = false;
        
        // Some error messages have newlines in them (I'm looking at YOU MySQL!)
        // Break them into multiple messages and ignore empty lines       
        String left = text;
        
        do {
            // Find the newline
            final int nl = left.indexOf ('\n');
            
            // Grab what's before the newline or all of it if none
            String msg = (nl < 0) ? left : left.substring (0, nl);
            
            // If it's not an empty message (MySQL!) then log it
            if (!msg.isEmpty ())
                result |= log_single (when, level, source, msg);
            
            // If no more newlines then done (fast optimisation)
            if (nl < 0)
                break;
            
            // Chop of everthing up to and including the newline
            left = left.substring (nl + 1);
        } while (!left.isEmpty ());
        
        // If any of it worked then it all worked. Avoids repeated choking
        return result;
    }
    
    /**
     * Internal function to log a single record.
     * @param when The timestamp.
     * @param level The log entry level.
     * @param text The text of the log.
     * @return true if the logging went well.
     */
    private boolean log_single (String when, String level, String source, String text)
    {
        // Try connection if one doesn't exist
        if (connection == null && !MysqlConnect ())
            return false;
        
        // We allow two attempts, reconnecting the database inbetween
        
        for (int go = 0; go < 2; ++go) {
            try (PreparedStatement command = connection.prepareStatement("INSERT INTO LOG (CREATED, LEVEL, SOURCE, TEXT) VALUES (?, ?, ?, ?)")) {

                command.setString (1, when);
                command.setString (2, level);
                command.setString (3, source);
                command.setString (4, text);

                final int result = command.executeUpdate ();

                // Exactly one record should be affected (the new one)
                if (result != 1)
                    System.out.println ("Update count " + result);
                
                // And we're good
                return true;
            }            

            // Well that didn't go according to plan!
            catch (SQLException e) {
                try {
                    // Try and reconnect if we lost the connection. We only do
                    // this on the first attempt and the connection seems broken.
                    if (go == 0 && !connection.isValid (1) && MysqlConnect ()) {
                        LOG.log (Level.INFO, "Recovered database connection");
                        continue;
                    }                  
                }
             
                // We can't log here because we'll just end up recursing.
                catch (SQLException e2) {
                    System.out.println ("Can't tell if connection is valid: " + e2.getMessage());
                }
                    
                // If we already tried to fix it up and failed.
                if (go > 0)
                    System.out.println ("Database log failed: " + e.getMessage());
            }
        }
        
        // If we get here then we weren't able to recover.
        return false;
    }
    
    /**
     * Put a log record into the queue.
     * @param r The log record.
     * @return Whether it was queued OK.
     */
    private boolean queue_log (LogRecord r)
    {
        if (log_queue.size () >= MAX_LOG_QUEUE || !log_queue.offer (r)) {
            System.out.println ("Log queue full!");
            return false;
        }
        
        return true;
    }
    
    /**
     * Log a weather reading into the MySQL database.
     * 
     * @param when The time the record is dated.
     * @param rain_total The rain (in mm) during this period.
     * @param wind_speed The average wind speed during this period.
     * @param wind_peak The peak wind speed during this period.
     * @param air_quality The average air quality during the period.
     * @param bmp180_temp The temperate from the BMP180 (not very accurate).
     * @param pressure The air pressure (in mBar).
     * @param htu21d_temp The temperature from the HTU21D (more accurate).
     * @param humidity The humidity (in percent RH).
     * @param wind_direction The average wind direction.
     * @param ground_temp The Ground temperature.
     * @return true if it was logged ok.
     */
    public boolean log (String when, double rain_total,
            double wind_speed, double wind_peak,
            double air_quality,
            double bmp180_temp, double pressure,
            double htu21d_temp, double humidity,
            double wind_direction,
            double ground_temp)
    {    
        Reading r = new Reading (when, htu21d_temp, ground_temp, air_quality,
            pressure, humidity, wind_direction, wind_speed, wind_peak,
            rain_total);
        
        // Jump the queue if it is empty (most common case)
        if (reading_queue.isEmpty () && log_single (r))
            return true;
        
        // Add it to the end of the queue, make a note if it worked.
        final boolean queued = queue_log (r);

        // Try to clear the backlog
        
        Reading x;
        
        // While there is something in the queue, pull off the first
        while ((x = reading_queue.poll ()) != null) {
            // Try to log it
            if (!log_single (x)) {
                // Failed! Put in back on the *front* of the queue and bomb out
                reading_queue.addFirst (x);
                return false;
            }
        }
        
        // Last chance, if the queue was full but now it's empty....
        // Rare but possible, especially with a small queue limit.
        if (!queued) {
            // Try again
            if (log_single (r))
                return true;
            
            // Very bad luck, try queuing it
            return queue_log (r);
        }
        
        // All cleared!
        return true;
    }
    
    /**
     * Try to log a single reading. 
     * @param r The reading.
     * @return If it worked.
     */
    private boolean log_single (Reading r)
    {    
        // If we don't have a connection, wake one up.
        if (connection == null && !MysqlConnect ())
            return false;       

        // We have two goes, this allows for a database reconnection if needed
        for (int go = 0; go < 2; ++go) {
            try (PreparedStatement command = connection.prepareStatement
                ("INSERT INTO WEATHER_MEASUREMENT " + 
                 "(CREATED, AMBIENT_TEMPERATURE, GROUND_TEMPERATURE, AIR_QUALITY, AIR_PRESSURE, HUMIDITY, WIND_DIRECTION, WIND_SPEED, WIND_GUST_SPEED, RAINFALL) VALUES" +
                 "(?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {            
                command.setString ( 1, r.when);
                command.setDouble ( 2, r.htu21d_temp);
                command.setDouble ( 3, r.ground_temp);
                command.setDouble ( 4, r.air_quality);
                command.setDouble ( 5, r.pressure);
                command.setDouble ( 6, r.humidity);
                command.setDouble ( 7, r.wind_direction);
                command.setDouble ( 8, r.wind_speed);
                command.setDouble ( 9, r.wind_peak);
                command.setDouble (10, r.rain_total);

                final int result = command.executeUpdate ();

                // It should only affect one row, if not something odd is happening
                if (result != 1)
                    LOG.log (Level.WARNING, "Update count {0}", result);
                
                // And we're done....
                return true;
            }            

            // Well that didn't work out too well....
            catch (SQLException e) {
                try {
                    // Try and reconnect if we lost the connection. We only do
                    // this on the first attempt and the connection seems broken.
                    if (go == 0 && !connection.isValid (1) && MysqlConnect ()) {
                        LOG.log (Level.INFO, "Recovered database connection");
                        continue;
                    }                  
                }
                
                // This should never happen as we used a 1 second timeout to the
                // database. Itr gets thrown on negative values.
                catch (SQLException e2) {
                    LOG.log (Level.SEVERE, "Can't tell if connection is valid: {0}", e2.getMessage());
                }
                    
                // If we already tried to fix it up and failed.
                if (go > 0)
                    LOG.log (Level.SEVERE, "Database data INSERT failed: {0}", e.getMessage());
            }
        }
        
        return false;
    }
    
    private boolean queue_log (Reading r)
    {
        if (reading_queue.size() >= MAX_READING_QUEUE || !reading_queue.offer (r)) {
            LOG.log (Level.SEVERE, "Reading queue full!");
            return false;
        }
        
        return true;
    }
    
    public DatabaseConfig getConfig ()
    {
        return config;
    }
    
    /**
     * Shut down the connection to the MySQL database.
     */
    public void close ()
    {
        if (connection != null) {
            Connection c = connection;
            
            connection = null;
            
            try {
                c.close ();
            }
            
            catch (SQLException e) {
                LOG.log (Level.SEVERE, "Failed to close connection: {0}", e.getMessage ());
            }
        }
    }
    
    @Override
    protected void finalize () throws Throwable
    {
        close ();
        super.finalize ();
    }
    
    private DatabaseConfig config = null;
    private final String config_file_name;
    private Connection connection = null;
    private boolean mysql_failing = false;
    private LinkedList <LogRecord> log_queue = new LinkedList <> ();
    private final int MAX_LOG_QUEUE = 1000;
    private LinkedList <Reading> reading_queue = new LinkedList <> ();
    private final int MAX_READING_QUEUE = 1000;

    /**
     * A very lazy class to hold readings for when the database has gone away.
     */
    private class Reading
    {
        public Reading (String when_in,
            double htu21d_temp_in,
            double ground_temp_in,
            double air_quality_in,
            double pressure_in,
            double humidity_in,
            double wind_direction_in,
            double wind_speed_in,
            double wind_peak_in,
            double rain_total_in)
        {
            when           = when_in;
            htu21d_temp    = htu21d_temp_in;
            ground_temp    = ground_temp_in;
            air_quality    = air_quality_in;
            pressure       = pressure_in;
            humidity       = humidity_in;
            wind_direction = wind_direction_in;
            wind_speed     = wind_speed_in;
            wind_peak      = wind_peak_in;
            rain_total     = rain_total_in; 
        }
        
        public final String when;
        public final double htu21d_temp;
        public final double ground_temp;
        public final double air_quality;
        public final double pressure;
        public final double humidity;
        public final double wind_direction;
        public final double wind_speed;
        public final double wind_peak;
        public final double rain_total;
    }
    
    public class DatabaseConfig
    {
        public DatabaseConfig (String config_file)
        {
            final Path p = Paths.get (config_file);
            
            try (InputStream config_stream = Files.newInputStream (p)) {
                final LinkOption options[] = {};
                last_modified = Files.getLastModifiedTime (p, options);
                
                try {
                    final JSONTokener tokener = new JSONTokener (config_stream);
                    final JSONObject config = new JSONObject (tokener);

                    try {
                        final JSONObject mysql = config.getJSONObject ("mysql");
                        final String host = mysql.getString ("host");
                        final String user = mysql.getString ("user");
                        final String pass = mysql.getString ("pass");
                        final String db   = mysql.getString ("database");

                        // If we got them all then we're good

                        mysql_host = host;
                        mysql_user = user;
                        mysql_pass = pass;
                        mysql_db   = db;
                        mysql_good = true;

                        LOG.config ("MySQL configured OK");
                    }

                    catch (JSONException e) {
                        LOG.log (Level.WARNING, "Invalid or missing MySQL credentials: {0}", e.getMessage ());
                    }
                    
                    try {
                        final JSONObject cloud = config.getJSONObject ("cloud");
                        final String url  = cloud.getString ("url");
                        final String user = cloud.getString ("user");
                        final String pass = cloud.getString ("pass");

                        // If we got them all then we're good

                        cloud_url  = url;
                        cloud_user = user;
                        cloud_pass = pass;
                        cloud_good = true;

                        LOG.config ("Cloud configured OK");
                    }

                    catch (JSONException e) {
                        LOG.log (Level.WARNING, "Invalid or missing cloud credentials: {0}", e.getMessage ());
                    }
                    
                    try {
                        final JSONObject twitter       = config.getJSONObject ("twitter");
                        final String consumerkey       = twitter.getString ("consumerkey");
                        final String consumersecret    = twitter.getString ("consumersecret");
                        final String accesstoken       = twitter.getString ("accesstoken");
                        final String accesstokensecret = twitter.getString ("accesstokensecret");

                        // If we got them all then we're good

                        twitter_consumer_key        = consumerkey;
                        twitter_consumer_secret     = consumersecret;
                        twitter_access_token        = accesstoken;
                        twitter_access_token_secret = accesstokensecret;
                        
                        if (twitter.has ("timezone"))
                            twitter_timezone = twitter.getString ("timezone");
                        
                        twitter_good = true;

                        LOG.config ("Twitter configured OK");
                    }

                    catch (JSONException e) {
                        LOG.log (Level.WARNING, "Invalid or missing twitter credentials: {0}", e.getMessage ());
                    }
                }

                catch (JSONException e) {
                    LOG.log(Level.WARNING, "JSON parsing error: {0}", e.getMessage ());
                }
            }

            catch (FileNotFoundException e) {
                LOG.log (Level.WARNING, "Unable to open credential file: {0}, {1}", new Object[]{config_file, e.getMessage()});
            }

            catch (IOException e) {
                LOG.log (Level.WARNING, "IO exception when reading credential file {0}", config_file);
            }
        }
        
        public DatabaseConfig newer (String config_file)
        {
            FileTime new_time;
            
            // Find out the last modified time of the new file
            try {
                final Path p = Paths.get (config_file);
                final LinkOption options[] = {};
                new_time = Files.getLastModifiedTime (p, options);
            }
            
            // That didn't end well, no updates for you!
            catch (IOException e) {
                return null;
            }
            
            // Is the file newer? If so, load it in.
            if (new_time.compareTo (last_modified) > 0) {
                DatabaseConfig new_config = new DatabaseConfig (config_file);
                
                if (new_config.isNoWorseThan (this))
                    return new_config;
                
                LOG.log (Level.WARNING, "Configuration file updated but is worse than existing!");
            }
            
            return null;
        }
        
        public String getMysqlHost ()
        {
            return mysql_host;
        }
        
        public String getMysqlUser ()
        {
            return mysql_user;
        }
        
        public String getMysqlPass ()
        {
            return mysql_pass;
        }
        
        public String getMysqlDb ()
        {
            return mysql_db;
        }
        
        public boolean getMysqlGood ()
        {
            return mysql_good;
        }
     
        public String getCloudUrl ()
        {
            return cloud_url;
        }
        
        public String getCloudUser ()
        {
            return cloud_user;
        }
        
        public String getCloudPass ()
        {
            return cloud_pass;
        }
               
        public boolean getCloudGood ()
        {
            return cloud_good;
        }

        public String getTwitterConsumerKey ()
        {
            return twitter_consumer_key;
        }
        
        public String getTwitterConsumerSecret ()
        {
            return twitter_consumer_secret;
        }
        
        public String getTwitterAccessToken ()
        {
            return twitter_access_token;
        }
        
        public String getTwitterAccessTokenSecret ()
        {
            return twitter_access_token_secret;
        }
        
        public String getTwitterTimezone ()
        {
            return twitter_timezone;
        }
         
        public boolean getTwitterGood ()
        {
            return twitter_good;
        }
        
         /**
         * Is this configuration no worse than another. We must not lose
         * information so if the other one has configuration items that we
         * don't return false. Otherwise return true.
         * @param other The other configuration
         * @return If we're at least as good.
         */
        public boolean isNoWorseThan (DatabaseConfig other)
        {
            if (other == null)
                return true;
            
            if (!cloud_good && other.cloud_good)
                return false;
            
            if (!mysql_good && other.mysql_good)
                return false;
            
            if (!twitter_good && other.twitter_good)
                return false;
            
            return true;
        }
        
        /**
         * Is this information newer than the other one?
         * @param other The one to compare with.
         * @return If our data source has a later timestamp.
         */
        public boolean isNewerThan (DatabaseConfig other)
        {
            return isNewerThan (other.last_modified);
        }
        
        /**
         * Is this information newer than a given timestamp?
         * @param other The one to compare with.
         * @return If our data source has a later timestamp.
         */
        public boolean isNewerThan (FileTime other)
        {
            return last_modified.compareTo (other) > 0;
        }
        
        private FileTime last_modified = FileTime.fromMillis (0L);
        
        private String  mysql_host = null;
        private String  mysql_user = null;
        private String  mysql_pass = null;
        private String  mysql_db   = null;
        private boolean mysql_good = false;
        
        private String  cloud_url  = null;
        private String  cloud_user = null;
        private String  cloud_pass = null;
        private boolean cloud_good = false;
        
        private String  twitter_consumer_key = null;
        private String  twitter_consumer_secret = null;
        private String  twitter_access_token = null;
        private String  twitter_access_token_secret = null;
        private String  twitter_timezone = null;
        private boolean twitter_good = false;
    }
}