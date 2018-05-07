package soc.robot.stac;


import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Random;
import java.util.Vector;

import originalsmartsettlers.DebugFrame;
import originalsmartsettlers.boardlayout.BoardLayout;
import originalsmartsettlers.boardlayout.GameStateConstants;
import originalsmartsettlers.player.Player;
import soc.disableDebug.D;
import soc.game.SOCBoard;
import soc.game.SOCCity;
import soc.game.SOCDevCardConstants;
import soc.game.SOCDevCardSet;
import soc.game.SOCGame;
import soc.game.SOCPlayer;
import soc.game.SOCPlayingPiece;
import soc.game.SOCResourceConstants;
import soc.game.SOCResourceSet;
import soc.game.SOCRoad;
import soc.game.SOCSettlement;
import soc.game.SOCTradeOffer;
import soc.message.SOCRobotFlag;
import soc.robot.SOCPossibleCard;
import soc.robot.SOCPossibleCity;
import soc.robot.SOCPossiblePiece;
import soc.robot.SOCPossibleRoad;
import soc.robot.SOCPossibleSettlement;
import soc.robot.SOCRobotBrain;
import soc.robot.SOCRobotClient;
import soc.robot.SOCRobotFactory;
import soc.util.CappedQueue;
import soc.util.SOCRobotParameters;

public class OriginalSSRobotBrain extends StacRobotBrain implements GameStateConstants{
	
	/**
	 * The board layout in SmartSettlers format. Also contains all the utilities for performing simulations etc
	 */
    public BoardLayout bl;
    //NOTE: this is only used for debugging and it doesn't affect performance. Removed to be able to perform simulations on the cluster
//    /**
//     * The object that initialises and contains the above boardLayout object
//     */
//    private DebugFrame debugframe;
    /**
     * location of the last placed settlement, required when sending the state over to SS
     */
    public int lastSettlement;
    /**
     * used in planning from whom to steal
     */
	int robberVictim = -1;
    /**
     * used in planning where to put our first settlement
     */
    protected int firstSettlement = -1;
    /**
     * used in planning where to put our second settlement
     */
    protected int secondSettlement = -1;

   
	
	public OriginalSSRobotBrain(SOCRobotClient rc, SOCRobotParameters params,
            SOCGame ga, CappedQueue mq, boolean fullPlan, StacRobotType robotType, BoardLayout bl, HashMap<String,ArrayList<String>> tradePreferences) {
        super(rc, params, ga, mq, fullPlan, robotType, tradePreferences);
//        debugframe = new DebugFrame();
//        debugframe.setVisible(true);  //uncomment for visual debugging
        this.bl = bl;
    }
		
	////////////////////////////SOC MODIFIED METHODS/////////////////////
	
	@Override
	public void kill() {
		super.kill();
//		debugframe.dispose();
//		debugframe = null;
		bl = null;
	}
	
//	@Override
//	protected void handleDICERESULT(SOCDiceResult mes) {
//		super.handleDICERESULT(mes);
//		if(mes.getResult()==7){
//			//reset the fields required for the robbing action
//			robberHex = -1;
//			robberVictim =-1;
//		}
//	}
	
	protected void rollOrPlayKnightOrExpectDice()
    {
        expectPLAY = false;
        if ((!waitingForOurTurn) && (ourTurn))
        {
            if (!expectPLAY1 && !expectDISCARD && !expectPLACING_ROBBER && !(expectDICERESULT && (counter < 4000)))
            {
                getActionForPLAY();
            }
        }
        else
        {
            /**
             * not our turn
             */
            expectDICERESULT = true;
        }
    }
	
	
	   /**
     * If it's our turn and we have an expect flag set
     * (such as {@link #expectPLACING_SETTLEMENT}), then
     * call {@link SOCRobotClient#putPiece(SOCGame, SOCPlayingPiece) client.putPiece}.
     *<P>
     * Looks for one of these game states:
     *<UL>
     * <LI> {@link SOCGame#START1A} - {@link SOCGame#START2B}
     * <LI> {@link SOCGame#PLACING_SETTLEMENT}
     * <LI> {@link SOCGame#PLACING_ROAD}
     * <LI> {@link SOCGame#PLACING_CITY}
     * <LI> {@link SOCGame#PLACING_FREE_ROAD1}
     * <LI> {@link SOCGame#PLACING_FREE_ROAD2}
     *</UL>
     * @since 1.1.09
     */
    protected void placeIfExpectPlacing()
    {
        if (waitingForGameState)
            return;

        switch (game.getGameState())
        {
            case SOCGame.PLACING_SETTLEMENT:
            {
                if ((ourTurn) && (!waitingForOurTurn) && (expectPLACING_SETTLEMENT))
                {
                    expectPLACING_SETTLEMENT = false;
                    waitingForGameState = true;
                    counter = 0;
                    expectPLAY1 = true;
    
                    D.ebugPrintlnINFO("!!! PUTTING PIECE "+whatWeWantToBuild+" !!!");
                    pause(1);
                    client.putPiece(game, whatWeWantToBuild);
                    pause(2);
                }
            }
            break;

            case SOCGame.PLACING_ROAD:
            {
                if ((ourTurn) && (!waitingForOurTurn) && (expectPLACING_ROAD))
                {
                    expectPLACING_ROAD = false;
                    waitingForGameState = true;
                    counter = 0;
                    expectPLAY1 = true;

                    pause(1);
                    client.putPiece(game, whatWeWantToBuild);
                    pause(2);
                }
            }
            break;

            case SOCGame.PLACING_CITY:
            {
                if ((ourTurn) && (!waitingForOurTurn) && (expectPLACING_CITY))
                {
                    expectPLACING_CITY = false;
                    waitingForGameState = true;
                    counter = 0;
                    expectPLAY1 = true;

                    pause(1);
                    client.putPiece(game, whatWeWantToBuild);
                    pause(2);
                }
            }
            break;

            case SOCGame.PLACING_FREE_ROAD1:
            {
                if ((ourTurn) && (!waitingForOurTurn) && (expectPLACING_FREE_ROAD1))
                {
                    expectPLACING_FREE_ROAD1 = false;
                    waitingForGameState = true;
                    counter = 0;
                    expectPLACING_FREE_ROAD2 = true;
                    getActionForFREEROAD();
                }
            }
            break;

            case SOCGame.PLACING_FREE_ROAD2:
            {
                if ((ourTurn) && (!waitingForOurTurn) && (expectPLACING_FREE_ROAD2))
                {
                    expectPLACING_FREE_ROAD2 = false;
                    waitingForGameState = true;
                    counter = 0;
                    expectPLAY1 = true;
                    getActionForFREEROAD();
                }
            }
            break;
            
            //same as with placing robber: do the action first and then change states for the following cases
            case SOCGame.START1A:
            {	
                if ((!waitingForOurTurn) && (ourTurn) && (!(expectPUTPIECE_FROM_START1A && (counter < 4000))))
                {
                    placeFirstSettlement();
                    expectPUTPIECE_FROM_START1A = true;
                    waitingForGameState = true;
                    counter = 0;
                }
                expectSTART1A = false;
            }
            break;

            case SOCGame.START1B:
            {
    
                if ((!waitingForOurTurn) && (ourTurn) && (!(expectPUTPIECE_FROM_START1B && (counter < 4000))))
                {
                    placeInitRoad();
                    expectPUTPIECE_FROM_START1B = true;
                    counter = 0;
                    waitingForGameState = true;
                    pause(3);
                }
                expectSTART1B = false;
            }
            break;

            case SOCGame.START2A:
            {
                if ((!waitingForOurTurn) && (ourTurn) && (!(expectPUTPIECE_FROM_START2A && (counter < 4000))))
                {
                    placeSecondSettlement();
                    expectPUTPIECE_FROM_START2A = true;
                    counter = 0;
                    waitingForGameState = true;
                }
                expectSTART2A = false;
            }
            break;

            case SOCGame.START2B:
            {
                if ((!waitingForOurTurn) && (ourTurn) && (!(expectPUTPIECE_FROM_START2B && (counter < 4000))))
                {
                	placeInitRoad();
                    expectPUTPIECE_FROM_START2B = true;
                    counter = 0;
                    waitingForGameState = true;
                    pause(3);
                }
                expectSTART2B = false;
            }
            break;

        }
    }
	
	
    /**
     * plan and place first settlement
     */
    @Override
    protected void placeFirstSettlement()
    {
    	if(firstSettlement !=-1){
    		System.err.println("Robot " + getPlayerNumber() + " asked to place first settlement twice");
    		client.putPiece(game, new SOCSettlement(ourPlayerData, firstSettlement, null));
    		return; 
    	}
    	//fool the server into believing we are a human player so we won't get interrupted by the force end turn thread
    	client.put(SOCRobotFlag.toCmd(getGame().getName(), false, getPlayerNumber()));
    	
        sendStateToSmartSettlers(S_SETTLEMENT1);
        Player p = bl.player[getPlayerNumber()];
        bl.possibilities.Clear();
        p.listInitSettlementPossibilities(bl.state);
        p.selectAction(bl.state, bl.action);
        p.performAction(bl.state, bl.action);
        String s = String.format("Performing action: [%d %d %d %d %d]", bl.action[0], bl.action[1], bl.action[2], bl.action[3], bl.action[4]);
        D.ebugPrintlnINFO(s);        
        firstSettlement = translateVertexToJSettlers(bl.action[1]);

        D.ebugPrintlnINFO("BUILD REQUEST FOR FIRST SETTLEMENT AT "+Integer.toHexString(firstSettlement));
        pause(2);
        client.putPiece(game, new SOCSettlement(ourPlayerData, firstSettlement, null));
        pause(1);
        
        client.put(SOCRobotFlag.toCmd(getGame().getName(), true, getPlayerNumber()));//we are a robot again   
    }
	
