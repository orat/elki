package de.lmu.ifi.dbs.elki.data.uncertain.probabilitydensityfunction;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import de.lmu.ifi.dbs.elki.algorithm.clustering.uncertain.PWCClusteringAlgorithm;
import de.lmu.ifi.dbs.elki.data.DoubleVector;
import de.lmu.ifi.dbs.elki.data.NumberVector;
import de.lmu.ifi.dbs.elki.data.spatial.SpatialComparable;
import de.lmu.ifi.dbs.elki.data.uncertain.ContinuousUncertainObject;
import de.lmu.ifi.dbs.elki.data.uncertain.UOModel;
import de.lmu.ifi.dbs.elki.data.uncertain.UncertainObject;
import de.lmu.ifi.dbs.elki.data.uncertain.UncertainUtil;
import de.lmu.ifi.dbs.elki.datasource.filter.transform.UncertainifyFilter;
import de.lmu.ifi.dbs.elki.math.random.RandomFactory;
import de.lmu.ifi.dbs.elki.result.textwriter.TextWriterStream;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.AbstractParameterizer;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.OptionID;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameterization.Parameterization;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameters.DoubleParameter;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameters.LongParameter;

/**
 *
 * ProbabilityDensityFunction class to model dimensional independent
 * gaussian distributions.
 *
 * Used for construction of {@link UncertainObject}, filtering with
 * {@link UncertainifyFilter} and sampling with
 * {@link PWCClusteringAlgorithm}.
 *
 * @author Alexander Koos
 *
 */
public class IndependentGaussianDistributionFunction extends AbstractGaussianDistributionFunction<DoubleVector> {

  /**
   *
   * Constructor.
   *
   * @param minDev
   * @param maxDev
   * @param minMin
   * @param maxMin
   * @param minMax
   * @param maxMax
   * @param multMin
   * @param multMax
   * @param rand
   */
  public IndependentGaussianDistributionFunction(final double minDev, final double maxDev, final double minMin, final double maxMin, final double minMax, final double maxMax, final long multMin, final long multMax, final Random rand) {
    this.minDev = minDev;
    this.maxDev = maxDev;
    this.minMin = minMin;
    this.maxMin = maxMin;
    this.minMax = minMax;
    this.maxMax = maxMax;
    this.multMin = multMin;
    this.multMax = multMax;
    this.urand = rand;
  }

  /**
   *
   * Constructor.
   *
   * @param means
   * @param variances
   */
  public IndependentGaussianDistributionFunction(final List<DoubleVector> means, final List<DoubleVector> variances) {
    this(means, variances, null);
  }

  /**
   *
   * Constructor.
   *
   * @param means
   * @param variances
   * @param weights
   */
  public IndependentGaussianDistributionFunction(final List<DoubleVector> means, final List<DoubleVector> variances, final List<Integer> weights) {
    if(means.size() != variances.size() || (weights != null && variances.size() != weights.size())) {
      throw new IllegalArgumentException("[W: ]\tSize of 'means' and 'variances' has to be the same, also Dimensionality of weights.");
    }
    for(int i = 0; i < means.size(); i++) {
      if(variances.get(i).getDimensionality() != means.get(i).getDimensionality()) {
        throw new IllegalArgumentException("[W: ]\tDimensionality of contained DoubleVectors for 'means' and 'variances' hast to be the same.");
      }
    }
    if(weights == null) {
      this.weights = new ArrayList<Integer>();
      if(means.size() == 1) {
        this.weights.add(Integer.valueOf(1));
      } else {
        for(int i = 0; i < means.size(); i++) {
          this.weights.add(this.weightMax / means.size());
        }
      }
    } else {
      this.weights = weights;
      this.weightMax = 0;
      for(int i = 0; i < weights.size(); i++) {
        this.weightMax += weights.get(i);
      }
    }

    this.means = means;
    this.variances = variances;
  }

  @Override
  public DoubleVector drawValue(final SpatialComparable bounds, final Random rand) {
    int index = 0;
    final double[] values = new double[bounds.getDimensionality()];

    for(int j = 0; j < UOModel.DEFAULT_TRY_LIMIT; j++) {
      if(this.weights.size() > 1) {
        index = UncertainUtil.drawIndexFromIntegerWeights(rand, this.weights, this.weightMax);
      }
      boolean inBounds = index < this.weights.size();
      if(!inBounds) {
        continue;
      }
      for(int i = 0; i < values.length; i++) {
        values[i] = this.means.get(index).doubleValue(i) + rand.nextGaussian() * this.variances.get(index).doubleValue(i);
        inBounds &= values[i] <= bounds.getMax(i) && values[i] >= bounds.getMin(i);
      }
      if(inBounds) {
        return new DoubleVector(values);
      }
    }

    return AbstractGaussianDistributionFunction.noSample;
  }

  @Override
  protected List<DoubleVector> getDeviationVector() {
    return this.variances;
  }

