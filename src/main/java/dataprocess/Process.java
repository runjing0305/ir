package dataprocess;

import context.ProblemContext;
import datareader.DataReader;
import datareader.LoadData;

/**
 * Process （处理）
 * 处理原始数据
 *
 * @author s00536729
 * @since 2022-06-16
 */
public interface Process {
    /**
     * 读取原始数据进行处理并产生问题情景
     *
     * @param reader 数据读取器
     * @return ProblemContext 问题情景
     */
    public ProblemContext build(LoadData reader);
}