    /**
     * place planned second settlement
     */
    @Override
    protected void placeSecondSettlement()
    {
    	if(secondSettlement !=-1){
    		System.err.println("Robot " + getPlayerNumber() + " asked to place second settlement twice");;
    		client.putPiece(game, new SOCSettlement(ourPlayerData, secondSettlement, null));
    		return; 
    	}
    	//fool the server into believing we are a human player so we won't get interrupted by the force end turn thread
    	client.put(SOCRobotFlag.toCmd(getGame().getName(), false, getPlayerNumber()));
        sendStateToSmartSettlers(S_SETTLEMENT2);
        Player p = bl.player[getPlayerNumber()];
        bl.possibilities.Clear();
        p.listInitSettlementPossibilities(bl.state);
        p.selectAction(bl.state, bl.action);
        p.performAction(bl.state, bl.action);
        String s = String.format("Performing action: [%d %d %d %d %d]", bl.action[0], bl.action[1], bl.action[2], bl.action[3], bl.action[4]);
        D.ebugPrintlnINFO(s);        
        secondSettlement = translateVertexToJSettlers(bl.action[1]);
 
        D.ebugPrintlnINFO("BUILD REQUEST FOR SECOND SETTLEMENT AT "+Integer.toHexString(secondSettlement));
        pause(2);
        client.putPiece(game, new SOCSettlement(ourPlayerData, secondSettlement, null));
        pause(1);
        
        client.put(SOCRobotFlag.toCmd(getGame().getName(), true, getPlayerNumber()));//we are a robot again   
    }
    
    
    /**
     * place a road attached to the last initial settlement
     */
    @Override
    public void placeInitRoad()
    {
        sendStateToSmartSettlers(S_ROAD1); // does not matter if ROAD1 or ROAD2
        Player p = bl.player[game.getCurrentPlayerNumber()];
        bl.possibilities.Clear();
        p.listInitRoadPossibilities(bl.state);
        p.selectAction(bl.state, bl.action);
        p.performAction(bl.state, bl.action);
        
        String s = String.format("Performing action: [%d %d %d %d %d]", bl.action[0], bl.action[1], bl.action[2], bl.action[3], bl.action[4]);
        D.ebugPrintlnINFO(s);         
        int roadEdge = translateEdgeToJSettlers(bl.action[1]);

        D.ebugPrintlnINFO("!!! PUTTING INIT ROAD !!!");
        pause(2);
        D.ebugPrintlnINFO("Trying to build first road at "+Integer.toHexString(roadEdge));
        client.putPiece(game, new SOCRoad(ourPlayerData, roadEdge, null));
        pause(1);

        //dummy.destroyPlayer();
    }
    
    
    @Override
    protected void moveRobber()
    {
    	//fool the server into believing we are a human player so we won't get interrupted by the force end turn thread
    	client.put(SOCRobotFlag.toCmd(getGame().getName(), false, getPlayerNumber()));
        pause(1);
        
    	boolean unhandled = false;
    	try {
    		sendStateToSmartSettlers(S_ROBBERAT7); 
        	Player p = bl.player[getPlayerNumber()];
        	bl.possibilities.Clear();
        	p.listRobberPossibilities(bl.state,A_PLACEROBBER);
        	p.selectAction(bl.state, bl.action);
        	p.performAction(bl.state, bl.action);
		} catch (Exception e) {
			System.err.println("Unhandled exception");
			unhandled = true;
		}
         
    	//any exceptions thrown or illegal nothing action result in a random move instead
    	if(unhandled){
    		sendStateToSmartSettlers(S_ROBBERAT7); 
        	Player p = bl.player[getPlayerNumber()];
        	bl.possibilities.Clear();
        	p.listRobberPossibilities(bl.state,A_PLACEROBBER);
        	Random r = new Random();
        	int aind = r.nextInt(bl.action.length);
            for (int i=0; i<bl.action.length; i++)
                bl.action[i] = bl.possibilities.action[aind][i];
            p.performAction(bl.state, bl.action);
    	}
        if(bl.action[0]==A_NOTHING){
        	//illegal action do a random one instead
    		sendStateToSmartSettlers(S_ROBBERAT7); 
        	Player p = bl.player[getPlayerNumber()];
        	bl.possibilities.Clear();
        	p.listRobberPossibilities(bl.state,A_PLACEROBBER);
        	Random r = new Random();
        	int aind = r.nextInt(bl.action.length);
            for (int i=0; i<bl.action.length; i++)
                bl.action[i] = bl.possibilities.action[aind][i];
            p.performAction(bl.state, bl.action);
        }
        
        String s = String.format("Performing action: [%d %d %d %d %d]", bl.action[0], bl.action[1], bl.action[2], bl.action[3], bl.action[4]);
        D.ebugPrintlnINFO(s);  
        
        int robberHex = translateHexToJSettlers(bl.action[1]);
        robberVictim = bl.action[2];
        D.ebugPrintlnINFO("!!! MOVING ROBBER !!!");
        client.moveRobber(game, ourPlayerData, robberHex);
        int xn = (int) bl.hextiles[bl.action[1]].pos.x;
        int yn = (int) bl.hextiles[bl.action[1]].pos.y;
        
        D.ebugPrintlnINFO("MOVE robber to hex " + robberHex +"( hex " + bl.action[1] + ", coord: " + xn + "," + yn + "), steal from" + robberVictim);
        pause(2);
        
        client.put(SOCRobotFlag.toCmd(getGame().getName(), true, getPlayerNumber()));//we are a robot again 
    }
    
