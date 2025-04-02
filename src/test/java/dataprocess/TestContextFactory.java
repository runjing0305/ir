package dataprocess;

import context.ProblemContext;
import datareader.DataReader;

public class TestContextFactory {
    public static void main(String[] arg) {
        DataReader reader = new DataReader();
        ContextFactory contextFactory = new ContextFactory();
        ProblemContext problemContext = contextFactory.build(reader);
        contextFactory.update(problemContext);
        System.out.println("Context factory works!");
    }
}
