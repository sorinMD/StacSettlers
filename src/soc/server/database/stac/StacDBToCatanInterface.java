package soc.server.database.stac;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.Vector;

import mcts.game.catan.Board;
import mcts.game.catan.Catan;
import mcts.game.catan.CatanConfig;
import mcts.game.catan.GameStateConstants;
import soc.game.SOCBoard;
import soc.game.SOCDevCardConstants;
import soc.game.SOCGame;
import soc.game.SOCPlayingPiece;
import soc.game.SOCResourceConstants;

/**
 * Utility to translate between the lightweight Catan and JSettlers state and action types. Also provides a method to turn a db state into a
 * SS state such that it can be used to list the available actions the player had in that state.
 * 
 * @author MD
 *
 */
public class StacDBToCatanInterface implements GameStateConstants {

    public static int translateSSStateToJS(int ssState){
		switch (ssState) {
		case S_SETTLEMENT1:
			return SOCGame.START1A;
			
		case S_ROAD1:
			return SOCGame.START1B;

		case S_SETTLEMENT2:
			return SOCGame.START2A;
			
		case S_ROAD2:
			return SOCGame.START2B;
			
		case S_BEFOREDICE:
			return SOCGame.PLAY;
			
		case S_NORMAL:
			return SOCGame.PLAY1;
			
		case S_PAYTAX:
			return SOCGame.WAITING_FOR_DISCARDS;
			
		case S_FREEROAD1:
			return SOCGame.PLACING_FREE_ROAD1;
		
		case S_FREEROAD2:
			return SOCGame.PLACING_FREE_ROAD2;
			
		case S_ROBBERAT7:
			return SOCGame.PLACING_ROBBER;
			
		default:
			return -1;//illegal state id
		}
	}
    
    public static int translateJSStateToSS(int jsState){
		switch (jsState) {
		case SOCGame.START1A:
			return S_SETTLEMENT1;
			
		case SOCGame.START1B:
			return S_ROAD1;

		case SOCGame.START2A:
			return S_SETTLEMENT2;
			
		case SOCGame.START2B:
			return S_ROAD2;
			
		case SOCGame.PLAY:
			return S_BEFOREDICE;
			
		case SOCGame.PLAY1:
			return S_NORMAL;
			
		case SOCGame.WAITING_FOR_DISCARDS:
			return S_PAYTAX;
			
		case SOCGame.PLACING_FREE_ROAD1:
			return S_FREEROAD1;
		
		case SOCGame.PLACING_FREE_ROAD2:
			return S_FREEROAD2;
			
		case SOCGame.PLACING_ROBBER:
			return S_ROBBERAT7;
			
		default:
			return -1;//illegal state id
		}
	}
    
    /**
     * @param ssAction
     * @return
     */
    public static double translateSSActionToJS(int ssAction){
		switch (ssAction) {
		case A_BUILDSETTLEMENT:
			return GameActionRow.BUILDSETT;

		case A_BUILDROAD:
			return GameActionRow.BUILDROAD;
			
		case A_BUILDCITY:
			return GameActionRow.BUILDCITY;
			
		case A_THROWDICE:
			return GameActionRow.ROLL;
		
		case A_ENDTURN:
			return GameActionRow.ENDTURN;
		
		case A_PORTTRADE:
			return GameActionRow.TRADE;
					
		case A_PAYTAX:
			return GameActionRow.DISCARD;
			
		case A_PLACEROBBER:
			return GameActionRow.MOVEROBBER;
		
		case A_BUYCARD:
			return GameActionRow.BUYDEVCARD;
			
		case A_PLAYCARD_KNIGHT:
			return GameActionRow.PLAYKNIGHT;
		
		case A_PLAYCARD_FREEROAD:
			return GameActionRow.PLAYROAD;
		
		case A_PLAYCARD_FREERESOURCE:
			return GameActionRow.PLAYDISC;
		
		case A_PLAYCARD_MONOPOLY:
			return GameActionRow.PLAYMONO;
			
		default:
			System.err.println("Couldn't translate to JS action");
			return -1;//illegal action id
		}
	}
    
