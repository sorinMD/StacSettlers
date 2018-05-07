package soc.server.database.stac;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;

/**
 * 
 * Splits the human data into train and test. This is not recommended and cross-validation should be used. 
 * 
 * @author MD
 *
 */
public class SplitTrainTestHumanData {

	/**
	 * TODO: update paths
	 */
	static final String dirPath = "./data/human";
	static final String outDirPath = "./data/human2";
	
	public static void main(String[] args) throws IOException {
		for(int taskId = DBGameParser.ROAD_BUILDING; taskId <= DBGameParser.MOVE_ROBBER; taskId++ ){
//			if(taskId != DBGameParser.MOVE_ROBBER){
//				System.out.println("task " + taskId);
//				continue;
//			}
			
			BufferedReader reader = new BufferedReader(new FileReader(new File(dirPath + "/alldata-" + taskId + ".txt")));
			List<String> contents = new ArrayList<String>();
	        String nextLine = reader.readLine();
			while (nextLine != null) {
	            contents.add(nextLine);
	            nextLine = reader.readLine();
	        }
			
			new File(outDirPath).mkdirs();
			File trainF = new File(outDirPath + "/train-" + taskId + ".txt");
			FileWriter trainFileWriter = null;
			try {
				trainFileWriter = new FileWriter(trainF);
			} catch (IOException e1) {
				e1.printStackTrace();
			}
			
			File testF = new File(outDirPath + "/test-"+ taskId+ ".txt");
			FileWriter testFileWriter = null;
			try {
				testFileWriter = new FileWriter(testF);
			} catch (IOException e1) {
				e1.printStackTrace();
			}
			
			
			int counter = 0;
			for(String s: contents){
				counter++;
				if(counter <= 8){
					trainFileWriter.append(s);
					trainFileWriter.append("\n");
					trainFileWriter.flush();
					
				}else{
					testFileWriter.append(s);
					testFileWriter.append("\n");
					testFileWriter.flush();
					
					if(counter == 10)
						counter = 0;//reset it
				}
				
			}
			//finally close the stream for training
			try {	
				trainFileWriter.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
			
			//close the stream for testing
			try {	
				testFileWriter.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
			
			//copy the metadata file twice in the new folder
			File metadata = new File(dirPath + "/alldata-" + taskId + "-metadata.txt");
			File trainmetadata = new File(outDirPath + "/train-" + taskId + "-metadata.txt");
			File testmetadata = new File(outDirPath + "/test-" + taskId + "-metadata.txt");
			copyFile(metadata, trainmetadata);
			copyFile(metadata, testmetadata);
			
		}
			
	}
	
	
	 public static void copyFile(File sourceFile, File destFile) throws IOException {
	     if(!destFile.exists()) {
	      destFile.createNewFile();
	     }

	     FileChannel source = null;
	     FileChannel destination = null;
	     try {
	      source = new RandomAccessFile(sourceFile,"rw").getChannel();
	      destination = new RandomAccessFile(destFile,"rw").getChannel();

	      long position = 0;
	      long count    = source.size();

	      source.transferTo(position, count, destination);
	     }
	     finally {
	      if(source != null) {
	       source.close();
	      }
	      if(destination != null) {
	       destination.close();
	      }
	    }
	 }
	

}
