/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package soc.dialogue;

import soc.game.StacTradeOffer;

/**
 *
 * @author markus
 */
public class StacClarificationRequest extends StacDialogueAct {

    private final int type;
    
    private final StacTradeOffer offer;
    
    public StacClarificationRequest(String sender, String receiver, String nlString, int type, StacTradeOffer offer) {
        super(sender, receiver, nlString);
        
        this.type = type;
        this.offer = offer;
    }
    
}
