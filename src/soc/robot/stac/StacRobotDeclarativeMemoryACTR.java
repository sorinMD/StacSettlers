package soc.robot.stac;

import actr.model.*;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import soc.disableDebug.D;
import soc.game.SOCGame;
import soc.game.SOCResourceConstants;
import soc.game.SOCResourceSet;
import soc.game.SOCTradeOffer;
import soc.game.StacTradeOffer;
import soc.util.NullOutputStream;

/**
 * Subclass of StacRobotDeclarativeMemory that uses the ACT-R DM to store some of the information, in particular the robot's beliefs.
 * 
 * @author
 * Markus Guhe
 */

public class StacRobotDeclarativeMemoryACTR extends StacRobotDeclarativeMemory {

    /**
     * Helper method to get the string "unknown" for SOCResourceConstants.UNKNOWN instead of NULL.
     * @param type
     * @return a string representing the specified resource type
     */
    private String resourceNameForResourceType(int rsrcType) {
        if (rsrcType == SOCResourceConstants.UNKNOWN) {
            return "unknown";
        } else {
            return SOCResourceConstants.resName(rsrcType);
        }
    }

    /**
     * Output stream for output from ACT-R.
     */
    private PrintStream actrOutput;
    
    /**
     * The ACT-R model we use for storing items in the declarative memory.
     */
    Model actrModel;

    /**
     * The ACT-R declarative memory of actrModel. (Just a convenience declaration.)
     */
    Declarative actrDM;

    /**
     * Number of successful ACT_R DM memory retrievals.
     */
    int successfulMemoryRetrievals;
    
    /**
     * Number of unsuccessful ACT_R DM memory retrievals.
     */
    int unsuccessfulMemoryRetrievals;
    
    /**
     * Create the Declarative Memory for our brain.
     * @param brain 
     */
    public StacRobotDeclarativeMemoryACTR(StacRobotBrain brain, SOCGame game) {
        super(brain, game);

        try {
            if (brain.isRobotType(StacRobotType.LOG_ACT_R_OUTPUT)) {
                actrOutput = new PrintStream(new FileOutputStream("logs/actrlogs/" + game.getName() + ".actrlog"));
//                actrOutput = System.out; //for debugging
            } else {
                actrOutput = new PrintStream(new NullOutputStream()); //new FileOutputStream("/dev/null"));
            }
        } catch (FileNotFoundException ex) {
            Logger.getLogger(StacRobotDeclarativeMemoryACTR.class.getName()).log(Level.SEVERE, null, ex);
        }

        //initialise the ACT-R declarative memory

        //load the ACT-R model
        File file = new File("java/actr/actrModel.actr");
        this.actrModel = Model.compile(file, null);
        this.actrDM = actrModel.getDeclarative();

        //set ACT-R parameters
        if (brain.isRobotType(StacRobotType.ACT_R_PARAMETER_RT)) {
            Double rt = (Double)brain.getTypeParam(StacRobotType.ACT_R_PARAMETER_RT);
            actrModel.setParameter(":rt", rt.toString());
        }

        //create some SOC specific pervasive symbols
        Symbol.createPervasiveSymbol("clay");
        Symbol.createPervasiveSymbol("ore");
        Symbol.createPervasiveSymbol("rock");
        Symbol.createPervasiveSymbol("wheat");
        Symbol.createPervasiveSymbol("wood");
//            Symbol.createPervasiveSymbol("player");
//            Symbol.createPervasiveSymbol("resource");
//            System.err.println("DM of new ACT-R model: " + this.actrDM.toString());

//            //player names are not yet initialised, so we can't create symbols for them
//            for (SOCPlayer p : game.getPlayers()) {
//                String pName = p.getName();
//                Symbol.createPervasiveSymbol(pName);
//            }
//            for (int pn = 0; pn < 4; pn++) {
//                for (int rsrcType = SOCResourceConstants.CLAY; rsrcType < SOCResourceConstants.WOOD; rsrcType++) {
//                    addIsSellingChunk(pn, rsrcType, true);                    
//                }
//            }

        successfulMemoryRetrievals = 0;
        unsuccessfulMemoryRetrievals = 0;
    }

    ///////////////////////////////////////////////////////////////////////////////////
    // OBSERVABLE INFORMATION
    ///////////////////////////////////////////////////////////////////////////////////
    
    //handled by super

    ///////////////////////////////////////////////////////////////////////////////////
    // INFORMATION ABOUT OURSELF
    ///////////////////////////////////////////////////////////////////////////////////
    
    //handled by super
    
    ///////////////////////////////////////////////////////////////////////////////////
    // OUR OWN HAND
    ///////////////////////////////////////////////////////////////////////////////////
    
    //handled by super
    
    ///////////////////////////////////////////////////////////////////////////////////
    // PAST AND CURRNET TRADING
    ///////////////////////////////////////////////////////////////////////////////////
    
    @Override
    boolean pastTradeOfferExists(StacTradeOffer offerToCheck) {
    //    int opn = getPlayerNumber();// ourPlayerData.getPlayerNumber();
  //      boolean[] offeredTo = new boolean[game.maxPlayers];
//        SOCTradeOffer offerToCheck = new SOCTradeOffer(game.toString(), opn, offeredTo, offerToCheck.getGiveSet(), offerToCheck.getGetSet());
        
//                offerMade = pastTradeOfferExistsInACTRDM(offerToCheck);
      //      } else {

        //    return false;
        return pastTradeOfferExistsInACTRDM(offerToCheck);
    }