  @Override
  public UncertainObject<UOModel<SpatialComparable>> uncertainify(final NumberVector vec, final boolean blur, final boolean uncertainify, final int dims) {
    final int multiplicity = this.urand.nextInt((int) (this.multMax - this.multMin) + 1) + (int) this.multMin;
    final List<DoubleVector> means = new ArrayList<DoubleVector>();
    final List<DoubleVector> variances = new ArrayList<DoubleVector>();
    List<Integer> weights = new ArrayList<Integer>();
    if(uncertainify) {
      weights = UncertainUtil.calculateRandomIntegerWeights(multiplicity, this.weightMax, this.urand);
      for(int h = 0; h < multiplicity; h++) {
        final double[] imeans = new double[vec.getDimensionality()];
        final double[] ivariances = new double[vec.getDimensionality()];
        final double minBound = (this.urand.nextDouble() * (this.maxMin - this.minMin)) + this.minMin;
        final double maxBound = (this.urand.nextDouble() * (this.maxMax - this.minMax)) + this.minMax;
        for(int i = 0; i < vec.getDimensionality(); i++) {
          ivariances[i] = (this.urand.nextDouble() * (this.maxDev - this.minDev)) + this.minDev;
          if( blur ) {
            for(int j = 0; j < UOModel.DEFAULT_TRY_LIMIT; j++) {
              final double val = this.urand.nextGaussian() * ivariances[i] + vec.doubleValue(i);
              if( val >= vec.doubleValue(i) - minBound && val <= vec.doubleValue(i) + maxBound ) {
                imeans[i] = val;
                break;
              }
            }
            if(imeans[i] == 0.0 && (imeans[i] < vec.doubleValue(i) - (minBound * ivariances[i]) || imeans[i] > vec.doubleValue(i) + (maxBound * ivariances[i]))) {
              imeans[i] = this.urand.nextInt(2) == 1 ? vec.doubleValue(i) - (minBound * ivariances[i]) : vec.doubleValue(i) + (maxBound * ivariances[i]);
            }
          } else {
            imeans[i] = vec.doubleValue(i);
          }
        }
        means.add(new DoubleVector(imeans));
        variances.add(new DoubleVector(ivariances));
      }
    } else {
      final int scale = 1 + 2 * dims;
      for(int i = 0; i < dims; i++) {
        weights.add((int) vec.doubleValue( i * scale ));
        means.add(this.extractVectorFromData(vec, dims, (i + 1) * scale - (2 * dims)));
        variances.add(this.extractVectorFromData(vec, dims, (i + 1) * scale - (dims)));
      }
    }

    return new UncertainObject<UOModel<SpatialComparable>>(new ContinuousUncertainObject<IndependentGaussianDistributionFunction>(new IndependentGaussianDistributionFunction(means, variances, weights), vec.getDimensionality()), new DoubleVector(vec.getColumnVector()));
  }

  /**
   *
   * Method to extract a vector of length dims
   * with offset from a given data vector.
   *
   * @param data
   * @param dims
   * @param offset
   * @return
   */
  private DoubleVector extractVectorFromData(final NumberVector data, final int dims, final int offset) {
    final double[] result = new double[dims];
    for(int i = 0; i < dims; i++) {
      result[i] = data.doubleValue(offset + i);
    }
    return new DoubleVector(result);
  }

  /**
   *
   * Parameterizer class.
   *
   * @author Alexander Koos
   *
   */
  public static class Parameterizer extends AbstractParameterizer {
    /**
     * Field to hold parameter value.
     */
    protected double stddevMin;

    /**
     * Field to hold parameter value.
     */
    protected double stddevMax;

    /**
     * Field to hold parameter value.
     */
    protected double minMin;

    /**
     * Field to hold parameter value.
     */
    protected double maxMin;

    /**
     * Field to hold parameter value.
     */
    protected double minMax;

    /**
     * Field to hold parameter value.
     */
    protected double maxMax;

    /**
     * Field to hold parameter value.
     */
    protected long multMin;

    /**
     * Field to hold parameter value.
     */
    protected long multMax;

    /**
     * Field to hold RandomFactory for creation of Random.
     */
    protected RandomFactory randFac;

    /**
     * Parameter to specify the minimum randomly drawn variances
     * shall have.
     */
    public final static OptionID STDDEV_MIN_ID = new OptionID("uo.stddev.min","Minimum variance to be used.");

    /**
     * Parameter to specify the maximum randomly drawn variances
     * shall have.
     */
    public final static OptionID STDDEV_MAX_ID = new OptionID("uo.stddev.max","Maximum variance to be used.");

    /**
     * Parameter to specify the minimum value for randomly drawn
     * deviation from the groundtruth in negative direction.
     */
    public final static OptionID MIN_MIN_ID = new OptionID("uo.lbound.min","Minimum deviation for lower boundary - boundary calculates: mean - lbound");

    /**
     * Parameter to specify the maximum value for randomly drawn
     * deviation from the groundtruth in negative direction.
     */
    public final static OptionID MAX_MIN_ID = new OptionID("uo.lbound.max","Maximum deviation for lower boundary - see uo.lbound.min");

