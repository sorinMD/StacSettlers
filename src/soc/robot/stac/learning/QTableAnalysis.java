package soc.robot.stac.learning;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import soc.robot.stac.learning.JMTLearner.JMTAction;
import soc.robot.stac.learning.JMTLearner.JMTQLearner;
import soc.robot.stac.learning.JMTLearner.JMTState;

// Class to analyse Q-tables output of JMT learning processes
// Arguments = Q-table filenames
// One argument: output the set of all states for which the action has been learned (ie Use-JMT)
// Multiple arguments: output the set of all states for which learned actions are different.  
//   Use the larger q-table first, as elements occurring in the second but not first will be ignored
public class QTableAnalysis {

    /**
     * @param args arg = filename of q table
     */
    public static void main(String[] args) throws Exception {
        Map<JMTState, JMTAction> learnedActions[] = new Map[args.length];
        for (int i=0; i<args.length; i++) {
            BufferedReader qt = new BufferedReader(new FileReader(new File(args[i])));
            JMTQLearner l = JMTQLearner.fromStream(qt, 0, 0, 0, 0);
            Map<JMTQLearner.QKey, Double> q = l.getQ();
            learnedActions[i] = new HashMap<JMTState, JMTAction>();
            
            for (JMTQLearner.QKey k : q.keySet()) {
                JMTState s = k.getFirst();
                // Only do it once
                if (!learnedActions[i].containsKey(s)) {
                    // Try both options (may not be applicable, which is fine - try false first)
                    JMTQLearner.QKey kFalse = l.new QKey(s, JMTAction.NO_JMT);
                    Double qFalse = q.get(kFalse);
                    JMTQLearner.QKey kTrue = l.new QKey(s, JMTAction.USE_JMT);
                    Double qTrue = q.get(kTrue);
                    
                    // We only care if both are non-null, otherwise nothing has been learned or there
                    //  is no decision to make
                    if (qFalse!=null && qTrue !=null) {
                        int c = qFalse.compareTo(qTrue);
                        if (c<0) {
                            // false is lower 
                            //System.out.println(s.toString() + ": TRUE");  
                            learnedActions[i].put(s, JMTAction.USE_JMT);
                        }
                        else if (c>0) {
                            // false is higher
                            //System.out.println(s.toString() + ": FALSE"); 
                            learnedActions[i].put(s, JMTAction.NO_JMT);
                        }
                        // Otherwise, nothing has been learned
                    }
                    
                }
            }
        }
        
        // Sort the set of states from the first table
        List<JMTState> sortedStates = new ArrayList<JMTState>();
        sortedStates.addAll(learnedActions[0].keySet());
        Collections.sort(sortedStates);
        
        for (JMTState s : sortedStates) {
            JMTAction a = learnedActions[0].get(s);
            if (args.length == 1) {            
                if (a == JMTAction.USE_JMT) {
                    System.out.println(s.toString());
                }
            }
            else {
                // Output if the tables differ in at least one place
                boolean diff = false;                
                String out = s.toString() + ": " + a.toString() + ",";
                for (int i=1; i<args.length; i++) {
                    JMTAction aa = learnedActions[i].get(s);
                    if (aa == null) {
                        diff = true;
                        out += "null,";
                    }
                    else {
                        out += aa.toString() + ",";
                        if (!aa.equals(a)) {
                            diff = true;
                        }
                    }
                }
                if (diff) {
                    System.out.println(out);
                }
            }
        }
    }

}