    /**
     * Check whether the ACT-R DM contains the specified past-trade-offer.
     * Only the give-resources and get-resource but not the "from" and "to" information given in the specified trade offer are 
     * used in the retrieval attempt.
     * Retrieval can fail if there was no such offer in the past, or if the ACT-R DM does not retrieve the existing information.
     * @param offerToCheck  the specified past-trade-offer we're trying to retrieve
     * @return true if a corresponding chunk was retrieved from the ACT-R DM, false otherwise
     * @author Markus Guhe
     */
    protected boolean pastTradeOfferExistsInACTRDM(SOCTradeOffer offerToCheck) {
        boolean result = false;
        
        //redirect stdout
        PrintStream original = System.out;
        System.setOut(actrOutput);

        //create the goal chunk
        Chunk goalChunk = new Chunk(Symbol.get("retrieve-past-trade-offer-goal"), actrModel);
        goalChunk.set (Symbol.get("isa"), Symbol.get("retrieve-past-trade-offer"));
        //addTradeOfferInfoToChunk() set the to and from information as well, which we don't want
        goalChunk.set (Symbol.get("give-clay"), Symbol.get(offerToCheck.getGiveSet().getAmount(SOCResourceConstants.CLAY)));
        goalChunk.set (Symbol.get("give-ore"), Symbol.get(offerToCheck.getGiveSet().getAmount(SOCResourceConstants.ORE)));
        goalChunk.set (Symbol.get("give-sheep"), Symbol.get(offerToCheck.getGiveSet().getAmount(SOCResourceConstants.SHEEP)));
        goalChunk.set (Symbol.get("give-wheat"), Symbol.get(offerToCheck.getGiveSet().getAmount(SOCResourceConstants.WHEAT)));
        goalChunk.set (Symbol.get("give-wood"), Symbol.get(offerToCheck.getGiveSet().getAmount(SOCResourceConstants.WOOD)));
        goalChunk.set (Symbol.get("get-clay"), Symbol.get(offerToCheck.getGetSet().getAmount(SOCResourceConstants.CLAY)));
        goalChunk.set (Symbol.get("get-ore"), Symbol.get(offerToCheck.getGetSet().getAmount(SOCResourceConstants.ORE)));
        goalChunk.set (Symbol.get("get-sheep"), Symbol.get(offerToCheck.getGetSet().getAmount(SOCResourceConstants.SHEEP)));
        goalChunk.set (Symbol.get("get-wheat"), Symbol.get(offerToCheck.getGetSet().getAmount(SOCResourceConstants.WHEAT)));
        goalChunk.set (Symbol.get("get-wood"), Symbol.get(offerToCheck.getGetSet().getAmount(SOCResourceConstants.WOOD)));

        actrModel.setGoalFocus(goalChunk);
        System.out.println("********** RUNNING MODEL FOR RETRIEVE PAST-TRADE-OFFER **********");
        actrModel.outputWhyNot();
        actrModel.run(false);
        if (brain.isRobotType(StacRobotType.LOG_ACT_R_OUTPUT)) {
            //System.out.println("*** ACT-R DM (" + this.brain.getPlayerData().getName() + ") ***\n" + 
            System.out.println("*** ACT-R DM (past-trade-offer) ***\n" + 
                    actrModel.getDeclarative().toString("past-trade-offer")); //toStringWithActivation());
            System.out.println("*** BUFFERS ***\n");
            actrModel.outputBuffers();
            //System.err.println(actrModel.toString());
        }
        System.out.println("********** FINISHED RUNNING MODEL FOR RETRIEVE PAST-TRADE-OFFER **********");

        //get the retrieved chunk from the retrieval buffer (we assume that the model stopped in this partiuclar state!)
        Buffers allBuffers = actrModel.getBuffers();
        Chunk retrievedChunk = allBuffers.get(Symbol.retrieval);
        if (retrievedChunk == null) {
            System.out.println("Retrieval: No chunk retrieved.");
        } else {
            System.out.println("Retrieval: Chunk: " + retrievedChunk.toString());
            result = true;
        }

        //reinstate stdout
        System.setOut(original);

        return result;
    }

    /**
     * Add the information about get-resources, give-resources and players to the chunk.
     * @param offer   SOCTradeOffer with the info to be added
     * @param chunk   an existing Chunk the into is added to
     * @author Markus Guhe
     */
    private void addTradeOfferInfoToChunk(SOCTradeOffer offer, Chunk chunk) {
        chunk.set (Symbol.get("give-clay"), Symbol.get(offer.getGiveSet().getAmount(SOCResourceConstants.CLAY)));
        chunk.set (Symbol.get("give-ore"), Symbol.get(offer.getGiveSet().getAmount(SOCResourceConstants.ORE)));
        chunk.set (Symbol.get("give-sheep"), Symbol.get(offer.getGiveSet().getAmount(SOCResourceConstants.SHEEP)));
        chunk.set (Symbol.get("give-wheat"), Symbol.get(offer.getGiveSet().getAmount(SOCResourceConstants.WHEAT)));
        chunk.set (Symbol.get("give-wood"), Symbol.get(offer.getGiveSet().getAmount(SOCResourceConstants.WOOD)));
        chunk.set (Symbol.get("get-clay"), Symbol.get(offer.getGetSet().getAmount(SOCResourceConstants.CLAY)));
        chunk.set (Symbol.get("get-ore"), Symbol.get(offer.getGetSet().getAmount(SOCResourceConstants.ORE)));
        chunk.set (Symbol.get("get-sheep"), Symbol.get(offer.getGetSet().getAmount(SOCResourceConstants.SHEEP)));
        chunk.set (Symbol.get("get-wheat"), Symbol.get(offer.getGetSet().getAmount(SOCResourceConstants.WHEAT)));
        chunk.set (Symbol.get("get-wood"), Symbol.get(offer.getGetSet().getAmount(SOCResourceConstants.WOOD)));
        String fromName = brain.getGame().getPlayer(offer.getFrom()).getName().toLowerCase();
        chunk.set (Symbol.get("from"), Symbol.get(fromName));
        String flag = offer.getTo()[0] ? "true" : "false";
        chunk.set (Symbol.get("to-player-1"), Symbol.get(flag));
        flag = offer.getTo()[1] ? "true" : "false";
        chunk.set (Symbol.get("to-player-2"), Symbol.get(flag));
        flag = offer.getTo()[2] ? "true" : "false";
        chunk.set (Symbol.get("to-player-3"), Symbol.get(flag));
        flag = offer.getTo()[3] ? "true" : "false";
        chunk.set (Symbol.get("to-player-4"), Symbol.get(flag));
    }

//This is not yet done by the ACT-R version of the DeclMem, so we let super handle this.
//TODO: handle bestCompleteTradeOffer in ACT-R version of the Declarative Memory
//    /**
//     * The partialising agent uses this to store the complete offer it wanted to make initially.
//     */
//    private SOCTradeOffer originalBestCompleteTradeOffer = null;
//    
//    /**
//     * Remembering the full offer the partialising agent wanted to make initially.
//     * @param offer 
//     */
//    protected void  setOriginalBestCompleteTradeOffer(SOCTradeOffer offer) {
//        if (offer == null) {
//            this.originalBestCompleteTradeOffer = null;
//        } else {
//            this.originalBestCompleteTradeOffer = new SOCTradeOffer(offer);
//        }
//    }
//    
//    /**
//     * Get the full offer the partialising agent wanted to make initially.
//     * @retrun the original complete offer
//     */
//    protected SOCTradeOffer getOriginalBestCompleteTradeOffer() {
//        return this.originalBestCompleteTradeOffer;
//    }
    
