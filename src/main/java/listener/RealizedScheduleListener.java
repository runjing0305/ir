package listener;

import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import entity.RealizedSchedule;

import java.util.ArrayList;
import java.util.List;

/**
 * RealizedScheduleListener （RealizedSchedule 倾听器）
 * RealizedSchedule 倾听器
 *
 * @author s00536729
 * @since 2022-07-01
 */
public class RealizedScheduleListener extends AnalysisEventListener<RealizedSchedule> {
    List<RealizedSchedule> realizedScheduleList = new ArrayList<>();
    @Override
    public void invoke(RealizedSchedule realizedSchedule, AnalysisContext analysisContext) {
        realizedScheduleList.add(realizedSchedule);
    }

    @Override
    public void doAfterAllAnalysed(AnalysisContext analysisContext) {

    }

    public List<RealizedSchedule> getData() {
        return realizedScheduleList;
    }
}
