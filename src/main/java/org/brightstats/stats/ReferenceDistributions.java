package org.brightstats.stats;

/** Legacy numerical approximations used only for explicitly selected reference compatibility. */
public final class ReferenceDistributions {
    private ReferenceDistributions() {}
    private static double centredNormalQuantile(double centredProbability){
        if(centredProbability>.42){double root=Math.sqrt(-Math.log(.5-centredProbability));return (((2.3212128*root+4.8501413)*root-2.2979648)*root-2.7871893)/((1.6370678*root+3.5438892)*root+1);}
        double square=centredProbability*centredProbability;
        return centredProbability*(((-25.4410605*square+41.3911977)*square-18.6150006)*square+2.5066282)
                /((((3.1308291*square-21.062241)*square+23.0833674)*square-8.4735109)*square+1);
    }
    public static double normalQuantile(double probability){
        if(probability>1||probability<0)return 0;
        double upper=probability<.5?1-probability:probability;
        double value=centredNormalQuantile(upper-.5);return probability<.5?-value:value;
    }
    public static double normalCdf(double z){
        double absolute=Math.abs(z);
        double polynomial=(((((5.383e-6*absolute+4.88906e-5)*absolute+3.80036e-5)*absolute+.0032776263)*absolute+.0211410061)*absolute+.049867347)*absolute+1;
        double cdf=1-Math.pow(polynomial,-16)/2;return z<0?1-cdf:cdf;
    }
    /** Positive t critical value for a two-sided tail area, retaining the reference's approximation. */
    public static double tCritical(double tailArea,double df){
        if(df==1)return Math.cos(tailArea*Math.PI/2)/Math.sin(tailArea*Math.PI/2);
        if(df==2)return Math.sqrt(2/(tailArea*(2-tailArea))-2);
        double reciprocal=1/(df-.5),large=48/(reciprocal*reciprocal);
        double correction=((20700*reciprocal/large-98)*reciprocal-16)*reciprocal+96.36;
        double scale=((94.5/(large+correction)-3)/large+1)*Math.sqrt(reciprocal*Math.PI*.5)*df;
        double argument=scale*tailArea,power=Math.pow(argument,2/df);
        if(power>.05+reciprocal){
            argument=centredNormalQuantile(.5*(1-tailArea));power=argument*argument;
            if(df<5)correction+=.3*(df-4.5)*(argument+.6);
            correction=(((.05*scale*argument-5)*argument-7)*argument-2)*argument+large+correction;
            power=(((((.4*power+6.3)*power+36)*power+94.5)/correction-power-3)/large+1)*argument;
            power=reciprocal*power*power;
            power=power>.002?Math.exp(power)-1:.5*power*power+power;
        }else power=((1/(((df+6)/(df*power)-.089*scale-.822)*(df+2)*3)+.5/(df+4))*power-1)*(df+1)/(df+2)+1/power;
        return Math.sqrt(df*power);
    }
    public static double tTwoSided(double statistic,int df){
        if(!Double.isFinite(statistic))return Double.NaN;
        double high=1,low=0,middle=0;
        while(high-low>=1e-13){middle=(high-low)/2+low;double critical=tCritical(middle,df);if(statistic<critical)low=middle;else if(statistic>critical)high=middle;else break;}
        return middle;
    }
}