    @Override
    protected void chooseRobberVictim(boolean[] choices)
    {
    
    	pause(1);
        client.choosePlayer(game, robberVictim);
        pause(1);
        
    }
    
    protected void getActionForPLAY1()    
    {
    	//fool the server into believing we are a human player so we won't get interrupted by the force end turn thread
    	client.put(SOCRobotFlag.toCmd(getGame().getName(), false, getPlayerNumber()));
    	boolean unhandled = false;
    	try {
	    	sendStateToSmartSettlers(S_NORMAL); 
	        printCardState();
	        int pn = getPlayerNumber();
	        Player p = bl.player[getPlayerNumber()];
	        bl.possibilities.Clear();
	        p.listNormalPossibilities(bl.state);
	        p.selectAction(bl.state, bl.action);
	        p.performAction(bl.state, bl.action);
		} catch (Exception e) {
			System.err.println("Unhandled exception");
			unhandled = true;
		}
        
    	//any exceptions thrown or illegal nothing action result in a random move instead
    	if(unhandled){
    		sendStateToSmartSettlers(S_NORMAL);  
        	Player p = bl.player[getPlayerNumber()];
        	bl.possibilities.Clear();
        	p.listNormalPossibilities(bl.state);
        	Random r = new Random();
        	int aind = r.nextInt(bl.action.length);
            for (int i=0; i<bl.action.length; i++)
                bl.action[i] = bl.possibilities.action[aind][i];
            p.performAction(bl.state, bl.action);
    	}
        if(bl.action[0]==A_NOTHING){
        	//illegal action do a random one instead
    		sendStateToSmartSettlers(S_NORMAL);  
        	Player p = bl.player[getPlayerNumber()];
        	bl.possibilities.Clear();
        	p.listNormalPossibilities(bl.state);
        	Random r = new Random();
        	int aind = r.nextInt(bl.action.length);
            for (int i=0; i<bl.action.length; i++)
                bl.action[i] = bl.possibilities.action[aind][i];
            p.performAction(bl.state, bl.action);
        }
        
        int coord;
        SOCPossiblePiece targetPiece;
        switch (bl.action[0])
        {
            case A_BUILDROAD:
                coord = translateEdgeToJSettlers(bl.action[1]);
                targetPiece = new SOCPossibleRoad(getPlayerData(), coord, new Vector());
                lastMove = targetPiece;

                waitingForGameState = true;
                counter = 0;
                expectPLACING_ROAD = true;
                whatWeWantToBuild = new SOCRoad(getPlayerData(), targetPiece.getCoordinates(), null);
                //check for resources and location
            	D.ebugPrintlnINFO("!!! BUILD REQUEST FOR A ROAD AT " + Integer.toHexString(targetPiece.getCoordinates()) + " !!!");
            	client.buildRequest(game, SOCPlayingPiece.ROAD);
                break;
            case A_BUILDSETTLEMENT:
                coord = translateVertexToJSettlers(bl.action[1]);
                targetPiece = new SOCPossibleSettlement(getPlayerData(), coord, new Vector());
                lastMove = targetPiece;

                waitingForGameState = true;
                counter = 0;
                expectPLACING_SETTLEMENT = true;
                whatWeWantToBuild = new SOCSettlement(getPlayerData(), targetPiece.getCoordinates(), null);
                //check for resources and location
            	D.ebugPrintlnINFO("!!! BUILD REQUEST FOR A SETTLEMENT " + Integer.toHexString(targetPiece.getCoordinates()) + " !!!");
            	client.buildRequest(game, SOCPlayingPiece.SETTLEMENT);
            	D.ebugPrintlnINFO("SETTLEMENT REQUESTED AT: " + Integer.toHexString(targetPiece.getCoordinates()));
                break;
            case A_BUILDCITY:
                coord = translateVertexToJSettlers(bl.action[1]);
                targetPiece = new SOCPossibleCity(this, getPlayerData(), coord);
                lastMove = targetPiece;

                waitingForGameState = true;
                counter = 0;
                expectPLACING_CITY = true;
                whatWeWantToBuild = new SOCCity(getPlayerData(), targetPiece.getCoordinates(), null);
                D.ebugPrintlnINFO("!!! BUILD REQUEST FOR A CITY " + Integer.toHexString(targetPiece.getCoordinates()) + " !!!");
                client.buildRequest(game, SOCPlayingPiece.CITY);
               break;
            case A_BUYCARD:
                
            	targetPiece = new SOCPossibleCard(getPlayerData(), 1);
                lastMove = targetPiece;
                
                
                client.buyDevCard(game);
                printCardState();
                waitingForDevCard = true;
                D.ebugPrintlnINFO("CARD bought ");
                break;
            case A_PLAYCARD_MONOPOLY:
                decisionMaker.monopolyChoice = translateResToJSettlers(bl.action[1]);

                expectWAITING_FOR_MONOPOLY = true;
                waitingForGameState = true;
                counter = 0;
                client.playDevCard(game, SOCDevCardConstants.MONO);
                D.ebugPrintlnINFO("MONOPOLY played on "+bl.action[1] );
                break;
            case A_PLAYCARD_FREERESOURCE:

                int a1 = bl.action[1];
                int a2 = bl.action[2];
                int cl = ((a1==RES_CLAY ) ?1:0)  + ((a2==RES_CLAY ) ?1:0);
                int or = ((a1==RES_STONE) ?1:0)  + ((a2==RES_STONE) ?1:0);
                int sh = ((a1==RES_SHEEP) ?1:0)  + ((a2==RES_SHEEP) ?1:0);
                int wh = ((a1==RES_WHEAT) ?1:0)  + ((a2==RES_WHEAT) ?1:0);
                int wo = ((a1==RES_WOOD ) ?1:0)  + ((a2==RES_WOOD ) ?1:0);
                decisionMaker.resourceChoices = new SOCResourceSet(cl, or, sh, wh, wo, 0);
                //chooseFreeResources(targetResources);
                
                expectWAITING_FOR_DISCOVERY = true;
                waitingForGameState = true;
                counter = 0;
                client.playDevCard(game, SOCDevCardConstants.DISC);
                D.ebugPrintlnINFO("FREE RESOURCE to get" + bl.action[1] + " , " + bl.action[2]);
                break;
            case A_PLAYCARD_FREEROAD:
                waitingForGameState = true;
                counter = 0;
                expectPLACING_FREE_ROAD1 = true;

                D.ebugPrintlnINFO("!! PLAYING ROAD BUILDING CARD");
                client.playDevCard(game, SOCDevCardConstants.ROADS);
                break;
            case A_PLAYCARD_KNIGHT:
                expectPLACING_ROBBER = true;
                waitingForGameState = true;
                counter = 0;
                D.ebugPrintlnINFO("!! PLAYING KNIGHT CARD");
                client.playDevCard(game, SOCDevCardConstants.KNIGHT);
                pause(1);
                
                break;
            case A_PORTTRADE:
                boolean[] to = new boolean[SOCGame.MAXPLAYERS];
                for (int i = 0; i < SOCGame.MAXPLAYERS; i++)
                    to[i] = false;
                SOCResourceSet give = new SOCResourceSet();
                SOCResourceSet get = new SOCResourceSet();
                give.add(bl.action[1], translateResToJSettlers(bl.action[2]));
                get.add(bl.action[3], translateResToJSettlers(bl.action[4]));
                SOCTradeOffer bankTrade = 
                        new SOCTradeOffer(game.getName(), getPlayerData().getPlayerNumber(), to, give, get);
               
                waitingForTradeMsg = true;
                D.ebugPrintlnINFO("!! PORT TRADING");
                client.bankTrade(game, bankTrade.getGiveSet(), bankTrade.getGetSet());
                pause(2);
                break;
            case A_ENDTURN:
                waitingForGameState = true;
                counter = 0;
                expectPLAY = true;
                waitingForOurTurn = true;

                if (robotParameters.getTradeFlag() == 1)
                {
                    doneTrading = false;
                }
                else
                {
                    doneTrading = true;
                }

                D.ebugPrintlnINFO("!!! ENDING TURN !!!");
                negotiator.resetIsSelling();
                negotiator.resetOffersMade();
                buildingPlan.clear();
                negotiator.resetTargetPieces();
                pause(1);
                client.endTurn(game);
                break;
        }
    	client.put(SOCRobotFlag.toCmd(getGame().getName(), true, getPlayerNumber()));//we are a robot again 
    }
	
////////////////////////////SMARTSETTLERS METHODS/////////////////////
    
