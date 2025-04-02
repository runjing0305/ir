package model.assignmodel;

import constant.Constants;
import context.ProblemContext;
import context.Schedule;
import gurobi.GRB;
import gurobi.GRBException;
import gurobi.GRBModel;
import gurobi.GRBVar;
import lombok.Getter;
import solution.Solution;
import solution.SolutionEvaluator;

import java.util.*;

/**
 * @description:
 * @author: Shengcheng Shao
 * @date: 2022/8/8
 */
@Getter
public class AssignVariable {
    private Map<Schedule, List<GRBVar>> xVars = new HashMap<>();

    private Map<Schedule, GRBVar> yVars = new HashMap<>();

    public void createVars(ProblemContext context, GRBModel solver, Solution curSol) throws GRBException {
        for (int i = 0; i < context.getSchedules().size(); i++) {
            Schedule schedule = context.getSchedules().get(i);
            List<GRBVar> changeVars = new ArrayList<>();
            if (!schedule.getRealizedNodes().isEmpty()
                    && schedule.getRealizedEnterTimes().get(schedule.getRealizedNodes().size()) != 0) {
                changeVars.add(solver.addVar(1,1,0, GRB.CONTINUOUS, schedule.getCourseId() +
                        " skip var"));
            } else {
                for (int change : Constants.COURSE_START_TIME_CHANGE) {
                    List<Integer> arrivals  = curSol.getScheduleStationArrivalTimeMap().get(schedule);
                    double delayPenalty = arrivals.get(arrivals.size() - 1) + change - schedule.getEndTime();
                    // 注意，此时我们利用线性函数来估计delay penalty
                    delayPenalty = Math.max(delayPenalty, 0) * Constants.DELAY_PENALTY / Constants.SECONDS_IN_MINUTE;
                    changeVars.add(solver.addVar(0,1,delayPenalty, GRB.BINARY, schedule.getCourseId() +
                            " skip var"));
                }
            }
            xVars.put(schedule, changeVars);
            SolutionEvaluator se = new SolutionEvaluator(context);
            Solution tempSol = new Solution(curSol);
            List<Boolean> skipStations = new ArrayList<>(curSol.getScheduleSkipStationMap().get(schedule));
            Collections.fill(skipStations, Boolean.TRUE);
            tempSol.getScheduleSkipStationMap().put(schedule, skipStations);
            double penalty = se.calcSkipStationPenalty(tempSol, schedule);
            GRBVar cancellationVar = solver.addVar(0, 0, penalty, GRB.BINARY, schedule.getCourseId() +
                    " cancellation var");
            yVars.put(schedule, cancellationVar);
        }
    }
}
