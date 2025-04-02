package solutionwriter;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.write.builder.ExcelWriterBuilder;
import context.ProblemContext;
import context.Schedule;
import solution.Solution;

import java.util.ArrayList;
import java.util.List;

public class SolutionWriter {
    public void output(ProblemContext problemContext, Solution solution, String fileName) {
        ExcelWriterBuilder builder = EasyExcel.write(fileName, ScheduleSolution.class);
        builder.sheet("sol").doWrite(getSolutionList(problemContext, solution));
    }


    private List<ScheduleSolution> getSolutionList(ProblemContext problemContext, Solution solution) {
        List<ScheduleSolution> solutions = new ArrayList<>();
        for (Schedule schedule : problemContext.getSchedules()) {
            if (!solution.getSchedule2RollingStockMap().containsKey(schedule)) {
                continue;
            }
            for (int i = 0; i < solution.getScheduleSkipStationMap().get(schedule).size(); ++i) {
                ScheduleSolution scheduleSolution = new ScheduleSolution();
                scheduleSolution.setTrainCourseId(schedule.getCourseId());
                scheduleSolution.setRollingStock(solution.getSchedule2RollingStockMap().get(schedule).getIndex());
                scheduleSolution.setSeq(i + 1);
                if (schedule.getRealizedNodes().isEmpty()) {
                    scheduleSolution.setNode(schedule.getPlannedNodes().get(i).getCode());
                } else {
                    scheduleSolution.setNode(schedule.getRealizedNodes().get(i).getCode());
                }
                String status = solution.getScheduleSkipStationMap().get(schedule).get(i) ? "PASS" : "STOP";
                scheduleSolution.setStatus(status);
                Integer arrival = solution.getScheduleStationArrivalTimeMap().get(schedule).get(i);
                scheduleSolution.setArrivalSeconds(arrival == null ? "" : arrival.toString());
                Integer departure = solution.getScheduleStationDepartureTimeMap().get(schedule).get(i);
                scheduleSolution.setDepartureSeconds(departure == null ? "" : departure.toString());

                scheduleSolution.setTrack(solution.getScheduleStationTrackMap().get(schedule).get(i) == null ? ""
                        : solution.getScheduleStationTrackMap().get(schedule).get(i));
                solutions.add(scheduleSolution);
            }
        }
        return solutions;
    }
}