    public void getActionForPLAY()
    {
    	//fool the server into believing we are a human player so we won't get interrupted by the force end turn thread
    	client.put(SOCRobotFlag.toCmd(getGame().getName(), false, getPlayerNumber()));
        
        boolean unhandled = false;
        try {
    	sendStateToSmartSettlers(S_BEFOREDICE); 
        printCardState();
        Player p = bl.player[getPlayerNumber()];
        bl.possibilities.Clear();
        p.listPossibilities(bl.state);
        p.selectAction(bl.state, bl.action);
        p.performAction(bl.state, bl.action);
		} catch (Exception e) {
			System.err.println("Unhandled exception");
			unhandled = true;
		}
        
    	//any exceptions thrown or illegal nothing action result in a random move instead
    	if(unhandled){
        	sendStateToSmartSettlers(S_BEFOREDICE); 
            Player p = bl.player[getPlayerNumber()];
            bl.possibilities.Clear();
            p.listPossibilities(bl.state);
        	Random r = new Random();
        	int aind = r.nextInt(bl.action.length);
            for (int i=0; i<bl.action.length; i++)
                bl.action[i] = bl.possibilities.action[aind][i];
            p.performAction(bl.state, bl.action);
    	}
        if(bl.action[0]==A_NOTHING){
        	//illegal action do a random one instead
        	sendStateToSmartSettlers(S_BEFOREDICE); 
            Player p = bl.player[getPlayerNumber()];
            bl.possibilities.Clear();
            p.listPossibilities(bl.state);
        	Random r = new Random();
        	int aind = r.nextInt(bl.action.length);
            for (int i=0; i<bl.action.length; i++)
                bl.action[i] = bl.possibilities.action[aind][i];
            p.performAction(bl.state, bl.action);
        }
        
        String s = String.format("Performing action: [%d %d %d %d %d]", bl.action[0], bl.action[1], bl.action[2], bl.action[3], bl.action[4]);
        D.ebugPrintlnINFO(s);        
        switch (bl.action[0])
        {
            // !!! TODO: to permit these, have to remember last state (PLAY or PLAY1?)
            // !!! oldState is not enough: for free roads, have to revert to a stae 2 steps back
//            case A_PLAYCARD_MONOPOLY:
//                monopolyChoice = rc.translateResToJSettlers(rc.bl.action[1]);
//
//                expectWAITING_FOR_MONOPOLY = true;
//                waitingForGameState = true;
//                counter = 0;
//                client.playDevCard(game, SOCDevCardConstants.MONO);
//                System.out.println("MONOPOLY played on "+rc.bl.action[1] );
//                break;
//            case A_PLAYCARD_FREERESOURCE:
//
//                int a1 = rc.bl.action[1];
//                int a2 = rc.bl.action[2];
//                int cl = ((a1==RES_CLAY ) ?1:0)  + ((a2==RES_CLAY ) ?1:0);
//                int or = ((a1==RES_STONE) ?1:0)  + ((a2==RES_STONE) ?1:0);
//                int sh = ((a1==RES_SHEEP) ?1:0)  + ((a2==RES_SHEEP) ?1:0);
//                int wh = ((a1==RES_WHEAT) ?1:0)  + ((a2==RES_WHEAT) ?1:0);
//                int wo = ((a1==RES_WOOD ) ?1:0)  + ((a2==RES_WOOD ) ?1:0);
//                resourceChoices = new SOCResourceSet(cl, or, sh, wh, wo, 0);
//                //chooseFreeResources(targetResources);
//                
//                expectWAITING_FOR_DISCOVERY = true;
//                waitingForGameState = true;
//                counter = 0;
//                client.playDevCard(game, SOCDevCardConstants.DISC);
//                System.out.printf("FREE RESOURCE to get %d,%d \n",rc.bl.action[1],rc.bl.action[2] );
//                break;
//            case A_PLAYCARD_FREEROAD:
//                waitingForGameState = true;
//                counter = 0;
//                expectPLACING_FREE_ROAD1 = true;
//
//                System.out.println("!! PLAYING ROAD BUILDING CARD");
//                client.playDevCard(game, SOCDevCardConstants.ROADS);
//                System.out.printf("FREE ROADS played \n");
//                break;
            case A_PLAYCARD_KNIGHT:
                expectPLACING_ROBBER = true;
                waitingForGameState = true;
                counter = 0;
                D.ebugPrintlnINFO("!! PLAYING KNIGHT CARD");
                client.playDevCard(game, SOCDevCardConstants.KNIGHT);
                pause(1);
                
                break;
            case A_THROWDICE:
                expectDICERESULT = true;
                counter = 0;
                
                D.ebugPrintlnINFO("!!! ROLLING DICE !!!");
                client.rollDice(game);
                break;
        }
        client.put(SOCRobotFlag.toCmd(getGame().getName(), true, getPlayerNumber()));//we are a robot again 
    }
	
    
    public void printCardState()
    {
        int[] st = bl.state;
        int fsmlevel = st[OFS_FSMLEVEL];
        int pl = st[OFS_FSMPLAYER+fsmlevel]; 
        
        String s = String.format("--- Card state according to SmartSettlers:\n"
                + "pl:%d  cardplayed: %d,  new: %d %d %d %d %d,   old: %d %d %d %d %d \n", 
                pl, st[OFS_PLAYERDATA[pl] + OFS_HASPLAYEDCARD],
            st[OFS_PLAYERDATA[pl] + OFS_NEWCARDS + CARD_KNIGHT], 
            st[OFS_PLAYERDATA[pl] + OFS_NEWCARDS + CARD_FREEROAD],
            st[OFS_PLAYERDATA[pl] + OFS_NEWCARDS + CARD_FREERESOURCE],
            st[OFS_PLAYERDATA[pl] + OFS_NEWCARDS + CARD_MONOPOLY],
            st[OFS_PLAYERDATA[pl] + OFS_NEWCARDS + CARD_ONEPOINT],
            st[OFS_PLAYERDATA[pl] + OFS_OLDCARDS + CARD_KNIGHT], 
            st[OFS_PLAYERDATA[pl] + OFS_OLDCARDS + CARD_FREEROAD],
            st[OFS_PLAYERDATA[pl] + OFS_OLDCARDS + CARD_FREERESOURCE],
            st[OFS_PLAYERDATA[pl] + OFS_OLDCARDS + CARD_MONOPOLY],
            st[OFS_PLAYERDATA[pl] + OFS_OLDCARDS + CARD_ONEPOINT]);
        D.ebugPrintINFO(s);
        int spl = getMemory().getCurrentPlayerNumber();
        SOCPlayer sp = getMemory().getPlayer(spl);
        SOCDevCardSet ds = sp.getDevCards();
        
        s = String.format("--- Card state according to JSettlers:\n"
                + "pl:%d  cardplayed: %d,  new: %d %d %d %d %d,   old: %d %d %d %d %d \n", 
                spl,  sp.hasPlayedDevCard() ?1:0,
                    ds.getAmount(SOCDevCardSet.NEW, SOCDevCardConstants.KNIGHT),
                    ds.getAmount(SOCDevCardSet.NEW, SOCDevCardConstants.ROADS),
                    ds.getAmount(SOCDevCardSet.NEW, SOCDevCardConstants.DISC),
                    ds.getAmount(SOCDevCardSet.NEW, SOCDevCardConstants.MONO),
                    0,
                    ds.getAmount(SOCDevCardSet.OLD, SOCDevCardConstants.KNIGHT),
                    ds.getAmount(SOCDevCardSet.OLD, SOCDevCardConstants.ROADS),
                    ds.getAmount(SOCDevCardSet.OLD, SOCDevCardConstants.DISC),
                    ds.getAmount(SOCDevCardSet.OLD, SOCDevCardConstants.MONO),
                    ds.getNumVPCards());
        D.ebugPrintINFO(s);
        
    }
    
    
    public void getActionForFREEROAD()
    {
    	//fool the server into believing we are a human player so we won't get interrupted by the force end turn thread
    	client.put(SOCRobotFlag.toCmd(getGame().getName(), false, getPlayerNumber()));
    	boolean unhandled = false;
    	try {
    	sendStateToSmartSettlers(S_FREEROAD1); 
        Player p = bl.player[getPlayerNumber()];
        bl.possibilities.Clear();
        p.listRoadPossibilities(bl.state);
        p.selectAction(bl.state, bl.action);
        p.performAction(bl.state, bl.action);
		} catch (Exception e) {
			System.err.println("Unhandled exception");
			unhandled = true;
		}
        
    	//any exceptions thrown or illegal nothing action result in a random move instead
    	if(unhandled){
    		sendStateToSmartSettlers(S_FREEROAD1); 
            Player p = bl.player[getPlayerNumber()];
            bl.possibilities.Clear();
            p.listRoadPossibilities(bl.state);
        	Random r = new Random();
        	int aind = r.nextInt(bl.action.length);
            for (int i=0; i<bl.action.length; i++)
                bl.action[i] = bl.possibilities.action[aind][i];
            p.performAction(bl.state, bl.action);
    	}
        if(bl.action[0]==A_NOTHING){
        	//illegal action do a random one instead
        	sendStateToSmartSettlers(S_FREEROAD1); 
            Player p = bl.player[getPlayerNumber()];
            bl.possibilities.Clear();
            p.listRoadPossibilities(bl.state);
        	Random r = new Random();
        	int aind = r.nextInt(bl.action.length);
            for (int i=0; i<bl.action.length; i++)
                bl.action[i] = bl.possibilities.action[aind][i];
            p.performAction(bl.state, bl.action);
        }
        
        String s = String.format("Performing action: [%d %d %d %d %d]", bl.action[0], bl.action[1], bl.action[2], bl.action[3], bl.action[4]);
        D.ebugPrintlnINFO(s); 
        
        int coord;
        SOCPossiblePiece targetPiece;
        coord = translateEdgeToJSettlers(bl.action[1]);
        targetPiece = new SOCPossibleRoad(getPlayerData(), coord, new Vector());
        whatWeWantToBuild = new SOCRoad(getPlayerData(), targetPiece.getCoordinates(), null);
        D.ebugPrintlnINFO("!!! FREE ROAD ROAD AT " + Integer.toHexString(targetPiece.getCoordinates()) + " !!!");
	       
    	D.ebugPrintlnINFO("$$Placing free road at: " + whatWeWantToBuild.getCoordinates() + "    " + Integer.toHexString(whatWeWantToBuild.getCoordinates()));
        pause(1);
        client.putPiece(game, whatWeWantToBuild);
        pause(1);
        client.put(SOCRobotFlag.toCmd(getGame().getName(), true, getPlayerNumber()));//we are a robot again 
    }
    
