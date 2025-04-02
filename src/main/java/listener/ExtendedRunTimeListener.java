package listener;

import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import entity.ExtendedRunTime;

import java.util.ArrayList;
import java.util.List;

/**
 * ExtendedRunTimeListener （ExtendedRunTime 倾听器）
 * ExtendedRunTime 倾听器
 *
 * @author s00536729
 * @since 2022-07-01
 */
public class ExtendedRunTimeListener extends AnalysisEventListener<ExtendedRunTime> {
    List<ExtendedRunTime> ertList = new ArrayList<>();
    @Override
    public void invoke(ExtendedRunTime extendedRunTime, AnalysisContext analysisContext) {
        ertList.add(extendedRunTime);
    }

    @Override
    public void doAfterAllAnalysed(AnalysisContext analysisContext) {

    }

    public List<ExtendedRunTime> getData() {
        return ertList;
    }
}
