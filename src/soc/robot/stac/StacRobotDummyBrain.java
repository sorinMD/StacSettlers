package soc.robot.stac;

import java.util.HashMap;
import java.util.Iterator;

import soc.game.SOCCity;
import soc.game.SOCGame;
import soc.game.SOCPlayer;
import soc.game.SOCPlayingPiece;
import soc.game.SOCRoad;
import soc.game.SOCSettlement;
import soc.message.SOCPutPiece;
import soc.robot.SOCBuildPlanStack;
import soc.robot.SOCBuildingSpeedEstimate;
import soc.robot.SOCBuildingSpeedFast;
import soc.robot.SOCBuildingSpeedFastFractional;
import soc.robot.SOCBuildingSpeedProbabilistic;
import soc.robot.SOCPlayerTracker;
import soc.robot.SOCRobotBrain;
import soc.robot.SOCRobotBrainImpl;
import soc.robot.SOCRobotClient;
import soc.robot.SOCRobotNegotiator;
import soc.robot.SOCRobotNegotiatorImpl;
import soc.robot.StacRobotBrainInfo;
import soc.util.CappedQueue;
import soc.util.SOCRobotParameters;

/**
 * Dummy brain implementation for calculating EGSR in the replay client. Also for storing the game and player data/trackers during manual simulations.
 * @author MD
 *
 */
public class StacRobotDummyBrain extends SOCRobotBrainImpl{
	public int ourPlayerNum;
	public StacRobotDummyBrain(SOCRobotClient rc, SOCRobotParameters params,
			SOCGame ga, CappedQueue mq, int ourPN) {
		super(rc, params, ga, mq);
		// default constructor// do we need all this info??
		//the only thing that we would need is to create the trackers
		ourPlayerNum = ourPN; //we need to have a player number due to the way the game logic handles update trackers
		playerTrackers = new HashMap<Integer, SOCPlayerTracker>();
		//we need our player data/tracker here
		ourPlayerData = ga.getPlayer(ourPlayerNum);
        ourPlayerTracker = new SOCPlayerTracker(game.getPlayer(ourPlayerNum), this);
		for (int pn = 0; pn < game.maxPlayers; pn++)
	    {
	        if (! game.isSeatVacant(pn))
	        {
	            SOCPlayerTracker tracker = new SOCPlayerTracker(game.getPlayer(pn), this);
	            playerTrackers.put(Integer.valueOf(pn), tracker);
	        }
	    }
	}

	/**
	 * Same as the method in parent, but without handling states or special cases.
	 * @param mes
	 */
	public void handlePUTPIECE_updateTrackers(SOCPutPiece mes)
    {
        final int pn = mes.getPlayerNumber();
        final int coord = mes.getCoordinates();
        final int pieceType = mes.getPieceType();

        switch (pieceType)
        {
        case SOCPlayingPiece.ROAD:

            SOCRoad newRoad = new SOCRoad(game.getPlayer(pn), coord, null);
            trackNewRoad(newRoad, false);

            break;

        case SOCPlayingPiece.SETTLEMENT:

            SOCPlayer newSettlementPl = game.getPlayer(pn);
            SOCSettlement newSettlement = new SOCSettlement(newSettlementPl, coord, null);
            trackNewSettlement(newSettlement, false);

            break;

        case SOCPlayingPiece.CITY:

            SOCCity newCity = new SOCCity(game.getPlayer(pn), coord, null);
            trackNewCity(newCity, false);

            break;
        }
	
    }
    
	/**
	 * Replaces the game object and updates all the references to it
	 * @param game
	 */
	public void setGame(SOCGame game) {
		this.game = game;
        Iterator trackersIter = playerTrackers.values().iterator();
        //we need to update the player object in the trackers as we are not modifying the game object in this class, but only replacing it
        while (trackersIter.hasNext())
        {
            SOCPlayerTracker tracker = (SOCPlayerTracker) trackersIter.next();
            tracker.setPlayer(game.getPlayer(tracker.getPlayer().getPlayerNumber()));
        }
        ourPlayerData = game.getPlayer(ourPlayerNum);
	}
	
	@Override
    public SOCBuildingSpeedEstimate getEstimator() {
		return new SOCBuildingSpeedFast();//always fast
    }

	/**
	 * 
	 * @param data our SOCPlayer object
	 */
	public void setOurPlayerData(SOCPlayer data){
		ourPlayerData = data;
	}
	
	/**
	 * 
	 * @param tracker our SOCPlayerTracker object
	 */
	public void setOurPlayerTracker(SOCPlayerTracker tracker){
		ourPlayerTracker = tracker;
	}
	
	/**
	 * 
	 * @param playerTrackers all SOCPlayerTrackers objects in a hashmap linking them to the player numbers of each player
	 */
    public void setPlayerTrackers(HashMap<Integer, SOCPlayerTracker> playerTrackers) {
		this.playerTrackers = playerTrackers;
	}
	
}