    ///////////////////TRANSLATION METHODS FROM SSCLIENT//////////////
    
    public void sendGameToSmartSettlers()
    {
        int xo, yo;
        int xn, yn;
        int indo, indn;
        int coordo;
        int to, tn;
        
        SOCBoard bo = game.getBoard();
        for (xn=0; xn<BoardLayout.MAXX; xn++)
            for (yn=0; yn<BoardLayout.MAXX; yn++)
            {
                if ((xn+yn<3) || (xn+yn>9))
                    continue;
                indn = bl.hexatcoord[xn][yn];
                
                xo = 2*xn+1;
                yo = 2*(xn+yn)-5;
                coordo = 16*xo + yo;
                //indo = bo.hexIDtoNum[coordo];
                to = bo.getHexTypeFromCoord(coordo);
                //int hexType = bo.getHexLayout()[to];

                //System.out.println(to);
                if ((to>=0) && (to<=5))
                {
                    switch (to)
                    {
                        case 0:
                            tn = LAND_DESERT;
                            break;                            
                        case 1:
                            tn = LAND_CLAY;
                            break;                            
                        case 2:
                            tn = LAND_STONE;
                            break;                            
                        case 3:
                            tn = LAND_SHEEP;
                            break;                            
                        case 4:
                            tn = LAND_WHEAT;
                            break;                            
                        case 5:
                            tn = LAND_WOOD;
                            break;     
                        default:
                            tn = -1; // should cause error
                    }
                    bl.hextiles[indn].subtype = tn;
                    bl.hextiles[indn].type = TYPE_LAND;
                    if (tn != LAND_DESERT)//what's the point of this if then???? :|
                        bl.hextiles[indn].productionNumber = bo.getNumberOnHexFromCoord(coordo);
                    else
                        bl.hextiles[indn].productionNumber = bo.getNumberOnHexFromCoord(coordo);
                    
                }
                else if ((to >= 7) && (to <= 12))
                {
                    switch (to)
                    {
                        case SOCBoard.MISC_PORT_HEX:
                            tn = PORT_MISC;
                            break;
                        case SOCBoard.CLAY_PORT_HEX:
                            tn = PORT_CLAY;
                            break;
                        case SOCBoard.ORE_PORT_HEX:
                            tn = PORT_STONE;
                            break;
                        case SOCBoard.SHEEP_PORT_HEX:
                            tn = PORT_SHEEP;
                            break;
                        case SOCBoard.WHEAT_PORT_HEX:
                            tn = PORT_WHEAT;
                            break;
                        case SOCBoard.WOOD_PORT_HEX:
                            tn = PORT_WOOD;
                            break;
                        default:
                            tn = PORT_MISC;
                    }
                    bl.hextiles[indn].subtype = tn;
                    bl.hextiles[indn].type = TYPE_PORT;
//                    bl.hextiles[indn].orientation = 
                }
                else 
                {
                    bl.hextiles[indn].type = TYPE_SEA;
                    bl.hextiles[indn].subtype = SEA;
                    bl.hextiles[indn].orientation = -1;
                    //System.out.println(".");
                }

                
//                bl.hextiles[indn].subtype = LAND_WOOD;
//                bl.hextiles[indn].type = TYPE_LAND;
            }
        initTranslationTables(game.getBoard());

//        bl.GameTick(bl.state, bl.action);
//        debugframe.repaint();
    }
    
