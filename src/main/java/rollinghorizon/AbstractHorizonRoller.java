package rollinghorizon;

import context.ProblemContext;
import graph.Graph;
import gurobi.GRB;
import gurobi.GRBException;
import gurobi.GRBModel;
import model.Model;
import solution.Solution;
import solver.MipSolver;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * AbstractHorizonRoller （滚动时间窗接口）
 * 生成多个时间窗下的子模型，求解一个子模型就代表我们得到了一个时间段的任务分配结果
 *
 * @author s00536729
 * @since 2022-06-17
 */
public abstract class AbstractHorizonRoller {
    protected int duration = 0; // 总时间窗个数
    protected Graph lastGraph = null;

    protected Model lastModel = null;

    /**
     * 生成多个时间窗下的子模型
     *
     * @param problemContext 待优化原模型
     * @return List<Model> 待优化子模型
     */
    protected abstract ProblemContext genHorizon(ProblemContext problemContext, int index);

    /**
     * 计算总时间窗个数
     *
     * @param problemContext 待优化问题情景
     */
    protected abstract void calDuration(ProblemContext problemContext);

    /**
     * 求解待优化问题情景
     *
     * @param problemContext 待优化问题情景
     * @return Solution 问题解
     * @throws GRBException GUROBI异常
     */
    public Solution solve(ProblemContext problemContext) throws GRBException {
        long algoStartTime = System.currentTimeMillis();
        Solution solution = null;
        calDuration(problemContext);
        Collections.sort(problemContext.getSchedules());
        for (int index = 0; index < duration; index++) {
            long startTime = System.currentTimeMillis();
            System.out.println("Current index is " + index);
            preprocess(problemContext);
            ProblemContext subProb = genHorizon(problemContext, index);
            solution = roll(solution, subProb);
            if (solution.getResultStatus() == GRB.INFEASIBLE) {
                System.out.println("Roller exit due to infeasible solution");
                break;
            }
            long endTime = System.currentTimeMillis();
            Solution.printSolInfo(solution, subProb);
            System.out.println("It takes " + TimeUnit.MILLISECONDS.toSeconds(endTime - startTime)
                    + " seconds to roll index: " + index);
        }
        assert solution != null;
        if (solution.getResultStatus() != GRB.INFEASIBLE) {
            solution.updateObjectValue();
            long algoEndTime = System.currentTimeMillis();
            solution.setElapsedTime((long) ((algoEndTime - algoStartTime) / 1000.0));
        }
        return solution;
    }

    /**
     * 求解子模型，基于当前部分解，得到该时间段的任务分配
     *
     * @param solution    部分解
     * @param horizonProb 时间窗子问题情境
     */
    protected Solution roll(Solution solution, ProblemContext horizonProb) throws GRBException {
        lastGraph = new Graph(horizonProb);
        Model subModel = new Model(horizonProb, lastGraph);
        lastModel = subModel;
        subModel.createVars();
        subModel.createCons();
        MipSolver solver = new MipSolver();
        Solution newSol = solver.solve(subModel);
        if (newSol.getResultStatus() == GRB.INFEASIBLE) {
            return newSol;
        }
        afterProcess(horizonProb, newSol);
        return update(solution, newSol);
    }

    /**
     * 子问题情景求解后刷新各rolling stock执行的schedule的列表
     *
     * @param horizonProb 子问题情景
     * @param newSol 子问题解
     */
    protected abstract void afterProcess(ProblemContext horizonProb, Solution newSol);

    /**
     * 基于schedule的终点刷新rolling stock的起始位置
     *
     * @param horizonProb 子问题情景
     */
    protected abstract void preprocess(ProblemContext horizonProb);

    /**
     * 基于子问题解刷新当前解
     *
     * @param solution 当前解
     * @param newSol 子问题解
     * @return Solution 刷新后的当前解
     */
    protected abstract Solution update(Solution solution, Solution newSol);
}
