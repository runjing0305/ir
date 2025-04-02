package graph;

import context.ProblemContext;
import dataprocess.ContextFactory;
import datareader.DataReader;

public class TestGraph {
    public static void main(String[] args) {
        DataReader reader = new DataReader();
        ContextFactory contextFactory = new ContextFactory();
        ProblemContext problemContext = contextFactory.build(reader);
        contextFactory.update(problemContext);
        Graph graph = new Graph(problemContext);
        System.out.println("Build graph successfully");
    }
}
