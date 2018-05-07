package soc.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import soc.disableDebug.D;



/**
 * A very basic utility for deep copying objects by serializing and deserializing the original. 
 * It can either copy the object straight into a new one or write the bytes array into a file (i.e. "saving" the object).
 * 
 * @author MD
 *
 */
public class DeepCopy {
	/**
	 * Constant for the directories.  
	 */
	public static final String SAVES_DIR = "saves/";
	
	/**
     * Copy an object into a new one.
     *  
     * @param original the object to be copied
     *  @return a clone/deep copy of the original
     */
    public static Object copy(Object original) {
        Object obj = null;
        try {
            // Write the object out to a byte array
            ByteArrayOutputStream bout = new ByteArrayOutputStream();
            ObjectOutputStream out = new ObjectOutputStream(bout);
            out.writeObject(original);
            out.flush();
            out.close();

            // Make an input stream from the byte array and read
            // a copy of the object back in.
            ObjectInputStream in = new ObjectInputStream(
                new ByteArrayInputStream(bout.toByteArray()));
            obj = in.readObject();
        }
        catch(IOException e) {
        	D.ebugFATAL(e, "DeepCopy copy ERROR - "  + e.getClass() + e.getMessage());
        }
        catch(ClassNotFoundException e) {
        	D.ebugFATAL(e, "DeepCopy copy ERROR - "  + e.getClass() + e.getMessage());
        }
        return obj;
    }
    /**
     * This method will serialize the orginal object and write the byte array to a file with the name as: "source_original.dat", 
     * where source is either "server" or a player number corresponding to the player this information is cloned for.
     * @param original the object to be serialized and written to a file;
     */
    public static boolean copyToFile(Object original, String source, String folderName){
        try {
            // Write the object out to a byte array
            ByteArrayOutputStream bout = new ByteArrayOutputStream();
            ObjectOutputStream out = new ObjectOutputStream(bout);
            out.writeObject(original);
            out.flush();
            out.close();
            
            //remove any special characters if they could exist
            String className = original.getClass().getName();
            if(className.contains("[")){
            	className = className.replace("[]", "");
            }
            //create a file and write to it the bytes
            File file = new File(folderName + "/" + source + "_" + original.getClass().getName() + ".dat");
            byte[] byteData = bout.toByteArray();
            FileOutputStream fout = new FileOutputStream(file);
            fout.write(byteData);
            fout.close();

        }
        catch(IOException e) {
        	D.ebugFATAL(e, "DeepCopy copyToFile ERROR - "  + e.getClass() + e.getMessage());
            return false;
        }
        
        return true;
    }
    /**
     * Reads the bytes from the file in the saves directory
     * Always cast this to the required instance type;
     * @param fileName the name of the file that contains the bytes for reconstructing the object; 
     * 			Format: "source_original" where source is either server or one of the clients and original the object's class name
     * 			(don't include the termination ".dat")
     * @param isRobot boolean for differentiating between a user loading and automatic loading
     * @return the object reconstructed from the bytes inside the file;
     */
    public static Object readFromFile(String fileName){
    	Object obj = null;
    	Path path;
    	fileName = fileName + ".dat";//add the termination
    	File f = new File(fileName);	
    	//very brittle way of getting the path, but should work if the working directory always has the same structure
    	path = f.toPath();
    		
    	try{
    		byte[] data = Files.readAllBytes(path);
    		ObjectInputStream in = new ObjectInputStream(
    	            new ByteArrayInputStream(data));
    	        obj = in.readObject();
    	}catch (Exception e) {
    		D.ebugFATAL(e, "DeepCopy readFromFile ERROR - " + e.getClass() + e.getMessage());
    	}
    	return obj;
    }
    
}
