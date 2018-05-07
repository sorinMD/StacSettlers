package soc.robot.stac.learning;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import soc.game.SOCGame;
import soc.message.SOCGameStats;
import soc.robot.stac.StacRobotBrain;
import soc.robot.stac.learning.reinforcement.QLearningAgent;
import aima.core.agent.Action;
import aima.core.learning.reinforcement.PerceptStateReward;
import aima.core.probability.mdp.ActionsFunction;

public class JMTLearner extends Learner {
    
    // TODO: Figure out optimal learning rate params
    private static final double ALPHA = 0.1;
    private static final double GAMMA = 0.95;
    private static final double EPSILON = 0.2;
    
    private static final int EPSILON_HL = 3000;  // Old = 250
    
    // Store some stats here until we decide how to rework logging for this
    private int totalGames = 0;
    private int gamesWithJMT = 0;
    
    // Store learners based on the player name
    private static final HashMap<String, JMTLearner> learners = new HashMap<String, JMTLearner>();
        
    private final JMTQLearner qLearner;
    
    private boolean jmtUsed = false;
    
    private static final String QTABLE_FILE = "jmtq.txt";
    
    public boolean isJmtUsed() {
        return jmtUsed;
    }

    // NB: Do not provide a direct constructor.  Since learning over multiple games may be involved, 
    //  we probably want to reuse a learner
    public static JMTLearner getLearner(StacRobotBrain brain) {
        JMTLearner ret = learners.get(brain.getPlayerName());
        if (ret == null) {            
            ret = new JMTLearner();
            
            learners.put(brain.getPlayerName(), ret);
        }
        
        return ret;
    }    
    

    private  JMTLearner() {
        qLearner = new JMTQLearner(ALPHA, GAMMA, EPSILON, EPSILON_HL);
    }    

    // TODO: Figure out a better way to do this, just wanted to make a placeholder and get
    //  it out of the general constructor
    public JMTLearner(String fileName) {
        JMTQLearner q;
        try {
            BufferedReader qt = new BufferedReader(new FileReader(new File(QTABLE_FILE)));
            q = JMTQLearner.fromStream(qt, ALPHA, GAMMA, EPSILON, EPSILON_HL);
        }
        catch (IOException ex) {
            ex.printStackTrace();
            q = new JMTQLearner(ALPHA, GAMMA, EPSILON, EPSILON_HL);
        }
        qLearner = q;
    }
    
    public boolean useJMT(StacRobotBrain brain, boolean productive) {
        qLearner.setIsLearning(isLearning);
        JMTPerceptStateReward psr = new JMTPerceptStateReward(brain.getPlayerNumber(), brain.getGame(), jmtUsed, productive);
        JMTAction a = qLearner.execute(psr);
        
        return (a == JMTAction.USE_JMT);
        
        // For now, ensure JMT can only be used once per game
        //  If this is successful, we'll try to relax this to make this a sequence of decisions, though we'll
        //  have to track uses of JMT in the state space at that point
        /*if (jmtUsed) {
            return false;
        }*/
        
        
        // TODO: Add configurability of JMT decision type.  For now, uncomment the behaviour you want
        
        // Sanity check - never use JMT
        //return false;
        
        // Baseline behavior - always use JMT
        //return true;
        
        // Decision behavior - use JMT if any player is at 9 points
        /*
        SOCGame game = brain.getGame();
        for (int i=0; i<4; i++) {
            if (game.getPlayer(i).getPublicVP()>8) {
                return true;
            }
        }
        return false;
        */     
    }
    
    public void setUsed() {
        jmtUsed = true;
    }

