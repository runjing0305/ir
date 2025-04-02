package listener;

import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import entity.TrainSchedule;

import java.util.ArrayList;
import java.util.List;

/**
 * TrainScheduleListener （TrainSchedule 倾听器）
 * TrainSchedule 倾听器
 *
 * @author s00536729
 * @since 2022-07-01
 */
public class TrainScheduleListener extends AnalysisEventListener<TrainSchedule> {
    List<TrainSchedule> trainScheduleList = new ArrayList<>();
    @Override
    public void invoke(TrainSchedule trainSchedule, AnalysisContext analysisContext) {
        trainScheduleList.add(trainSchedule);
    }

    @Override
    public void doAfterAllAnalysed(AnalysisContext analysisContext) {

    }

    public List<TrainSchedule> getData() {
        return trainScheduleList;
    }
}
