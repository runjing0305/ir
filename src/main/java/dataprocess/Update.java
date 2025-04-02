package dataprocess;

import context.ProblemContext;

/**
 * Update （更新）
 * 更新问题instance相关数据
 *
 * @author s00536729
 * @since 2022-06-16
 */
public interface Update {
    /**
     * 将问题instance导入问题情景中
     *
     * @param problemContext 问题情景
     */
    void update(ProblemContext problemContext);
}
