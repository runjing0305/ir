package graph;

import context.ProblemContext;
import context.Schedule;
import dataprocess.ContextFactory;
import datareader.DataReader;
import graph.scgraph.MultiCopyGraph;
import graph.scgraph.SingleCommodityGraph;
import solution.Solution;
import solution.SolutionGenerator;
import solution.modifier.SolutionCourseModifier;

import java.util.Comparator;

/**
 * @description:
 * @author: Shengcheng Shao
 * @date: 2022/8/5
 */
public class TestMCGraph {
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
//        contextFactory.loadCourseSol(reader, solution, problemContext);
        MultiCopyGraph graph = new MultiCopyGraph(problemContext, solution);
        System.out.println("Multi Copy Graph works");
    }
}
