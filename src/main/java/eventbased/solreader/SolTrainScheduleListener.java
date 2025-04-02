package eventbased.solreader;

import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;

import java.util.ArrayList;
import java.util.List;

public class SolTrainScheduleListener extends AnalysisEventListener<SolTrainSchedule> {
    List<SolTrainSchedule> solTrainScheduleList = new ArrayList<>();

    @Override
    public void invoke(SolTrainSchedule SolTrainSchedule, AnalysisContext analysisContext) {
        solTrainScheduleList.add(SolTrainSchedule);
    }

    @Override
    public void doAfterAllAnalysed(AnalysisContext analysisContext) {
    }

    public List<SolTrainSchedule> getData() {
        return solTrainScheduleList;
    }
}
