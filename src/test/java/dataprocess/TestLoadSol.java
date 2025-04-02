package dataprocess;

import context.ProblemContext;
import context.Schedule;
import datareader.DataReader;
import solution.Solution;
import solution.SolutionGenerator;

import java.util.Comparator;

/**
 * @description:
 * @author: Shengcheng Shao
 * @date: 2022/8/1
 */
public class TestLoadSol {
    public static void main(String[] args) {
        DataReader reader = new DataReader();
        ContextFactory contextFactory = new ContextFactory();
        ProblemContext problemContext = contextFactory.build(reader);
        contextFactory.update(problemContext);
        contextFactory.filter(problemContext);
        problemContext.getSchedules().sort(Comparator.comparingInt(Schedule::getStartTime));
        SolutionGenerator generator = new SolutionGenerator(problemContext);
        Solution solution = generator.generate();
        contextFactory.loadCourseSol(reader, solution, problemContext);
        Solution.printSolInfo(solution, problemContext);
        System.out.println("Load solution works!");
    }
}