    /**
     * @param jsAction
     * @return
     */
    public static int translateJSActionToSS(double jsAction){
		if(jsAction == GameActionRow.BUILDSETT){
			return A_BUILDSETTLEMENT;
		}else if(jsAction == GameActionRow.BUILDROAD){
			return A_BUILDROAD;
		}else if(jsAction == GameActionRow.BUILDCITY){
			return A_BUILDCITY;
		}else if(jsAction == GameActionRow.ROLL){
			return A_THROWDICE;
		}else if(jsAction == GameActionRow.ENDTURN){
			return A_ENDTURN;
		}else if(jsAction == GameActionRow.TRADE){
			return A_PORTTRADE;
		}else if(jsAction == GameActionRow.DISCARD){
			return A_PAYTAX;
		}else if(jsAction == GameActionRow.MOVEROBBER){
			return A_PLACEROBBER;
		}else if(jsAction == GameActionRow.BUYDEVCARD){
			return A_BUYCARD;
		}else if(jsAction == GameActionRow.PLAYKNIGHT){
			return A_PLAYCARD_KNIGHT;
		}else if(jsAction == GameActionRow.PLAYROAD){
			return A_PLAYCARD_FREEROAD;
		}else if(jsAction == GameActionRow.PLAYDISC){
			return A_PLAYCARD_FREERESOURCE;
		}else if(jsAction == GameActionRow.PLAYMONO){
			return A_PLAYCARD_MONOPOLY;
		}
		System.err.println("Couldn't translate to SS action");
		return -1;//illegal action id
    	
	}
    
    ///// Utilities to turn the db representation into a lightweight board representation ////
    
    private static int[] vertexToSS;
    private static int[] edgeToSS;
    private static int[] vertexToJS;
    private static int[] edgeToJS;
	
	/**
	 * 
	 * @param ogsr
	 * @param egsr
	 * @param GAMESTATE must be a state from {@link GameStateConstants}
	 * @return
	 */
	public static Catan generateGameFromDB(ObsGameStateRow ogsr, ExtGameStateRow egsr, int GAMESTATE){
		
		SOCBoard board = SOCBoard.createBoard(null, 4);
		board.setHexLayout(StacDBHelper.transformToIntArr(ogsr.getHexLayout()));
		board.setNumberLayout(StacDBHelper.transformToIntArr(ogsr.getNumberLayout()));
		
		generateBoard(board);
		
		return generateGame(GAMESTATE, ogsr, egsr);
	}

	  /**
     * Sending the board layout and initialising all translation stuff
     */
    private static void generateBoard(SOCBoard bo)
    {
    	Board bl = new Board();
    	bl.InitBoard();
        int xo, yo;
        int xn, yn;
        int indn;
        int coordo;
        int to, tn;
        
        for (xn=0; xn<Board.MAXX; xn++)
            for (yn=0; yn<Board.MAXY; yn++)
            {
                if ((xn+yn<3) || (xn+yn>9))
                    continue;
                indn = bl.hexatcoord[xn][yn];
                
                xo = 2*xn+1;
                yo = 2*(xn+yn)-5;
                coordo = 16*xo + yo;
                to = bo.getHexTypeFromCoord(coordo);

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
                }
                else 
                {
                    bl.hextiles[indn].type = TYPE_SEA;
                    bl.hextiles[indn].subtype = SEA;
                    bl.hextiles[indn].orientation = -1;
                }
            }
        initTranslationTables(bo, bl);

