package solutionwriter;

import context.ProblemContext;
import dataprocess.ContextFactory;
import datareader.DataReader;
import solution.Solution;
import solution.SolutionGenerator;

public class TestSolutionWriter {
    public static void main(String[] arg) {
        DataReader reader = new DataReader();
        ContextFactory contextFactory = new ContextFactory();
        ProblemContext problemContext = contextFactory.build(reader);
        contextFactory.update(problemContext);
        contextFactory.filter(problemContext);
        SolutionGenerator generator = new SolutionGenerator(problemContext);
        Solution solution = generator.generate();
        Solution.printSolInfo(solution, problemContext);
        SolutionWriter writer = new SolutionWriter();
        writer.output(problemContext, solution, "sol.xlsx");
        System.out.println("SolutionWriter successfully, takes " + solution.getElapsedTime() +" seconds");
    }
}
