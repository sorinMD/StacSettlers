package soc.stats;

import java.io.File;

import soc.message.SOCMessage;
import soc.util.LogParser;

/**
 * Abstract class to consume and process logs, eg to gather stats or find specific phenomena
 * @author kho30
 *
 */
public abstract class LogReader {

    protected String currentFile = null;
    
    public final void readDirectory(String folderName) {
        File folder = new File(folderName);
        for (File f : folder.listFiles()) {
            if (f.isFile()) {
                String fileName = f.getAbsolutePath();
                readFile(fileName);
            }
            // Consider adding capability for recursion
        }
    }
    
    public final void readFile(String file) {
        newFile(file);
        LogParser lp = LogParser.getParser(file);
        while (!lp.eof()) {
            SOCMessage msg = lp.parseLine();
            if (msg!=null) {
                handleMsg(msg);
            }
        }
        lp.close();
    }
    
    /**
     * The meat of the class: what are we actually doing with the messages we get? 
     * @param msg
     */
    protected abstract void handleMsg(SOCMessage msg);
    
    /**
     * Indicate we are starting to process a new game.  Override this if you need to reset
     *  stats, etc
     * @param fileName
     */
    protected void newFile(String fileName) {
        currentFile = fileName;
        System.out.println(fileName);
    }
    
    /**
     * Print the reader's findings, eg stats.  Default is to do nothing, which may
     *  be appropriate, eg if we're using this to display matching lines as we find them
     */
    public void printOutput() {}
}
