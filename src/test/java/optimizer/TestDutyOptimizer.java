package optimizer;

import context.Link;
import context.Node;
import context.ProblemContext;
import context.Schedule;
import context.scenario.*;
import dataprocess.ContextFactory;
import datareader.DataReader;
import entity.LateDeparture;
import entity.RealizedSchedule;

public class TestDutyOptimizer {
    public static void main(String[] args) {
        DataReader reader = new DataReader();
        ContextFactory contextFactory = new ContextFactory();
        ProblemContext problemContext = contextFactory.build(reader);
        contextFactory.update(problemContext);
        System.out.println("Duty Optimizer works!");
    }
}
