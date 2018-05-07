package soc.server.logger;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import soc.disableDebug.D;
import soc.message.SOCMessage;
import soc.message.SOCMessageForGame;
import soc.server.SOCServer;

/**
 * Logger which stores all logged messages, and when a game is completed, creates a log file 
 * iff the designated search string is found in at least one message.
 * 
 * NB: This does not differentiate between client/server messages
 * @author Kevin O'Connor
 *
 */
public class SOCConditionalLogger implements SOCLogger {

    private final String searchString;
    private final String logDir;
    
    private final HashMap<String, List<String>> logForGames;
        
    public SOCConditionalLogger(String logDir, String searchString) {
        this.logDir = logDir;
        this.searchString = searchString;
        logForGames = new HashMap<String, List<String>>();
    }
    
    @Override
    public void startLog(String logName) {
        List<String> newLog = new ArrayList<String>();
        synchronized(logForGames) {
            logForGames.put(logName,  newLog);
        }
    }
    
    private void logMessage(SOCMessage message) {        
        String dateStr = SOCServer.formattedDate();
        D.ebugPrintlnINFO(dateStr + ":" + message);
        if (message instanceof SOCMessageForGame) {
            String game = ((SOCMessageForGame) message).getGame();
            synchronized(logForGames) {
                List<String> log = logForGames.get(game);
                if (log != null) {
                    log.add(dateStr + ":" + message);                    
                }
            }
        }
    }

    @Override
    public void logString(String logName, String out) {
        D.ebugPrintlnINFO(out);
        synchronized(logForGames) {
            List<String> log = logForGames.get(logName);
            // Log may be null due to the game being finished and cleaned up elsewhere.  Synchronization needs to be higher up to prevent this from being an issue
            if (log!=null) {
                log.add(out);   
            }
        }
    }

    @Override
    public void endLog(String logName) {
        synchronized(logForGames) {
            List<String> log = logForGames.get(logName);
            for (String s : log) {
                if (s.contains(searchString)) {
                    writeLog(logName, log);
                    break;
                }
            }        
            logForGames.remove(logName);
        }
    }

    
    private void writeLog(String logName, List<String> log) {
        try {
            FileWriter outFile = new FileWriter(logDir + logName + "-" + SOCServer.formattedDateShort() + ".soclog");
            PrintWriter out = new PrintWriter(outFile);
            for (String s : log) {
                out.println(s);
            }
            out.close();
        }
        catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    @Override
    public void logServerMessage(String recipients, SOCMessage message) {
        logMessage(message);        
    }

    @Override
    public void logClientMessage(String sender, SOCMessage message) {
        logMessage(message);
    }

    @Override
    public void logServerMessage(SOCMessage message) {
         logMessage(message);        
    }
}