        Catan.board = bl;
    }
    
    private static int translateHexToSmartSettlers(int indo, Board b)
    {
        if (indo==-1)
            return -1;
        int xo = indo/16;
        int yo = indo%16;
        
        int xn = (xo-1)/2;
        int yn = (yo+5)/2-xn;
        
        //System.out.printf("%d  (%d,%d) -> (%d,%d) \n", indo, xo,yo, xn, yn);
        return b.hexatcoord[xn][yn];
    }
    
    private static int translateVertexToSmartSettlers(int indo)
    {
        if (vertexToSS == null)
            return 0;
        return vertexToSS[indo];
    }

    private static int translateEdgeToSmartSettlers(int indo)
    {
        return edgeToSS[indo];
    }
    
    
    private static void initTranslationTables(SOCBoard bo, Board bl)
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
            hn = translateHexToSmartSettlers(ho, bl);
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

    /**
     * Translates and sends everything to the SmartSettlers model
     * @param GAMESTATE the current state
     * @return the game state as an array that can be understood by SS
     */
    private static Catan generateGame(int GAMESTATE, ObsGameStateRow ogsr, ExtGameStateRow egsr)
    {
        int[] st = new int[STATESIZE];
        int val, fsmlevel;
        int indn;
        
        st[OFS_STARTING_PLAYER] = ogsr.getStartingPlayer();
        
        if(GAMESTATE == S_PAYTAX){
        	//need to handle discard as a special case
        	fsmlevel = 1;
        	st[OFS_FSMPLAYER+fsmlevel] = ogsr.getCurrentPlayer();//NOTE: the correct next player is reset when getting all states of a kind
        	st[OFS_FSMPLAYER] = ogsr.getCurrentPlayer(); //actual current player 

        }else{
	        fsmlevel = 0;
	        if (GAMESTATE == S_ROBBERAT7)
	            fsmlevel = 1;
	        st[OFS_FSMPLAYER+fsmlevel] = ogsr.getCurrentPlayer();
        }
    	
        st[OFS_FSMLEVEL] = fsmlevel;
    	st[OFS_FSMSTATE + fsmlevel] = GAMESTATE;
    	
        val = ogsr.getRobberHex();
        st[OFS_ROBBERPLACE] = translateHexToSmartSettlers(val,Catan.board);
        
        val = ogsr.getDiceResult();

        if (val==-1)
        {
            st[OFS_DICE] = 0;
        }
        else
        {
            st[OFS_DICE] = val;            
        }
        
        Integer[][] pob = ogsr.getPiecesOnBoard();
        
		for(Integer[] p : pob){
        	if(p[0] == SOCPlayingPiece.ROAD){
        		val = p[2];
        		indn = translateEdgeToSmartSettlers(p[1]);
                st[OFS_EDGES+indn] = EDGE_OCCUPIED + val;
        	}else if(p[0] == SOCPlayingPiece.SETTLEMENT){
        		val = p[2];
        		indn = translateVertexToSmartSettlers(p[1]);
        		st[OFS_VERTICES+indn] = VERTEX_HASSETTLEMENT + val;
        	}else if(p[0] == SOCPlayingPiece.CITY){
        		val = p[2];
        		indn = translateVertexToSmartSettlers(p[1]);
        		st[OFS_VERTICES+indn] = VERTEX_HASCITY + val;
        	}//otherwise we ignore (we shouldn't get here!)
        }
		
        //System.out.println();
        int i, j;
        for (i=0; i<N_VERTICES; i++)
        {
            boolean islegal = true;
            if (st[OFS_VERTICES + i] >= VERTEX_HASSETTLEMENT)
                continue;
            for (j=0; j<6; j++)
            {
                indn = Catan.board.neighborVertexVertex[i][j];
                if ((indn!=-1) && (st[OFS_VERTICES + indn] >= VERTEX_HASSETTLEMENT))
                {
                    islegal = false;
                    break;
                }
            }
            if (!islegal)
                st[OFS_VERTICES + i] = VERTEX_TOOCLOSE;
        }
        
        val = -1;
        int pl;
        for (pl=0; pl<NPLAYERS; pl++){
        	//TODO: check this
        	if(ogsr.getLALabel(pl)==1){
        		val = pl;
        		break;
        	}
        }
        st[OFS_LARGESTARMY_AT] = val;
        
        val = -1;
        for (pl=0; pl<NPLAYERS; pl++){
        	if(ogsr.getLRLabel(pl)==1){
        		val = pl;
        		break;
        	}
        }
        st[OFS_LONGESTROAD_AT] = val;
       
        st[OFS_NCARDSGONE] = NCARDS-ogsr.getDevCardsLeft();
                
        
        int numvp, numkn, numdisc, nummono, numrb;
        numvp = numdisc = numkn = nummono = numrb = 0;
        for (pl=0; pl<NPLAYERS; pl++)
        {
        	int[] rss = ogsr.getResources(pl);
        	int[] tpt = ogsr.getTouchingPortTypes(pl);
        	
        	int[] ndv = ogsr.getNewDevCards(pl);
        	int[] udv = ogsr.getUnplayedDevCards(pl);
        	
            st[OFS_PLAYERDATA[pl] + OFS_NSETTLEMENTS] = ogsr.getSettlementsForPlayer(pl).length;
            st[OFS_PLAYERDATA[pl] + OFS_NCITIES] = ogsr.getCitiesForPlayer(pl).length;
            st[OFS_PLAYERDATA[pl] + OFS_NROADS] = ogsr.getRoadsForPlayer(pl).length;
            st[OFS_PLAYERDATA[pl] + OFS_PLAYERSLONGESTROAD] = egsr.getLongestRoads(pl);
            st[OFS_PLAYERDATA[pl] + OFS_HASPLAYEDCARD] = ogsr.hasPlayedDevCard() ?1:0;
            
            st[OFS_PLAYERDATA[pl] + OFS_ACCESSTOPORT + PORT_CLAY-1] = tpt[SOCBoard.CLAY_PORT];
            st[OFS_PLAYERDATA[pl] + OFS_ACCESSTOPORT + PORT_WOOD-1] = tpt[SOCBoard.WOOD_PORT];
            st[OFS_PLAYERDATA[pl] + OFS_ACCESSTOPORT + PORT_STONE-1]= tpt[SOCBoard.ORE_PORT];
            st[OFS_PLAYERDATA[pl] + OFS_ACCESSTOPORT + PORT_SHEEP-1]= tpt[SOCBoard.SHEEP_PORT];
            st[OFS_PLAYERDATA[pl] + OFS_ACCESSTOPORT + PORT_WHEAT-1] = tpt[SOCBoard.WHEAT_PORT];
            st[OFS_PLAYERDATA[pl] + OFS_ACCESSTOPORT + PORT_MISC-1] = tpt[SOCBoard.MISC_PORT];
            
            st[OFS_PLAYERDATA[pl] + OFS_USEDCARDS + CARD_KNIGHT] = 
                    ogsr.getPlayedKnights(pl);
            st[OFS_PLAYERDATA[pl] + OFS_USEDCARDS + CARD_FREERESOURCE] = 
                    ogsr.getPlayedDisc(pl);
            st[OFS_PLAYERDATA[pl] + OFS_USEDCARDS + CARD_FREEROAD] = 
                    ogsr.getPlayedRB(pl);
            st[OFS_PLAYERDATA[pl] + OFS_USEDCARDS + CARD_MONOPOLY] = 
                    ogsr.getPlayedMono(pl);
            st[OFS_PLAYERDATA[pl] + OFS_USEDCARDS + CARD_ONEPOINT] = 0;//these are never played
            
            st[OFS_PLAYERDATA[pl] + OFS_RESOURCES + RES_CLAY ] = rss[SOCResourceConstants.CLAY-1];
            st[OFS_PLAYERDATA[pl] + OFS_RESOURCES + RES_WOOD ] = rss[SOCResourceConstants.WOOD-1];
            st[OFS_PLAYERDATA[pl] + OFS_RESOURCES + RES_STONE] = rss[SOCResourceConstants.ORE-1];
            st[OFS_PLAYERDATA[pl] + OFS_RESOURCES + RES_SHEEP] = rss[SOCResourceConstants.SHEEP-1];
            st[OFS_PLAYERDATA[pl] + OFS_RESOURCES + RES_WHEAT] = rss[SOCResourceConstants.WHEAT-1];
            
            st[OFS_PLAYERDATA[pl] + OFS_NEWCARDS + CARD_KNIGHT] = 
                    ndv[SOCDevCardConstants.KNIGHT];
            st[OFS_PLAYERDATA[pl] + OFS_NEWCARDS + CARD_FREEROAD] = 
            		ndv[SOCDevCardConstants.ROADS];
            st[OFS_PLAYERDATA[pl] + OFS_NEWCARDS + CARD_FREERESOURCE] = 
            		ndv[SOCDevCardConstants.DISC];
            st[OFS_PLAYERDATA[pl] + OFS_NEWCARDS + CARD_MONOPOLY] = 
            		ndv[SOCDevCardConstants.MONO];
            st[OFS_PLAYERDATA[pl] + OFS_NEWCARDS + CARD_ONEPOINT] = 0;

            st[OFS_PLAYERDATA[pl] + OFS_OLDCARDS + CARD_KNIGHT] = 
                    udv[SOCDevCardConstants.KNIGHT];
            st[OFS_PLAYERDATA[pl] + OFS_OLDCARDS + CARD_FREEROAD] = 
            		udv[SOCDevCardConstants.ROADS];
            st[OFS_PLAYERDATA[pl] + OFS_OLDCARDS + CARD_FREERESOURCE] = 
            		udv[SOCDevCardConstants.DISC];
            st[OFS_PLAYERDATA[pl] + OFS_OLDCARDS + CARD_MONOPOLY] = 
            		udv[SOCDevCardConstants.MONO];
            st[OFS_PLAYERDATA[pl] + OFS_OLDCARDS + CARD_ONEPOINT] = ogsr.getVictoryDevCards(pl);

            //keep track of all the cards drawn from the deck (played and unplayed)
            numkn = numkn + ogsr.getPlayedKnights(pl) + ndv[SOCDevCardConstants.KNIGHT] + udv[SOCDevCardConstants.KNIGHT];
            numdisc = numdisc + ogsr.getPlayedDisc(pl) + ndv[SOCDevCardConstants.DISC] + udv[SOCDevCardConstants.DISC];
            nummono = nummono + ogsr.getPlayedMono(pl) + ndv[SOCDevCardConstants.MONO] + udv[SOCDevCardConstants.MONO];
            numrb = numrb + ogsr.getPlayedRB(pl) + ndv[SOCDevCardConstants.ROADS] + udv[SOCDevCardConstants.ROADS];
            numvp = numvp + ogsr.getVictoryDevCards(pl);//these are never played or never new
            
        }
        
		//if it is one of the initial road placements state, get the last settlement placed on the board so we know what the next legal locations are
		if(GAMESTATE == S_ROAD1){
			pob = ogsr.getPiecesOnBoard();
			int cpn = ogsr.getCurrentPlayer();
			Set settCoords = new HashSet<Integer>();
	        for(Integer[] p : pob){
	        	if(p[0] == SOCPlayingPiece.SETTLEMENT && p[2] == cpn){
	        		settCoords.add(p[1]);
	        	}
	        }
	        
	        for(Object o : settCoords)//should be only one
	        	st[OFS_LASTVERTEX] = translateVertexToSmartSettlers((int) o);
	        
		}else if(GAMESTATE == S_ROAD2){
			pob = ogsr.getPiecesOnBoard();
			int cpn = ogsr.getCurrentPlayer();
			Set settCoords = new HashSet<Integer>();
			Set roads = new HashSet<Integer>();
	        for(Integer[] p : pob){
	        	if(p[0] == SOCPlayingPiece.ROAD && p[2] == cpn){
	        		roads.add(p[1]);
	        	}else if(p[0] == SOCPlayingPiece.SETTLEMENT && p[2] == cpn){
	        		settCoords.add(p[1]);
	        	}
	        }
	        
	        //for each road remove the adjacent settlement from the list 
	        for(Object o : roads){
	        	int[] nodes = SOCBoard.getAdjacentNodesToEdge_arr((int) o);
	        	settCoords.remove(nodes[0]);
	        	settCoords.remove(nodes[1]);
	        }
	        if(!settCoords.isEmpty())
	        	for(Object o : settCoords)//should be only one
	        		st[OFS_LASTVERTEX] = translateVertexToSmartSettlers((int) o);
	        
		}
		//trades are stored as a single action in the database
		CatanConfig config = new CatanConfig();
		config.NEGOTIATIONS = false;
        Catan ga = new Catan(st,config);
        ga.recalcScores();
        return ga;
    }
	
}