    /**
     * Store the best completed trade offer that should be the answer to a partial trade offer.
     * The ETA (ETB) is not preserved in the ACT-R DM but only in the variable in the robot's declarativeMemory
     * @param bestCompletedTradeOffer
     * @param eta
     * @param globalETA
     */
        
    @Override
    protected void rememberBestCompletedTradeOffer(StacTradeOffer bestCompletedTradeOffer, int eta, int globalETA) {
        super.rememberBestCompletedTradeOffer(bestCompletedTradeOffer, eta, globalETA);

        //redirect stdout
        PrintStream original = System.out;
        System.setOut(actrOutput);

        //create the goal chunk
        Chunk goalChunk = new Chunk(Symbol.get("add-best-completed-trade-offer-goal"), actrModel);
        goalChunk.set (Symbol.get("isa"), Symbol.get("add-best-completed-trade-offer"));
        addTradeOfferInfoToChunk(bestCompletedTradeOffer, goalChunk);

        actrModel.setGoalFocus(goalChunk);
        System.out.println("********** RUNNING MODEL FOR ADD BEST-COMPLETED-TRADE-OFFER **********");
        actrModel.outputWhyNot();
        actrModel.run(false);
        if (brain.isRobotType(StacRobotType.LOG_ACT_R_OUTPUT)) {
            //System.out.println("*** ACT-R DM (" + this.brain.getPlayerData().getName() + ") ***\n" + 
            System.out.println("*** ACT-R DM (best-completed-trade-offer) ***\n" + 
                    actrModel.getDeclarative().toString("best-completed-trade-offer")); //toStringWithActivation());
            System.out.println("*** BUFFERS ***\n");
            actrModel.outputBuffers();
            //System.err.println(actrModel.toString());
        }
        System.out.println("********** FINISHED RUNNING MODEL FOR ADD BEST-COMPLETED-TRADE-OFFER **********");

        //reinstate stdout
        System.setOut(original);
    }
    
    /**
     * Retrieve the best completed trade offer that we can remember.
     * 
     * @return SOCTradeOffer that we can remember as best completed trade offer (so far)
     */
    @Override
    protected StacTradeOffer retrieveBestCompletedTradeOffer() {
        super.retrieveBestCompletedTradeOffer();

        //redirect stdout
        PrintStream original = System.out;
        System.setOut(actrOutput);

        StacTradeOffer bestCompletedTradeOffer = null;
    
        //create the goal chunk
        Chunk goalChunk = new Chunk(Symbol.get("retrieve-best-completed-trade-offer-goal"), actrModel);
        goalChunk.set (Symbol.get("isa"), Symbol.get("retrieve-best-completed-trade-offer"));

        actrModel.setGoalFocus(goalChunk);
        System.out.println("********** RUNNING MODEL FOR RETRIEVE BEST-COMPLETED-TRADE-OFFER **********");
        actrModel.outputWhyNot();
        actrModel.run(false);
        if (brain.isRobotType(StacRobotType.LOG_ACT_R_OUTPUT)) {
            //System.out.println("*** ACT-R DM (" + this.brain.getPlayerData().getName() + ") ***\n" + 
            System.out.println("*** ACT-R DM (best-completed-trade-offer) ***\n" + 
                    actrModel.getDeclarative().toString("best-completed-trade-offer")); //toStringWithActivation());
            System.out.println("*** BUFFERS ***\n");
            actrModel.outputBuffers();
            //System.err.println(actrModel.toString());
        }
        System.out.println("********** FINISHED RUNNING MODEL FOR RETRIEVE BEST-COMPLETED-TRADE-OFFER **********");

        //get the retrieved chunk from the retrieval buffer (we assume that the model stopped in this partiuclar state!)
        Buffers allBuffers = actrModel.getBuffers();
        Chunk retrievedChunk = allBuffers.get(Symbol.retrieval);
        if (retrievedChunk == null) {
            System.out.println("Retrieval: No chunk retrieved.");
            System.err.println("no best completed offer retrieved");
            bestCompletedTradeOffer = null;
        } else {
            System.out.println("Retrieval: Chunk: " + retrievedChunk.toString());
            String fromString = retrievedChunk.getSlotValue(Symbol.get("from")).toString();
            String flag1 = retrievedChunk.getSlotValue(Symbol.get("to-player-1")).toString();
            String flag2 = retrievedChunk.getSlotValue(Symbol.get("to-player-2")).toString();
            String flag3 = retrievedChunk.getSlotValue(Symbol.get("to-player-3")).toString();
            String flag4 = retrievedChunk.getSlotValue(Symbol.get("to-player-4")).toString();
            if (fromString == null || flag1 == null || flag2 == null || flag3 == null || flag4 == null) {
                bestCompletedTradeOffer = null;
            } else {
                int fromInt = 0;
                if (fromString != null) {
                    //TODO: there probabaly are null pointer exceptions because of differing capitalisations of the player name
                    try {
                        for (int i = 0; i < 4; i++) {
                            if (brain.getGame().getPlayer(i).getName().toLowerCase().equals(fromString.toLowerCase())) {
                                fromInt = i;
                                break;
                            }
                        }
                    } catch (Exception e) {
                        System.err.println("Players: " + 
                        		brain.getGame().getPlayer(0).getName() + ", " + 
                        		brain.getGame().getPlayer(1).getName() + ", " + 
                        		brain.getGame().getPlayer(2).getName() + ", " + 
                        		brain.getGame().getPlayer(3).getName());
                        System.err.println("fromString: " + fromString);
                    }
                }
                boolean[] toBool = new boolean[4];
                if (flag1 != null) {
                    toBool[0] = flag1.equals("true");
                }
                if (flag2 != null) {
                    toBool[1] = flag2.equals("true");
                }
                if (flag3 != null) {
                    toBool[2] = flag3.equals("true");
                }
                if (flag4 != null) {
                    toBool[3] = flag4.equals("true");
                }
                SOCResourceSet giveSet = new SOCResourceSet(
                    retrievedChunk.getSlotValue(Symbol.get("give-clay")).toInt(),
                    retrievedChunk.getSlotValue(Symbol.get("give-ore")).toInt(),
                    retrievedChunk.getSlotValue(Symbol.get("give-sheep")).toInt(),
                    retrievedChunk.getSlotValue(Symbol.get("give-wheat")).toInt(),
                    retrievedChunk.getSlotValue(Symbol.get("give-wood")).toInt(),
                    0);
                SOCResourceSet getSet = new SOCResourceSet(
                    retrievedChunk.getSlotValue(Symbol.get("get-clay")).toInt(),
                    retrievedChunk.getSlotValue(Symbol.get("get-ore")).toInt(),
                    retrievedChunk.getSlotValue(Symbol.get("get-sheep")).toInt(),
                    retrievedChunk.getSlotValue(Symbol.get("get-wheat")).toInt(),
                    retrievedChunk.getSlotValue(Symbol.get("get-wood")).toInt(),
                    0);
                bestCompletedTradeOffer = new StacTradeOffer(brain.getGame().getName(), fromInt, toBool, giveSet, false, getSet, false);
            }
        }

        //reinstate stdout
        System.setOut(original);

        return bestCompletedTradeOffer; 
    }
    
