package soc.server.logger;

import java.io.IOException;

import soc.message.SOCMessage;

/**
 * Interface to support logging of messages (to a file, DB, null logging, conditional logging, etc).  
 * @author Kevin O'Connor
 *
 */
public interface SOCLogger {
    public void startLog(String logName) throws IOException;
    // Messages sent to restricted members of the game
    public void logServerMessage(String recipients, SOCMessage message);
    // Messages sent to everyone
    public void logServerMessage(SOCMessage message);
    public void logClientMessage(String sender, SOCMessage message);
    public void logString(String logName, String out);
    public void endLog(String logName);
}

