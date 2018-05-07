/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package soc.dialogue;

import java.util.ArrayList;
import java.util.ListIterator;
import soc.game.SOCGame;

/**
 * EXPERIMENTAL SDRT STUFF
 *
 * @author markus
 */
public class StacSDRTParser {

    /** The list of SDRT nodes for this parser */
    ArrayList<StacSDRTNode> sdrt;

    int sdrtID;

    public boolean sdrtNewConversation;

    /** The StacDialogueManager that uses this parser */
    StacDialogueManager dialogueManager;
    
    /**
     * The default constructor.
     * @param diaMan    the dialogue manager using this parser
     */
    public StacSDRTParser(StacDialogueManager diaMan) {
        this.sdrtNewConversation = true;
        this.sdrtID = 0;
        this.dialogueManager = diaMan;
        this.sdrt = new ArrayList<StacSDRTNode>(diaMan.getGame().maxPlayers);
    }
    
    /**
     * Generate a new SDRT node for a StacTradeMessage and link it to the previous discourse.
     * @param msg
     * @param playerNum     the speaker
     */
    public void parseTextMessageIntoSDRT(int playerNum, String msg) {        
        if (msg == null || msg.equals(""))
            return;
        
        //analyse the message
        String text = StacDialogueManager.fromMessage(msg);
        System.err.println(text);
        
        //start turn
        if (playerNum == dialogueManager.getPlayerNumber()) {
            String[] msgParts = text.split(":");
            if (StacDialogueManager.NULL_CHAT.startsWith(msgParts[0]) && StacDialogueManager.START_TURN.startsWith(msgParts[1])) {
                printSDRTs();
                sdrtNewConversation = true;
                return;
            }
        }
        
        //trade messages
        if (text.startsWith(StacTradeMessage.TRADE)) {
            StacTradeMessage tradeMessage = StacTradeMessage.parse(text);
                
            handleDialogueAct(tradeMessage);
        }
    }
    
    public void handleDialogueAct(StacDialogueAct dialogueAct) {
        System.err.println("Handle dialogue act: " + dialogueAct.toString());
        if (dialogueAct.getClass().equals(StacTradeMessage.class)) {
            
            int playerNum = Integer.parseInt(dialogueAct.getSender());
            
            //skip no-response messages
            if (dialogueAct.getClass().equals(StacTradeMessage.class)) {
                if (((StacTradeMessage)dialogueAct).isNoResponse())
                    return;
            }
            
            //add SDRT nodes for relations to previous nodes
            String receivers[] = dialogueAct.getReceivers().split(",");
            ArrayList<StacSDRTNode> attachmentPoints = new ArrayList<>();
            //look for attachment points if we're not at the start of a turn
            if (!sdrtNewConversation) {
                String daSender = dialogueAct.getSender();
                for (String daReceiver : receivers) {
                    //search for the attachment points by looking backwards
                    ListIterator li = sdrt.listIterator(sdrt.size());
                    while (li.hasPrevious()) {
                        StacSDRTNode ap = (StacSDRTNode) li.previous();
                        String apSender = Integer.toString(ap.getSender());
                        String apReceivers = ap.getAddressees();
                        if ((apReceivers.contains(daReceiver) && apSender.equals(daSender) ) || //the previous DA by the current addressee
                                daReceiver.equals(apSender)) { // my previous message to this addressee
                            if (!attachmentPoints.contains(ap))
                                attachmentPoints.add(ap);
                            break;
                        }
                    }
                }
            }
                
            StacTradeMessage tradeMessage = (StacTradeMessage)dialogueAct;
            String discourseRelation = "unknown";
            if (tradeMessage.isReject())
                discourseRelation = "correction";
            else if (tradeMessage.isAccept())
                discourseRelation = "acknowledgement";
            else if (tradeMessage.getOffer() != null)
                discourseRelation = "elaboration";
            
            //create the new node and add it as child to its attachment points
            StacSDRTNode n = new StacSDRTNode(++sdrtID, dialogueManager.getGame(), playerNum, tradeMessage.getReceivers(), tradeMessage, attachmentPoints, discourseRelation);
            for (StacSDRTNode ap : attachmentPoints) {
                ap.addChild(n);
            }
            sdrt.add(n);

            //we now started a new conversasion
            sdrtNewConversation = false;
        }
    }

