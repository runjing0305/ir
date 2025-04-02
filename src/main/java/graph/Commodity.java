package graph;

import context.RollingStock;
import lombok.Getter;
import lombok.Setter;


/**
 * Commodity （商品）
 * 代表一个列车
 *
 * @author s00536729
 * @since 2022-07-01
 */
@Getter
@Setter
public class Commodity {
    private int index;
    private String name;
    private RollingStock rs;

    /**
     * 商品的构造器
     *
     * @param rs 列车组
     */
    public Commodity(RollingStock rs) {
        this.rs = rs;
        this.index = rs.getIndex();
        this.name = String.valueOf(rs.getIndex());
    }
}
