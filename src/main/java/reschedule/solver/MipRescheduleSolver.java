package reschedule.solver;

import context.ProblemContext;
import reschedule.graph.CellGraph;
import reschedule.model.CourseLevelModel;
import solution.Solution;
import solution.SolutionEvaluator;

import java.util.concurrent.TimeUnit;

public class MipRescheduleSolver implements RescheduleSolver {
    private int iter = 0;
    private final int ITERATION_LIMIT = 1;

    @Override
    public Solution solve(ProblemContext problemContext, Solution solution) {
        long startTime = System.currentTimeMillis();
        CellGraph graph = new CellGraph(problemContext, solution);
        CourseLevelModel model = new CourseLevelModel(problemContext, graph);
        model.build();
        System.out.println("start to solve reschedule model iteration: " + iter);
        model.solve(solution);
        SolutionEvaluator evaluator = new SolutionEvaluator(problemContext);
        // 模型中根据输入解的时间添加链路延长运行时间及站点停留时间延长情景的约束，输出的解可能不满足Scenario约束，迭代求解修复
        if (evaluator.calcVio(solution) > 0 && iter < ITERATION_LIMIT) {
            ++iter;
            solve(problemContext, solution);
        }
        long elapsedTime = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis() - startTime);
        solution.setElapsedTime(elapsedTime);
        return solution;
    }
}