    public int translateHexToSmartSettlers(int indo)
    {
        if (indo==-1)
            return -1;
        int xo = indo/16;
        int yo = indo%16;
        
        int xn = (xo-1)/2;
        int yn = (yo+5)/2-xn;
        
        //System.out.printf("%d  (%d,%d) -> (%d,%d) \n", indo, xo,yo, xn, yn);
        return bl.hexatcoord[xn][yn];
    }
    
    public int translateHexToJSettlers(int indn)
    {
        if (indn==-1)
            return -1;
        
        int xn = (int) bl.hextiles[indn].pos.x;
        int yn = (int) bl.hextiles[indn].pos.y;
        
        int xo = 2*xn+1;
        int yo = 2*(xn+yn)-5;
        
        //System.out.printf("%d  (%d,%d) -> (%d,%d) \n", indo, xo,yo, xn, yn);
        return xo*16+yo;
    }

    public int[] vertexToSS;
    public int[] edgeToSS;
    public int[] vertexToJS;
    public int[] edgeToJS;
    
    public int translateVertexToSmartSettlers(int indo)
    {
        if (vertexToSS == null)
            return 0;
        return vertexToSS[indo];
    }

    public int translateEdgeToSmartSettlers(int indo)
    {
        return edgeToSS[indo];
    }
    
    public int translateVertexToJSettlers(int indo)
    {
        return vertexToJS[indo];
    }

    public int translateEdgeToJSettlers(int indo)
    {
        return edgeToJS[indo];
    }
    
    public int translateResToJSettlers(int ind)
    {
        switch (ind)
        {
            case RES_WOOD:
                return SOCResourceConstants.WOOD;
            case RES_CLAY:
                return SOCResourceConstants.CLAY;
            case RES_SHEEP:
                return SOCResourceConstants.SHEEP;
            case RES_WHEAT:
                return SOCResourceConstants.WHEAT;
            case RES_STONE:
                return SOCResourceConstants.ORE;
            default:
                return -1;
        }
    }
    
    public int translateResToSmartSettlers(int ind)
    {
        switch (ind)
        {
            case SOCResourceConstants.WOOD:
                return RES_WOOD;
            case SOCResourceConstants.CLAY:
                return RES_CLAY;
            case SOCResourceConstants.SHEEP:
                return RES_SHEEP;
            case SOCResourceConstants.WHEAT:
                return RES_WHEAT;
            case SOCResourceConstants.ORE:
                return RES_STONE;
            default:
                return -1;
        }
    }
    
    public void initTranslationTables(SOCBoard bo)
    {
        int vo, vn;
        int eo, en;
        int ho, hn, j;
        vertexToSS = new int[SOCBoard.MAXNODE+1];
        edgeToSS = new int[SOCBoard.MAXEDGE_V1+1];
        vertexToJS = new int[N_VERTICES];        
        edgeToJS = new int[N_EDGES];

        int[] numToHexID = 
        {
            0x17, 0x39, 0x5B, 0x7D,
            0x15, 0x37, 0x59, 0x7B, 0x9D,
            0x13, 0x35, 0x57, 0x79, 0x9B, 0xBD,
            0x11, 0x33, 0x55, 0x77, 0x99, 0xBB, 0xDD,
            0x31, 0x53, 0x75, 0x97, 0xB9, 0xDB,
            0x51, 0x73, 0x95, 0xB7, 0xD9,
            0x71, 0x93, 0xB5, 0xD7
        };
        
        for (j=0; j<numToHexID.length; j++)
        //for (ho = SOCBoard.MINHEX; ho<=SOCBoard.MAXHEX; ho++)
        {
            ho = numToHexID[j];
            if (bo.getHexTypeFromCoord(ho) >= SOCBoard.WATER_HEX)
                continue;
            hn = translateHexToSmartSettlers(ho);
            int i = 0;
            Vector vlist = SOCBoard.getAdjacentNodesToHex(ho);
            Vector elist = SOCBoard.getAdjacentEdgesToHex(ho);
            for (i = 0; i<6; i++)
            {
            	vo = (Integer) vlist.get(i);
                vn = bl.neighborHexVertex[hn][i];
                vertexToSS[vo] = vn;
                vertexToJS[vn] = vo;
                eo = (Integer) elist.get(i);
                en = bl.neighborHexEdge[hn][i];
                edgeToSS[eo] = en;
                edgeToJS[en] = eo;
            }
        }
        
    }

