/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package soc.dialogue;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.Random;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import resources.Resources;
import soc.game.SOCGame;

/**
 *
 * @author markus
 */
public abstract class StacDialogueManager {

    /** Static fields that define type of messages, information shared etc. */
    //Signalling end of chat or starting a turn
    protected static final String NULL_CHAT = "NULL:";
    protected static final String START_TURN = "ST";

    public abstract SOCGame getGame();
    public abstract int getPlayerNumber();

    /**
     * This dialogue manager's parser for SDRT structures
     */
    protected StacSDRTParser sdrtParser;
        
    private Document xmlDoc; //XML file, used to query
    private XPath xpath;
    public static final String xmlQueryMessageSuffix = "/message/text()";

    /** Random numbers */
    protected Random random;

    public StacDialogueManager() {
        this.random = new Random();

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        try{
            xpath = (XPathFactory.newInstance()).newXPath();
            DocumentBuilder builder = factory.newDocumentBuilder();
    		File f = new File("nlTemplates.xml");
    		if(f.exists()){
    			try {
    				xmlDoc = builder.parse(f);
    			} catch (FileNotFoundException e) {
    				e.printStackTrace();
    			}
    		}else{
    			URL url = Resources.class.getResource(Resources.nlTemplatesName);
    			InputStream is = url.openStream();
    			xmlDoc = builder.parse(is);
    		}
        }
        catch (ParserConfigurationException | SAXException | IOException e){
            soc.debug.D.ebugERROR(e.getMessage());
        }
    }
    
    public StacSDRTParser getSdrtParser() {
        return sdrtParser;
    }

    /** 
     * Query the XML file for an NL expression.
     * (This was originally in PersuasionGenerator.)
     * @param query         the full query used for the xml file
     * @param replaceTable  a one to one mapping of values that need to be replaced
     * @return
     */
   protected String queryXMLFile(String query, HashMap<String,String> replaceTable){
       String message = "";
       try {
           XPathExpression expr = xpath.compile(query);
           NodeList nodes = (NodeList) expr.evaluate(xmlDoc, XPathConstants.NODESET);
           try{
               message = nodes.item(random.nextInt(nodes.getLength())).getNodeValue();
           }
           catch (IllegalArgumentException e){
               soc.debug.D.ebugERROR("Could not find Query for " + query + "\nEXCEPTION:\n" + e.toString());
               return "";
           }
           for (String a: replaceTable.keySet()){
               message = message.replaceAll(a, replaceTable.get(a));
           }
       } catch (XPathExpressionException e) {
           e.printStackTrace();
       } catch (ArrayIndexOutOfBoundsException e) {
           e.printStackTrace();
       }
       //Capitalize the first character
       message = message.substring(0,1).toUpperCase() + message.substring(1);
       return message;
   }
   

    // | is used as a delimiter both for messages, and for data types (eg resourceset)
    //  When sending data types in messages, use a different separator.  Helpers to manage this:
    // TODO: These should probably be handled outside (ie send messages with |, let Brain realize
    //  these need replacing, and vice versa)...
    // Not sure we should overflow the brain implementation with such handling of messages; maybe TradeMessage class would be a better fit for containing these or just leave them here?
    public static String fromMessage(String s) {
        return s.replace('~', '|');
}
    public static String toMessage(String s) {
        return s.replace('|', '~');
    }
}
