package soc.robot.stac.flatmcts;
import javax.swing.*;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.util.HashMap;

/**
 * The graphical part of the MCTS has been taken from http://www.mcts.ai and it has only small additions and changes. It is only used as a utility
 * for debugging purposes.
 * @author MD
 */
public class TreeView extends JComponent {
    TreeNode root;
    int nw = 30;
    int nh = 20;
    int inset = 20;
    int minWidth = 300;
    int heightPerLevel = 40;
    Color fg = Color.black;
    Color bg = Color.cyan;
    Color nodeBg = Color.white;
    Color highlighted = Color.red;
    // the highlighted set of nodes...
    HashMap<TreeNode, Color> high;

    public TreeView(TreeNode root) {
        this.root = root;
        high = new HashMap<TreeNode, Color>();
    }

    public void paintComponent(Graphics gg) {
        // Font font =
        // g.setFont();
        Graphics2D g = (Graphics2D) gg;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int y = inset;
        int x = getWidth() / 2;
        g.setColor(bg);
        g.fillRect(0, 0, getWidth(), getHeight());
        draw(g, root, x, y, (int) (1.1 * getWidth()) - inset * 0);
    }

    private void draw(Graphics2D g, TreeNode cur, int x, int y, int wFac) {
        // draw this one, then it's children

        int arity = cur.arity();
        for (int i = 0; i < arity; i++) {
            if (cur.children[i].nVisits > 0) {
                int xx = (int) ((i + 1.0) * wFac / (arity + 1) + (x - wFac / 2));
                int yy = y + heightPerLevel;
                g.setColor(fg);
                g.drawLine(x, y, xx, yy);
                draw(g, cur.children[i], xx, yy, wFac / arity);
            }
        }
        //when drawing the actual node, add a label over it to get the tooltip text
        BlankArea blankArea = new BlankArea(cur);
        add(blankArea);
        blankArea.setLocation(x-10, y-10);
        blankArea.setSize(20, 20);
        drawNode(g, cur, x, y);
    }

    private void drawNode(Graphics2D g, TreeNode node, int x, int y) {
        String s = (int) node.totValue + "/" + (int) node.nVisits;
        g.setColor(nodeBg);
        // if (high.contains(node)) g.setColor(highlighted);
        g.fillOval(x - nw / 2, y - nh / 2, nw, nh);
        g.setColor(fg);
        g.drawOval(x - nw / 2, y - nh / 2, nw, nh);
        g.setColor(fg);
        FontMetrics fm = g.getFontMetrics();
        Rectangle2D rect = fm.getStringBounds(s, g);
        g.drawString(s, x - (int) (rect.getWidth() / 2), (int) (y + rect.getHeight() / 2));
    }

    public Dimension getPreferredSize() {
        // should make this depend on the tree ...
        return new Dimension(minWidth, heightPerLevel * (10 - 1) + inset * 2);
    }

    public TreeView showTree(String title) {
        new SimpleFrame(this, title);
        return this;
    }
    
    /**
     * Simple class modelling a JLabel that will provide information when hovering over it.
     * Intended for the graphical display of the MCTS tree.
     * @author MD
     *
     */
    private static class BlankArea extends JLabel{
        Dimension minSize = new Dimension(10, 10);
        TreeNode node;
        
        public BlankArea(TreeNode n) {
        	setBackground(new Color(0, 0, 0, 0f));
        	setOpaque(true);
            node = n;
            if(node.message!=null)
            	setToolTipText(node.message.toString());
            else
            	setToolTipText("Current State");
        }
     
        public BlankArea(String text){
        	setBackground(new Color(0, 0, 0, 0f));
        	setOpaque(true);
        	setToolTipText(text);
        }
        
        public Dimension getMinimumSize() {
            return minSize;
        }
     
        public Dimension getPreferredSize() {
            return minSize;
        }

    }
}
