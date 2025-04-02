package listener;

import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import entity.Headway;

import java.util.ArrayList;
import java.util.List;

/**
 * HeadwayListener （Headway 倾听器）
 * Headway 倾听器
 *
 * @author s00536729
 * @since 2022-07-01
 */
public class HeadwayListener extends AnalysisEventListener<Headway> {
    List<Headway> headwayList = new ArrayList<>();
    @Override
    public void invoke(Headway headway, AnalysisContext analysisContext) {
        headwayList.add(headway);
    }

    @Override
    public void doAfterAllAnalysed(AnalysisContext analysisContext) {

    }

    public List<Headway> getData() {
        return headwayList;
    }
}