    /**
     * Parameter to specify the minimum value for randomly drawn
     * deviation from the groundtruth in positive direction.
     */
    public final static OptionID MIN_MAX_ID = new OptionID("uo.ubound.min","Minimum deviation for upper boundary - boundary calculates: mean + ubound");

    /**
     * Parameter to specify the maximum value for randomly drawn
     * deviation from the groundtruth in positive direction.
     */
    public final static OptionID MAX_MAX_ID = new OptionID("uo.ubound.max","Maximum deviation for upper boundary - see uo.ubound.min");

    /**
     * Parameter to specify the minimum value for randomly drawn
     * multiplicity of an uncertain object, i.e. how many gaussian
     * distributions are hold.
     */
    public final static OptionID MULT_MIN_ID = new OptionID("uo.mult.min","Minimum amount of possible gaussian distributions per uncertain object.");

    /**
     * Parameter to specify the maximum value for randomly drawn
     * multiplicity of an uncertain object, i.e. how many gaussian
     * distributions are hold.
     */
    public final static OptionID MULT_MAX_ID = new OptionID("uo.mult.max","Maximum amount of possible gaussian distributions per uncertain object.");

    /**
     * Parameter to seed the Random used for uncertainification.
     */
    public final static OptionID SEED_ID = new OptionID("uo.seed","Seed used for uncertainification.");

    @Override
    protected void makeOptions(final Parameterization config) {
      super.makeOptions(config);
      final DoubleParameter pDevMin = new DoubleParameter(Parameterizer.STDDEV_MIN_ID,UOModel.DEFAULT_STDDEV);
      if(config.grab(pDevMin)) {
        this.stddevMin = pDevMin.getValue();
      }
      final DoubleParameter pDevMax = new DoubleParameter(Parameterizer.STDDEV_MAX_ID,UOModel.DEFAULT_STDDEV);
      if(config.grab(pDevMax)) {
        this.stddevMax = pDevMax.getValue();
      }
      final DoubleParameter pMinMin = new DoubleParameter(Parameterizer.MIN_MIN_ID, UOModel.DEFAULT_MIN_MAX_DEVIATION_GAUSSIAN);
      if(config.grab(pMinMin)) {
        this.minMin = pMinMin.getValue();
      }
      final DoubleParameter pMaxMin = new DoubleParameter(Parameterizer.MAX_MIN_ID, UOModel.DEFAULT_MIN_MAX_DEVIATION_GAUSSIAN);
      if(config.grab(pMaxMin)) {
        this.maxMin = pMaxMin.getValue();
      }
      final DoubleParameter pMinMax = new DoubleParameter(Parameterizer.MIN_MAX_ID, UOModel.DEFAULT_MIN_MAX_DEVIATION_GAUSSIAN);
      if(config.grab(pMinMax)) {
        this.minMax = pMinMax.getValue();
      }
      final DoubleParameter pMaxMax = new DoubleParameter(Parameterizer.MAX_MAX_ID, UOModel.DEFAULT_MIN_MAX_DEVIATION_GAUSSIAN);
      if(config.grab(pMaxMax)) {
        this.maxMax = pMaxMax.getValue();
      }
      final LongParameter pMultMin = new LongParameter(Parameterizer.MULT_MIN_ID, UOModel.DEFAULT_MULTIPLICITY);
      if(config.grab(pMultMin)) {
        this.multMin = pMultMin.getValue();
      }
      final LongParameter pMultMax = new LongParameter(Parameterizer.MULT_MAX_ID, UOModel.DEFAULT_MULTIPLICITY);
      if(config.grab(pMultMax)) {
        this.multMax = pMultMax.getValue();
      }
      final LongParameter pseed = new LongParameter(Parameterizer.SEED_ID);
      pseed.setOptional(true);
      if(config.grab(pseed)) {
        this.randFac = new RandomFactory(pseed.getValue());
      } else {
        this.randFac = new RandomFactory(null);
      }
    }

    @Override
    protected Object makeInstance() {
      return new IndependentGaussianDistributionFunction(this.stddevMin, this.stddevMax, this.minMin, this.maxMin, this.minMax, this.maxMax, this.multMin, this.multMax, this.randFac.getRandom());
    }
  }

  @Override
  public void writeToText(final TextWriterStream out, final String label) {
    String res = "";
    if(label != null) {
      for(int i = 0; i < this.means.size(); i++) {
        res += label + "= " + "mean_" + i + ": " + this.means.get(i).toString() + "\n"
            + "\t variance_" + i + ": " + this.variances.get(i).toString() + "\n"
            + "\t weight_" + i + ": " + this.weights.get(i) + "\n";
      }
    } else {
      for(int i = 0; i < this.means.size(); i++) {
        res += label + "= " + "mean_" + i + ": " + this.means.get(i).toString() + "\n"
            + "\t variance_" + i + ": " + this.variances.get(i).toString() + "\n"
            + "\t weight_" + i + ": " + this.weights.get(i) + "\n";
      }
    }
    out.inlinePrintNoQuotes(res);
  }

}