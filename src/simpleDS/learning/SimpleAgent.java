/* @description This class implements a WebServer for communicating with ConvNetJS.
 *              
 * @history 2.Nov.2015 Beta version
 *              
 * @author <ahref="mailto:h.cuayahuitl@gmail.com">Heriberto Cuayahuitl</a>
 */

package simpleDS.learning;

import java.util.ArrayList;

import simpleDS.networking.SimpleWebServer;
import simpleDS.networking.SimpleSocketHandler;
import simpleDS.util.Logger;
import simpleDS.util.StringUtil;

public class SimpleAgent extends Thread {
	private SimpleSocketHandler handler = null;
	private SimpleWebServer deepServer = null;
	private String lastDeepAction = null;
	private String lastDeepTrade_0 = null;
	private String lastDeepTrade_1 = null;
	private String lastDeepTrade_2 = null;
	private String lastDeepTrade_3 = null;
	private String numInputOutputs = null;
	public String executionMode = null;
	public String dialogues = null;
	public String verbose = null;
	public boolean connected = false;

	public SimpleAgent(String numInputOutputs) {
		this.numInputOutputs = numInputOutputs;

		try {
			Thread thread = new Thread("SERVER");
			thread.start();
			
			synchronized(thread) {
				try{
					Logger.debug(this.getClass().getName(), "SimpleAgent", "Waiting for deepServer to complete...");
					thread.wait();
					
				} catch(InterruptedException e) {
					e.printStackTrace();
				}
			}
			
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void run() {
		synchronized(this){
			Logger.debug(this.getClass().getName(), "run", "Trying to connect...");
			handler = new SimpleSocketHandler();
			handler.addMessageHandler(new SimpleSocketHandler.MessageHandler() {

				public void handleMessage(String action) {
					if (action.startsWith("Hello Server")) {
						connected = true;
						action = null;
						sendMessage(numInputOutputs);
						
					} else if (action.startsWith("params")) {
						ArrayList<String> list = StringUtil.getArrayListFromString(action, "=,");						
						for (int i=0; i<list.size(); i++) {
							if (i==3) executionMode = list.get(3);
							else if (i==4) dialogues = list.get(4);
							else if (i==5) verbose = list.get(5);
						}
						
					} else if (action.startsWith("action")) {
						String value = action.substring(action.indexOf("=")+1);
						setLastAction(value);

					} else if (action.indexOf("trade")>=0) {
						String agent = action.substring(0, action.indexOf("_"));
						String value = action.substring(action.indexOf("=")+1);
						setLastTrade(agent, value);

					} else {
						Logger.debug(this.getClass().getName(), "run", "WARNING: Unknown param:"+action);
					}
				}
			});
			deepServer = new SimpleWebServer(handler);
			
			
			while (!connected) {
				try {
					Thread.sleep(100);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
			
			Logger.debug(this.getClass().getName(), "run", "Connected to deepServer!");
			notify();
		}
	}

	public void setLastAction(String action) {
		lastDeepAction = action;
	}

	public String getLastAction() {
		return lastDeepAction;
	}

	public void setLastTrade(String agent, String action) {
		if (agent.equals("0")) lastDeepTrade_0 = action;
		else if (agent.equals("1")) lastDeepTrade_1 = action;
		else if (agent.equals("2")) lastDeepTrade_2 = action;
		else if (agent.equals("3")) lastDeepTrade_3 = action;
		else System.out.println("SimpleAgent.setLastTrade(): unknown agent="+agent);
		//lastDeepTrade = action;
	}

	public String getLastTrade(String agent) {
		if (agent.equals("0")) return lastDeepTrade_0;
		else if (agent.equals("1")) return lastDeepTrade_1;
		else if (agent.equals("2")) return lastDeepTrade_2;
		else if (agent.equals("3")) return lastDeepTrade_3;
		else { System.out.println("SimpleAgent.setLastTrade(): unknown agent="+agent); return "-1"; }
		//return lastDeepTrade;
	}

	public void sendMessage(String msg) {
		handler.sendMessage(msg);
	}
}

