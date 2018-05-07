package soc.server.database;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Date;
import java.util.List;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtilities;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import soc.game.SOCGame;

import soc.robot.stac.learning.JMTLearner;
import soc.robot.stac.learning.Learner;

// Logger which generates learning graphs
//  Hack: have this take control of turning learning on/off
public class LearningLogger extends DBLogger {    
    
    private String perspective;
    private String gameName;
    
    private int frequency;
    private int testGames;
    
    private int iteration;
    private int gamesInIteration;
    private int winsInIteration;
    private int totalGames;
    
    private XYSeries series1;
    
    // Default isLearning to false - this will have it behave exactly as DBLogger until told otherwise
    private boolean isLearning = false;
    
	public void setPerspective(String perspective) {
        this.perspective = perspective;
    }

    public void setFrequency(int frequency) {
        this.frequency = frequency;
    }

    public void setTestGames(int testGames) {
        this.testGames = testGames;
    }

    public void setLearning(boolean isLearning) {
        this.isLearning = isLearning;
    }

    public LearningLogger() {}	
	
	public void startRun(String gameName, List<String> config) {
	    super.startRun(gameName, config);    	
	    this.gameName = gameName;
	    gamesInIteration = 0;
	    winsInIteration = 0;
	    totalGames = 0;
	    iteration = 0;
	    series1 =  new XYSeries(perspective + " wins");
	    Learner.setLearning(isLearning);
	}

	@Override
	public boolean saveGameScores(SOCGame ga) throws SQLException {
		
            boolean ret = super.saveGameScores(ga);
            
            String player1 = ga.getPlayer(0).getName();
            String player2 = ga.getPlayer(1).getName();
            String player3 = ga.getPlayer(2).getName();
            String player4 = ga.getPlayer(3).getName();
            short score1 = (short) ga.getPlayer(0).getTotalVP();
            short score2 = (short) ga.getPlayer(1).getTotalVP();
            short score3 = (short) ga.getPlayer(2).getTotalVP();
            short score4 = (short) ga.getPlayer(3).getTotalVP();

	    if (isLearning) {
    	    gamesInIteration++;
    	    totalGames ++;
    	    String pp = perspective + "_1";
    	    if (gamesInIteration == frequency) {
    	        // stop learning for test reporting
                Learner.setLearning(false);
                // TODO: Total hack!  Tell JMTlearner to output q table.
                //  Should probably have a general learner registry and tell it to do this
                JMTLearner.writeQ(pp, dirName + "/qtable_" + (iteration+1)*frequency + ".txt");
                
            }
    	    else if (gamesInIteration > frequency) {
        	    
        	    if (pp.equals(player1)) {
        	        if (score1>=10) {
        	            winsInIteration++;
        	        }
        	    }
        	    if (pp.equals(player2)) {
                    if (score2>=10) {
                        winsInIteration++;
                    }
                }
        	    if (pp.equals(player3)) {
                    if (score3>=10) {
                        winsInIteration++;
                    }
                }
        	    if (pp.equals(player4)) {
                    if (score4>=10) {
                        winsInIteration++;
                    }
                }
        	    
        	    if (gamesInIteration == frequency + testGames) {
        	        // save the scores to the time series
        	        iteration++;
        	        series1.add(frequency * iteration, ((double) winsInIteration) / ((double) testGames));
        	        
        	        
        	        XYSeriesCollection dataset = new XYSeriesCollection();
        	        dataset.addSeries(series1);
        	        JFreeChart chart = ChartFactory.createXYLineChart(
        	                gameName,      // chart title
        	                "Games",                      // x axis label
        	                "Wins",                      // y axis label
        	                dataset,                  // data
        	                PlotOrientation.VERTICAL,
        	                true,                     // include legend
        	                true,                     // tooltips
        	                false                     // urls
        	            );
        	        
        	        
        	        try {
        	            ChartUtilities.saveChartAsPNG(new File(dirName + "/learning.png"), chart, 640, 480);
        	            
        	        }
        	        catch (IOException e) {
        	            e.printStackTrace();
        	        }
        	        
        	        
        	        // reset for our next it
        	        winsInIteration = 0;
        	        gamesInIteration = 0;
        	        
        	        Learner.setLearning(true);
        	    }
    	    }
	    }
	    return ret;
	}

	
	public void endRun() {
	    super.endRun();	
	    /* I believe this is redundant
	    if (isLearning) {
    	    XYSeriesCollection dataset = new XYSeriesCollection();
            dataset.addSeries(series1);
            JFreeChart chart = ChartFactory.createXYLineChart(
                    gameName,      // chart title
                    "Games",                      // x axis label
                    "Wins",                      // y axis label
                    dataset,                  // data
                    PlotOrientation.VERTICAL,
                    true,                     // include legend
                    true,                     // tooltips
                    false                     // urls
                );
            
            
            try {
                ChartUtilities.saveChartAsPNG(new File(RESULTS_DIR + gameName + "_learning.png"), chart, 640, 480);
            }
            catch (IOException e) {
                e.printStackTrace();
            }
	    }
	    */
	}    
}
