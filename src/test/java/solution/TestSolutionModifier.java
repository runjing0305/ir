package solution;

import context.ProblemContext;
import context.Schedule;
import dataprocess.ContextFactory;
import datareader.DataReader;
import solution.modifier.SolutionCourseModifier;

import java.util.Comparator;

/**
 * @description:
 * @author: Shengcheng Shao
 * @date: 2022/8/2
 */
public class TestSolutionModifier {
    public static void main(String[] args) {
        DataReader reader = new DataReader();
        ContextFactory contextFactory = new ContextFactory();
        ProblemContext problemContext = contextFactory.build(reader);
        contextFactory.update(problemContext);
        contextFactory.filter(problemContext);
        problemContext.getSchedules().sort(Comparator.comparingInt(Schedule::getStartTime));
        SolutionGenerator generator = new SolutionGenerator(problemContext);
        Solution solution = generator.generate();
        SolutionCourseModifier modifier = new SolutionCourseModifier(problemContext);
        modifier.modify(solution);
        Solution.printSolInfo(solution, problemContext);
        System.out.println("Solution modifier works");
    }
}
