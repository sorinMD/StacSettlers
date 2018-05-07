package representation;

/**
 * Offsets and sizes of vectors for binary feature vectors.
 *
 * @author MD
 */
public interface BinaryFeatureVectorOffsets{

    int N_PLAYERS = 4;
    
    /**
     * planes or "classes" for features which have a large range, so as to approximate and reduce the size of the space.
     */
    int N_PLANES = 5;
    
    //general description of the board
	/** Reduced as with numbers on hexes. If it is 0, or less than it should be set	to 0; if it is 7 than it is set to 1 and 2-6 for the remaining values */
	int OFS_BIAS_FEATURE = 0;
	int OFS_DICERESULT = OFS_BIAS_FEATURE + 1;
    int OFS_TOTALSETTLEMENTS = OFS_DICERESULT + N_PLANES + 1;
    int OFS_TOTALROADS = OFS_TOTALSETTLEMENTS + N_PLANES;
    int OFS_TOTALCITIES = OFS_TOTALROADS + N_PLANES;    
    int OFS_ANYDEVCARDSLEFT = OFS_TOTALCITIES + N_PLANES;
    int OFS_RESOURCEBLOCKED = OFS_ANYDEVCARDSLEFT + 1;
    int OFS_NUMBERBLOCKED = OFS_RESOURCEBLOCKED + N_PLANES;
    //description for current player
    int OFS_BOARDPOSITION = OFS_NUMBERBLOCKED + N_PLANES;
    int OFS_HASPLAYEDDEVCARD = OFS_BOARDPOSITION + N_PLAYERS;
    /**representing 1-10 and 11,12 being equal to 10 vps*/
    int OFS_PLAYERSCORE = OFS_HASPLAYEDDEVCARD + 1;
    int OFS_LR = OFS_PLAYERSCORE + N_PLANES*2;
    /** maximum is 15 */
    int OFS_CURRENTLONGESTROAD = OFS_LR +1;
    int OFS_ROADS = OFS_CURRENTLONGESTROAD + N_PLANES * 3;
    int OFS_SETTLEMENTS = OFS_ROADS + N_PLANES;
    /**max number of cities is 4*/
    int OFS_CITIES = OFS_SETTLEMENTS + N_PLANES;
    /**limit rss to a max of 5 for each type that a player can have in his/her hand*/
    int OFS_CLAYINHAND = OFS_CITIES + N_PLANES -1; 
    int OFS_OREINHAND = OFS_CLAYINHAND + N_PLANES;
    int OFS_SHEEPINHAND = OFS_OREINHAND + N_PLANES;
    int OFS_WHEATINHAND = OFS_SHEEPINHAND + N_PLANES;
    int OFS_WOODINHAND = OFS_WHEATINHAND + N_PLANES; 
    /**6 types of ports*/
    int OFS_TOUCHING_PORTS = OFS_WOODINHAND + N_PLANES;
    //number of settlements/cities adjacent to each rss, where sett = 1; city = 2 in value up to 5 or more;
    /** access is limited to 8 pieces to one rss type for each player */
    int OFS_CLAYACCESS = OFS_TOUCHING_PORTS + N_PLANES + 1;
    int OFS_OREACCESS = OFS_CLAYACCESS + N_PLANES + 3;
    int OFS_SHEEPACCESS = OFS_OREACCESS + N_PLANES + 3;
    int OFS_WHEATACCESS = OFS_SHEEPACCESS + N_PLANES + 3;
    int OFS_WOODACCESS = OFS_WHEATACCESS + N_PLANES + 3;
    //production as sum of numbers touching for each resource
    /** production is limited to 40 per player and is divided in increments of 5 */
    int OFS_CLAYPRODUCTION = OFS_WOODACCESS + N_PLANES + 3;
    int OFS_OREPRODUCTION = OFS_CLAYPRODUCTION + N_PLANES + 4;
    int OFS_SHEEPPRODUCTION = OFS_OREPRODUCTION + N_PLANES + 4;
    int OFS_WHEATPRODUCTION = OFS_SHEEPPRODUCTION + N_PLANES + 4;
    int OFS_WOODPRODUCTION = OFS_WHEATPRODUCTION + N_PLANES + 4;
    //effect of robber on this player + how many pieces affected
    int OFS_AFFECTEDBYROBBER = OFS_WOODPRODUCTION + N_PLANES + 4;
    /** a maximum of 6 production units can be affected by the robber, where a settlement gives 1 and a city gives 2 */
    int OFS_NPIECESAFFECTED = OFS_AFFECTEDBYROBBER + 1;
    /** a maximum of 3 players can be affected by the robber */
    int OFS_NPLAYERSAFFECTED = OFS_NPIECESAFFECTED + N_PLANES + 1;
    //development cards
    int OFS_LA = OFS_NPLAYERSAFFECTED + 3;
    /**limit knights to 5 maximum per player*/
    int OFS_NPLAYEDKNIGHTS = OFS_LA + 1;
    /** Counter for the 3 special cards (there are 2 in total for each type in the deck) */
    int OFS_PLAYEDDEVCARDSINHAND = OFS_NPLAYEDKNIGHTS + N_PLANES;
    /**only 4 dev cards can be played*/
    int OFS_OLDDEVCARDSINHAND = OFS_PLAYEDDEVCARDSINHAND + (N_PLANES - 2)*2;
    int OFS_NEWDEVCARDSINHAND = OFS_OLDDEVCARDSINHAND + N_PLANES - 1;
    int OFS_VPCARDS = OFS_NEWDEVCARDSINHAND + N_PLANES -1;
    //can do actions
    int OFS_CANBUYCARD = OFS_VPCARDS + N_PLANES;
    int OFS_CANBUILDROAD = OFS_CANBUYCARD + 1;
    int OFS_CANBUILDSETTLEMENT = OFS_CANBUILDROAD + 1;
    int OFS_CANBUILDCITY = OFS_CANBUILDSETTLEMENT + 1;
    int OFS_CANBANKORPORTTRADE = OFS_CANBUILDCITY + 1;
    int OFS_OVER7CARDS = OFS_CANBANKORPORTTRADE + 1;
    //distances
    int OFS_DISTANCETOPORT = OFS_OVER7CARDS + 1;
    int OFS_DISTANCETOLEGAL = OFS_DISTANCETOPORT + 1;
    int OFS_DISTANCETOOPP = OFS_DISTANCETOLEGAL + 1;
    //description for opponents
    int OFS_OPP_SCORE = 0;
    int OFS_OPP_LA = OFS_OPP_SCORE + N_PLANES - 1;
    int OFS_OPP_LR = OFS_OPP_LA + 1;
    int OFS_OPP_CURRENTLONGESTROAD = OFS_OPP_LR + 1;
    int OFS_OPP_NPLAYEDKNIGHTS = OFS_OPP_CURRENTLONGESTROAD + N_PLANES - 1;
    int OFS_OPP_HASDEVCARDS = OFS_OPP_NPLAYEDKNIGHTS + 2;
    int OFS_OPP_AFFECTEDBYROBBER = OFS_OPP_HASDEVCARDS + 1;
    int OPP_PLAYERSTATESIZE = OFS_OPP_AFFECTEDBYROBBER + 1;
    
