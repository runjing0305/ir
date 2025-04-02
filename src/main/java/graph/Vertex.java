package graph;

import lombok.Getter;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

/**
 * Vertex （顶点）
 * 图中顶点
 *
 * @author s00536729
 * @since 2022-07-01
 */
@Getter
@Setter
public class Vertex extends AbstractNode<Edge> implements Comparable<Vertex> {
    /**
     * Type （顶点类型）
     * 停留或者跳过
     *
     * @author s00536729
     * @since 2022-07-01
     */
    public enum Type {
        STOP,
        PASS
    }
    private Set<Edge> inEdges = new HashSet<>();
    private Set<Edge> outEdges = new HashSet<>();
    private double minDistance = Double.POSITIVE_INFINITY; // used in Dijkstra
    private Vertex previous; // used in Dijkstra
    private double capacity = 0;
    private Type type;
    private boolean isVirtual = false; // 代表顶点是否是虚拟顶点

    /**
     * 顶点构造器
     *
     * @param argName 顶点名
     */
    public Vertex(String argName) {
        name = argName;
    }

    public String toString() {
        return name;
    }

    public int compareTo(Vertex other) {
        return Double.compare(minDistance, other.minDistance);
    }

    /**
     * 获取顶点的最小逗留时间
     *
     * @return int 最小逗留时间
     */
    public int getMinimumDwellTime() {
        if (type.equals(Type.STOP)) {
            return 30;
        } else {
            return 0;
        }
    }
}
