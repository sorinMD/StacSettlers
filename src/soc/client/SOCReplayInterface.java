package soc.client;

import soc.game.SOCGame;

/**
 * Interface for the Replay client.  Very similar to the PlayerInterface, but uses a panel for
 *  playback control instead of the Building Panel (bottom center). 
 * 
 * @author kho30
 *
 */
public class SOCReplayInterface extends SOCPlayerInterface {

	protected SOCReplayPanel replayPanel;
	
	protected SOCReplayClient rcl;
	
	public SOCReplayInterface(String title, SOCReplayClient cl, SOCGame ga) {
		super(title, cl, ga);
		rcl = cl;
	}
	
	protected void initInterfaceElements(boolean firstCall)
    {
		super.initInterfaceElements(firstCall);
		remove(buildingPanel);
		// Now add the new panel
		replayPanel = new SOCReplayPanel(this, client);
		replayPanel.setSize(buildingPanel.getWidth(), buildingPanel.getHeight());
		add(replayPanel);	
    }
	
	// When adding players, mark them as "playerIsClient" to show resources
	public void addPlayer(String n, int pn)
    {
		// Fake that we are this client to ensure it's added in the proper mode
		String oldName = rcl.getNickname();
		rcl.setNickname(n);
		super.addPlayer(n, pn);
		hands[pn].passiveMode();
		
		rcl.setNickname(oldName);
    }
	
	// Don't pop up the Dev Card dialogs
	public void showMonopolyDialog() {}
	public void showDiscoveryDialog() {}
	
	public void doLayout()
    {
		super.doLayout();
		
		// Set the replay panel to be the same size as the building panel was
		replayPanel.setBounds(buildingPanel.getBounds());
		repaint();
    }
}
