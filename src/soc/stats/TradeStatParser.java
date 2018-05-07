package soc.stats;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import soc.message.SOCGameTextMsg;
import soc.message.SOCMessage;

/**
 * Class to parse a set of log files and list stats on trading.
 * @author kho30
 *
 */
public class TradeStatParser extends LogReader {

    // Make these doubles just so we don't have to cast when dividing (<1 will be the norm for some fields)
    private double numGames = 0;
    private double numPlayerTrades = 0;
    private double numBankTrades = 0;
    private double numImbalancedTrades = 0;
    
    @Override
    protected void newFile(String fileName) {
        super.newFile(fileName);
        numGames ++;
    }

    @Override
    public void printOutput() {
        System.out.println(numGames + " games");
        System.out.println(numBankTrades/numGames + " bank trades");
        System.out.println(numPlayerTrades/numGames + " interplayer trades");
        System.out.println(numImbalancedTrades/numGames + " non 1:1 interplayer trades");
    }
    
    private static final Pattern resources = Pattern.compile(".+ traded (\\d+) \\w+ for (\\d+) \\w+ from .+");

    @Override
    protected void handleMsg(SOCMessage msg) {
        if (msg instanceof SOCGameTextMsg) {
            SOCGameTextMsg gtm = (SOCGameTextMsg) msg;          
            String t = gtm.getText();
            // Examine all server text messages which include the word "traded"
            if (gtm.getNickname().equals("Server") && t.contains(" traded ")) {               
                if (t.contains("from a port.") || t.contains("from the bank")) {
                    numBankTrades++;
                }
                else {
                    numPlayerTrades++;
                    // ',' indicates multiple resource types going in one direction
                    if (t.contains(",")) {
                        numImbalancedTrades++;
                    }
                    else {
                        Matcher m = resources.matcher(t);
                        m.matches();
                        if (!m.matches()) {
                            System.out.println("Unexpected: " + t);
                        }
                        else if ( ! m.group(1).equals("1") || !m.group(2).equals("1")) {                            
                            numImbalancedTrades++;  
                        }                        
                    }                    
                }
            }
        }        
    }
    
    /**
     * @param args
     */
    public static void main(String[] args) {
        TradeStatParser tsp = new TradeStatParser();
        tsp.readDirectory("logs");
        tsp.printOutput();
    }  
}
