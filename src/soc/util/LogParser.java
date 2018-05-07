package soc.util;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import soc.message.SOCMessage;

/**
 * Class to manage parsing of log files.
 * @author kho30
 *
 */
public class LogParser {

    private final BufferedReader br;
    private boolean eof = false;
    
    // Augmented log file - add game state alongside all chat messages
    private final BufferedWriter augLog;
    
    private String gameName = null;    
    private SOCMessage lastMsg = null;
    private Date lastDate = null;
    
    public static final DateFormat df = new SimpleDateFormat("yyyy:MM:dd:HH:mm:ss:SSS:Z");
    
    public static LogParser getParser(String fileName) {
        return getParser(fileName, false);
    }
    
    public static LogParser getParser(String fileName, boolean useAugLog) {
        try {
            File in = new File(fileName);
        
            BufferedReader br;
            BufferedWriter augLog = null;
            try {
                br = new BufferedReader(new FileReader(in));
            }
            catch (FileNotFoundException ex) {
                // Try again with the default extension - I forget all the time
                in = new File(fileName + ".soclog");
                br = new BufferedReader(new FileReader(in));                      
            }
            
            if (useAugLog) {
                augLog = new BufferedWriter(new FileWriter(new File(fileName + ".auglog")));
            }
            return new LogParser(br, augLog);
        }
        catch (IOException ex) {
            ex.printStackTrace();
        }
        return null;        
    }
    
    private LogParser(BufferedReader br, BufferedWriter augLog) {
        this.br = br;       
        this.augLog = augLog;
    }
    
    public Date getMsgDate() {
        return lastDate;
    }
    
    public SOCMessage getMsg() {
        return lastMsg;
    }
    
    public SOCMessage parseLine() {
        lastMsg = null;
        lastDate = null;
        try {               
            String msg = br.readLine();        
            
            if (msg == null) {
                eof = true;
                return null;
            }
            
            if (augLog!=null) {
                augLog.write(msg);
                augLog.newLine();
            }
            
            if (msg.length()>30 && msg.startsWith("201")) {
                lastDate = df.parse( msg.substring(0, 29));                        
                // Chop off the date 
                msg = msg.substring(30);
                // Extract the game name - this should really be provided as part of SOCMessage interface, but isn't
                String[] pieces = SOCMessage.stripAttribNames(msg).split(SOCMessage.sep2);
                gameName = pieces[0];
                // Parse the message
                lastMsg = SOCMessage.parseMsgStr(msg);
            }
        }
        catch (Exception ex) {
            ex.printStackTrace();
            lastDate = null;
            lastMsg = null;
        }
        return lastMsg;                        
    }
    
    public boolean eof() {
        return eof;
    }

    public String getGameName() {
        return gameName; 
    }
    
    public void close() {
        try {
            br.close();
            if (augLog!=null) {
                augLog.flush();
                augLog.close();
            }
        }
        catch (IOException ex) {
            ex.printStackTrace();
        }
    }
    
    // Add text to the augmented logfile, if appropriate
    public void writeAugLog(String msg) {
        try {
            if (augLog!=null) {
                augLog.write(msg);
                augLog.newLine();
            }
        }
        catch (IOException ex) {
            ex.printStackTrace();
        }
    }
}