    /**
     * Print the current SDRT structures to the standard console.
     */
    public void printSDRTs() {
        String playerName =  dialogueManager.getGame().getPlayer(dialogueManager.getPlayerNumber()).getName();
        System.out.println("\n\nSDRT for " + playerName);
        for (StacSDRTNode n : sdrt) {
            if (n.getAttachmentPoints().isEmpty()) {
                printSDRT(n, 0);
            }
        }
    }

    /**
     * Print an SDRT node and recursively call myself for the children of this node.
     * @param node  StacSDRTNode to print
     * @param depth current depth of the node to print
     */
    void printSDRT(StacSDRTNode node, int depth) {
        String indent = new String(new char[depth*4]).replace("\0", " ");
        if (indent.length() >= 4) {
            indent = indent.substring(0, indent.length() - 4) + " └──";
        }
        System.out.println(node.idString() + indent + " " + node.toString());
        depth++;
        for (StacSDRTNode child : node.getChildren()) {
            printSDRT(child, depth);
        }
    }
    
//    protected StacSDRTNode findAntecedent(int player) {
//        ListIterator li = sdrt.listIterator(sdrt.size());
//        while (li.hasPrevious()) {
//            StacSDRTNode node = (StacSDRTNode) li.previous();
//            if (node.getSender() == player)
//                return node;
//        }
//        return null;
//    }

    public String currentSDRTString() {
        SOCGame ga = dialogueManager.getGame();
        int playerNumber = dialogueManager.getPlayerNumber();
        String playerName =  ga.getPlayer(playerNumber).getName();
        String output = "", outputDialogue = "";
        ListIterator li = sdrt.listIterator(sdrt.size());
        while (li.hasPrevious()) {
            StacSDRTNode node = (StacSDRTNode) li.previous();
            outputDialogue = node.idString() + " " + node.getSenderName() + ": " + node.getDialogueAct().getNLChatString() + "\n" + outputDialogue;
            ArrayList<StacSDRTNode> attachmentPoints = node.getAttachmentPoints();
            if (attachmentPoints.isEmpty()) {
//                output += "\n" + node.getRelation() + "(" + node.idString() + ", " + "root" + ")";
                //no more attachments points, so we reached the root node
                break;
            } else {
                for (StacSDRTNode ap : attachmentPoints) {
//                    if (node.getSender() == playerNumber || ap.getSender() == playerNumber) {
                        if (output.length() > 0)
                            output = "\n& " + output;
                        output = node.getRelation() + "(" + node.idString() + ", " + ap.idString() + ")" + output;
//                    }
                }
            }
        }
//        System.out.println("### SDRT for " + playerName + " ###\n" + outputDialogue + output);
        return outputDialogue + "\n" + output;
    }
    
    public String currentSDRTGraphvizString() {
        String output = "digraph sdrt {\n"; 
//        output += "\trankdir=LR;\n";
        output += "\trankdir=BT;\n";
        output += "\tsize=\"8,5\";\n";
        output += "\tnode [shape = plaintext];\n";
//        for (StacSDRTNode node : sdrt) {
//            for (StacSDRTNode ap : node.getAttachmentPoints()) {
//                output += "\t" + node.idString() + " -> " + ap.idString() + " [ label = \"" + node.getRelation().substring(0, 3) + "\" ];\n";
//            }
//        }
        ListIterator li = sdrt.listIterator(sdrt.size());
        while (li.hasPrevious()) {
            StacSDRTNode node = (StacSDRTNode) li.previous();
            ArrayList<StacSDRTNode> attachmentPoints = node.getAttachmentPoints();
            if (attachmentPoints.isEmpty())
                break;
            else
                for (StacSDRTNode ap : node.getAttachmentPoints()) {
//                    output += "\t" + node.idString() + " -> " + ap.idString() + " [ label = \"" + node.getRelation().substring(0, 3) + "\" ];\n";
                    output += "\t\"" + node.shortLabelString() + "\" -> \"" + ap.shortLabelString() + "\" [ label = \"" + node.getRelation().substring(0, 3) + "\" ];\n";
                }
        }
        output += "}";

        return output;
    }
    
}
