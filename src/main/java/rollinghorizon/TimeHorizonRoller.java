package rollinghorizon;

import context.Duty;
import context.ProblemContext;
import context.RollingStock;
import context.Schedule;
import context.scenario.RealizedScheduleScenario;
import solution.Solution;

import java.util.Map;
import java.util.Objects;

/**
 * TimeHorizonRoller （功能简述）
 * 按预先设计好的taskStepSize，往后滚动时间窗，生成多个子模型，基于子模型进行问题求解
 *
 * @author z00470266
 * @since 2022-07-01
 */
public class TimeHorizonRoller extends AbstractHorizonRoller {
    private static double timeStepSize = 600;
    private static int minTime = Integer.MAX_VALUE;
    private static int maxTime = Integer.MIN_VALUE;

    @Override
    protected ProblemContext genHorizon(ProblemContext problemContext, int index) {
        // 为了方便，我们在Graph里面刷新
        return problemContext;
    }

    @Override
    protected void calDuration(ProblemContext problemContext) {
        // 计算从第一个任务到最后一个任务开始有多少时间窗, 则我们分配多少个Slot
        minTime = Integer.MAX_VALUE;
        maxTime = Integer.MIN_VALUE;
        for (Duty duty : problemContext.getDuties()) {
            if (minTime > duty.getStartTime()) {
                minTime = duty.getStartTime();
            }
            if (maxTime < duty.getEndTime()) {
                maxTime = duty.getEndTime();
            }
        }
        for (RealizedScheduleScenario realizedScheduleScenario : problemContext.getScenario().getRealizedScheduleScenarios()) {
            int arrivalSeconds = realizedScheduleScenario.getArrivalSeconds();
            if (arrivalSeconds != 0 && minTime < arrivalSeconds) {
                minTime = arrivalSeconds;
            }
            int DepartureSeconds = realizedScheduleScenario.getDepartureSeconds();
            if (DepartureSeconds != 0 && minTime < DepartureSeconds) {
                minTime = DepartureSeconds;
            }
        }
        duration = (int) Math.ceil((maxTime - minTime) / timeStepSize);
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


    public double getTimeStepSize() {
        return timeStepSize;
    }

    public int getMinTime() {
        return minTime;
    }

    public int getMaxTime() {
        return maxTime;
    }

    public void setTimeStepSize(double timeStepSize) {
        this.timeStepSize = timeStepSize;
    }

    public void setMinTime(int minTime) {
        this.minTime = minTime;
    }

    public void setMaxTime(int maxTime) {
        this.maxTime = maxTime;
    }
}
