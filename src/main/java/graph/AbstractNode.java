package graph;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


/**
 * AbstractNode （抽象点）
 * 所有点的抽象
 *
 * @author s00536729
 * @since 2022-07-01
 */
@Getter
@Setter
public class AbstractNode<T extends AbstractArc>{
    protected int index; // 点的index
    protected int id; // 点的唯一id
    protected List<T> inArcList = new ArrayList<>(); // 入边的列表
    protected List<T> outArcList = new ArrayList<>(); // 出边的列表
    protected String name; // 点的名字
}
