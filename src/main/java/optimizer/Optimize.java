package optimizer;

import context.ProblemContext;

public interface Optimize {
    /**
     * fill realizedDuties in RollingStocks as the result of optimization
     * @param problemContext inputData
     */
    void optimize(ProblemContext problemContext);
}
