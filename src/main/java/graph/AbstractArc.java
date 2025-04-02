package graph;

import lombok.Getter;
import lombok.Setter;

/**
 * AbstractArc （抽象边）
 * 所有边的抽象
 *
 * @author s00536729
 * @since 2022-07-01
 */
@Getter
@Setter
public abstract class AbstractArc<T extends AbstractNode> {
    protected int index; // 边的index
    protected T head; // 边的头
    protected T tail; // 边的尾
    protected String name; // 边的名字
    protected boolean isFeasible = true; // 是否为可行边

    public String toString() {
        return genArcName(head.getName(), tail.getName());
    }

    /**
     * 基于头和尾产生边的名字
     *
     * @param from 头
     * @param to 尾
     * @return String 边名
     */
    public static String genArcName(String from, String to) {
        return from + "_" + to;
    }
}
