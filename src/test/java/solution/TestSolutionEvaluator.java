package solution;

import context.Link;
import context.ProblemContext;
import context.Schedule;
import dataprocess.ContextFactory;
import datareader.DataReader;
import graph.Graph;
import gurobi.GRBException;
import model.Model;
import solver.MipSolver;

import java.util.*;

/**
 * TestSolutionEvaluator （功能简述）
 * 功能详细描述
 *
 * @author s00536729
 * @since 2022-06-20
 */
public class TestSolutionEvaluator {
    public static void main(String[] args) throws GRBException {
        DataReader reader = new DataReader();
        ContextFactory contextFactory = new ContextFactory();
        ProblemContext problemContext = contextFactory.build(reader);
        contextFactory.update(problemContext);
        contextFactory.filter(problemContext);
        problemContext.getSchedules().sort(Comparator.comparingInt(Schedule::getStartTime));
        problemContext.setSchedules(problemContext.getSchedules().subList(0, 10));

        Map<String, Schedule> courseId2Schedule = new HashMap<>();
        for (Schedule schedule : problemContext.getSchedules()) {
            courseId2Schedule.put(schedule.getCourseId(), schedule);
        }
        problemContext.setCourseId2Schedule(courseId2Schedule);
        List<Link> links = new ArrayList<>();
        for (Link link : problemContext.getLinks()) {
            List<Schedule> schedules = new ArrayList<>();
            for (Schedule schedule : link.getSchedules()) {
                if (problemContext.getCourseId2Schedule().containsKey(schedule.getCourseId())) {
                    schedules.add(schedule);
                }
            }
            if (schedules.size() > 0) {
                link.setSchedules(schedules);
                links.add(link);
            }
        }
        problemContext.setLinks(links);


        Graph graph = new Graph(problemContext);
        Model model = new Model(problemContext, graph);
        model.createVars();
        model.createCons();
        MipSolver solver = new MipSolver();
        Solution solution = solver.solve(model);
        Solution.printSolInfo(solution, problemContext);
        System.out.println("Solution evaluator works!");
    }
}
