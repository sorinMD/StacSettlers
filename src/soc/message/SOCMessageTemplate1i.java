/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * This file Copyright (C) 2008,2010 Jeremy D Monin <jeremy@nand.net>
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * The maintainer of this program can be reached at jsettlers@nand.net
 **/
package soc.message;

// import java.util.StringTokenizer;

//import java.io.StringWriter;
//import javax.xml.parsers.DocumentBuilder;
//import javax.xml.parsers.DocumentBuilderFactory;
//import javax.xml.transform.OutputKeys;
//import javax.xml.transform.Transformer;
//import javax.xml.transform.TransformerFactory;
//import javax.xml.transform.dom.DOMSource;
//import javax.xml.transform.stream.StreamResult;
//
//import org.w3c.dom.Document;
//import org.w3c.dom.Element;

/**
 * Template for per-game message types with 1 integer parameter.
 * You will have to write parseDataStr, because of its return
 * type and because it's static.
 *<P>
 * Sample implementation:
 *<code>
 *   public static SOCLongestRoad parseDataStr(String s)
 *   {
 *       String ga; // the game name
 *       int pn; // the seat number
 *
 *       StringTokenizer st = new StringTokenizer(s, sep2);
 *
 *       try
 *       {
 *           ga = st.nextToken();
 *           pn = Integer.parseInt(st.nextToken());
 *       }
 *       catch (Exception e)
 *       {
 *           return null;
 *       }
 *
 *        return new SOCLongestRoad(ga, pn);
 *   }
 *</code>
 *
 * @author Jeremy D Monin <jeremy@nand.net>
 */
public abstract class SOCMessageTemplate1i extends SOCMessage
    implements SOCMessageForGame
{
    /**
     * Name of the game.
     */
    protected String game;

    /**
     * Single integer parameter.
     */
    protected int p1;

    /**
     * Create a new message.
     *
     * @param id  Message type ID
     * @param ga  Name of game this message is for
     * @param p   Parameter
     */
    protected SOCMessageTemplate1i(int id, String ga, int p)
    {
        messageType = id;
        game = ga;
        p1 = p;
    }

    /**
     * @return the name of the game
     */
    public String getGame()
    {
        return game;
    }

    /**
     * @return the single parameter
     */
    public int getParam()
    {
        return p1;
    }

    /**
     * MESSAGETYPE sep game sep2 param
     *
     * @return the command String
     */
    public String toCmd()
    {
        return toCmd(messageType, game, p1);
    }

    /**
     * MESSAGETYPE sep game sep2 param
     *
     * @param messageType The message type id
     * @param ga  the game name
     * @param param The parameter
     * @return    the command string
     */
    public static String toCmd(int messageType, String ga, int param)
    {
        return Integer.toString(messageType) + sep + ga + sep2 + param;
    }

    /**
     * Parse the command String into a MessageType message
     *
     * @param s   the String to parse
     * @return    a LongestRoad message, or null if parsing errors
    public static SOCLongestRoad parseDataStr(String s)
    {
        String ga; // the game name
        int pn; // the seat number

        StringTokenizer st = new StringTokenizer(s, sep2);

        try
        {
            ga = st.nextToken();
            pn = Integer.parseInt(st.nextToken());
        }
        catch (Exception e)
        {
            return null;
        }

        return new SOCLongestRoad(ga, pn);
    }
     */

    /**
     * @return a human readable form of the message
     */
    public String toString()
    {
        return getClassNameShort() + ":game=" + game + "|param=" + p1;
    }

    /**
     * Create an XML representation of the message.
     * This is intended to be used for exchanging information with the Toulouse parser.
     * @return string with an XML representation of the message
     */
    public String toXML() {
        return "<" + getClassNameShort() + "><game>" + game + "</game><param>" + p1 + "</param></" + getClassNameShort() + ">";
//        String result = null;
//        
//        DocumentBuilderFactory icFactory = DocumentBuilderFactory.newInstance();
//        DocumentBuilder icBuilder;
//        try {
//            icBuilder = icFactory.newDocumentBuilder();
//            Document doc = (Document) icBuilder.newDocument();
//            Element mainRootElement = doc.createElementNS("http://settlers.inf.ed.ac.uk/STACSettlersXMLDOM", "STACSettlersParserInterface");
//            doc.appendChild(mainRootElement);
//
//            String nodeName = this.getClass().toString();
//            if (this instanceof SOCDiceResult) {
//                nodeName = "dice-roll";
//            }
//            // append child elements to root element
//            Element diceResult = doc.createElement("result");
//            diceResult.setAttribute("num", Integer.toString(getParam()));
//
//            Element classNode = doc.createElement(nodeName);            
//            classNode.appendChild(diceResult);
//            
//            mainRootElement.appendChild(classNode);
//            
//            Transformer transformer = TransformerFactory.newInstance().newTransformer();
//            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
//            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
//            StreamResult streamResult = new StreamResult(new StringWriter());
//            DOMSource source = new DOMSource(doc);
//            transformer.transform(source, streamResult);
//            result = streamResult.getWriter().toString();
////            System.err.println("Result: " + result);
//                        
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//        return result;
    }
}
