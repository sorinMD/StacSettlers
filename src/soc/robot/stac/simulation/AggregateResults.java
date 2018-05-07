package soc.robot.stac.simulation;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * Utility to combine several results for a given agent name from the results folder into a single results and summary file.
 * Looks for the results folder and can handle multiple folders, so it can be run over multiple experiments.
 * Note: it ignores most of the summary contents at the moment
 * 
 * @author MD
 *
 */
public class AggregateResults {

	public static void listf(String directoryName, ArrayList<File> files) {
	    File directory = new File(directoryName);

	    // get all the files from a directory
	    File[] fList = directory.listFiles();
	    for (File file : fList) {
	        if (file.isFile()) {
	            files.add(file);
	        } else if (file.isDirectory()) {
	            listf(file.getAbsolutePath(), files);
	        }
	    }
	    
	}
		
	public static void findResultsDirectory(String directoryName, ArrayList<File> directories) {
	    File directory = new File(directoryName);

	    // get all the files from a directory
	    File[] fList = directory.listFiles();
	    for (File file : fList) {
	    	if (file.isDirectory()) {
	    		if(file.getName().equals("results"))
	    			directories.add(file);
	    		findResultsDirectory(file.getAbsolutePath(), directories);
	        }
	    }
	    
	}
	
	public static void main(String[] args) {
		String agentName = "Agent";
		if(args.length > 0)
			agentName = args[0];
		
		ArrayList<File> dirs = new ArrayList<File>();
		findResultsDirectory("./", dirs);

		for(File dir : dirs){
			//check if we already collected the results
			File results = new File(dir, "results_summary.txt");
			try {
				results.createNewFile();
			} catch (IOException e) {
				e.printStackTrace();
			}
			
			int[] averageVPs = new int[12];
			Arrays.fill(averageVPs, 0);
			BufferedReader reader = null;
			
			double[] statsModifiedAgent = new double[15];
			Arrays.fill(statsModifiedAgent, .0);
			double[] statsBaseline = new double[15];
			Arrays.fill(statsBaseline, .0);
			int totalNoGames = 0;
			
			ArrayList<File> files = new ArrayList<File>();
			listf(dir.getAbsolutePath(), files);
			for(File f : files){
				if(f.getName().equals("results.txt")){
					try {
						reader = new BufferedReader(new FileReader(f));
					} catch (FileNotFoundException e) {
						e.printStackTrace();
					}
					try {
						String nextLine = reader.readLine();
						nextLine = reader.readLine();
						
				        while (nextLine != null) {
				        	String[] parts = nextLine.split(agentName);
				            parts = parts[1].split("\\s");
				            if(Integer.parseInt(parts[2]) < 12)
				            	averageVPs[Integer.parseInt(parts[2])]++;
				            else
				            	averageVPs[11]++; //higher than 11 vp??? why???
				        	nextLine = reader.readLine();
				        }
					}catch(Exception e){
						e.printStackTrace();
					}
					
				}else if(f.getName().equals("summary.txt")){
					try {
						reader = new BufferedReader(new FileReader(f));
					} catch (FileNotFoundException e) {
						e.printStackTrace();
					}
					
					try {
						String nextLine = reader.readLine();
						nextLine = reader.readLine();
						String[] p = nextLine.split("=");
						int noGames = Integer.parseInt(p[1]); 
						totalNoGames += noGames;
				        int counter = 1;
						while (nextLine != null) {
							//lines 5678 only
							if(counter > 4 && counter <9){
								String[] parts = nextLine.split("\\s");
								//TODO:make a note of what each of these are
								if(parts[0].equals(agentName)){
									statsModifiedAgent[0] += Double.parseDouble(parts[19])*noGames;
									statsModifiedAgent[1] += Double.parseDouble(parts[20])*noGames;
									statsModifiedAgent[2] += Double.parseDouble(parts[21])*noGames;
									statsModifiedAgent[3] += Double.parseDouble(parts[22])*noGames;
									statsModifiedAgent[4] += Double.parseDouble(parts[23])*noGames;
									statsModifiedAgent[5] += Double.parseDouble(parts[24])*noGames;//LA
									statsModifiedAgent[6] += Double.parseDouble(parts[25])*noGames;//LR
									statsModifiedAgent[7] += Double.parseDouble(parts[12])*noGames;
									statsModifiedAgent[8] += Double.parseDouble(parts[11])*noGames;
									statsModifiedAgent[9] += Double.parseDouble(parts[13])*noGames;
									statsModifiedAgent[10] += Double.parseDouble(parts[14])*noGames;
									statsModifiedAgent[11] += Double.parseDouble(parts[15])*noGames;
									statsModifiedAgent[12] += Double.parseDouble(parts[16])*noGames;
									statsModifiedAgent[13] += Double.parseDouble(parts[17])*noGames;
									statsModifiedAgent[14] += Double.parseDouble(parts[18])*noGames;
								}else{
									statsBaseline[0] += Double.parseDouble(parts[19])*noGames;
									statsBaseline[1] += Double.parseDouble(parts[20])*noGames;
									statsBaseline[2] += Double.parseDouble(parts[21])*noGames;
									statsBaseline[3] += Double.parseDouble(parts[22])*noGames;
									statsBaseline[4] += Double.parseDouble(parts[23])*noGames;
									statsBaseline[5] += Double.parseDouble(parts[24])*noGames;//LA
									statsBaseline[6] += Double.parseDouble(parts[25])*noGames;//LR
									statsBaseline[7] += Double.parseDouble(parts[12])*noGames;
									statsBaseline[8] += Double.parseDouble(parts[11])*noGames;
									statsBaseline[9] += Double.parseDouble(parts[13])*noGames;
									statsBaseline[10] += Double.parseDouble(parts[14])*noGames;
									statsBaseline[11] += Double.parseDouble(parts[15])*noGames;
									statsBaseline[12] += Double.parseDouble(parts[16])*noGames;
									statsBaseline[13] += Double.parseDouble(parts[17])*noGames;
									statsBaseline[14] += Double.parseDouble(parts[18])*noGames;
								}
								
							}
				        	nextLine = reader.readLine();		        	
				        	counter++;
				        }
					}catch(Exception e){
						e.printStackTrace();
					}
				}
			}
			
			//write the aggregated results to the master results file
			BufferedWriter bw = null;
			FileWriter fw = null;
						
			try {

				fw = new FileWriter(results);
				bw = new BufferedWriter(fw);
				bw.write(Arrays.toString(averageVPs) + "\n");
				int sum = 0;		
				for(int i = 0; i < averageVPs.length; i++){
					sum+= averageVPs[i];
				}			
				double winPercentage = (((double)averageVPs[10] + averageVPs[11])/sum)*100;
				DecimalFormat df = new DecimalFormat("#.00"); 
				bw.write(sum + "\n");
				bw.write("Win rate: " + df.format(winPercentage) + "% \n\n");

				//make an average of the weighted values from the summary file;
				for(int j = 0; j < statsModifiedAgent.length; j++){ //1 modified agent
					statsModifiedAgent[j]/=totalNoGames;
				}
				for(int j = 0; j < statsBaseline.length; j++){ //3 baseline agents
					statsBaseline[j]/=totalNoGames*3;
				}
				DecimalFormat decimalFormat = new DecimalFormat("#.########");
				
				bw.write("Modified agent stats:  ");
				
				for(int j = 0; j < statsModifiedAgent.length; j++){
					bw.write(decimalFormat.format(statsModifiedAgent[j]) + "   ");
				}
				bw.write("\n");
				bw.write("Baseline agent stats:  ");
				for(int j = 0; j < statsModifiedAgent.length; j++){
					bw.write(decimalFormat.format(statsBaseline[j]) + "   ");
				}
				bw.write("\n");
				
			} catch (IOException e) {

				e.printStackTrace();

			} finally {
				try {
					if (bw != null)
						bw.close();
					if (fw != null)
						fw.close();
				} catch (IOException ex) {
					ex.printStackTrace();
				}
			}
	
		}
	}

}
