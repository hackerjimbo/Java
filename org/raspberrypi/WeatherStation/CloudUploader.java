/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.raspberrypi.WeatherStation;

import java.io.IOException;
import java.io.InputStream;

import java.net.ProtocolException;
import java.net.URL;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.HttpsURLConnection;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

/**
 * This class handles upload to the cloud.
 * @author jim
 */
public class CloudUploader extends Thread
{
    private static final Logger LOG = Logger.getLogger ("WeatherStation.CloudUploader");
      
    public CloudUploader ()
    {
        // Load MySQL drivers
        try {
            Class.forName ("com.mysql.jdbc.Driver");  
            any_chance = true;
            LOG.log (Level.INFO, "CloudUploader thread loaded");
        }
        
        catch (ClassNotFoundException e) {
            LOG.log (Level.SEVERE, "Unable to get MySQL driver: {0}", e.getMessage ());
            any_chance = false;
        }
    }
    
    /**
     * The thread's main run. This just loops uploading data to the cloud.
     */
    @Override
    public void run ()
    {
        LOG.log (Level.INFO, "CloudUploader thread running");
        
        while (true) {
            // Run an upload
            runUpload ();
            
            try {
                // Wait for an hour before trying it again
                Thread.sleep (3600 * 1000);
            }
            
            catch (InterruptedException e) {
                LOG.log (Level.SEVERE, "Thread interrupted: {0}", e.getMessage ());
            }
        }
   }
    
    /**
     * Perform a single upload run.
     */
    private void runUpload ()
    {
        // If we failed to load the database driver then we're really broken
        if (!any_chance)
            return;
        
        // Get the latest configuration information
        Database.DatabaseConfig config = WeatherStation.getDatabase ().getConfig ();
        
        // Check it's not totally broken
        if (!config.getMysqlGood() || !config.getCloudGood()) {
            LOG.log (Level.WARNING, "Configuration inadequate for cloud upload");
            return;
        }

        // Let's rock!
        final long start_time = System.currentTimeMillis ();
        int uploaded = 0;
        
        // We hold both the database connection and the prepared statement in
        // try blocks to ensure that it's all shut down at the end.
	try (Connection con = DriverManager.getConnection ("jdbc:mysql://" + config.getMysqlHost() + ":3306/" + config.getMysqlDb (),
                config.getMysqlUser (), config.getMysqlPass())) {
            try (PreparedStatement command = con.prepareStatement("SELECT * FROM WEATHER_MEASUREMENT WHERE REMOTE_ID IS NULL LIMIT 50",
                                                                  ResultSet.TYPE_FORWARD_ONLY,
                                                                  ResultSet.CONCUR_UPDATABLE);
                    ResultSet r = command.executeQuery ()) {

                    // Loop over records
                    while (r.next ()) {
                        final int remote = sendToCloud (r, config.getCloudUrl (), config.getCloudUser (), config.getCloudPass ());
                        
                        // Did it work?
                        if (remote > 0) {
                            // Mark it as such in the database
                            r.updateInt ("REMOTE_ID", remote);
                            r.updateRow ();
                            uploaded += 1;
                        }
                    }
            }            

            catch (SQLException e) {
                LOG.log (Level.SEVERE, "Database error read failed: {0}", e.getMessage());
            }
         }
        
        catch (SQLException e) {  
            LOG.log (Level.SEVERE, "Unable to connect to database: {0}", e.getMessage());
        }
        
        // Clear it all down (or hint that it can)
        sendToCloud (null, config.getCloudUrl (), null, null);
                
        LOG.log (Level.INFO, "Uploaded {0} reading{1} in {2} seconds",
                new Object[] {uploaded, uploaded == 1 ? "" : "s",
                    String.format ("%.2f", (System.currentTimeMillis() - start_time) / 1000.0)});
    }
    
