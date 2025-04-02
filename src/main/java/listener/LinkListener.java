package listener;

import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import entity.Link;

import java.util.ArrayList;
import java.util.List;

/**
 * LinkListener （Link 倾听器）
 * Link 倾听器
 *
 * @author s00536729
 * @since 2022-07-01
 */
public class LinkListener extends AnalysisEventListener<Link> {
    List<Link> linkList = new ArrayList<>();
    @Override
    public void invoke(Link link, AnalysisContext analysisContext) {
        linkList.add(link);
    }

    @Override
    public void doAfterAllAnalysed(AnalysisContext analysisContext) {

    }

    public List<Link> getData() {
        return linkList;
    }
}
