package com.github.evermore;

import com.google.ortools.Loader;
import com.google.ortools.linearsolver.MPConstraint;
import com.google.ortools.linearsolver.MPObjective;
import com.google.ortools.linearsolver.MPSolver;
import com.google.ortools.linearsolver.MPVariable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ValuationEngine {
    static {
        Loader.loadNativeLibraries();
    }

    public int ARem;
    public int BRem;
    public int CRem;
    public int ERem;
    public int DRem;
    public List<Politician> remPoliticians;
    public Map<Integer, Integer> expPrices;

    public ValuationEngine(int A, int B, int C, int D, int E, List<Politician> initialPool) {
        this.ARem = A;
        this.BRem = B;
        this.CRem = C;
        this.DRem = D;
        this.ERem = E;
        this.remPoliticians = new ArrayList<>(initialPool);
        this.expPrices = new HashMap<>();
    }

    public void updateExpectedPrices(double globalMultiplier) {
        for (var p : remPoliticians) {
            int expPrice = (int) Math.ceil(p.basePrice * globalMultiplier);
            expPrices.put(p.ID, expPrice);
        }
    }

    public void recordAuctionResult(Politician target, boolean bought, int pricePaid) {
        remPoliticians.removeIf(p -> p.ID == target.ID);
        expPrices.remove(target.ID);

        if (bought) {
            ARem -= pricePaid;
            BRem = Math.max(0, BRem - 1);
            CRem = Math.max(0, CRem - (target.isFemale ? 1 : 0));
            ERem = Math.max(0, ERem - (target.isIndian ? 1 : 0));
            DRem -= target.volatilityIndex;
        }
    }

    public int getMaximumBid(Politician target, boolean applyVolatilityBuffer, int volatilityBuffer, boolean applyPriceBuffer, double inflationFactor) {
        int SStar = maxScoreIfIgnore(target, applyVolatilityBuffer, volatilityBuffer, applyPriceBuffer, inflationFactor);
        int targetScoreDiff = (SStar < 0) ? 0 : SStar - target.total;

        int CRestStar = minCostIfInclude(target, targetScoreDiff, applyVolatilityBuffer, volatilityBuffer, applyPriceBuffer, inflationFactor);

        if (CRestStar < 0) return 0;

        int maxBid = ARem - CRestStar;
        return Math.max(0, maxBid);
    }

    private int maxScoreIfIgnore(Politician target, boolean applyVolatilityBuffer, int volatilityBuffer, boolean applyPriceBuffer, double inflationFactor) {
        MPSolver solver = MPSolver.createSolver("SCIP");
        MPVariable[] vars = new MPVariable[remPoliticians.size()];
        MPObjective objective = solver.objective();

        int VRHS = DRem;
        if (applyVolatilityBuffer) {
            VRHS -= volatilityBuffer;
        }

        MPConstraint ACt = solver.makeConstraint(0, ARem);
        MPConstraint BCt = solver.makeConstraint(BRem, MPSolver.infinity());
        MPConstraint CCt = solver.makeConstraint(CRem, MPSolver.infinity());
        MPConstraint DCt = solver.makeConstraint(0, VRHS);
        MPConstraint ECt = solver.makeConstraint(ERem, MPSolver.infinity());

        for (int i = 0; i < remPoliticians.size(); i++) {
            Politician p = remPoliticians.get(i);
            vars[i] = solver.makeIntVar(0, 1, "p" + p.ID);

            if (p.ID == target.ID) {
                vars[i].setBounds(0, 0);
            } else {
                ACt.setCoefficient(vars[i], expPrices.get(p.ID));

                BCt.setCoefficient(vars[i], 1);
                CCt.setCoefficient(vars[i], p.isFemale ? 1 : 0);
                ECt.setCoefficient(vars[i], p.isIndian ? 1 : 0);
                DCt.setCoefficient(vars[i], p.volatilityIndex);
                objective.setCoefficient(vars[i], p.total);
            }
        }

        objective.setMaximization();
        MPSolver.ResultStatus status = solver.solve();
        return (status == MPSolver.ResultStatus.OPTIMAL) ? (int) Math.round(objective.value()) : -1;
    }

    private int minCostIfInclude(Politician target, int scoreDiff, boolean applyVolatilityBuffer, int volatilityBuffer, boolean applyPriceBuffer, double inflationFactor) {
        MPSolver solver = MPSolver.createSolver("SCIP");
        MPVariable[] vars = new MPVariable[remPoliticians.size()];
        MPObjective objective = solver.objective();

        int VRHS = DRem - target.volatilityIndex;
        if (applyVolatilityBuffer) {
            VRHS -= volatilityBuffer;
        }
        if (VRHS < 0) return -1;

        MPConstraint SCt = solver.makeConstraint(scoreDiff, MPSolver.infinity());
        MPConstraint ACt = solver.makeConstraint(Math.max(0, BRem - 1), MPSolver.infinity());
        MPConstraint CCt = solver.makeConstraint(Math.max(0, CRem - (target.isFemale ? 1 : 0)), MPSolver.infinity());
        MPConstraint DCt = solver.makeConstraint(0, VRHS);
        MPConstraint ECt = solver.makeConstraint(Math.max(0, ERem - (target.isIndian ? 1 : 0)), MPSolver.infinity());

        for (int i = 0; i < remPoliticians.size(); i++) {
            Politician p = remPoliticians.get(i);
            vars[i] = solver.makeIntVar(0, 1, "p" + p.ID);

            if (p.ID == target.ID) {
                vars[i].setBounds(0, 0); // Exclusion
            } else {
                SCt.setCoefficient(vars[i], p.total);
                ACt.setCoefficient(vars[i], 1);
                CCt.setCoefficient(vars[i], p.isFemale ? 1 : 0);
                ECt.setCoefficient(vars[i], p.isIndian ? 1 : 0);
                DCt.setCoefficient(vars[i], p.volatilityIndex);

                if (applyPriceBuffer) {
                    objective.setCoefficient(vars[i], (int) Math.ceil(expPrices.get(p.ID) * inflationFactor));
                } else {
                    objective.setCoefficient(vars[i], expPrices.get(p.ID));
                }
            }
        }

        objective.setMinimization();
        MPSolver.ResultStatus status = solver.solve();
        if (status == MPSolver.ResultStatus.OPTIMAL) {
            return (int) Math.round(objective.value());
        } else {
            return -1;
        }
    }
}