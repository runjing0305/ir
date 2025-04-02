package reschedule.solver;

import context.ProblemContext;
import solution.Solution;

public interface RescheduleSolver {

    Solution solve(ProblemContext problemContext, Solution solution);
}
