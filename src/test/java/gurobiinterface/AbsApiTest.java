package gurobiinterface;

import gurobi.*;

/**
 * @description:
 * @author: Shengcheng Shao
 * @date: 2022/7/8
 */
public class AbsApiTest {
    public static void main(String[] args) {
        try {

            // Create empty environment, set options, and start
            GRBEnv env = new GRBEnv(true);
            env.set("logFile", "mip1.log");
            env.start();

            // Create empty model
            GRBModel model = new GRBModel(env);

            // Create variables
            GRBVar v1 = model.addVar(0, Double.POSITIVE_INFINITY, 0.0, GRB.INTEGER, "v1");
            GRBVar v2 = model.addVar(0, Double.POSITIVE_INFINITY, 0.0, GRB.INTEGER, "v2");
            GRBVar v3 = model.addVar(0, Double.POSITIVE_INFINITY, 0.0, GRB.INTEGER, "v3");

            GRBVar diff1 = model.addVar(Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, 0.0, GRB.CONTINUOUS, "diff1");
            GRBVar diff2 = model.addVar(Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, 0.0, GRB.CONTINUOUS, "diff2");

            GRBVar abs1 = model.addVar(0.0, Double.POSITIVE_INFINITY, 0.0, GRB.CONTINUOUS, "abs1");
            GRBVar abs2 = model.addVar(0.0, Double.POSITIVE_INFINITY, 0.0, GRB.CONTINUOUS, "abs2");

            GRBLinExpr expr = new GRBLinExpr();
            expr.addTerm(3, v1);
            expr.addTerm(1, v2);
            expr.addTerm(1, v3);
            model.addConstr(expr, GRB.LESS_EQUAL, 72, "constr1");

            expr = new GRBLinExpr();
            expr.addTerm(2, v1);
            expr.addTerm(3, v2);
            expr.addTerm(2, v3);
            model.addConstr(expr, GRB.LESS_EQUAL, 80, "constr2");

            expr = new GRBLinExpr();
            expr.addTerm(1, diff1);
            expr.addTerm(-1, v1);
            expr.addTerm(1, v2);
            model.addConstr(expr, GRB.EQUAL, 0, "diffConstr1");

            expr = new GRBLinExpr();
            expr.addTerm(1, diff2);
            expr.addTerm(-1, v2);
            expr.addTerm(1, v3);
            model.addConstr(expr, GRB.EQUAL, 0, "diffConstr2");

            model.addGenConstrAbs(abs1, diff1, "absConstr1");
            model.addGenConstrAbs(abs2, diff2, "absConstr2");

            expr = new GRBLinExpr();
            expr.addTerm(1, abs1);
            expr.addTerm(1, abs2);
            model.addConstr(expr, GRB.LESS_EQUAL, 10, "constr3");

            expr = new GRBLinExpr();
            expr.addTerm(1, v1);
            expr.addTerm(1, v2);
            expr.addTerm(1, v3);
            model.setObjective(expr, GRB.MAXIMIZE);

            // Optimize model
            model.optimize();

            System.out.println(v1.get(GRB.StringAttr.VarName)
                    + " " +v1.get(GRB.DoubleAttr.X));
            System.out.println(v2.get(GRB.StringAttr.VarName)
                    + " " +v2.get(GRB.DoubleAttr.X));
            System.out.println(v3.get(GRB.StringAttr.VarName)
                    + " " +v3.get(GRB.DoubleAttr.X));


            System.out.println("Obj: " + model.get(GRB.DoubleAttr.ObjVal));

            // Dispose of model and environment
            model.dispose();
            env.dispose();

        } catch (GRBException e) {
            System.out.println("Error code: " + e.getErrorCode() + ". " +
                    e.getMessage());
        }
    }
}
