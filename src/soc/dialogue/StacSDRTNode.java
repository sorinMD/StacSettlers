/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package soc.dialogue;

import java.util.ArrayList;
import soc.game.SOCGame;

/**
 * Class representing one node in an SDRT structure
 * @author markus
 */
public class StacSDRTNode {
    private int ID;
    private SOCGame game;
    private int sender;
    private String addressees;
    private StacDialogueAct dialogueAct;
    private ArrayList<StacSDRTNode> attachmentPoints;
    private ArrayList<StacSDRTNode> children;
    private String relation;
    
    /**
     * Default constructor.
     * @param ID
     * @param game
     * @param sender            speaker of the dialogue act
     * @param addressees        receivers of the dialogue act
     * @param dialogueAct       
     * @param attachmentPoint 
     * @param relation          the discourse relation
     */
    protected StacSDRTNode (int ID, SOCGame game, int sender, String addressees, StacDialogueAct dialogueAct, StacSDRTNode attachmentPoint, String relation) {
        this.ID = ID;
        this.game = game;
        this.dialogueAct = dialogueAct;
        this.sender = sender;
        this.addressees = addressees;
        attachmentPoints = new ArrayList<>();
        attachmentPoints.add(attachmentPoint);
        children = new ArrayList<>();
        this.relation = relation;
    }

    protected StacSDRTNode (int ID, SOCGame game, int sender, String addressees, StacDialogueAct dialogueAct, ArrayList<StacSDRTNode> attachmentPoints, String relation) {
        this(ID, game, sender, addressees, dialogueAct, (StacSDRTNode)null, relation);
        this.attachmentPoints = attachmentPoints;
    }

    protected int getSender() {
        return sender;
    }
    
    protected String getSenderName() {
        return game.getPlayerNames()[sender];
    }
    
    protected String getAddressees() {
        return addressees;
    }
    
    protected StacDialogueAct getDialogueAct() {
        return dialogueAct;
    }
    
    protected ArrayList<StacSDRTNode> getAttachmentPoints() {
        return attachmentPoints;
    }
    
    protected ArrayList<StacSDRTNode> getChildren() {
        return children;
    }
    
    protected String getRelation() {
        return relation;
    }
    
    protected void addChild(StacSDRTNode child) {
        if (!children.contains(child))
           children.add(child);
    }

    protected String shortIdString() {
        return String.format("%03d", ID);
    }
    protected String idString() {
        return String.format("id%03d", ID);
    }
           
    @Override
    public String toString() {
        String aIDs = "root";
        if (attachmentPoints.size() > 0) {            
            aIDs = "";
            for (StacSDRTNode a : attachmentPoints) {
                aIDs += a.idString() + ", ";
            }
            aIDs = aIDs.substring(0, (aIDs.length()-2));
        }
        String childIDs = "";
        for (StacSDRTNode c : children) {
            childIDs += c.idString() + ", ";
        }
        if (childIDs.length() > 0)
            childIDs = childIDs.substring(0, (childIDs.length()-2));
        String playerNames[] = game.getPlayerNames();
        String[] ads = addressees.split(",");
        String address = "";
        for (String a : ads) {
            address += playerNames[Integer.parseInt(a)] + ", ";
        }
        address = address.substring(0, (address.length()-2));

//        return playerNames[sender] + " -> " + address + " (" + idString() + " -> " + aIDs + ") " 
//                + " # " + dialogueAct.getNLChatString()
//                + " {" + childIDs + "}";
        return playerNames[sender] + " -> " + address + " # " + relation + "(" + idString() + ", " + aIDs + ") " 
                + " # " + dialogueAct.getNLChatString()
                + " {" + childIDs + "}"
                ;
    }
    
    /**
     * Generate a label that can be used for Graphviz nodes.
     * @return 
     */
    protected String shortLabelString() {
        String nlString = dialogueAct.getNLChatString();
        //Integer.min() caused an issue on some machines
//        int endIndex = Integer.min(nlString.length(), 14);
        int endIndex = nlString.length();
        if (endIndex > 14) {
            endIndex = 14;
        }
        return shortIdString() + ": " + getSenderName() + ": " + nlString.substring(0, endIndex);
    }
}
