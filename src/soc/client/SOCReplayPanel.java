/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2007-2011 Jeremy D Monin <jeremy@nand.net>
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
package soc.client;

import java.awt.Button;
import java.awt.Color;
import java.awt.Font;
import java.awt.Label;
import java.awt.Panel;
import java.awt.TextField;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;


/**
 * This class is a panel that provides functionality for playing/pausing replays.  Just three
 * buttons for now.
 */
public class SOCReplayPanel extends Panel implements ActionListener
{
    Label title;
    Button playBut;
    Button pauseBut;  
    Button toTextBut;
    Button toTurnBut;
    Button toBrkBut;
    TextField brkText;
    Label turnLab;

    SOCReplayInterface pi;

    private static final String PLAY = "play";
	private static final String PAUSE = "pause";
	private static final String TO_TEXT = "toText";
	private static final String TO_TURN = "toTurn";
        private static final String TO_BREAK = "toBreakPoint";

	private SOCReplayClient rcl;

    /**
     * make a new building panel
     *
     * @param pi  the player interface that this panel is in
     */
    public SOCReplayPanel(SOCReplayInterface pi, SOCPlayerClient cl)
    {
        super();
        rcl = (SOCReplayClient) cl; // Need this unsafe cast due to the call hierarchy - this is called before we call our own constructor
        setLayout(null);

        this.pi = pi;

        setBackground(new Color(156, 179, 94));
        setForeground(Color.black);
        setFont(new Font("Helvetica", Font.PLAIN, 10));
        
        playBut = new Button(">");
        playBut.setEnabled(false);
        add(playBut);
        playBut.setActionCommand(PLAY);
        playBut.addActionListener(this);
        
        pauseBut = new Button("||");
        pauseBut.setEnabled(false);
        add(pauseBut);
        pauseBut.setActionCommand(PAUSE);
        pauseBut.addActionListener(this);
        
        toTextBut = new Button(">>");
        toTextBut.setEnabled(false);
        add(toTextBut);
        toTextBut.setActionCommand(TO_TEXT);
        toTextBut.addActionListener(this);
        
        toTurnBut = new Button(">>|");
        toTurnBut.setEnabled(false);
        add(toTurnBut);
        toTurnBut.setActionCommand(TO_TURN);
        toTurnBut.addActionListener(this);
        
        toBrkBut = new Button(">>>");
        toBrkBut.setEnabled(false);
        add(toBrkBut);
        toBrkBut.setActionCommand(TO_BREAK);
        toBrkBut.addActionListener(this);
        
        brkText = new TextField("");
        brkText.setEnabled(false);
        add(brkText);
        
        turnLab = new Label("Turn: 0", Label.LEFT);
        turnLab.setFont(new Font("SansSerif", Font.PLAIN, 12));
        add(turnLab);
    }

    /**
     * custom layout for this panel.
     * If you change the line spacing or total height laid out here,
     * please update {@link #MINHEIGHT}.
     */
    public void doLayout()
    {
        int curY = 1;
        final int lineH = ColorSquare.HEIGHT;
        final int butW = 50;
        final int margin = 2;
        final int buttonMargin = 2 * margin;
       
        playBut.setSize(butW, lineH);
        playBut.setLocation(buttonMargin, curY);     
        playBut.setEnabled(true);

        pauseBut.setSize(butW, lineH);
        pauseBut.setLocation(buttonMargin * 2 + butW, curY);
        pauseBut.setEnabled(true);
        
        toTextBut.setSize(butW, lineH);
        toTextBut.setLocation(buttonMargin * 3 + butW * 2, curY);
        toTextBut.setEnabled(true);        
        
        toTurnBut.setSize(butW, lineH);
        toTurnBut.setLocation(buttonMargin * 4 + butW * 3, curY);
        toTurnBut.setEnabled(true);
        
        curY += lineH + margin;
        toBrkBut.setSize(butW, lineH);
        toBrkBut.setLocation(buttonMargin, curY);
        toBrkBut.setEnabled(true);
        
        brkText.setSize(butW * 5, lineH);
        brkText.setLocation(buttonMargin * 2 + butW, curY);
        brkText.setEnabled(true);
        
        curY += lineH + margin;
        turnLab.setSize(100, 18);
        turnLab.setLocation(buttonMargin, curY);
    }

    /**
     * Handle button clicks in this panel.  Call the appropriate functions in the client.
     *
     * @param e button click event
     */
    public void actionPerformed(ActionEvent e)
    {
        String target = e.getActionCommand();
        if (target == PLAY) {
        	rcl.play();
        }
        else if (target == PAUSE) {
        	rcl.pause();
        }
        else if (target == TO_TEXT) {
        	rcl.toText();
        }
        else if (target == TO_TURN) {
        	rcl.toTurn();
        }
        else if (target == TO_BREAK) {
        	rcl.toBreakPoint(brkText.getText());
        }
    }
    
}
