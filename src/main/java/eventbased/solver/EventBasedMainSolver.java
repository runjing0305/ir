package eventbased.solver;

import context.ProblemContext;
import eventbased.graph.Graph;
import eventbased.graph.NodeGraph;
import eventbased.model.EventBasedModel;
import gurobi.GRB;
import gurobi.GRBException;
import solution.Solution;

import java.util.List;
import java.util.Set;

/**
 * @author longfei
 */
public class EventBasedMainSolver {

    public Solution solve(ProblemContext problemContext, Graph eventBasedGraph, List<NodeGraph> nodeGraphList, EventBasedModel model, Solution solution, Set<String> updatedCourses) {

        try {
            model.getGrbModel().set(GRB.DoubleParam.MIPGap, 0.005);
            model.getGrbModel().set(GRB.IntParam.Method, 1);
            model.getGrbModel().set(GRB.DoubleParam.TimeLimit, 300.0);
            model.getGrbModel().optimize();
            model.recoverySolutionData(problemContext, eventBasedGraph, nodeGraphList, solution, updatedCourses);

            model.getGrbModel().dispose();
            model.getGrbEnv().dispose();
        } catch (GRBException grbException) {
            grbException.printStackTrace();
        }

        return solution;
    }
}