    /**
     * Remember a trade offer.
     * 
     * @param offer  a SOCTradeOffer object; if offer is null, nothing is stored
     */
    @Override
    protected void rememberTradeOffer(StacTradeOffer offer) {
        super.rememberTradeOffer(offer);

        //TODO: The partialising agent adds the trade offer to the past-offers twice; so this gives a boost to the ACT-R chunk, which we don't want
        
        //redirect stdout
        PrintStream original = System.out;
        System.setOut(actrOutput);

        //create the goal chunk
        Chunk goalChunk = new Chunk(Symbol.get("add-past-trade-offer-goal"), actrModel);
        goalChunk.set (Symbol.get("isa"), Symbol.get("add-past-trade-offer"));
        addTradeOfferInfoToChunk(offer, goalChunk);

        actrModel.setGoalFocus(goalChunk);
        System.out.println("********** RUNNING MODEL FOR ADD PAST-TRADE-OFFER **********");
        actrModel.outputWhyNot();
        actrModel.run(false);
        if (brain.isRobotType(StacRobotType.LOG_ACT_R_OUTPUT)) {
            //System.out.println("*** ACT-R DM (" + this.brain.getPlayerData().getName() + ") ***\n" + 
            System.out.println("*** ACT-R DM (past-trade-offer) ***\n" + 
                    actrModel.getDeclarative().toString("past-trade-offer")); //toStringWithActivation());
            System.out.println("*** BUFFERS ***\n");
            actrModel.outputBuffers();
            //System.err.println(actrModel.toString());
        }
        System.out.println("********** FINISHED RUNNING MODEL FOR ADD PAST-TRADE-OFFER **********");

        //reinstate stdout
        System.setOut(original);
    }

    /**
     * Forget all trade offers.
     */
    protected void forgetAllTradeOffers() {
        super.forgetAllTradeOffers();

        //redirect stdout
        PrintStream original = System.out;
        System.setOut(actrOutput);

        //create the goal chunk
        Chunk goalChunk = new Chunk(Symbol.get("forget-past-trade-offers-goal"), actrModel);
        goalChunk.set (Symbol.get("isa"), Symbol.get("forget-past-trade-offers"));

        actrModel.setGoalFocus(goalChunk);
        System.out.println("********** RUNNING MODEL FOR FORGET PAST-TRADE-OFFERS **********");
        actrModel.outputWhyNot();
        actrModel.run(false);
        if (brain.isRobotType(StacRobotType.LOG_ACT_R_OUTPUT)) {
            //System.out.println("*** ACT-R DM (" + this.brain.getPlayerData().getName() + ") ***\n" + 
            System.out.println("*** ACT-R DM (past-trade-offer) ***\n" + 
                    actrModel.getDeclarative().toString("past-trade-offer")); //toStringWithActivation());
            System.out.println("*** BUFFERS ***\n");
            actrModel.outputBuffers();
            //System.err.println(actrModel.toString());
        }
        System.out.println("********** FINISHED RUNNING MODEL FOR FORGET PAST-TRADE-OFFER **********");

        //reinstate stdout
        System.setOut(original);
    }
    
    /**
     * Retrieve all trade offers.
     * 
     * @return list of all trade offers stored in the declarative memory.
     */
    //TODO: this uses the value from the perfect memory version and must still be implemented for ACT-R
    protected ArrayList retrieveAllTradeOffers() {
        return super.retrieveAllTradeOffers();
    }