    public int[] sendStateToSmartSettlers(int GAMESTATE)
    {
        int[] st = new int[STATESIZE];
        int val, fsmlevel;
        Vector v;
        Enumeration pEnum;
        int indo, indn;
        
        
        st[OFS_TURN] = 0;
        fsmlevel = 0;
        if (GAMESTATE == S_ROBBERAT7)
            fsmlevel = 1;
        st[OFS_FSMLEVEL] = fsmlevel;
        st[OFS_FSMPLAYER+fsmlevel] = game.getCurrentPlayerNumber(); 
        st[OFS_FSMSTATE+fsmlevel] = GAMESTATE;
        
        val = game.getBoard().getRobberHex();
        st[OFS_ROBBERPLACE] = translateHexToSmartSettlers(val);
//        if ((GAMESTATE == S_ROAD1) 
//                
//                || (GAMESTATE == S_SETTLEMENT2)
//        {
        if (lastSettlement !=-1)
        {
            //System.out.printf("last STLMT %d\n" , lastSettlement );
            st[OFS_LASTVERTEX] = translateVertexToSmartSettlers(lastSettlement);
        }
        
        val = game.getCurrentDice(); ///??? always -1
        //System.out.println(val);
        if (val==-1)
        {
            st[OFS_DIE1] = 0;
            st[OFS_DIE2] = 0;                        
        }
        else if (val<7)
        {
            st[OFS_DIE1] = 1;
            st[OFS_DIE2] = val-1;            
        }
        else
        {
            st[OFS_DIE1] = 6;
            st[OFS_DIE2] = val-6;            
        }
        
        v = game.getBoard().getSettlements();
        pEnum = v.elements();
        while (pEnum.hasMoreElements())
        {
            SOCSettlement p = (SOCSettlement) pEnum.nextElement();
            
            //System.out.printf("%X ", p.getCoordinates());
            indn = translateVertexToSmartSettlers(p.getCoordinates());
            val = p.getPlayer().getPlayerNumber();
            st[OFS_VERTICES+indn] = VERTEX_HASSETTLEMENT + val;
        }
        //System.out.println();
        v = game.getBoard().getCities();
        pEnum = v.elements();
        while (pEnum.hasMoreElements())
        {
            SOCCity p = (SOCCity) pEnum.nextElement();
            
            //System.out.printf("%X ", p.getCoordinates());
            indn = translateVertexToSmartSettlers(p.getCoordinates());
            val = p.getPlayer().getPlayerNumber();
            st[OFS_VERTICES+indn] = VERTEX_HASCITY + val;
        }
        int i, j;
        for (i=0; i<N_VERTICES; i++)
        {
            boolean islegal = true;
            if (st[OFS_VERTICES + i] >= VERTEX_HASSETTLEMENT)
                continue;
            for (j=0; j<6; j++)
            {
                indn = bl.neighborVertexVertex[i][j];
                if ((indn!=-1) && (st[OFS_VERTICES + indn] >= VERTEX_HASSETTLEMENT))
                {
                    islegal = false;
                    break;
                }
            }
            if (!islegal)
                st[OFS_VERTICES + i] = VERTEX_TOOCLOSE;
        }

        v = game.getBoard().getRoads();
        pEnum = v.elements();
        while (pEnum.hasMoreElements())
        {
            SOCRoad p = (SOCRoad) pEnum.nextElement();
            
            //System.out.printf("%X ", p.getCoordinates());
            indn = translateEdgeToSmartSettlers(p.getCoordinates());
            val = p.getPlayer().getPlayerNumber();
            st[OFS_EDGES+indn] = EDGE_OCCUPIED + val;
        }
        
        
        if (game.getPlayerWithLargestArmy() == null)
            val = -1;
        else
            val = game.getPlayerWithLargestArmy().getPlayerNumber();
        st[OFS_LARGESTARMY_AT] = val;
        if (game.getPlayerWithLongestRoad() == null)
            val = -1;
        else
            val = game.getPlayerWithLongestRoad().getPlayerNumber();
        st[OFS_LONGESTROAD_AT] = val;
        st[OFS_NCARDSGONE] = NCARDS-game.getNumDevCards();
        int pl;        
        for (pl=0; pl<NPLAYERS; pl++)
        {
            SOCPlayer p = game.getPlayer(pl);
            st[OFS_PLAYERDATA[pl] + OFS_NSETTLEMENTS] = 5-p.getNumPieces(SOCPlayingPiece.SETTLEMENT);
            st[OFS_PLAYERDATA[pl] + OFS_NCITIES] = 4-p.getNumPieces(SOCPlayingPiece.CITY);
            st[OFS_PLAYERDATA[pl] + OFS_NROADS] = 15-p.getNumPieces(SOCPlayingPiece.ROAD);
            st[OFS_PLAYERDATA[pl] + OFS_PLAYERSLONGESTROAD] = p.getLongestRoadLength();
            st[OFS_PLAYERDATA[pl] + OFS_HASPLAYEDCARD] = p.hasPlayedDevCard() ?1:0;

            boolean hasports[] = p.getPortFlags();
            st[OFS_PLAYERDATA[pl] + OFS_ACCESSTOPORT + PORT_CLAY-1] = hasports[SOCBoard.CLAY_PORT] ?1:0;
            st[OFS_PLAYERDATA[pl] + OFS_ACCESSTOPORT + PORT_WOOD-1] = hasports[SOCBoard.WOOD_PORT] ?1:0;
            st[OFS_PLAYERDATA[pl] + OFS_ACCESSTOPORT + PORT_STONE-1]= hasports[SOCBoard.ORE_PORT] ?1:0;
            st[OFS_PLAYERDATA[pl] + OFS_ACCESSTOPORT + PORT_SHEEP-1]= hasports[SOCBoard.SHEEP_PORT] ?1:0;
            st[OFS_PLAYERDATA[pl] + OFS_ACCESSTOPORT + PORT_WHEAT-1] = hasports[SOCBoard.WHEAT_PORT] ?1:0;
            st[OFS_PLAYERDATA[pl] + OFS_ACCESSTOPORT + PORT_MISC-1] = hasports[SOCBoard.MISC_PORT] ?1:0;
            
            SOCResourceSet rs = p.getResources();
            st[OFS_PLAYERDATA[pl] + OFS_RESOURCES + RES_CLAY ] = rs.getAmount(SOCResourceConstants.CLAY);
            st[OFS_PLAYERDATA[pl] + OFS_RESOURCES + RES_WOOD ] = rs.getAmount(SOCResourceConstants.WOOD);
            st[OFS_PLAYERDATA[pl] + OFS_RESOURCES + RES_STONE] = rs.getAmount(SOCResourceConstants.ORE);
            st[OFS_PLAYERDATA[pl] + OFS_RESOURCES + RES_SHEEP] = rs.getAmount(SOCResourceConstants.SHEEP);
            st[OFS_PLAYERDATA[pl] + OFS_RESOURCES + RES_WHEAT] = rs.getAmount(SOCResourceConstants.WHEAT);
            
            SOCDevCardSet ds = p.getDevCards();
            st[OFS_PLAYERDATA[pl] + OFS_NEWCARDS + CARD_KNIGHT] = 
                    ds.getAmount(SOCDevCardSet.NEW, SOCDevCardConstants.KNIGHT);
            st[OFS_PLAYERDATA[pl] + OFS_NEWCARDS + CARD_FREEROAD] = 
                    ds.getAmount(SOCDevCardSet.NEW, SOCDevCardConstants.ROADS);
            st[OFS_PLAYERDATA[pl] + OFS_NEWCARDS + CARD_FREERESOURCE] = 
                    ds.getAmount(SOCDevCardSet.NEW, SOCDevCardConstants.DISC);
            st[OFS_PLAYERDATA[pl] + OFS_NEWCARDS + CARD_MONOPOLY] = 
                    ds.getAmount(SOCDevCardSet.NEW, SOCDevCardConstants.MONO);
            st[OFS_PLAYERDATA[pl] + OFS_NEWCARDS + CARD_ONEPOINT] = 0;

            st[OFS_PLAYERDATA[pl] + OFS_OLDCARDS + CARD_KNIGHT] = 
                    ds.getAmount(SOCDevCardSet.OLD, SOCDevCardConstants.KNIGHT);
            st[OFS_PLAYERDATA[pl] + OFS_OLDCARDS + CARD_FREEROAD] = 
                    ds.getAmount(SOCDevCardSet.OLD, SOCDevCardConstants.ROADS);
            st[OFS_PLAYERDATA[pl] + OFS_OLDCARDS + CARD_FREERESOURCE] = 
                    ds.getAmount(SOCDevCardSet.OLD, SOCDevCardConstants.DISC);
            st[OFS_PLAYERDATA[pl] + OFS_OLDCARDS + CARD_MONOPOLY] = 
                    ds.getAmount(SOCDevCardSet.OLD, SOCDevCardConstants.MONO);
            st[OFS_PLAYERDATA[pl] + OFS_OLDCARDS + CARD_ONEPOINT] = ds.getNumVPCards();
            
            st[OFS_PLAYERDATA[pl] + OFS_USEDCARDS + CARD_KNIGHT] = 
                    p.getNumKnights();
            //!!! other used cards are not stored...
        }
        bl.setState(st);
        bl.recalcScores(); // fills OFS_SCORE fields
 
//		TODO:What are these for?
//    int OFS_TURN                = 0;
//    int OFS_FSMLEVEL            = OFS_TURN          +1 ;
//    int OFS_FSMSTATE            = OFS_FSMLEVEL      +1 ;
// +  int OFS_FSMPLAYER           = OFS_FSMSTATE      +3 ;
// +  int OFS_NCARDSGONE          = OFS_FSMPLAYER     +3 ;
// +  int OFS_DIE1                = OFS_NCARDSGONE    +1 ;
// +  int OFS_DIE2                = OFS_DIE1          +1 ;
// +  int OFS_ROBBERPLACE         = OFS_DIE2          +1 ;
// +  int OFS_LONGESTROAD_AT      = OFS_ROBBERPLACE   +1 ;
// +  int OFS_LARGESTARMY_AT      = OFS_LONGESTROAD_AT   +1 ;
// +  int OFS_EDGES               = OFS_LARGESTARMY_AT   +1 ;
// +  int OFS_VERTICES            = OFS_EDGES         +N_EDGES ;
// x  int OFS_EDGEACCESSIBLE      = OFS_VERTICES      +N_VERTICES;
// x  int OFS_VERTEXACCESSIBLE    = OFS_EDGEACCESSIBLE+N_EDGES;
//            
//    
// +      int OFS_SCORE               = 0;
// +      int OFS_NSETTLEMENTS        = 1;
// +      int OFS_NCITIES             = 2;
// +      int OFS_NROADS              = 3;
// +      int OFS_PLAYERSLONGESTROAD  = 4;
// +      int OFS_RESOURCES           = OFS_PLAYERSLONGESTROAD   +1;
// +      int OFS_ACCESSTOPORT        = OFS_RESOURCES     +NRESOURCES;
// +      int OFS_USEDCARDS           = OFS_ACCESSTOPORT  +(NRESOURCES+1);
// +      int OFS_OLDCARDS            = OFS_USEDCARDS     +N_DEVCARDTYPES;
// +      int OFS_NEWCARDS            = OFS_OLDCARDS      +N_DEVCARDTYPES;
//        int PLAYERSTATESIZE         = OFS_NEWCARDS      +N_DEVCARDTYPES;
//    
//    int[] OFS_PLAYERDATA        = { OFS_VERTEXACCESSIBLE+N_VERTICES,
//                                    OFS_VERTEXACCESSIBLE+N_VERTICES + PLAYERSTATESIZE,
//                                    OFS_VERTEXACCESSIBLE+N_VERTICES + 2*PLAYERSTATESIZE,
//                                    OFS_VERTEXACCESSIBLE+N_VERTICES + 3*PLAYERSTATESIZE};    
//    int STATESIZE = OFS_VERTEXACCESSIBLE+N_VERTICES + 4*PLAYERSTATESIZE;
//    int ACTIONSIZE = 5;
        
        //bl.GameTick(bl.state, bl.action);
        
//        debugframe.repaint();
        return st;
    }
    


//public void recordGameEvent(SOCMessage mes, String gameName, String event)
//{
//    SOCGame ga = declarativeMemory.getGame();
//    if (ga==null)
//        return;
//    int socState = ga.getGameState();
//    int pl = ga.getCurrentPlayerNumber();
//    int ssState;
//    switch (socState)
//    {
//        case SOCGame.START1A:
//            ssState = S_SETTLEMENT1; 
//            break;
//        case SOCGame.START1B:
//            ssState = S_ROAD1; 
//            break;
//        case SOCGame.START2A:
//            ssState = S_SETTLEMENT2; 
//            break;
//        case SOCGame.START2B:
//            ssState = S_ROAD2; 
//            break;
//        case SOCGame.PLAY:
//            ssState = S_BEFOREDICE; 
//            break;
//        case SOCGame.PLAY1:
//            ssState = S_NORMAL; 
//            break;
//        case SOCGame.PLACING_FREE_ROAD1:
//            ssState = S_FREEROAD1; 
//            break;
//        case SOCGame.PLACING_FREE_ROAD2:
//            ssState = S_FREEROAD2; 
//            break;
//        case SOCGame.PLACING_ROBBER:
//            ssState = S_ROBBERAT7; ///??? 
//            break;
//        case SOCGame.WAITING_FOR_DISCARDS:
//            ssState = S_PAYTAX;   ///???
//            break;
//        case SOCGame.OVER:
//            ssState = S_FINISHED; 
//            break;
//        default:
//            ssState = S_NORMAL;
//    }
//
////    if (ssState == -1) 
////        return;
//    
//    int[] st = sendStateToSmartSettlers(ssState);
//    int[] a = new int[ACTIONSIZE];
//    for (int i=0; i<a.length; i++)
//        a[i]=0;
//    
//    int pos, r1, r2;
//    a[0] = -1;
//    switch (mes.getType())
//    {
//        case SOCMessage.BANKTRADE:
//            SOCResourceSet get = ((SOCBankTrade)mes).getGetSet();
//            SOCResourceSet give = ((SOCBankTrade)mes).getGiveSet();
//            a[0] = A_PORTTRADE;
//            System.out.println("get:  " + get.toString());
//            System.out.println("give: " + give.toString());
//            r1 = give.pickResource();
//            a[1] = give.getAmount(r1);
//            a[2] = translateResToSmartSettlers(r1);
//            r2 = get.pickResource();
//            a[3] = get.getAmount(r2);
//            a[4] = translateResToSmartSettlers(r2);
//            // give what: [2], amt: [1]        
//            break;
//        case SOCMessage.BUILDREQUEST:
//            break;
//        case SOCMessage.BUYCARDREQUEST:
//            a[0] = A_BUYCARD;
//            break;
//        case SOCMessage.CHOOSEPLAYERREQUEST:
//        case SOCMessage.DEVCARD:
//            break;
//        case SOCMessage.DISCARD:
//            SOCResourceSet ds = ((SOCDiscard)mes).getResources();
//            a[0] = A_PAYTAX;
//            a[1] = ds.getTotal();
//            // !!!! details...
//            break;
//        case SOCMessage.DISCARDREQUEST:
//            break;
//        case SOCMessage.DISCOVERYPICK:
//            SOCResourceSet rs = ((SOCDiscoveryPick)mes).getResources();
//            r1 = rs.pickResource();
//            rs.subtract(1, r1);
//            r2 = rs.pickResource();
//            a[0] = A_PLAYCARD_FREERESOURCE;
//            a[1] = translateResToSmartSettlers(r1);
//            a[2] = translateResToSmartSettlers(r2);            
//            break;
//        case SOCMessage.ENDTURN:
//            a[0] = A_ENDTURN;
//            break;
//        case SOCMessage.MONOPOLYPICK:
//            a[0] = A_PLAYCARD_MONOPOLY;
//            a[1] = translateResToSmartSettlers(((SOCMonopolyPick)mes).getResource());
//            break;
//        case SOCMessage.PLAYDEVCARDREQUEST:
//            int dc = ((SOCPlayDevCardRequest)mes).getDevCard();
//            switch (dc)
//            {
//                case SOCDevCardConstants.KNIGHT:
//                    a[0] = A_PLAYCARD_KNIGHT;
//                    //!!!! do the rest
//                    break;
//                case SOCDevCardConstants.MONO:
//                    // action is translated at MONOPOLYPICK
//                    break;
//                case SOCDevCardConstants.DISC:
//                    // action is translated at DISCOVERYPICK
//                    break;
//                case SOCDevCardConstants.ROADS:
//                    a[0] = A_PLAYCARD_FREEROAD;
//                    break;                    
//            }
//            break;
//        case SOCMessage.PUTPIECE:            
//            pos = ((SOCPutPiece)mes).getCoordinates();
//            switch (((SOCPutPiece)mes).getPieceType())
//            {
//                case SOCPlayingPiece.ROAD:
//                    a[0] = A_BUILDROAD;
//                    a[1] = translateEdgeToSmartSettlers(pos);
//                    break;
//                case SOCPlayingPiece.SETTLEMENT:
//                    a[0] = A_BUILDSETTLEMENT;
//                    a[1] = translateVertexToSmartSettlers(pos);
//                    break;
//                case SOCPlayingPiece.CITY:
//                    a[0] = A_BUILDCITY;
//                    a[1] = translateVertexToSmartSettlers(pos);
//                    break;
//                
//            }
//            break;
//        case SOCMessage.MOVEROBBER:
//            // store robber position (robber place and victim will be passed as a single command)
//            logRobberPlace = ((SOCMoveRobber)mes).getCoordinates();
//            break;
//        case SOCMessage.CHOOSEPLAYER:
//            a[0] = A_PLACEROBBER;
//            a[1] = translateEdgeToSmartSettlers(logRobberPlace);
//            a[2] = ((SOCChoosePlayer)mes).getChoice();
//            break;
//        case SOCMessage.ROLLDICE:
//            a[0] = A_THROWDICE;
//            break;
//        case SOCMessage.ROLLDICEREQUEST:
//    }
//// TURN: do nothing
//    if (a[0] != -1)
//        bl.writeLog(st,a,ga.getCurrentPlayerNumber(),gameName,event);
//}
    
}
