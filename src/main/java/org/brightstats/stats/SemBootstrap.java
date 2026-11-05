package org.brightstats.stats;

import java.util.*;
import java.util.function.IntConsumer;

/** Paired-row RAM parameter bootstrap; marker/equality constraints remain fixed across fits. */
public final class SemBootstrap {
    private SemBootstrap(){}
    public record Result(CovarianceSem.Fit point,Bootstrap.Result inference,int improperReplicates){}
    public static Result fit(double[][] observations,SemModel.Model model,CovarianceSem.Options settings,Bootstrap.Options options,IntConsumer progress){
        var original=CovarianceSem.fit(observations,model,settings);if(!original.converged()||!original.identified())throw new IllegalArgumentException("SEM bootstrap requires a converged, identified original model.");
        if(improper(original))throw new IllegalArgumentException("Resolve the original improper SEM solution before bootstrapping.");
        List<Bootstrap.Parameter> parameters=model.parameters().stream().map(v->new Bootstrap.Parameter("SEM parameter",-1,-1,-1,v.name())).toList();int[] improper={0};
        var inference=Resampling.run(observations,parameters,original.estimates(),options,(sample,rows)->{
            var fitted=CovarianceSem.fit(sample,model,settings);if(!fitted.converged())throw new IllegalArgumentException("SEM did not converge");if(!fitted.identified())throw new IllegalArgumentException("SEM information is deficient");if(improper(fitted))improper[0]++;return fitted.estimates();
        },progress,"CB-SEM","Fixed marker loadings and configured equality constraints retained; no arbitrary factor sign reversal");
        if(model.parameters().stream().anyMatch(v->v.lower()!=null||v.upper()!=null))inference=Resampling.suppressTests(inference,v->true,"Configured bounds make regular ratio-test calibration unavailable; uncertainty intervals are empirical summaries, not a boundary-adjusted test.");
        if(improper[0]>0){List<String> warnings=new ArrayList<>(inference.warnings());warnings.add(improper[0]+" bootstrap/jackknife fits had improper disturbance variances/covariances; retained finite parameter draws disclose model instability rather than truncating their distribution.");inference=new Bootstrap.Result(inference.options(),inference.method(),inference.randomGenerator(),inference.signHandling(),inference.successful(),inference.failed(),inference.failures(),inference.estimates(),warnings,inference.estimator());}
        return new Result(original,inference,improper[0]);
    }
    private static boolean improper(CovarianceSem.Fit fit){return fit.warnings().stream().anyMatch(v->v.contains("Heywood")||v.contains("not positive semidefinite"));}
}
