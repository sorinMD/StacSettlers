/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package soc.dialogue;

/**
 *
 * @author markus
 */
public abstract class StacDialogueAct {
    
    protected final String sender;
    protected final String receivers;
    protected final String NLChatString;

    /**
     * Designated constructor.
     * @param sender                sender of the message
     * @param receivers             receivers of the message
     * @param nlString              the message in natural language that can be printed to the player client's chat
     */
    public StacDialogueAct(String sender, String receivers, String nlString) {
        
        if (sender == null || sender.equals("") || receivers == null || receivers.equals("")) {
            String out = "StacDialogueAct requires non-empty sender and reciever!\n";
            for (StackTraceElement trace : Thread.currentThread().getStackTrace()) {
                out += "\t" + trace + "\n";
            }
            System.err.println(out);
        }

        if (nlString == null || nlString.equals("")) {
            String out = "StacDialogueAct requires an NL string!\n";
            for (StackTraceElement trace : Thread.currentThread().getStackTrace()) {
                out += "\t" + trace + "\n";
            }
            System.err.println(out);
        }

        this.sender = sender;
        this.receivers = receivers;
        NLChatString = nlString;
    }
    
    public String getSender() {
        return sender;
    }
    
    public int getSenderInt() {
        return Integer.parseInt(sender);
    }
    
    public String getReceivers() {
        return receivers;
    }
    
    public boolean hasReceiver(int myPlayerNumber) {
        return receivers.contains(Integer.toString(myPlayerNumber));
    }

    public String getNLChatString() {
        return NLChatString;
    }

}
