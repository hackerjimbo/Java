/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.raspberrypi.WeatherStation;

import java.util.logging.Handler;
import java.util.logging.LogRecord;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.ZoneOffset;

import java.text.MessageFormat;

/**
 * This class handles logging. It extends the logging Handler class to try to
 * put items into the database and perform other useful actions.
 * @author jim
 */
public class Reporter extends Handler {
    /**
     * This is the contstructor.
     */
    public Reporter ()
    {
    }
    
    /**
     * Publish a log record. This tries to put a log record into the database.
     * If it fails then log it to stdout instead.
     * @param r The record to publish.
     */
    @Override
    public void publish (LogRecord r) 
    {
        // Try to push it to the database
        if (database_ == null || !database_.log (r)) {
            // Well that didn't got well, write it to stdout (we have little choice)
            
            // Extract raw message from the record
            String text = r.getMessage ();
        
            // The message can be null, if so ignore it.
            if (text == null)
                return;
            
            final Object[] parameters = r.getParameters();
                    
            // Do we need to format the message? Thanks to Brenton for finding this
            // algorithm. For full details see:
            // https://docs.oracle.com/javase/8/docs/api/java/util/logging/Formatter.html#formatMessage-java.util.logging.LogRecord-
            if (parameters != null && parameters.length != 0 && text.contains ("{0"))
                text = new MessageFormat (text).format (parameters);
            
            final int seconds = (int) ((r.getMillis () + 500) / 1000);
            final ZonedDateTime zoned = ZonedDateTime.of (LocalDateTime.ofEpochSecond (seconds, 0, ZoneOffset.UTC), ZoneOffset.UTC) ;
            final String when = zoned.format (DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            
            System.out.println ("Logging record:");
            System.out.println ("       Time: " + when);
            System.out.println ("      Level: " + r.getLevel());
            System.out.println ("       Name: " + r.getLoggerName ());
            System.out.println ("  Formatted: " + text);
            System.out.println ();
        }
    }
    
    @Override
    public void close ()
    {
        System.out.println ("Reporter close");
    }
    
    @Override
    public void flush ()
    {
        System.out.println ("Reporter flush");
    }
    
    public void addDB (Database db)
    {
        database_ = db;
    }
    
    private Database database_ = null;
}