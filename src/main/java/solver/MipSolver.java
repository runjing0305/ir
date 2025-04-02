package solver;


import constant.Constants;
import gurobi.GRB;
import gurobi.GRBException;
import model.Model;
import solution.Solution;

import java.util.concurrent.TimeUnit;


/**
 * MipSolver （MIP求解器）
 * 调用求解器求解模型得到元问题的高质量可行解（可以不是最优），并且将结果写到solution中
 *
 * @author s00536729
 * @since 2022-01-30
 */
public class MipSolver implements Solver {
    @Override
    public Solution solve(Model model) throws GRBException {
        long startTime = System.currentTimeMillis();
        model.getSolver().set(GRB.DoubleParam.TimeLimit, Constants.SOLVER_TIME_LIMIT);
        model.getSolver().set(GRB.DoubleParam.MIPGap, Constants.SOLVER_MIP_GAP);
        model.getSolver().set(GRB.IntParam.OutputFlag, Constants.SOLVER_VERBOSITY_LEVEL);
        model.getSolver().optimize();
        int resultStatus = model.getSolver().get(GRB.IntAttr.Status);
        Solution ret;
        if (resultStatus == GRB.INFEASIBLE) {
            System.out.println("Model infeasible");
//            model.getSolver().write("infeasible_model.mps");
            ret = new Solution();
            ret.setResultStatus(GRB.INFEASIBLE);
        } else {
            long endTime = System.currentTimeMillis();
            model.setResultStatus(resultStatus);
            model.setElapsedTime(TimeUnit.MILLISECONDS.toSeconds(endTime - startTime));
            ret = model.genSol();
            // Dispose of model and environment
            model.getSolver().dispose();
            model.getEnv().dispose();
        }
        return ret;
    }

    public static void main(String[] args) {
        System.out.println("Begin to solve RAS problem.");
    }
}
