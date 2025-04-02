package dataprocess;

import context.ProblemContext;
import datareader.LoadData;
import solution.Solution;

/**
 * @description:
 * @author: Shengcheng Shao
 * @date: 2022/8/1
 */
public interface LoadSol {
    /**
     * 调用reader读取python生成的解文件，并且刷新到solution中
     *
     * @param reader 数据读取器
     * @param solution 当前解
     * @param problemContext 问题情景
     */
     public void loadCourseSol(LoadData reader, Solution solution, ProblemContext problemContext);
}
