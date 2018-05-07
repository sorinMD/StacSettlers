package soc.server.logger;

import java.io.IOException;

import soc.message.SOCMessage;

/**
 * Logger which simply swallows messages, for fastest execution
 * @author Kevin O'Connor
 *
 */
public class SOCNullLogger implements SOCLogger {

    @Override
    public void startLog(String logName) throws IOException {}

    @Override
    public void logString(String logName, String out) {}

    @Override
    public void endLog(String logName) {}

    @Override
    public void logServerMessage(String recipients, SOCMessage message) {}

    @Override
    public void logClientMessage(String sender, SOCMessage message) {}

    @Override
    public void logServerMessage(SOCMessage message) {}

}
