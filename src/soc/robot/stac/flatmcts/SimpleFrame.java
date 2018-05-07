package soc.robot.stac.flatmcts;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

/**
 * A utility for displaying the MCTS search when performing manual simulations.
 * @author MD
 */
public class SimpleFrame extends JFrame {
    public Component comp;
    public SimpleFrame(Component comp, String title) {
        super(title);
        this.comp = comp;
        getContentPane().add(BorderLayout.CENTER, comp);
        pack();
        this.setVisible(true);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        repaint();
    }
}

