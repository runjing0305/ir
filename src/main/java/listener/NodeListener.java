package listener;

import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import entity.Node;

import java.util.ArrayList;
import java.util.List;

/**
 * NodeListener （Node 倾听器）
 * Node 倾听器
 *
 * @author s00536729
 * @since 2022-07-01
 */
public class NodeListener extends AnalysisEventListener<Node> {
    List<Node> nodeList = new ArrayList<>();
    @Override
    public void invoke(Node node, AnalysisContext analysisContext) {
        nodeList.add(node);
    }

    @Override
    public void doAfterAllAnalysed(AnalysisContext analysisContext) {

    }

    public List<Node> getData() {
        return nodeList;
    }
}
