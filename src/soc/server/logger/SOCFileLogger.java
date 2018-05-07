package soc.server.logger;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;

import soc.disableDebug.D;
import soc.message.SOCMessage;
import soc.message.SOCMessageForGame;
import soc.server.SOCServer;

/**
 * Logger to write messages to a file, as per default implementation
 * @author Kevin O'Connor
 *
 */
public class SOCFileLogger implements SOCLogger {
    
    private final HashMap<String, PrintWriter[]> gameOutputStreams;
    private final String logDir;
    
    public SOCFileLogger(String logDir) {
        File dir = new File(logDir);
    	if(!dir.exists())
        	dir.mkdirs();
    	this.logDir = logDir;
        gameOutputStreams = new HashMap<String, PrintWriter[]>();
    }

    @Override
    public void startLog(String logName) throws IOException {
        String logNameAndDateString = logName + "-" + SOCServer.formattedDateShort();
        File dir = new File(logDir + logNameAndDateString);
    	if(!dir.exists())
        	dir.mkdirs();
        
        FileWriter outFile = new FileWriter(logDir + logNameAndDateString + "/" + logNameAndDateString + ".soclog");
        PrintWriter out = new PrintWriter(outFile);
        
        outFile = new FileWriter(logDir + logNameAndDateString + "/"  + "Server_" + logNameAndDateString + ".soclog");
        PrintWriter server = new PrintWriter(outFile);
        
        outFile = new FileWriter(logDir + logNameAndDateString + "/"  + "Client_" + logNameAndDateString + ".soclog");
        PrintWriter client = new PrintWriter(outFile);
        
        PrintWriter[] allLogs = new PrintWriter[] { out, server, client };
        gameOutputStreams.put(logName, allLogs);
    }

    @Override
    public void endLog(String logName) {
        PrintWriter[] out = gameOutputStreams.get(logName);
        for (PrintWriter o : out) {
            o.close();            
        }
        gameOutputStreams.remove(logName);
    }

    @Override
    public void logString(String logName, String outputString) {
        D.ebugPrintlnINFO(outputString);
        PrintWriter out = gameOutputStreams.get(logName)[0];
        out.println(outputString);
    }

    @Override
    public void logServerMessage(String recipients, SOCMessage message) {
        String dateStr = SOCServer.formattedDate();
        D.ebugPrintlnINFO(dateStr + ":" + message);
        if (message instanceof SOCMessageForGame) {
            String game = ((SOCMessageForGame) message).getGame();
            PrintWriter[] out = gameOutputStreams.get(game);
            if (out != null) {
                out[0].println(dateStr + ":" + message);
                out[1].println(recipients + ":" + dateStr + ":" + message);
            }
        }
        
    }

    @Override
    public void logClientMessage(String sender, SOCMessage message) {
        String dateStr = SOCServer.formattedDate();
        D.ebugPrintlnINFO(dateStr + ":" + message);
        if (message instanceof SOCMessageForGame) {
            String game = ((SOCMessageForGame) message).getGame();
            PrintWriter[] out = gameOutputStreams.get(game);
            if (out != null) {
                out[0].println(dateStr + ":" + message);            
                out[2].println(sender + ":" + dateStr + ":" + message);
            }
        } 
        
    }

    @Override
    public void logServerMessage(SOCMessage message) {
        logServerMessage("ALL", message);        
    }

}