    /**
     * Add an is-selling chunk to the ACT-R declarative memory.
     * 
     * @param playerName    the name of the player
     * @param resourceName  the name of the resource
     * @param flag          flag determining whether the player is selling or not
     */
    private void addIsSellingChunk(String playerName, String resourceName, boolean flag) {
        //redirect stdout
        PrintStream original = System.out;
        System.setOut(actrOutput);

        //collect information for chunk
        String flagString = flag ? "true" : "false";
        
        //VERSION WHERE WE'RE CREATING THE DM CHUNK DIRECTLY, WITHOUT RUNNING THE MODEL
        //COULD ALSO USE actrModel.runCommand();
        //    (add-is-selling-goal-test ISA add-is-selling player Original_3 resource wheat selling false)
        //        String chunkName = "is-selling-" + playerName + "-" + resourceName;
//        Chunk newChunk = new Chunk(Symbol.get(chunkName), actrModel);
//        newChunk.set (Symbol.get("isa"), Symbol.get("is-selling"));
//        newChunk.set (Symbol.get("player"), Symbol.get(playerName));
//        newChunk.set (Symbol.get("resource"), Symbol.get(resourceName));
//        newChunk.set (Symbol.get("selling"), Symbol.get(flagString));
//        this.actrDM.add(newChunk);
//        System.out.println("********** ACT-R DM (" + this.brain.getPlayerData().getName() + ") **********\n" + 
//                actrModel.getDeclarative().toString());
        

        //create chunk goal chunk with the required information (should perhaps create a new constructor for Chunk to do this in one go)
        Chunk goalChunk = new Chunk(Symbol.get("add-is-selling-goal"), actrModel);
        goalChunk.set (Symbol.get("isa"), Symbol.get("add-is-selling"));
        goalChunk.set (Symbol.get("player"), Symbol.get(playerName.toLowerCase()));
        goalChunk.set (Symbol.get("resource"), Symbol.get(resourceName));
        goalChunk.set (Symbol.get("selling"), Symbol.get(flagString));
        
        //add to ACT-R DM
        //set the goal focus to the new goal chunk and run the model
        System.out.println("Should add is-selling chunk for: " + playerName + " " + resourceName + " " + flagString);
        System.out.println("Chunk: " + goalChunk.toString());
        actrModel.setGoalFocus(goalChunk);
        System.out.println("********** RUNNING MODEL FOR ADD IS-SELLING **********");
        actrModel.outputWhyNot();
        actrModel.run(false);
        if (brain.isRobotType(StacRobotType.LOG_ACT_R_OUTPUT)) {
            System.out.println("*** ACT-R DM (" + this.brain.getPlayerData().getName() + ") ***\n" + 
                    actrModel.getDeclarative().toStringWithActivation());
            System.out.println("*** BUFFERS (" + this.brain.getPlayerData().getName() + ") ***\n");
            actrModel.outputBuffers();
        }
        System.out.println("********** FINISHED RUNNING MODEL FOR ADD IS-SELLING **********");

        //reinstate stdout
        System.setOut(original);
    }
    
    /**
     * Add an is-selling chunk to the ACT-R declarative memory.
     * 
     * @param pn        the number of the player
     * @param rsrcType  the type of resource
     * @param flag      flag determining whether the player is selling or not
     */
    private void addIsSellingChunk(int pn, int rsrcType, boolean flag) {
        String playerName = brain.getGame().getPlayer(pn).getName();
        String resourceName = resourceNameForResourceType(rsrcType);
        addIsSellingChunk(playerName, resourceName, flag);
    }
        
    /**
     * mark a player as not selling a resource
     *
     * @param pn         the number of the player
     * @param rsrcType   the type of resource
     */
    @Override
    public void markAsNotSelling(int pn, int rsrcType) {
        super.markAsNotSelling(pn, rsrcType);

        addIsSellingChunk(pn, rsrcType, false);
    }

    /**
     * mark a player as willing to sell a resource
     *
     * @param pn         the number of the player
     * @param rsrcType   the type of resource
     */
    @Override
    public void markAsSelling(int pn, int rsrcType) {
        super.markAsSelling(pn, rsrcType);

        addIsSellingChunk(pn, rsrcType, true);
    }

    /**
     * reset the isSellingResource array so that
     * if the player has the resource, then he is selling it
     */
    @Override
    protected void resetIsSelling() {
        super.resetIsSelling();
        
        D.ebugPrintlnINFO("*** resetIsSelling (true for every resource the player has) ***");

//        //set the selling slot for existing is-selling chuks to true
//        if (brain.isRobotType(StacRobotType.USE_ACT_R_DECLARATIVE_MEMORY)) {
//            //redirect stdout
//            PrintStream original = System.out;
//            System.setOut(actrOutput);
//
//            ////get the existing goal chunk for resetting is-selling
//            //Chunk goalChunk = actrDM.get(Symbol.get("reset-is-selling-goal"));
//            //create the goal chunk
//            Chunk goalChunk = new Chunk(Symbol.get("reset-is-selling-goal"), actrModel);
//            goalChunk.set (Symbol.get("isa"), Symbol.get("reset-is-selling"));
//            
//            actrModel.setGoalFocus(goalChunk);
//            System.out.println("********** RUNNING MODEL FOR RESET IS-SELLING **********");
//            actrModel.outputWhyNot();
//            actrModel.run(false);
//            if (brain.isRobotType(StacRobotType.LOG_ACT_R_OUTPUT)) {
//                //System.out.println("*** ACT-R DM (" + this.brain.getPlayerData().getName() + ") ***\n" + 
//                System.out.println("*** ACT-R DM ***\n" + 
//                        actrModel.getDeclarative().toStringWithActivation());
//                System.out.println("*** BUFFERS ***\n");
//                actrModel.outputBuffers();
//                //System.err.println(actrModel.toString());
//            }
//            System.out.println("********** FINISHED RUNNING MODEL FOR RESET IS-SELLING **********");
//
//            //reinstate stdout
//            System.setOut(original);
//        } 
        }
//     }
    
