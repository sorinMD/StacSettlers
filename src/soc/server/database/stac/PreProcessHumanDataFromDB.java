package soc.server.database.stac;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;


/**
 * 
 * Processor for turning the data from the STAC postgres database into a usable format for learning. 
 * 
 * @author MD
 *
 */
public class PreProcessHumanDataFromDB {

	static final String dirPath = "./data/human";
	
	public static void main(String[] args) {
	    File directory = new File(dirPath);
	    if (! directory.exists())
	        directory.mkdirs();
		//iterate over each task and repeat the below for each
		for(int taskId = DBGameParser.ROAD_BUILDING; taskId <= DBGameParser.MOVE_ROBBER; taskId++ ){
			//avoid doing anything else for now as these have already been done
//			if(taskId != DBGameParser.MOVE_ROBBER){
//				System.out.println("task " + taskId);
//				continue;
//			}
			
		File f = new File(dirPath + "/alldata-" + taskId + ".txt");
		FileWriter fileWriter = null;
		try {
			fileWriter = new FileWriter(f);
		} catch (IOException e1) {
			e1.printStackTrace();
		}
		
		
		DBGameParser parser = new DBGameParser();
		int maxSize = Integer.MIN_VALUE;//max size of the set of possible actions across all the games
		int nGames = 100;
		int totalGames = 5000;
		
		//loop over the 5k games that we have
//		for(int i = 0; i < totalGames/nGames; i++){
			ArrayList<Sample> samples = new ArrayList<Sample>();
			//loop over n games
			for(int gameID = 1 /*StacDBHelper.SIMGAMESSTARTID + i*nGames*/; gameID < 61 /*StacDBHelper.SIMGAMESSTARTID + 100 + i*nGames*/; gameID++){
				try {
						samples.addAll(parser.selectSamples(gameID, taskId));
				} catch (Exception e) {
					System.out.println("exception in game: " + gameID);
					e.printStackTrace();
				}

			}
			
			//shuffle the data
			Collections.shuffle(samples);
			
			//write it to file
			for(Sample s: samples){
				String txt= Arrays.toString(s.getRecord());
				//TODO: if the size of the set of legal actions is over a certain limit, break the sample in several ones;
				if(s.getTotalLegalActions() > maxSize)
					maxSize = s.getTotalLegalActions();
				try {
					fileWriter.append(txt.substring(1, txt.length()-1).replace(" ", ""));
					fileWriter.append("\n");
					fileWriter.flush();
				} catch (IOException e1) {
					e1.printStackTrace();
				}
			}
//		}
		//finally close the stream
		try {	
			fileWriter.close();
		} catch (IOException e) {
			e.printStackTrace();
		}

		//write the metadata
		f = new File(dirPath + "/alldata-"+ taskId+ "-metadata.txt");
		try {
			fileWriter = new FileWriter(f);
			fileWriter.append("State:" + (parser.STATE_VECTOR_SIZE));
			fileWriter.append("\n");
			fileWriter.append("Action:" + (parser.ACTION_VECTOR_SIZE));
			fileWriter.append("\n");
			fileWriter.append("Largest set of actions:" + maxSize);
			fileWriter.append("\n");
			fileWriter.flush();
			fileWriter.close();
		} catch (IOException e1) {
			e1.printStackTrace();
		}
		
		}
		
	}

}
