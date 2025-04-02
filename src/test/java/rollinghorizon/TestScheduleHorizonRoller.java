package rollinghorizon;

import context.ProblemContext;
import dataprocess.ContextFactory;
import datareader.DataReader;
import gurobi.GRB;
import gurobi.GRBException;
import solution.Solution;

/**
 * TestScheduleHorizonRoller （功能简述）
 * 功能详细描述
 *
 * @author s00536729
 * @since 2022-06-17
 */
public class TestScheduleHorizonRoller {
    public static void main(String[] args) throws GRBException {
        DataReader reader = new DataReader();
        ContextFactory contextFactory = new ContextFactory();
        ProblemContext problemContext = contextFactory.build(reader);
        contextFactory.update(problemContext);
        contextFactory.filter(problemContext);
        AbstractHorizonRoller roller = new ScheduleHorizonRoller();
        Solution solution = roller.solve(problemContext);
        if (solution.getResultStatus() != GRB.INFEASIBLE) {
            Solution.printSolInfo(solution, problemContext);
            System.out.println("Schedule horizon roller works!");
        } else {
            System.out.println("Schedule horizon roller fails!");
        }
    }
}