    /**
     * Retrieve whether somebody is selling the specified resource.
     * 
     * @param playerNumber the number of the player under consideration
     * @param resourceType the type of resource we're interested in
     * @return true if the player is selling the resource
     */
    @Override
    protected boolean isSellingResource(int playerNumber, int resourceType) {
        super.isSellingResource(playerNumber, resourceType);
        
        //redirect stdout
        PrintStream original = System.out;
        System.setOut(actrOutput);

        //get information for retrieval request chunk
        String playerName = brain.getGame().getPlayer(playerNumber).getName();
        String resourceName = resourceNameForResourceType(resourceType); // SOCResourceConstants.resName(resourceType);
        String chunkName = "remember-is-selling-goal"; //"remember-is-selling-" + playerName + "-" + resourceName;

        //get the goal chunk from DM that encodes the goal of retrieving a is-selling chunk
        //Symbol s = Symbol.get("remember-is-selling-goal"); //this Symbol assumes LOWERCASE!
        Chunk goalChunk = new Chunk(Symbol.get(chunkName), actrModel);
        goalChunk.set (Symbol.get("isa"), Symbol.get("remember-is-selling"));
        goalChunk.set (Symbol.get("player"), Symbol.get(playerName));
        goalChunk.set (Symbol.get("resource"), Symbol.get(resourceName));
        
        //set the new goal as goal-focus
        //TODO: seems to be a racing problem: null pointer exception without stopping at a break point before we get here
        actrModel.setGoalFocus(goalChunk);

        //run ACT-R model for this goal
        System.out.println("********** RUNNING MODEL FOR REMEMBER IS-SELLING **********");
        actrModel.outputWhyNot(); //IMPORTANT: THIS HAS SOME SIDE EFFECT; WITHOUT THIS, THE PRODUCTION IS NOT INSTANTIATED!
        actrModel.run(false);
        if (brain.isRobotType(StacRobotType.LOG_ACT_R_OUTPUT)) {
            System.out.println(actrModel.getBuffers().toString());
            System.out.println(actrDM.toString());
            //        System.out.println(actrModel.toString());
        }
        System.out.println("********** FINISHED RUNNING MODEL FOR REMEMBER IS-SELLING **********");
        
        //get the retrieved chunk from the retrieval buffer (we assume that the model stopped in this partiuclar state!)
        Buffers allBuffers = actrModel.getBuffers();
        Chunk retrievedChunk = allBuffers.get(Symbol.retrieval);
        if (retrievedChunk == null) {
            System.out.println("Retrieval of is-selling from ACT-R DM: No chunk retrieved.");
        } else {
            System.out.println("Retrieval of is-selling from ACT-R DM: Chunk: " + retrievedChunk.toString());
        }
  
        //        //DOING A RETRIEVAL DIRECTLY, I.E. OPERATE ON ACT-R DM WITHOUT THE REST OF THE SYSTEM
        //        //create chunk
        //        Chunk requestChunk = new Chunk(Symbol.get(chunkName), actrModel);
        //        requestChunk.set (Symbol.get("isa"), Symbol.get("is-selling"));
        //        requestChunk.set (Symbol.get("player"), Symbol.get(playerName));
        //        requestChunk.set (Symbol.get("resource"), Symbol.get(resourceName));
        //
        //        //perform retrieval
        //        Chunk retrievedChunk = actrModel.declarative.findRetrieval(requestChunk);

        //reinstate stdout
        System.setOut(original);


        if (retrievedChunk == null) {
            D.ebugPrintlnINFO("no is-selling chunk retrieved from ACT-R DM, assuming `true'");
            return true;
        } else {
            Symbol selling = retrievedChunk.getSlotValue(Symbol.get("selling"));
            if (selling == null) {
                D.ebugPrintlnINFO("retrieval of is-selling chunk from ACT-R DM returned chunk without `selling' slot, assuming `true'");
                return true;
            }
            String selString = selling.toString();
            boolean sel = selString.equals("true");
            return sel;
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////
    // OTHER PLAYERS' HANDS
    ///////////////////////////////////////////////////////////////////////////////////
    
    /**
     * Get the resources believed to be held by an opponent
     * @param playerNumber
     * @return
     */
    @Override
    public SOCResourceSet getOpponentResources(int playerNumber) {
        super.getOpponentResources(playerNumber);
        
        D.ebugPrintlnINFO("get opp res general -- " + brain.getPlayerName() + "(" + brain.getPlayerNumber() + ") for player " + playerNumber);
        
        //resource set to return
        SOCResourceSet resSet = new SOCResourceSet();

        for (int i = SOCResourceConstants.CLAY; i <= SOCResourceConstants.UNKNOWN; i++) { //i < SOCResourceConstants.WOOD; i++) {
            int amt = getOpponentResourceCountFromActrDM(playerNumber, i);
            resSet.setAmount(amt, i);
        }

        //return the resource set
        return resSet;
    }

    /**
     * Retrieve an opponent's resource amount from the ACT-R DM.
     * @param playerNumber
     * @param resourceType
     * @return the amount of the resource the opponent is believed or has been observed to have
     */
        public int getOpponentResourceCountFromActrDM(int playerNumber, int resourceType) {
        //TODO: this seems to be a debuggin hack?
        return super.getOpponentResourcesObserved(playerNumber).getAmount(resourceType);
        
//        //redirect stdout
//        PrintStream original = System.out;
//        System.setOut(actrOutput);
//
//        //collect information for chunk
//        String playerName = game.getPlayer(playerNumber).getName();
//        String resourceName = resourceNameForResourceType(resourceType);
//
//        //create the goal chunk
//        Chunk goalChunk = new Chunk(Symbol.get("remember-player-resource-goal"), actrModel);
//        goalChunk.set (Symbol.get("isa"), Symbol.get("remember-player-resource"));
//        goalChunk.set (Symbol.get("player"), Symbol.get(playerName));
//        goalChunk.set (Symbol.get("resource"), Symbol.get(resourceName));
//                
//        //retrieve the amount for the resourceName form the ACT-R DM
//        //set the goal focus to the new goal chunk and run the model
//        //System.out.println("Chunk: " + goalChunk.toString());
//        actrModel.setGoalFocus(goalChunk);
//        System.out.println("********** RUNNING MODEL FOR REMEMBER PLAYER RESOURCE **********");
//        actrModel.outputWhyNot();
//        actrModel.run(false);
//        if (brain.isRobotType(StacRobotType.LOG_ACT_R_OUTPUT)) {
//            System.out.println("*** ACT-R DM (" + this.brain.getPlayerData().getName() + ") ***\n" 
//                + actrModel.getDeclarative().toString("player-resource")); //.toStringWithActivation());//
//            System.out.println("*** BUFFERS (" + this.brain.getPlayerData().getName() + ") ***\n");
//            actrModel.outputBuffers();
//        }
//        System.out.println("********** FINISHED RUNNING MODEL FOR REMEMBER PLAYER RESOURCE **********");
//
//        //get the retrieved chunk from the retrieval buffer (we assume that the model stopped in this partiuclar state!)
//        //and set the value of the resource to return
//        int amount; //the amount to be returned
//        Buffers allBuffers = actrModel.getBuffers();
//        Chunk retrievedChunk = allBuffers.get(Symbol.retrieval);
//        if (retrievedChunk == null) {
//            System.out.println("Retrieval of opponent resource count from ACT-R DM: No chunk retrieved.");
//            amount = 0;
//        } else {
//            System.out.println("Retrieval of opponent resource count from ACT-R DM: Chunk: " + retrievedChunk.toString());
//            Symbol amountSym = retrievedChunk.getSlotValue(Symbol.get("amount"));
//            if (amountSym == null) {
//                D.ebugPrintln("retrieval of player-resource chunk from ACT-R DM returned chunk without `amount' slot, assuming `0'");
//                amount = 0;
//            } else {
//                amount = amountSym.toInt();
//            }
//        }
//
//        Chunk retrievalState = actrModel.getBuffers().get(Symbol.retrievalState);
//        //System.err.println("retrieval state: " + retrievalState.toString());
//        if (retrievalState.get(Symbol.state) == Symbol.error) {
//            unsuccessfulMemoryRetrievals++;
//        } else {
//            successfulMemoryRetrievals++;
//        }
//        
//        //reinstate stdout
//        System.setOut(original);
//        
//        return amount;
    }
    
//    /**
//     * Get the resources the opponent is believed to have without considering trust.
//     * @param playerNumber
//     * @return 
//     */
//    //TODO: there should be an ACT-R version of this if we decide to implement a model of trust
//    public SOCResourceSet getOpponentResourcesBelieved(int playerNumber) {
//        D.ebugPrintln("get opp res believed -- " + brain.getPlayerName() + "for player " + playerNumber);
//        return playerResourcesBelieved[playerNumber];
//    }
//
//    /**
//     * Get the resources the opponent is observed to have without considering trust.
//     * @param playerNumber
//     * @return 
//     */
//    //TODO: there should be an ACT-R version of this if we decide to implement a model of trust
//    public SOCResourceSet getOpponentResourcesObserved(int playerNumber) {
//        D.ebugPrintln("get opp res observed -- " + brain.getPlayerName() + "for player " + playerNumber);
//        return playerResourcesObserved[playerNumber];
//    }
//    
//    /**
//     * Get the total number of resources an opponent holds.
//     * This is observable information, so we don't have to use the ACT-R DM
//     * @param playerNumber
//     * @return the number of resources held by the player
//     */
//    //TODO: there should be an ACT-R version of this if we decide to implement a model of trust
//    public int getOpponentResourcesTotal(int playerNumber) {
//        SOCPlayer player = game.getPlayer(playerNumber);
//        return player.getResources().getTotal();
//    }
    
    /**
     * Add resources to an opponent's hand.  This is called when resources are observed, so should affect both the observed and believed
     * @param playerNumber  affected player
     * @param addType       resource type to be modified
     * @param addAmt        the new amount
     */
    @Override
    public void addOpponentResourcesObserved(int playerNumber, int addType, int addAmt) {
        super.addOpponentResourcesObserved(playerNumber, addType, addAmt);
        
        addOpponentResourcesObservedInActrDM(playerNumber, addType, addAmt);
    }

    /**
     * Add resources to an opponent's hand in the ACT-R DM.  This is called when resources are observed, so should affect both the observed and believed
     * @param playerNumber  affected player
     * @param addType       resource type to be modified
     * @param addAmt        the new amount
     */
    public void addOpponentResourcesObservedInActrDM(int playerNumber, int addType, int addAmt) {
        //redirect stdout
        PrintStream original = System.out;
        System.setOut(actrOutput);

        //collect information for chunk
        String playerName = brain.getGame().getPlayer(playerNumber).getName();
        String resourceName = resourceNameForResourceType(addType); //SOCResourceConstants.resName(addType);

        Chunk goalChunk = new Chunk(Symbol.get("add-player-resource-goal"), actrModel);
        goalChunk.set (Symbol.get("isa"), Symbol.get("add-player-resource"));
        goalChunk.set (Symbol.get("player"), Symbol.get(playerName));
        goalChunk.set (Symbol.get("resource"), Symbol.get(resourceName));
        goalChunk.set (Symbol.get("amount"), Symbol.get(addAmt));
//        goalChunk.set (Symbol.get("evidence"), Symbol.get("observed"));

//            actrModel.runCommand("(add-dm (" + playerName + "-" + resourceName + " isa player-resource player " + playerName + " resource " + resourceName + " amount " + addAmt + " evidence observed))");
        //also produces: (original_3-null isa player-resource player original_3 evidence observed resource null amount 1) 0.0

        //add to ACT-R DM
        //set the goal focus to the new goal chunk and run the model
        //System.out.println("Should add player-resource chunk for: " + playerName + " " + resourceName + " " + addAmt + " observed");
        //System.out.println("Chunk: " + goalChunk.toString());
        actrModel.setGoalFocus(goalChunk);
        System.out.println("********** RUNNING MODEL FOR ADD PLAYER RESOURCE **********");
        actrModel.outputWhyNot();
        actrModel.run(false);
        if (brain.isRobotType(StacRobotType.LOG_ACT_R_OUTPUT)) {
            System.out.println("*** ACT-R DM (" + this.brain.getPlayerData().getName() + ") ***\n" 
                    + actrModel.getDeclarative().toStringWithActivation()); //.toString("player-resource"));
                    //+ actrModel.getDeclarative().toString("is-selling"));
            System.out.println("*** BUFFERS (" + this.brain.getPlayerData().getName() + ") ***\n");
            actrModel.outputBuffers();
        }
        System.out.println("********** FINISHED RUNNING MODEL FOR ADD PLAYER RESOURCE **********");

        //reinstate stdout
        System.setOut(original);
    }
    
    /**
     * Subtract opponent resources in ACT-R DM.
     * Subtract resources from an opponent - these are observed subtractions, so should come from both believed and observed.
     *  If this yields an inconsistency in believed, we've been deceived - reset believed, and stop trusting that opponent.
     * @param playerNumber
     * @param subType
     * @param subAmt 
     */
    @Override
    public void subtractOpponentResources(int playerNumber, int subType, int subAmt) {
        super.subtractOpponentResources(playerNumber, subType, subAmt);
        
        //collect information for chunk
        String playerName = brain.getGame().getPlayer(playerNumber).getName();
        String resourceName = resourceNameForResourceType(subType); //SOCResourceConstants.resName(subType);

        Chunk goalChunk = new Chunk(Symbol.get("subtract-player-resource-goal"), actrModel);
        goalChunk.set (Symbol.get("isa"), Symbol.get("subtract-player-resource"));
        goalChunk.set (Symbol.get("player"), Symbol.get(playerName));
        if (subType != SOCResourceConstants.UNKNOWN) {
            goalChunk.set (Symbol.get("resource"), Symbol.get(resourceName));
            goalChunk.set (Symbol.get("amount"), Symbol.get(subAmt));
//            goalChunk.set (Symbol.get("evidence"), Symbol.get("observed"));
            executeSubtractOpponentResourcesInActrDM(goalChunk);
        } else {
            int amountToAddToUnknown = 0;
            for (int i = SOCResourceConstants.CLAY; i <= SOCResourceConstants.WOOD; i++) {
                int curAmt = getOpponentResourceCountFromActrDM(playerNumber, i);
                int lost = Math.min(curAmt, subAmt);
                amountToAddToUnknown += lost;
//                amount = lost;
//                rs.subtract(lost, i);
                goalChunk.set (Symbol.get("resource"), Symbol.get(resourceName));
                goalChunk.set (Symbol.get("amount"), Symbol.get(subAmt));
                executeSubtractOpponentResourcesInActrDM(goalChunk);
//                rs.add(lost, SOCResourceConstants.UNKNOWN);
            }
//            rs.subtract(subAmt, subType);
            amountToAddToUnknown -= subAmt;
            if (amountToAddToUnknown < 0) {
                amountToAddToUnknown = 0;
            }
            addOpponentResourcesObservedInActrDM(playerNumber, SOCResourceConstants.UNKNOWN, amountToAddToUnknown);
        }
    }

    private void executeSubtractOpponentResourcesInActrDM(Chunk goalChunk) {
        //redirect stdout
        PrintStream original = System.out;
        System.setOut(actrOutput);
        
        //subtract from resource info in ACT-R DM
        //set the goal focus to the new goal chunk and run the model
        //System.out.println("Should subtract player-resource chunk for: " + playerName + " " + resourceName + " " + subAmt);
        //System.out.println("Chunk: " + goalChunk.toString());
        actrModel.setGoalFocus(goalChunk);
        System.out.println("********** RUNNING MODEL FOR SUBTRACT PLAYER RESOURCE **********");
        actrModel.outputWhyNot();
        actrModel.run(false);
        if (brain.isRobotType(StacRobotType.LOG_ACT_R_OUTPUT)) {
            System.out.println("*** ACT-R DM (" + this.brain.getPlayerData().getName() + ") ***\n" 
                    + actrModel.getDeclarative().toStringWithActivation()); //.toString("player-resource"));
            System.out.println("*** BUFFERS (" + this.brain.getPlayerData().getName() + ") ***\n");
            actrModel.outputBuffers();
        }
        System.out.println("********** FINISHED RUNNING MODEL FOR SUBTRACT PLAYER RESOURCE **********");

        //reinstate stdout
        System.setOut(original);    
    }

    /**
     * Reconstruct the name of the simulation from the game name.
     */
    private String simulationName() {
        String gameName = brain.getGame().getName();
        int separatorIndex = gameName.lastIndexOf("_");
        String simulationName = gameName.substring(0, separatorIndex); //reconstruct the simulation name from the game name
        return simulationName;
    }
    
    /**
     * Print statistics about the lifetime of this declarative memory.
     * NOTE: This is very fragile: it assumes a certain convention for game names as well as the existence of directory "results/" relative to the current directory.
     * @author Markus Guhe
     */
    @Override
    protected void printStats() {
        if (successfulMemoryRetrievals > 0 || unsuccessfulMemoryRetrievals > 0) {
            String logfileName = "results/" + simulationName() + ".txt";
            String outputString = successfulMemoryRetrievals + "\t" + 
                    unsuccessfulMemoryRetrievals + "\t" + 
                    (double)successfulMemoryRetrievals / ((double)successfulMemoryRetrievals + (double)unsuccessfulMemoryRetrievals) + "\n";
            
            File f = new File(logfileName);
            if (!f.isFile()) {
                try {
                    FileWriter fw = new FileWriter(f);
                    fw.write(outputString);
                    fw.flush();
                } catch (IOException ex) {
                    Logger.getLogger(StacRobotDeclarativeMemoryACTR.class.getName()).log(Level.SEVERE, null, ex);
                }
            } else {
                try {
                    FileWriter fw = new FileWriter(f,true);
                    fw.write(outputString);
                    fw.flush();
                } catch (IOException ex) {
                    Logger.getLogger(StacRobotDeclarativeMemoryACTR.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
             
            
//            System.err.println("Memory retrieval success: " + 
//);
        }
    }
}
