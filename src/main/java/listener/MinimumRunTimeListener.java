package listener;

import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import entity.MinimumRunTime;

import java.util.ArrayList;
import java.util.List;

/**
 * MinimumRunTimeListener （MinimumRunTime 倾听器）
 * MinimumRunTime 倾听器
 *
 * @author s00536729
 * @since 2022-07-01
 */
public class MinimumRunTimeListener extends AnalysisEventListener<MinimumRunTime> {
    List<MinimumRunTime> minimumRunTimeList = new ArrayList<>();
    @Override
    public void invoke(MinimumRunTime minimumRunTime, AnalysisContext analysisContext) {
        minimumRunTimeList.add(minimumRunTime);
    }

    @Override
    public void doAfterAllAnalysed(AnalysisContext analysisContext) {

    }

    public List<MinimumRunTime> getData() {
        return minimumRunTimeList;
    }
}
