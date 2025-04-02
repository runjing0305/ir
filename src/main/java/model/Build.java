package model;

import gurobi.GRBException;

/**
 * Build （创建模型接口）
 * 创建模型的决策变量和约束
 *
 * @author s00536729
 * @since 2022-07-01
 */
public interface Build {
    /**
     * 创建决策变量
     *
     * @throws GRBException
     */
    void createVars() throws GRBException;

    /**
     * 创建约束
     *
     * @throws GRBException
     */
    void createCons() throws GRBException;
}
