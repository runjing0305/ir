package eventbased.graph;

import context.ProblemContext;
import solution.Solution;

/**
 * @author longfei
 */
public interface EdgeInfeasible {
    /**
     * The Edge is feasible or not
     *
     * @param headVertex     Head Vertex
     * @param tailVertex     Tail Vertex
     * @param problemContext Problem Context Data
     * @return true -> feasible, false -> infeasible
     */
    boolean isInfeasible(Vertex headVertex, Vertex tailVertex, ProblemContext problemContext, Solution solution);
}
