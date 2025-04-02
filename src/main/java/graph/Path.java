package graph;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Path （路径）
 * 商品的路径
 *
 * @author s00536729
 * @since 2022-07-01
 */
@Getter
@Setter
public class Path extends AbstractArc<Vertex> {
    private Commodity commodity;
    private List<Vertex> vertices = new ArrayList<>();
    private List<Edge> edges = new ArrayList<>();
    private double minWeight = Double.MAX_VALUE;

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < vertices.size(); i++) {
            sb.append(vertices.get(i));
            if (i < vertices.size() - 1) {
                sb.append("->");
            }
        }
        return sb.toString();
    }
}