    /**
     * Send a single item into the cloud.
     * @param r The reading.
     * @param url The cloud URL
     * @param user The cloud user name
     * @param pass The cloud user pass
     * @return The remote ID or negative if it failed
     */
    private static int sendToCloud (ResultSet r, String url, String user, String pass)
    { 
        // Let's try to connect. Note that this connection should be reused by
        // the underlying system so in fact we get the same connection we used
        // last time (if there is one and it's still OK).
        HttpsURLConnection con;
        
        try {
            con = (HttpsURLConnection) new URL (url).openConnection();	
        }
        
        // That didn't go according to plan!
        catch (IOException e) {
            LOG.log (Level.SEVERE, "Failed to connection to cloud: {0}", e.getMessage ());
            return -1000;
        }
        
        // If we're passed a null record pointer then it's actually a hint to
        // shut down the connection.
        if (r == null) {
            con.disconnect ();
            return 0;
        }
        
        // Build the message
        try {
            con.setRequestMethod ("POST");
        }
        
        catch (ProtocolException e) {
            LOG.log (Level.SEVERE, "Failed to set POST method: {0}", e.getMessage ());
            return -1000;
        }
        
	con.setDoOutput (false);
	con.setDoInput (true);

	con.setRequestProperty ("Content-type", "tex/plain");
	con.setRequestProperty ("Accept", "text/plain");

        try {
            // Add parameters with different names
            con.setRequestProperty ("LOCAL_ID", String.valueOf (r.getInt ("ID")));
            con.setRequestProperty ("AMB_TEMP", String.valueOf (r.getFloat ("AMBIENT_TEMPERATURE")));
            con.setRequestProperty ("GND_TEMP", String.valueOf (r.getFloat ("GROUND_TEMPERATURE")));
            
            // Add parameters with the same names
            final String[] keys = { "AIR_QUALITY", "AIR_PRESSURE", "HUMIDITY",
                "WIND_DIRECTION", "WIND_SPEED", "WIND_GUST_SPEED", "RAINFALL" };
            
            for (String key : keys)
                con.setRequestProperty (key, String.valueOf (r.getFloat (key)));
            
            // Finally add the timestamp
            final Timestamp created = r.getTimestamp("CREATED");
            
            final ZonedDateTime zoned = ZonedDateTime.of (LocalDateTime.ofEpochSecond (created.getTime () / 1000, 0, ZoneOffset.UTC), ZoneOffset.UTC) ;
            final String when = zoned.format (DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            con.setRequestProperty ("READING_TIMESTAMP", when);	
        }
        
        // Did we fail to obtain the parameter?
        catch (SQLException e) {
            LOG.log (Level.SEVERE, "Failed to obtain POST value: {0}", e.getMessage ());
            return -1000;
        }
        
        // Add database user name and password
        con.setRequestProperty ("WEATHER_STN_NAME", user);
	con.setRequestProperty ("WEATHER_STN_PASS", pass);
        
        // By reading the response code we trigger sending the message
        try {
            final int response_code = con.getResponseCode ();
            
            if (response_code != 201) {
                LOG.log (Level.WARNING, "Unexpected cloud response: {0}", response_code);
                
                return -1000;
            }
        }
        
        // If the request failed
        catch (IOException e) {
            LOG.log (Level.SEVERE, "Failed to get response: {0}", e.getMessage ());
            return -1000;
        }

        // We should have a JSON response. TRy to parse it
        try (InputStream ins = con.getInputStream ()) {
            final JSONTokener tokener = new JSONTokener (ins);
            final JSONObject response = new JSONObject (tokener);

            try {
                final int remote_id = response.getInt ("ORCL_RECORD_ID");
            
                // Skip anything else on the stream. We do this because in order
                // to re-use the connection the lower level needs to *know* that
                // there is no more data to consume. So, even though there is
                // no more data we need to make sure that is none.
                long skipped = 0;
            
                while (ins.read () >= 0)    // Om, nom, nom, nom!
                    skipped += 1;

                // Was there anything? If so that's odd....
                if (skipped > 0)
                    LOG.log (Level.WARNING, "Spurious bytes on end of response: {0}", skipped);

                // Return the useful reponse.
                return remote_id;
            }
            
            // If there is an issue it's not good....
            catch (JSONException e) {
                LOG.log (Level.WARNING, "Failed to parse cloud response", e.getMessage ());
            }
        }

        // Bad news if there was no proper response
        catch (IOException e)
        {
            LOG.log (Level.SEVERE, "Failed to read response: {0}", e.getMessage ());
        }
        
        return -1000;
    }  
    
    private boolean any_chance;
}