    @Override
    public void learn(StacRobotBrain brain, SOCGameStats stats) {
        if (isLearning) {
            // Determine reward
            // Note that productive is irrelevant for terminal states so use false
            JMTPerceptStateReward psr = new JMTPerceptStateReward(brain.getPlayerNumber(), brain.getGame(), jmtUsed, false);
            qLearner.execute(psr);
        }
        
        // Debug stats:
        //  TODO: Refactor this, probably output the Q table periodically
        totalGames++;
        if (jmtUsed) {
            gamesWithJMT++;
        }
        
        // TODO: Make save mode configurable - 
        if (totalGames % 100 == 0) {
            System.out.println(totalGames + " / " + gamesWithJMT);
            
        }
        // reset for next game
        jmtUsed = false;
    }
    
    public static void writeQ(String learnerName, String fileName) {
        JMTLearner l = learners.get(learnerName);
        if (l!=null) {
            try {
                BufferedWriter w = new BufferedWriter(new FileWriter(new File(fileName)));
                l.qLearner.printQTable(w, true);
                w.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    
    
    // Helper classes which define the MDP
    public static class JMTState implements Comparable<JMTState> {
        // vp[0] = my VP
        // 1...3 = sorted opponents
        private int[] vp = new int[4];
        private boolean jmtUsed;
        private boolean productive;
        
        private int hashCode;
        
        // TODO: Save some object creation by reusing our sorting list?  Might harm thread safety...
        private static final List<Integer> sorted = new ArrayList<Integer>(3);
        
        public JMTState(SOCGame g, int pNum, boolean jmtUsed, boolean productive) {
            sorted.clear();
            for (int i=0; i<4; i++) {
                if (i == pNum) {
                    vp[0] = g.getPlayer(i).getPublicVP();
                }
                else {
                    sorted.add(Integer.valueOf(g.getPlayer(i).getPublicVP()));
                }
            }
            Collections.sort(sorted);
            for (int i=0; i<3; i++) {
                // Do it backwards to allow for a more logical sort
                vp[3-i] = sorted.get(i).intValue();
            }
            // for now, shrink the state space
            // shrink vp[2] by a factor of 2 to compensate for the new productive flag
            vp[2]=vp[2]/2;
            vp[3]=0;
            this.jmtUsed = jmtUsed;
            
            this.productive = productive;
            
            calcHash();        
        }  
        
        // Private empty constructor if we are going to be setting fields ourselves (ie parsing from a file)
        private JMTState() {  }
        
        public boolean isTerminal() {
            for (int i=0; i<4; i++) {
                if (vp[i]>=10) {
                    return true;
                }
            }
            return false;
        }
        
        @Override
        public boolean equals(Object obj) {
            if (obj instanceof JMTState) {
                JMTState j = (JMTState) obj;
                if (j.jmtUsed!=jmtUsed) return false;
                if (j.productive!=productive) return false;
                for (int i=0; i<4; i++) {
                    if (j.vp[i] != vp[i]) {
                        return false;
                    }
                }
                return true;
            }
            else {
                return false;
            }
        }
        
        public String toString() {
            String ret = new String();
            for (int i=0; i<4; i++) {
                ret += vp[i] + ",";
            }
            ret += jmtUsed + ",";
            ret += productive;
            return ret;
        }
        
        public static JMTState parse(String s) {
            JMTState ret = new JMTState();
            
            String[] p = s.split(",");
            for (int i=0; i<4; i++) {
                ret.vp[i] = Integer.parseInt(p[i]);
            }
            ret.jmtUsed = Boolean.parseBoolean(p[4]);
            if (p.length > 5) {
                ret.productive = Boolean.parseBoolean(p[5]);
            }
            
            ret.calcHash();
            return ret;
        }

        @Override
        public int hashCode() {
            return hashCode;
        }
        
        private int calcHash() {
            int ret = 0;
            for (int i=0; i<4; i++) {
                ret+=vp[i]-2;
                ret*=10;                
            }
            ret*=2;
            if (jmtUsed) {
                ret++;
            }
            ret*=2;
            if (productive) {
                ret++;
            }
            hashCode = ret;
            return ret;
        }

        @Override
        public int compareTo(JMTState o) {
            return hashCode - o.hashCode ;
        }

       

             
    }
    
    public static class JMTAction implements Action {
        private boolean useJmt;
        private boolean noop;
        public static final JMTAction USE_JMT = new JMTAction(true);
        public static final JMTAction NO_JMT = new JMTAction(false);
        public static final JMTAction NOOP = new JMTAction(false, true);
        
        private JMTAction(boolean useJmt) {
            this (useJmt, false);
        }
        
        private JMTAction(boolean useJmt, boolean noop) {
            this.noop = noop;
            this.useJmt = useJmt;
        }
        
        @Override
        public boolean isNoOp() {
            return noop;
        }
        
        public String toString() {
            return Boolean.toString(useJmt);
        }
        
        public static JMTAction parse(String s) {
            return (Boolean.valueOf(s)) ? USE_JMT : NO_JMT;            
        }
    }
    
    public static class JMTActionsFunction implements ActionsFunction<JMTState, JMTAction> {

        private static final Set<JMTAction> ALL;
        // For now, force the agent to only use JMT once per game
        private static final Set<JMTAction> HAS_USED_JMT;
        // Noop only
        private static final Set<JMTAction> NOOP;
        
        public static final JMTActionsFunction FUNC = new JMTActionsFunction();
        
        static {
            ALL = new HashSet<JMTAction>();
            HAS_USED_JMT = new HashSet<JMTAction>();
            ALL.add(JMTAction.USE_JMT);
            ALL.add(JMTAction.NO_JMT);
            HAS_USED_JMT.add(JMTAction.NO_JMT);
            
            NOOP = new HashSet<JMTAction>();
            NOOP.add(JMTAction.NOOP);
        }
        
        @Override
        public Set<JMTAction> actions(JMTState state) {
            if (state.isTerminal()) {
                return NOOP;
            }
            return state.jmtUsed ? HAS_USED_JMT : ALL;
        }
        
    }
    
    public static class JMTPerceptStateReward implements PerceptStateReward<JMTState> {
        
        private JMTState state;
        public JMTPerceptStateReward(int pNum, SOCGame game, boolean jmtUsed, boolean productive) {
            this.state = new JMTState(game, pNum, jmtUsed, productive);
        }

        @Override
        public double reward() {
            // TODO: Would it be better to use VP instead, perhaps smooth some randomness?            
            if (state.isTerminal()) {
                // TODO: Make this selection configurable?  For now, just uncomment the desired behavior
                return (10* ( ((double)state.vp[0]) - 7.8));
                //return (state.vp[0] >= 10) ? 75 : -25;
            }
            else {
                return 0;
            }
        }

        @Override
        public JMTState state() {
            return state;
        }
        
    }

    public static class JMTQLearner extends QLearningAgent<JMTState, JMTAction> {

        public JMTQLearner(
                double alpha, double gamma, double epsilon, int epsilonHL) {
            super(JMTActionsFunction.FUNC, JMTAction.NOOP, alpha, gamma, epsilon, epsilonHL);
        }        
        
        @Override
        protected JMTAction breakTie(List<JMTAction> actionList) {
            return JMTAction.NO_JMT;
        }
        
        // Initialize an agent from a file
        public static JMTQLearner fromStream(BufferedReader r, double alpha, double gamma, double epsilon, int epsilonHL) throws IOException {
            JMTQLearner q = new JMTQLearner(alpha, gamma, epsilon, epsilonHL);

            String s;
            while ((s = r.readLine()) != null) {
                String[] p = s.split("/");
                JMTState js = JMTState.parse(p[0]);
                JMTAction ja = JMTAction.parse(p[1]);
                double qv = Double.parseDouble(p[2]);
                
                q.Q.put(q.new QKey(js, ja), Double.valueOf(qv));                
            }
            
            return q;
        }
        
        @Override
        protected boolean isTerminal(JMTState s) {
            return s.isTerminal();
        }
        
        protected Map<QKey, Double> getQ() {
            return Q;
        }
    }
}