    int[] OFS_OPPPLAYERDATA        = { OFS_DISTANCETOOPP + 1,
    		OFS_DISTANCETOOPP + 1 + OPP_PLAYERSTATESIZE,
    		OFS_DISTANCETOOPP + 1 + 2*OPP_PLAYERSTATESIZE}; 
    
    int STATE_VECTOR_SIZE = OFS_DISTANCETOOPP + 1 + 3*OPP_PLAYERSTATESIZE;
    
    int OFS_ACT_BIAS_FEATURE = 0;
    int OFS_ACT_PLAYERSCORE = OFS_ACT_BIAS_FEATURE + 1;
    int OFS_ACT_DICE = OFS_ACT_PLAYERSCORE + N_PLANES*2;
    int OFS_ACT_ROADS = OFS_ACT_DICE + 1;
    int OFS_ACT_SETTS = OFS_ACT_ROADS + N_PLANES;
    int OFS_ACT_CITIES = OFS_ACT_SETTS + N_PLANES;
    int OFS_ACT_RSSINHAND = OFS_ACT_CITIES + N_PLANES -1;
    int OFS_ACT_RSSPROD = OFS_ACT_RSSINHAND + N_PLANES*5;
    int OFS_ACT_RSSACCESS = OFS_ACT_RSSPROD + N_PLANES*5 + 5*4;
    int OFS_ACT_TOUCHINGPORTS = OFS_ACT_RSSACCESS + N_PLANES*5 + 5*3;
    int OFS_ACT_AFFECTEDBYROBBER = OFS_ACT_TOUCHINGPORTS + 6;
    int OFS_ACT_RSSBLOCKED = OFS_ACT_AFFECTEDBYROBBER + 1;
    int OFS_ACT_NUMBERBLOCKED = OFS_ACT_RSSBLOCKED + N_PLANES;
    int OFS_ACT_PRODBLOCKED = OFS_ACT_NUMBERBLOCKED + N_PLANES;
    int OFS_ACT_NOPLAYERSAFFECTED = OFS_ACT_PRODBLOCKED + N_PLANES + 1;
    int OFS_ACT_PLAYEDDEVCARDSINHAND = OFS_ACT_NOPLAYERSAFFECTED + 3;
    int OFS_ACT_OLDDEVCARDSINHAND = OFS_ACT_PLAYEDDEVCARDSINHAND + 3*2;
    int OFS_ACT_NEWDEVCARDSINHAND = OFS_ACT_OLDDEVCARDSINHAND + 4;
    int OFS_ACT_VPCARDS = OFS_ACT_NEWDEVCARDSINHAND + 4;
    int OFS_ACT_NPLAYEDKNIGHTS = OFS_ACT_VPCARDS + 1;
    int OFS_ACT_CURRENTLONGESTROAD = OFS_ACT_NPLAYEDKNIGHTS + N_PLANES;
    int OFS_ACT_LR = OFS_ACT_CURRENTLONGESTROAD + N_PLANES*3;
    int OFS_ACT_LA = OFS_ACT_LR + 1;
    int OFS_ACT_CANBUYCARD = OFS_ACT_LA + 1;
    int OFS_ACT_CANBUILDROAD = OFS_ACT_CANBUYCARD + 1;
    int OFS_ACT_CANBUILDSETTLEMENT = OFS_ACT_CANBUILDROAD + 1;
    int OFS_ACT_CANBUILDCITY = OFS_ACT_CANBUILDSETTLEMENT + 1;
    int OFS_ACT_CANBANKORPORTTRADE = OFS_ACT_CANBUILDCITY + 1;
    int OFS_ACT_OVER7CARDS = OFS_ACT_CANBANKORPORTTRADE + 1;
    int OFS_ACT_DISTANCETOPORT = OFS_ACT_OVER7CARDS + 1;
    int OFS_ACT_DISTANCETOLEGAL = OFS_ACT_DISTANCETOPORT + 1;
    int OFS_ACT_DISTANCETOOPP = OFS_ACT_DISTANCETOLEGAL + 1;
    
    int ACTION_VECTOR_SIZE = OFS_ACT_DISTANCETOOPP + 1;
    
}
