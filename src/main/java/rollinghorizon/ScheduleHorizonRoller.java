package rollinghorizon;

import context.ProblemContext;
import context.RollingStock;
import context.Schedule;
import solution.Solution;

import java.util.Map;
import java.util.Objects;

/**
 * ScheduleHorizonRoller （功能简述）
 * 按预先设计好的taskStepSize，往后滚动时间窗，生成多个子模型，基于子模型进行问题求解
 *
 * @author s00536729
 * @since 2022-06-17
 */
public class ScheduleHorizonRoller extends AbstractHorizonRoller{
    private final double scheduleStepSize = 5.0;

    @Override
    protected ProblemContext genHorizon(ProblemContext problemContext, int index) {
        calDuration(problemContext);
        ProblemContext subContext = problemContext.genScheduleHorizon((int) (index * scheduleStepSize),
                (int) ((index + 1) * scheduleStepSize));
        System.out.println("index " + index + " task num: " + subContext.getSchedules().size());
        return subContext;
    }

    @Override
    protected void calDuration(ProblemContext problemContext) {
        // 计算从第一个任务到最后一个任务开始有多少时间窗, 则我们分配多少个Slot
        duration = (int) Math.ceil(problemContext.getSchedules().size() / scheduleStepSize);
    }

    @Override
    protected void afterProcess(ProblemContext horizonProb, Solution newSol) {
        for (Map.Entry<Schedule, RollingStock> entry : newSol.getSchedule2RollingStockMap().entrySet()) {
            entry.getValue().getSchedules().add(entry.getKey());
        }
    }

    @Override
    protected void preprocess(ProblemContext horizonProb) {
        for (RollingStock rs : horizonProb.getRollingStocks()) {
            if (!rs.getSchedules().isEmpty()) {
                rs.setStartPos(Objects.requireNonNull(rs.getSchedules().pollLast()).getEndNode());
            }
        }
    }

    @Override
    protected Solution update(Solution solution, Solution newSol) {
        if (solution == null) {
            solution = newSol;
        } else {
            solution.setElapsedTime(solution.getElapsedTime() + newSol.getElapsedTime());
            solution.setResultStatus(newSol.getResultStatus());
            solution.getSchedule2RollingStockMap().putAll(newSol.getSchedule2RollingStockMap());
            solution.getScheduleSkipStationMap().putAll(newSol.getScheduleSkipStationMap());
            solution.getScheduleDestinationDelayMap().putAll(newSol.getScheduleDestinationDelayMap());
            solution.getScheduleStationArrivalTimeMap().putAll(newSol.getScheduleStationArrivalTimeMap());
            solution.getScheduleStationDepartureTimeMap().putAll(newSol.getScheduleStationDepartureTimeMap());
            solution.getRollingStock2ScheduleListMap().putAll(newSol.getRollingStock2ScheduleListMap());
        }
        return solution;
    }
}
