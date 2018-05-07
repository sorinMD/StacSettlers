package soc.client;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import soc.disableDebug.D;

/**
 * Logger to write game and chat messages to file
 *
 */
public class StacChatLogger{
    
//duplicated from SOCServer class in case the jar file is missing the server package for some reason
	public static final String LOG_DIR = "logs_client/";
	public static final DateFormat DATE_FORMAT_SHORT = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-Z");
	public static final DateFormat LOG_DATE_FORMAT = new SimpleDateFormat("yyyy:MM:dd:HH:mm:ss:SSS:Z");
	
    /**
     * Return the formatted date and time.
     * @return  String with the formatted date
     */
    public static String formattedDate() {    	
    	Date date = new Date();
    	String formattedDate = LOG_DATE_FORMAT.format(date);
    	return formattedDate;
    }
	
    /**
     * Return the formatted date and time in a shorter format.
     * @return  String with the formatted date
     */
    public static String formattedDateShort() {    	
    	Date date = new Date();
    	String formattedDate = DATE_FORMAT_SHORT.format(date);
    	return formattedDate;
    }
//end duplication	
    
    private final HashMap<String, PrintWriter[]> outputStreams;
    private final HashMap<String, String> discourseStructureFilenames;    
    
    public StacChatLogger() {
        File dir = new File(LOG_DIR);
    	if(!dir.exists())
        	dir.mkdirs();
        outputStreams = new HashMap<String, PrintWriter[]>();
        discourseStructureFilenames = new HashMap<String, String>();
    }

    public boolean hasLoggingStarted(String gameName){
    	PrintWriter[] out = outputStreams.get(gameName);
    	return out!=null;
    }
    
    public void startLog(String gameName, int pn) throws IOException {
        String gameNameAndDateString = gameName + "-" + StacChatLogger.formattedDateShort();
        File dir = new File(LOG_DIR + gameNameAndDateString);
        if (!dir.exists())
        	dir.mkdirs();

        FileWriter outFile = new FileWriter(LOG_DIR + gameNameAndDateString + "/" + "Chat_" + gameName + "_" + pn + "-" + StacChatLogger.formattedDateShort() + ".log");
        PrintWriter chat = new PrintWriter(outFile);
        
        outFile = new FileWriter(LOG_DIR + gameNameAndDateString + "/" + "Text_" + gameName + "_" + pn + "-" + StacChatLogger.formattedDateShort() + ".log");
        PrintWriter text = new PrintWriter(outFile);
        
        String fn = LOG_DIR + gameNameAndDateString + "/" + "SDRT_" + gameName + "_" + pn + "-" + StacChatLogger.formattedDateShort();
        discourseStructureFilenames.put(gameName, fn);
        outFile = new FileWriter(fn + ".log");
        PrintWriter sdrt = new PrintWriter(outFile);
        
        PrintWriter[] allLogs = new PrintWriter[] { chat, text, sdrt };
        outputStreams.put(gameName, allLogs);
    }

    public void endLog(String gameName) {
        PrintWriter[] out = outputStreams.get(gameName);
        for (PrintWriter o : out) {
            o.close();            
        }
        outputStreams.remove(gameName);
    }

    public void logChatMessage(String message, String gameName) {
        String dateStr = StacChatLogger.formattedDate();
        D.ebugPrintlnINFO(dateStr + ":" + message);
        PrintWriter[] out = outputStreams.get(gameName);
        if (out != null) {
        	out[0].println(dateStr + ":" + message);
        }
    }

    public void logTextMessage(String message, String gameName) {
        String dateStr = StacChatLogger.formattedDate();
        D.ebugPrintlnINFO(dateStr + ":" + message);
        PrintWriter[] out = outputStreams.get(gameName);
        if (out != null) {
            out[1].println(dateStr + ":" + message); 
        }
    }

    int discourseStructureCounter = 0;
    
    public void logDiscourseStructure(String gameName, String sdrt, String gv) {
        discourseStructureCounter++;
        
        //write the SDRT string to the SDRT log file
        String dateStr = StacChatLogger.formattedDate();
//        D.ebugPrintlnINFO(dateStr + ":" + message);
        PrintWriter[] outStreams = outputStreams.get(gameName);
        if (outStreams != null) {
            outStreams[2].println("*** " + discourseStructureCounter + " (" + dateStr + ") *** \n" + sdrt + "\n");
        }
        
        PrintWriter out;
        String fn = discourseStructureFilenames.get(gameName);
        try {
//            out = new PrintWriter(fn + "_" + discourseStructureCounter + ".gv");
//            out.println(gv);
//            out.close();
            out = new PrintWriter(fn + "_current.gv");
            out.println(gv);
            out.close();
        } catch (FileNotFoundException ex) {
            System.err.println("Can't write to SDRT output file!");
        }
    }
}
