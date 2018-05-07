package soc.util;

import java.io.File;
import java.util.Vector;

import javax.swing.text.AbstractDocument.BranchElement;

import soc.game.SOCBoard;
import soc.game.SOCGame;
import soc.game.SOCPlayingPiece;
import soc.robot.StacRobotBrainInfo;
/**
 * Offline process of saving a board layout for future games. Need to save the game you are playing, move the server_soc.game.SOCGame.dat
 * to a folder saves/board and run this file. If the file is not present this utility will generate the default board for 4 players as described in the SOC manual.
 * All you need to do next time you start a new game is to modify the loadBoard flag in the config.txt file or tick the corresponding box when starting a game via
 * the SOCPlayerClient class.
 * @author MD
 */
public class SaveBoardConfigFromGame {

	public static void main(String[] args) {
		File f = new File("saves/board/server_soc.game.SOCGame");
		SOCBoard board;
		if(f.exists()){
			SOCGame game = (SOCGame) DeepCopy.readFromFile("saves/board/server_soc.game.SOCGame");
			board = game.getBoard();
			//clear the board of all the pieces 
			Vector pieces = board.getPieces();
			for(Object p : pieces){
				board.removePiece((SOCPlayingPiece)p);
			}
			//place the robber back on the desert and clear previous hex
			int [] hexes = board.getHexLandCoords();
			int desertHex = -1;
			for(int h : hexes)
				if(board.getHexTypeFromCoord(h) == SOCBoard.DESERT_HEX){
					desertHex = h;
					break;
				}
					
			board.setRobberHex(desertHex, false);
		}else{
			board = SOCBoard.generateDefaultBoard();
		}
		//write to file
		DeepCopy.copyToFile(board, "", "saves/board");
	}

}
