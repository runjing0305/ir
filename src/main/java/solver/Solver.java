package solver;

import gurobi.GRBException;
import model.Model;
import solution.Solution;

/**
 * Solver （求解器接口）
 * 求解模型得到高质量可行解（可以不是最优）
 *
 * @author s00536729
 * @since 2022-01-28
 */
public interface Solver {
    /**
     * 调用求解器/算法求解模型得到原问题的高质量可行解（可以不是最优），并且将结果写到solution中
     *
     * @param model 待求解模型
     * @throws GRBException GUROBI异常
     * @return Solution 解
     */
    Solution solve(Model model) throws GRBException;
}
