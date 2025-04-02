package dataprocess;

import context.ProblemContext;

/**
 * Filter （过滤）
 * 去除不需要规划的路线
 *
 * @author s00536729
 * @since 2022-06-16
 */
public interface Filter {
    /**
     * 过滤
     * @param problemContext 问题情景
     */
    void filter(ProblemContext problemContext);
}
