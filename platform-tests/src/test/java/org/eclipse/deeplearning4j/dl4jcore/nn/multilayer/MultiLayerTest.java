/*
 *  ******************************************************************************
 *  *
 *  *
 *  * This program and the accompanying materials are made available under the
 *  * terms of the Apache License, Version 2.0 which is available at
 *  * https://www.apache.org/licenses/LICENSE-2.0.
 *  *
 *  *  See the NOTICE file distributed with this work for additional
 *  *  information regarding copyright ownership.
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *  * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 *  * License for the specific language governing permissions and limitations
 *  * under the License.
 *  *
 *  * SPDX-License-Identifier: Apache-2.0
 *  *****************************************************************************
 */
package org.eclipse.deeplearning4j.dl4jcore.nn.multilayer;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.deeplearning4j.BaseDL4JTest;
import org.eclipse.deeplearning4j.dl4jcore.TestUtils;
import org.deeplearning4j.datasets.iterator.ExistingDataSetIterator;
import org.deeplearning4j.datasets.iterator.impl.IrisDataSetIterator;
import org.deeplearning4j.datasets.iterator.impl.MnistDataSetIterator;
import org.deeplearning4j.datasets.iterator.utilty.SingletonMultiDataSetIterator;
import org.deeplearning4j.eval.Evaluation;
import org.deeplearning4j.exception.DL4JException;
import org.deeplearning4j.nn.api.Model;
import org.deeplearning4j.nn.api.OptimizationAlgorithm;
import org.deeplearning4j.nn.conf.*;
import org.deeplearning4j.nn.conf.distribution.NormalDistribution;
import org.deeplearning4j.nn.conf.inputs.InputType;
import org.deeplearning4j.nn.conf.layers.*;
import org.deeplearning4j.nn.conf.layers.objdetect.Yolo2OutputLayer;
import org.deeplearning4j.nn.conf.layers.variational.VariationalAutoencoder;
import org.deeplearning4j.nn.conf.preprocessor.CnnToFeedForwardPreProcessor;
import org.deeplearning4j.nn.conf.preprocessor.FeedForwardToRnnPreProcessor;
import org.deeplearning4j.nn.conf.preprocessor.RnnToCnnPreProcessor;
import org.deeplearning4j.nn.conf.preprocessor.RnnToFeedForwardPreProcessor;
import org.deeplearning4j.nn.gradient.DefaultGradient;
import org.deeplearning4j.nn.gradient.Gradient;
import org.deeplearning4j.nn.graph.ComputationGraph;
import org.deeplearning4j.nn.layers.BaseOutputLayer;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.nn.params.DefaultParamInitializer;
import org.deeplearning4j.nn.transferlearning.TransferLearning;
import org.deeplearning4j.nn.weights.WeightInit;
import org.deeplearning4j.nn.workspace.LayerWorkspaceMgr;
import org.deeplearning4j.optimize.api.BaseTrainingListener;
import org.deeplearning4j.optimize.listeners.ScoreIterationListener;
import org.deeplearning4j.util.ModelSerializer;
import org.junit.jupiter.api.*;import org.junit.jupiter.api.Test;
import org.nd4j.common.tests.tags.NativeTag;
import org.nd4j.common.tests.tags.TagNames;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.ops.executioner.OpExecutioner;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.dataset.SplitTestAndTrain;
import org.nd4j.linalg.dataset.api.MultiDataSet;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.heartbeat.Heartbeat;
import org.nd4j.linalg.heartbeat.reports.Environment;
import org.nd4j.linalg.heartbeat.reports.Event;
import org.nd4j.linalg.heartbeat.reports.Task;
import org.nd4j.linalg.heartbeat.utils.EnvironmentUtils;
import org.nd4j.linalg.heartbeat.utils.TaskUtils;
import org.nd4j.linalg.indexing.NDArrayIndex;
import org.nd4j.linalg.learning.config.Adam;
import org.nd4j.linalg.learning.config.NoOp;
import org.nd4j.linalg.learning.config.Sgd;
import org.nd4j.linalg.lossfunctions.LossFunctions;
import org.nd4j.common.primitives.Pair;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

import static org.junit.jupiter.api.Assertions.assertThrows;

@Slf4j
@DisplayName("Multi Layer Test")
@NativeTag
@Tag(TagNames.DL4J_OLD_API)
@Tag(TagNames.LARGE_RESOURCES)
@Tag(TagNames.LONG_TEST)
public class MultiLayerTest extends BaseDL4JTest {

    private static OpExecutioner.ProfilingMode origMode;

    @BeforeAll
    static void beforeClass() {
        origMode = Nd4j.getExecutioner().getProfilingMode();
    }

    @BeforeEach
    void before() {
        Nd4j.getExecutioner().setProfilingMode(OpExecutioner.ProfilingMode.SCOPE_PANIC);
    }

    @AfterAll
    static void afterClass() {
        Nd4j.getExecutioner().setProfilingMode(origMode);
    }

    @Override
    public DataType getDataType() {
        return DataType.FLOAT;
    }

    @Test
    @DisplayName("Test Set Params")
    void testSetParams() {
        MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder().list().layer(0, new DenseLayer.Builder().nIn(4).nOut(3).activation(Activation.TANH).build()).layer(1, new DenseLayer.Builder().nIn(3).nOut(2).build()).build();
        MultiLayerNetwork network3 = new MultiLayerNetwork(conf);
        network3.init();
        INDArray params = network3.params();
        INDArray weights = network3.getLayer(0).getParam(DefaultParamInitializer.WEIGHT_KEY).dup();
        INDArray bias = network3.getLayer(0).getParam(DefaultParamInitializer.BIAS_KEY).dup();
        network3.setParameters(params);
        assertEquals(weights, network3.getLayer(0).getParam(DefaultParamInitializer.WEIGHT_KEY));
        assertEquals(bias, network3.getLayer(0).getParam(DefaultParamInitializer.BIAS_KEY));
        INDArray params4 = network3.params();
        assertEquals(params, params4);
    }

    @Test
    @DisplayName("Test Batch Norm")
    void testBatchNorm() {
        Nd4j.getRandom().setSeed(123);
        MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder().optimizationAlgo(OptimizationAlgorithm.LINE_GRADIENT_DESCENT).seed(123).list().layer(0, new DenseLayer.Builder().nIn(4).nOut(3).weightInit(WeightInit.XAVIER).activation(Activation.TANH).build()).layer(1, new DenseLayer.Builder().nIn(3).nOut(2).weightInit(WeightInit.XAVIER).activation(Activation.TANH).build()).layer(2, new BatchNormalization.Builder().nOut(2).build()).layer(3, new OutputLayer.Builder(LossFunctions.LossFunction.MCXENT).weightInit(WeightInit.XAVIER).activation(Activation.SOFTMAX).nIn(2).nOut(3).build()).build();
        MultiLayerNetwork network = new MultiLayerNetwork(conf);
        network.init();
        network.setListeners(new ScoreIterationListener(1));
        DataSetIterator iter = new IrisDataSetIterator(150, 150);
        DataSet next = iter.next();
        next.normalizeZeroMeanZeroUnitVariance();
        SplitTestAndTrain trainTest = next.splitTestAndTrain(110);
        network.setLabels(trainTest.getTrain().getLabels());
        network.init();
        for (int i = 0; i < 5; i++) {
            network.fit(trainTest.getTrain());
        }
    }

    @Test
    @DisplayName("Test Back Prop")
    void testBackProp() {
        Nd4j.getRandom().setSeed(123);
        MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder().optimizationAlgo(OptimizationAlgorithm.LINE_GRADIENT_DESCENT).seed(123).list().layer(0, new DenseLayer.Builder().nIn(4).nOut(3).weightInit(WeightInit.XAVIER).activation(Activation.TANH).build()).layer(1, new DenseLayer.Builder().nIn(3).nOut(2).weightInit(WeightInit.XAVIER).activation(Activation.TANH).build()).layer(2, new OutputLayer.Builder(LossFunctions.LossFunction.MCXENT).weightInit(WeightInit.XAVIER).activation(Activation.SOFTMAX).nIn(2).nOut(3).build()).build();
        MultiLayerNetwork network = new MultiLayerNetwork(conf);
        network.init();
        network.setListeners(new ScoreIterationListener(1));
        DataSetIterator iter = new IrisDataSetIterator(150, 150);
        DataSet next = iter.next();
        next.normalizeZeroMeanZeroUnitVariance();
        SplitTestAndTrain trainTest = next.splitTestAndTrain(110);
        network.setInput(trainTest.getTrain().getFeatures());
        network.setLabels(trainTest.getTrain().getLabels());
        network.init();
        for (int i = 0; i < 5; i++) {
            network.fit(trainTest.getTrain());
        }
        DataSet test = trainTest.getTest();
        Evaluation eval = new Evaluation();
        INDArray output = network.output(test.getFeatures());
        eval.eval(test.getLabels(), output);
        log.info("Score " + eval.stats());
    }

    @Test
    @DisplayName("Test Gradient With As List")
    void testGradientWithAsList() {
        MultiLayerNetwork net1 = new MultiLayerNetwork(getConf());
        MultiLayerNetwork net2 = new MultiLayerNetwork(getConf());
        net1.init();
        net2.init();
        DataSet x1 = new IrisDataSetIterator(1, 150).next();
        DataSet all = new IrisDataSetIterator(150, 150).next();
        DataSet x2 = all.asList().get(0);
        // x1 and x2 contain identical data
        assertArrayEquals(asFloat(x1.getFeatures()), asFloat(x2.getFeatures()), 0.0f);
        assertArrayEquals(asFloat(x1.getLabels()), asFloat(x2.getLabels()), 0.0f);
        assertEquals(x1, x2);
        // Set inputs/outputs so gradient can be calculated:
        net1.feedForward(x1.getFeatures());
        net2.feedForward(x2.getFeatures());
        ((BaseOutputLayer) net1.getLayer(1)).setLabels(x1.getLabels());
        ((BaseOutputLayer) net2.getLayer(1)).setLabels(x2.getLabels());
        net1.gradient();
        net2.gradient();
    }

    /**
     *  This test intended only to test activateSelectedLayers method, it does not involves fully-working AutoEncoder.
     */
    @Test
    @DisplayName("Test Selected Activations")
    void testSelectedActivations() {
        // Train DeepAutoEncoder on very limited trainset
        final int numRows = 28;
        final int numColumns = 28;
        int seed = 123;
        int numSamples = 3;
        int iterations = 1;
        int listenerFreq = iterations / 5;
        log.info("Load data....");
        float[][] trainingData = new float[numSamples][numColumns * numRows];
        Arrays.fill(trainingData[0], 0.95f);
        Arrays.fill(trainingData[1], 0.5f);
        Arrays.fill(trainingData[2], 0.05f);
        log.info("Build model....");
        MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder().seed(seed).optimizationAlgo(OptimizationAlgorithm.LINE_GRADIENT_DESCENT).list().layer(0, new DenseLayer.Builder().nIn(numRows * numColumns).nOut(1000).build()).layer(1, new DenseLayer.Builder().nIn(1000).nOut(500).build()).layer(2, new DenseLayer.Builder().nIn(500).nOut(250).build()).layer(3, new DenseLayer.Builder().nIn(250).nOut(100).build()).layer(4, // encoding stops
        new DenseLayer.Builder().nIn(100).nOut(30).build()).layer(5, // decoding starts
        new DenseLayer.Builder().nIn(30).nOut(100).build()).layer(6, new DenseLayer.Builder().nIn(100).nOut(250).build()).layer(7, new DenseLayer.Builder().nIn(250).nOut(500).build()).layer(8, new DenseLayer.Builder().nIn(500).nOut(1000).build()).layer(9, new OutputLayer.Builder(LossFunctions.LossFunction.NEGATIVELOGLIKELIHOOD).nIn(1000).nOut(numRows * numColumns).activation(Activation.SOFTMAX).build()).build();
        MultiLayerNetwork model = new MultiLayerNetwork(conf);
        model.init();
        model.addListeners(new ScoreIterationListener(listenerFreq));
        log.info("Train model....");
        int cnt = 0;
        while (cnt < numSamples) {
            INDArray input = Nd4j.create(trainingData[cnt]).reshape(1, -1);
            model.fit(new DataSet(input, input));
            cnt++;
        }
        // Make two separate selective calls
        log.info("Testing full cycle...");
        List<INDArray> comparableResult = model.feedForward(Nd4j.create(trainingData[0], new long[] { 1, trainingData[0].length }));
        INDArray encodeResult = model.activateSelectedLayers(0, 4, Nd4j.create(trainingData[0], new long[] { 1, trainingData[0].length }));
        log.info("Compare feedForward results with selectedActivation");
        assertEquals(comparableResult.get(5), encodeResult);
        INDArray decodeResults = model.activateSelectedLayers(5, 9, encodeResult);
        log.info("Decode results: " + decodeResults.columns() + " " + decodeResults);
        log.info("Comparable  results: " + comparableResult.get(10).columns() + " " + comparableResult.get(10));
        assertEquals(comparableResult.get(10), decodeResults);
    }

    private static MultiLayerConfiguration getConf() {
        MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder().seed(12345L).list().layer(0, new DenseLayer.Builder().nIn(4).nOut(3).dist(new NormalDistribution(0, 1)).build()).layer(1, new OutputLayer.Builder(LossFunctions.LossFunction.MCXENT).activation(Activation.SOFTMAX).nIn(3).nOut(3).dist(new NormalDistribution(0, 1)).build()).build();
        return conf;
    }

    public static float[] asFloat(INDArray arr) {
        long len = arr.length();
        float[] f = new float[(int) len];
        for (int i = 0; i < len; i++) f[i] = arr.getFloat(i);
        return f;
    }

    @Test
    @DisplayName("Test Feed Forward To Layer")
    void testFeedForwardToLayer() {
        int nIn = 30;
        int nOut = 25;
        MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder().optimizationAlgo(OptimizationAlgorithm.CONJUGATE_GRADIENT).updater(new Sgd(1e-3)).list().layer(0, new DenseLayer.Builder().nIn(nIn).nOut(600).dist(new NormalDistribution(0, 1e-5)).build()).layer(1, new DenseLayer.Builder().nIn(600).nOut(250).dist(new NormalDistribution(0, 1e-5)).build()).layer(2, new DenseLayer.Builder().nIn(250).nOut(100).dist(new NormalDistribution(0, 1e-5)).build()).layer(3, new OutputLayer.Builder(LossFunctions.LossFunction.MCXENT).nIn(100).nOut(25).activation(Activation.SOFTMAX).weightInit(new NormalDistribution(0, 1e-5)).build()).build();
        MultiLayerNetwork network = new MultiLayerNetwork(conf);
        network.init();
        INDArray input = Nd4j.rand(5, nIn);
        List<INDArray> activations = network.feedForward(input);
        // 4 layers + input
        assertEquals(5, activations.size());
        List<INDArray> activationsAll = network.feedForwardToLayer(3, input);
        assertEquals(activations, activationsAll);
        for (int i = 3; i >= 0; i--) {
            List<INDArray> activationsPartial = network.feedForwardToLayer(i, input);
            // i+2: for layer 3: input + activations of {0,1,2,3} -> 5 total = 3+2
            assertEquals(i + 2, activationsPartial.size());
            for (int j = 0; j <= i; j++) {
                INDArray exp = activationsAll.get(j);
                INDArray act = activationsPartial.get(j);
                assertEquals(exp, act);
            }
        }
    }

    @Test
    @DisplayName("Test Backprop Gradient")
    void testBackpropGradient() {
        // Testing: MultiLayerNetwork.backpropGradient()
        // i.e., specifically without an output layer
        int nIn = 10;
        int nOut = 40;
        int miniBatch = 5;
        MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder().updater(new Sgd(0.1)).list().layer(0, new DenseLayer.Builder().nIn(nIn).nOut(20).activation(Activation.RELU).weightInit(WeightInit.XAVIER).build()).layer(1, new DenseLayer.Builder().nIn(20).nOut(30).activation(Activation.RELU).weightInit(WeightInit.XAVIER).build()).layer(2, new DenseLayer.Builder().nIn(30).nOut(nOut).activation(Activation.RELU).weightInit(WeightInit.XAVIER).build()).build();
        MultiLayerNetwork net = new MultiLayerNetwork(conf);
        net.init();
        Nd4j.getRandom().setSeed(12345);
        INDArray eps = Nd4j.rand(miniBatch, nOut);
        INDArray input = Nd4j.rand(miniBatch, nIn);
        net.setInput(input);
        // Need to feed forward before backprop
        net.feedForward(true, false);
        Pair<Gradient, INDArray> pair = net.backpropGradient(eps, LayerWorkspaceMgr.noWorkspaces());
        INDArray epsOut = pair.getSecond();
        assertNotNull(epsOut);
        assertArrayEquals(new long[] { miniBatch, nIn }, epsOut.shape());
        Gradient g = pair.getFirst();
        Map<String, INDArray> gradMap = g.gradientForVariable();
        // 3 layers, weight + bias gradients for each
        assertEquals(6, gradMap.size());
        String[] expKeys = { "0_" + DefaultParamInitializer.WEIGHT_KEY, "0_" + DefaultParamInitializer.BIAS_KEY, "1_" + DefaultParamInitializer.WEIGHT_KEY, "2_" + DefaultParamInitializer.BIAS_KEY, "2_" + DefaultParamInitializer.WEIGHT_KEY, "2_" + DefaultParamInitializer.BIAS_KEY };
        Set<String> keys = gradMap.keySet();
        for (String s : expKeys) {
            assertTrue(keys.contains(s));
        }
        /*
        System.out.println(pair);

        //Use updater to go from raw gradients -> updates
        //Apply learning rate, gradient clipping, adagrad/momentum/rmsprop etc
        Updater updater = UpdaterCreator.getUpdater(net);
        updater.update(net, g, 0, miniBatch);

        StepFunction stepFunction = new NegativeGradientStepFunction();
        INDArray params = net.params();
        System.out.println(Arrays.toString(params.get(NDArrayIndex.all(), NDArrayIndex.interval(0, 10)).dup().data().asFloat()));
        stepFunction.step(params, g.gradient());
        net.setParams(params);    //params() may not be in-place
        System.out.println(Arrays.toString(params.get(NDArrayIndex.all(), NDArrayIndex.interval(0, 10)).dup().data().asFloat()));
        */
    }

    @Test
    @DisplayName("Test Layer Names")
    void testLayerNames() {
        int nIn = 10;
        int nOut = 40;
        List<String> layerNameList = new ArrayList<>();
        layerNameList.add("dnn1");
        layerNameList.add("dnn2");
        layerNameList.add("dnn3");
        MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder().updater(new Sgd(0.1)).list().layer(0, new DenseLayer.Builder().name("dnn1").nIn(nIn).nOut(20).activation(Activation.RELU).weightInit(WeightInit.XAVIER).build()).layer(1, new DenseLayer.Builder().name("dnn2").nIn(20).nOut(30).activation(Activation.RELU).weightInit(WeightInit.XAVIER).build()).layer(2, new DenseLayer.Builder().name("dnn3").nIn(30).nOut(nOut).activation(Activation.SOFTMAX).weightInit(WeightInit.XAVIER).build()).build();
        MultiLayerNetwork net = new MultiLayerNetwork(conf);
        net.init();
        assertEquals(layerNameList.get(0), net.getLayer(0).conf().getLayer().getLayerName());
        assertEquals(layerNameList, net.getLayerNames());
        BaseLayer b = (BaseLayer) net.getLayer(layerNameList.get(2)).conf().getLayer();
        assertEquals(b.getActivationFn().toString(), "softmax");
    }

    @Test
    @DisplayName("Test Score Examples")
    void testScoreExamples() {
        Nd4j.getRandom().setSeed(12345);
        int nIn = 5;
        int nOut = 6;
        MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder().seed(12345).l1(0.01).l2(0.01).updater(new Sgd(0.1)).activation(Activation.TANH).weightInit(WeightInit.XAVIER).list().layer(0, new DenseLayer.Builder().nIn(nIn).nOut(20).build()).layer(1, new DenseLayer.Builder().nIn(20).nOut(30).build()).layer(2, new OutputLayer.Builder().lossFunction(LossFunctions.LossFunction.MSE).nIn(30).nOut(nOut).build()).build();
        MultiLayerConfiguration confNoReg = new NeuralNetConfiguration.Builder().seed(12345).updater(new Sgd(0.1)).activation(Activation.TANH).weightInit(WeightInit.XAVIER).list().layer(0, new DenseLayer.Builder().nIn(nIn).nOut(20).build()).layer(1, new DenseLayer.Builder().nIn(20).nOut(30).build()).layer(2, new OutputLayer.Builder().lossFunction(LossFunctions.LossFunction.MSE).nIn(30).nOut(nOut).build()).build();
        MultiLayerNetwork net = new MultiLayerNetwork(conf);
        net.init();
        MultiLayerNetwork netNoReg = new MultiLayerNetwork(confNoReg);
        netNoReg.init();
        netNoReg.setParameters(net.params().dup());
        // Score single example, and compare to scoreExamples:
        INDArray input = Nd4j.rand(3, nIn);
        INDArray output = Nd4j.rand(3, nOut);
        DataSet ds = new DataSet(input, output);
        INDArray scoresWithRegularization = net.scoreExamples(ds, true);
        INDArray scoresNoRegularization = net.scoreExamples(ds, false);
        assertArrayEquals(new long[] { 3, 1 }, scoresWithRegularization.shape());
        assertArrayEquals(new long[] { 3, 1 }, scoresNoRegularization.shape());
        for (int i = 0; i < 3; i++) {
            DataSet singleEx = new DataSet(input.getRow(i, true), output.getRow(i, true));
            double score = net.score(singleEx);
            double scoreNoReg = netNoReg.score(singleEx);
            double scoreUsingScoreExamples = scoresWithRegularization.getDouble(i);
            double scoreUsingScoreExamplesNoReg = scoresNoRegularization.getDouble(i);
            assertEquals(score, scoreUsingScoreExamples, 1e-4);
            assertEquals(scoreNoReg, scoreUsingScoreExamplesNoReg, 1e-4);
            // Regularization term increases score
            assertTrue(scoreUsingScoreExamples > scoreUsingScoreExamplesNoReg);
            // System.out.println(score + "\t" + scoreUsingScoreExamples + "\t|\t" + scoreNoReg + "\t" + scoreUsingScoreExamplesNoReg);
        }
    }

    @Test
    @DisplayName("Test Data Set Score")
    void testDataSetScore() {
        Nd4j.getRandom().setSeed(12345);
        MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder().weightInit(WeightInit.XAVIER).seed(12345L).list().layer(0, new DenseLayer.Builder().nIn(4).nOut(3).activation(Activation.SIGMOID).build()).layer(1, new OutputLayer.Builder(LossFunctions.LossFunction.MCXENT).activation(Activation.SOFTMAX).nIn(3).nOut(3).build()).build();
        MultiLayerNetwork net = new MultiLayerNetwork(conf);
        net.init();
        INDArray in = Nd4j.create(new double[] { 1.0, 2.0, 3.0, 4.0 }, new long[] { 1, 4 });
        INDArray out = Nd4j.create(new double[] { 1, 0, 0 }, new long[] { 1, 3 });
        double score = net.score(new DataSet(in, out));
    }

    @Test
    @DisplayName("Test Data Set Score CNN")
    void testDataSetScoreCNN() {
        int miniBatch = 3;
        int depth = 2;
        int width = 3;
        int height = 3;
        int nOut = 2;
        MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder().seed(12345L).list().layer(0, new ConvolutionLayer.Builder(2, 2).nOut(1).build()).layer(1, new OutputLayer.Builder(LossFunctions.LossFunction.MCXENT).activation(Activation.SOFTMAX).nOut(2).build()).setInputType(InputType.convolutionalFlat(height, width, depth)).build();
        MultiLayerNetwork net = new MultiLayerNetwork(conf);
        net.init();
        Nd4j.getRandom().setSeed(12345);
        Random r = new Random(12345);
        INDArray input = Nd4j.rand(miniBatch, depth * width * height);
        INDArray labels = Nd4j.create(miniBatch, nOut);
        for (int i = 0; i < miniBatch; i++) {
            labels.putScalar(new int[] { i, r.nextInt(nOut) }, 1.0);
        }
        double score = net.score(new DataSet(input, labels));
    }

    @Test
    @DisplayName("Test Predict")
    void testPredict() throws Exception {
        Nd4j.getRandom().setSeed(12345);
        MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder().weightInit(WeightInit.XAVIER).seed(12345L).list().layer(0, new DenseLayer.Builder().nIn(784).nOut(50).activation(Activation.RELU).build()).layer(1, new OutputLayer.Builder(LossFunctions.LossFunction.MCXENT).activation(Activation.SOFTMAX).nIn(50).nOut(10).build()).setInputType(InputType.convolutional(28, 28, 1)).build();
        MultiLayerNetwork net = new MultiLayerNetwork(conf);
        net.init();
        DataSetIterator ds = new MnistDataSetIterator(10, 10);
        net.fit(ds);
        DataSetIterator testDs = new MnistDataSetIterator(1, 1);
        DataSet testData = testDs.next();
        testData.setLabelNames(Arrays.asList("0", "1", "2", "3", "4", "5", "6", "7", "8", "9"));
        String actualLables = testData.getLabelName(0);
        List<String> prediction = net.predict(testData);
        assertTrue(actualLables != null);
        assertTrue(prediction.get(0) != null);
    }

    @Test
    @Disabled
    @DisplayName("Test Cid")
    void testCid() throws Exception {
        System.out.println(EnvironmentUtils.buildCId());
        Environment environment = EnvironmentUtils.buildEnvironment();
        environment.setSerialVersionID(EnvironmentUtils.buildCId());
        Task task = TaskUtils.buildTask(Nd4j.create(new double[] { 1, 2, 3, 4, 5, 6 }, new long[] { 1, 6 }));
        Heartbeat.getInstance().reportEvent(Event.STANDALONE, environment, task);
        Thread.sleep(25000);
    }

    @Test
    @DisplayName("Test Output")
    void testOutput() throws Exception {
        Nd4j.getRandom().setSeed(12345);
        MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder().weightInit(WeightInit.XAVIER).seed(12345L).list().layer(0, new DenseLayer.Builder().nIn(784).nOut(50).activation(Activation.RELU).build()).layer(1, new OutputLayer.Builder(LossFunctions.LossFunction.MCXENT).activation(Activation.SOFTMAX).nIn(50).nOut(10).build()).setInputType(InputType.convolutional(28, 28, 1)).build();
        MultiLayerNetwork net = new MultiLayerNetwork(conf);
        net.init();
        DataSetIterator fullData = new MnistDataSetIterator(1, 2);
        net.fit(fullData);
        fullData.reset();
        DataSet expectedSet = fullData.next(2);
        INDArray expectedOut = net.output(expectedSet.getFeatures(), false);
        fullData.reset();
        INDArray actualOut = net.output(fullData);
        assertEquals(expectedOut, actualOut);
    }

    @Test
    @DisplayName("Test Gradient Update")
    void testGradientUpdate() throws Exception {
        DataSetIterator iter = new IrisDataSetIterator(1, 1);
        Gradient expectedGradient = new DefaultGradient();
        expectedGradient.setGradientFor("0_W", Nd4j.ones(4, 5).castTo(DataType.DOUBLE));
        expectedGradient.setGradientFor("0_b", Nd4j.ones(1, 5).castTo(DataType.DOUBLE));
        expectedGradient.setGradientFor("1_W", Nd4j.ones(5, 3).castTo(DataType.DOUBLE));
        expectedGradient.setGradientFor("1_b", Nd4j.ones(1, 3).castTo(DataType.DOUBLE));
        MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder()
                .updater(new Sgd(1.0)).activation(Activation.RELU)
                .weightInit(WeightInit.XAVIER).list().layer(0, new DenseLayer.Builder().name("dnn1").nIn(4).nOut(5).build())
                .layer(1, new OutputLayer.Builder().name("output").nIn(5).nOut(3).activation(Activation.SOFTMAX)
                        .weightInit(WeightInit.XAVIER).build()).build();
        MultiLayerNetwork net = new MultiLayerNetwork(conf);
        net.init();
        net.fit(iter.next());
        // TODO validate actual layer gradientView - issue getting var out of BaseLayer w/o adding MLN getter that gets confused with local gradient vars
        Gradient actualGradient = net.getGradient();
        assertNotEquals(expectedGradient.getGradientFor("0_W"), actualGradient.getGradientFor("0_W"));
        net.update(expectedGradient);
        actualGradient = net.getGradient();
        assertEquals(expectedGradient.getGradientFor("0_W"), actualGradient.getGradientFor("0_W"));
        // Update params with set
        net.setParam("0_W", Nd4j.ones(4, 5).castTo(DataType.DOUBLE));
        net.setParam("0_b", Nd4j.ones(1, 5).castTo(DataType.DOUBLE));
        net.setParam("1_W", Nd4j.ones(5, 3).castTo(DataType.DOUBLE));
        net.setParam("1_b", Nd4j.ones(1, 3).castTo(DataType.DOUBLE));
        INDArray actualParams = net.params().castTo(DataType.DOUBLE);
        // Confirm params
        assertEquals(expectedGradient.gradient(), actualParams);
        net.update(expectedGradient);
        actualParams = net.params().castTo(DataType.DOUBLE);
        assertEquals(Nd4j.ones( 43).addi(1).castTo(DataType.DOUBLE), actualParams);
    }

    @Test
    @DisplayName("Test Cnn Invalid Data")
    void testCnnInvalidData() {
        assertThrows(DL4JException.class, () -> {
            int miniBatch = 3;
            int depth = 2;
            int width = 5;
            int height = 5;
            MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder().list().layer(0, new ConvolutionLayer.Builder().kernelSize(2, 2).stride(1, 1).padding(0, 0).nIn(2).nOut(2).build()).layer(1, new OutputLayer.Builder(LossFunctions.LossFunction.MCXENT).activation(Activation.SOFTMAX).nOut(2).build()).setInputType(InputType.convolutional(height, width, depth)).build();
            MultiLayerNetwork net = new MultiLayerNetwork(conf);
            net.init();
            // Order: examples, channels, height, width
            INDArray inputWrongDepth = Nd4j.rand(new int[] { miniBatch, 5, height, width });
            net.feedForward(inputWrongDepth);
        });
    }

    @Test
    @DisplayName("Test Applying Pre Train Config And Params")
    void testApplyingPreTrainConfigAndParams() {
        int nIn = 10;
        int nOut = 10;
        // Test pretrain true
        MultiLayerNetwork aePre = getAeModel(true, nIn, nOut);
        int actualNP = (int) aePre.numParams();
        assertEquals(2 * (nIn * nOut + nOut) + nIn, actualNP);
        INDArray params = aePre.params();
        // check num params
        assertEquals(params.length(), actualNP);
        Map<String, INDArray> paramTable = aePre.paramTable();
        // check vb exists for pretrain layer
        assertTrue(paramTable.containsKey("0_vb"));
        aePre.setParam("0_vb", Nd4j.ones(10));
        params = aePre.getParam("0_vb");
        // check set params for vb
        assertEquals(Nd4j.ones( 10), params);
        // Test pretrain false, expect same for true because its not changed when applying update
        MultiLayerNetwork aeNoPre = getAeModel(false, nIn, nOut);
        actualNP = (int) aeNoPre.numParams();
        assertEquals(2 * (nIn * nOut + nOut) + nIn, actualNP);
        params = aeNoPre.params();
        assertEquals(params.length(), actualNP);
        paramTable = aePre.paramTable();
        assertTrue(paramTable.containsKey("0_vb"));
    }

    public MultiLayerNetwork getAeModel(boolean preTrain, int nIn, int nOut) {
        MultiLayerConfiguration vae = new NeuralNetConfiguration.Builder().seed(42).updater(new NoOp()).weightInit(WeightInit.UNIFORM).list(new AutoEncoder.Builder().activation(Activation.IDENTITY).nOut(nIn).build(), new OutputLayer.Builder(LossFunctions.LossFunction.COSINE_PROXIMITY).activation(Activation.IDENTITY).nOut(nOut).build()).setInputType(InputType.feedForward(nOut)).build();
        MultiLayerNetwork network = new MultiLayerNetwork(vae);
        network.init();
        return network;
    }

    @Test
    @DisplayName("Test Iteration Count And Persistence")
    void testIterationCountAndPersistence() throws IOException {
        Nd4j.getRandom().setSeed(123);
        MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder().optimizationAlgo(OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT).seed(123).list().layer(0, new DenseLayer.Builder().nIn(4).nOut(3).weightInit(WeightInit.XAVIER).activation(Activation.TANH).build()).layer(1, new OutputLayer.Builder(LossFunctions.LossFunction.MCXENT).activation(Activation.SOFTMAX).nIn(3).nOut(3).build()).build();
        MultiLayerNetwork network = new MultiLayerNetwork(conf);
        network.init();
        DataSetIterator iter = new IrisDataSetIterator(50, 150);
        assertEquals(0, network.getLayerWiseConfigurations().getIterationCount());
        network.fit(iter);
        assertEquals(3, network.getLayerWiseConfigurations().getIterationCount());
        iter.reset();
        network.fit(iter);
        assertEquals(6, network.getLayerWiseConfigurations().getIterationCount());
        iter.reset();
        network.fit(iter.next());
        assertEquals(7, network.getLayerWiseConfigurations().getIterationCount());
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ModelSerializer.writeModel(network, baos, true);
        byte[] asBytes = baos.toByteArray();
        ByteArrayInputStream bais = new ByteArrayInputStream(asBytes);
        MultiLayerNetwork net = ModelSerializer.restoreMultiLayerNetwork(bais, true);
        assertEquals(7, net.getLayerWiseConfigurations().getIterationCount());
    }

    @Test
    @DisplayName("Test Bias L 1 L 2")
    void testBiasL1L2() {
        Nd4j.getRandom().setSeed(123);
        MultiLayerConfiguration conf1 = new NeuralNetConfiguration.Builder().optimizationAlgo(OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT).weightInit(WeightInit.XAVIER).activation(Activation.TANH).seed(123).list().layer(0, new DenseLayer.Builder().nIn(10).nOut(10).build()).layer(1, new OutputLayer.Builder(LossFunctions.LossFunction.MSE).activation(Activation.IDENTITY).nIn(10).nOut(10).build()).build();
        MultiLayerConfiguration conf2 = new NeuralNetConfiguration.Builder().optimizationAlgo(OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT).l1Bias(0.1).l2Bias(0.2).weightInit(WeightInit.XAVIER).activation(Activation.TANH).seed(123).list().layer(0, new DenseLayer.Builder().nIn(10).nOut(10).build()).layer(1, new OutputLayer.Builder(LossFunctions.LossFunction.MSE).activation(Activation.IDENTITY).nIn(10).nOut(10).build()).build();
        MultiLayerNetwork net1 = new MultiLayerNetwork(conf1);
        net1.init();
        MultiLayerNetwork net2 = new MultiLayerNetwork(conf2);
        net2.init();
        BaseLayer bl0 = (BaseLayer) net2.getLayer(0).conf().getLayer();
        assertEquals(0.1, TestUtils.getL1(bl0.getRegularizationBias()), 1e-6);
        assertEquals(0.2, TestUtils.getL2(bl0.getRegularizationBias()), 1e-6);
        INDArray features = Nd4j.rand(10, 10);
        INDArray labels = Nd4j.rand(10, 10);
        net2.setParams(net1.params().dup());
        net1.setInput(features);
        net1.setLabels(labels);
        net2.setInput(features);
        net2.setLabels(labels);
        net1.computeGradientAndScore();
        net2.computeGradientAndScore();
        double r = net1.calcRegularizationScore(true);
        assertEquals(0.0, r, 0.0);
        r = net2.calcRegularizationScore(true);
        assertEquals(0.0, r, 0.0);
        double s1 = net1.score();
        double s2 = net2.score();
        // Biases initialized to 0 -> should initially have same score
        assertEquals(s1, s2, 1e-6);
        for (int i = 0; i < 10; i++) {
            net1.fit(features, labels);
        }
        net2.setParams(net1.params().dup());
        net1.computeGradientAndScore();
        net2.computeGradientAndScore();
        r = net1.calcRegularizationScore(true);
        assertEquals(0.0, r, 0.0);
        r = net2.calcRegularizationScore(true);
        assertTrue(r > 0.0);
        s1 = net1.score();
        s2 = net2.score();
        // Scores should differ due to bias l1/l2
        assertNotEquals(s1, s2, 1e-6);
        for (int i = 0; i < 2; i++) {
            assertEquals(0.0, net1.getLayer(i).calcRegularizationScore(true), 0.0);
            assertTrue(net2.getLayer(i).calcRegularizationScore(true) > 0.0);
        }
    }

    /*
        Summary should pick up preprocessors set manually on inputs as well
     */
    @Test
    @DisplayName("Test Summary")
    void testSummary() {
        int V_WIDTH = 130;
        int V_HEIGHT = 130;
        int V_NFRAMES = 150;
        MultiLayerConfiguration confForArchitecture = // l2 regularization on all layers
        new NeuralNetConfiguration.Builder().seed(12345).l2(0.001).optimizationAlgo(OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT).list().layer(0, // 3 channels: RGB
        new ConvolutionLayer.Builder(10, 10).nIn(3).nOut(30).stride(4, 4).activation(Activation.RELU).weightInit(WeightInit.RELU).updater(Updater.ADAGRAD).build()).layer(1, new SubsamplingLayer.Builder(SubsamplingLayer.PoolingType.MAX).kernelSize(3, 3).stride(2, 2).build()).layer(2, new ConvolutionLayer.Builder(3, 3).nIn(30).nOut(10).stride(2, 2).activation(Activation.RELU).weightInit(WeightInit.RELU).updater(Updater.ADAGRAD).build()).layer(3, new DenseLayer.Builder().activation(Activation.RELU).nIn(490).nOut(50).weightInit(WeightInit.RELU).updater(Updater.ADAGRAD).gradientNormalization(GradientNormalization.ClipElementWiseAbsoluteValue).gradientNormalizationThreshold(10).build()).layer(4, new GravesLSTM.Builder().activation(Activation.SOFTSIGN).nIn(50).nOut(50).weightInit(WeightInit.XAVIER).updater(Updater.ADAGRAD).gradientNormalization(GradientNormalization.ClipElementWiseAbsoluteValue).gradientNormalizationThreshold(10).build()).layer(5, new RnnOutputLayer.Builder(LossFunctions.LossFunction.MCXENT).activation(Activation.SOFTMAX).nIn(50).nOut(// 4 possible shapes: circle, square, arc, line
        4).updater(Updater.ADAGRAD).weightInit(WeightInit.XAVIER).gradientNormalization(GradientNormalization.ClipElementWiseAbsoluteValue).gradientNormalizationThreshold(10).build()).inputPreProcessor(0, new RnnToCnnPreProcessor(V_HEIGHT, V_WIDTH, 3)).inputPreProcessor(3, new CnnToFeedForwardPreProcessor(7, 7, 10)).inputPreProcessor(4, new FeedForwardToRnnPreProcessor()).backpropType(BackpropType.TruncatedBPTT).tBPTTForwardLength(V_NFRAMES / 5).tBPTTBackwardLength(V_NFRAMES / 5).build();
        MultiLayerNetwork modelExpectedArch = new MultiLayerNetwork(confForArchitecture);
        modelExpectedArch.init();
        MultiLayerNetwork modelMow = new TransferLearning.Builder(modelExpectedArch).setFeatureExtractor(2).build();
        // System.out.println(modelExpectedArch.summary());
        // System.out.println(modelMow.summary());
        // System.out.println(modelMow.summary(InputType.recurrent(V_HEIGHT*V_WIDTH*3)));
    }

    @Test
    @DisplayName("Test Error No Output Layer")
    void testErrorNoOutputLayer() {
        assertThrows(DL4JException.class, () -> {
            MultiLayerConfiguration c = new NeuralNetConfiguration.Builder().list().layer(0, new DenseLayer.Builder().nIn(10).nOut(10).build()).build();
            MultiLayerNetwork net = new MultiLayerNetwork(c);
            net.init();
            INDArray f = Nd4j.create(1, 10);
            INDArray l = Nd4j.create(1, 10);
            net.setInput(f);
            net.setLabels(l);
            net.computeGradientAndScore();
        });
    }

    @Test
    @DisplayName("Test Set Param Table")
    void testSetParamTable() {
        Nd4j.getRandom().setSeed(123);
        MultiLayerConfiguration conf1 = new NeuralNetConfiguration.Builder().seed(123).list().layer(0, new DenseLayer.Builder().nIn(4).nOut(3).weightInit(WeightInit.XAVIER).activation(Activation.TANH).build()).layer(1, new DenseLayer.Builder().nIn(3).nOut(2).weightInit(WeightInit.XAVIER).activation(Activation.TANH).build()).layer(2, new LSTM.Builder().nIn(2).nOut(2).build()).layer(3, new RnnOutputLayer.Builder(LossFunctions.LossFunction.MCXENT).weightInit(WeightInit.XAVIER).activation(Activation.SOFTMAX).nIn(2).nOut(3).build()).build();
        MultiLayerConfiguration conf2 = new NeuralNetConfiguration.Builder().seed(987).list().layer(0, new DenseLayer.Builder().nIn(4).nOut(3).weightInit(WeightInit.XAVIER).activation(Activation.TANH).build()).layer(1, new DenseLayer.Builder().nIn(3).nOut(2).weightInit(WeightInit.XAVIER).activation(Activation.TANH).build()).layer(2, new LSTM.Builder().nIn(2).nOut(2).build()).layer(3, new RnnOutputLayer.Builder(LossFunctions.LossFunction.MCXENT).weightInit(WeightInit.XAVIER).activation(Activation.SOFTMAX).nIn(2).nOut(3).build()).build();
        MultiLayerNetwork net1 = new MultiLayerNetwork(conf1);
        net1.init();
        MultiLayerNetwork net2 = new MultiLayerNetwork(conf2);
        net2.init();
        assertNotEquals(net1.params(), net2.params());
        assertNotEquals(net1.paramTable(), net2.paramTable());
        net1.setParamTable(net2.paramTable());
        assertEquals(net1.params(), net2.params());
        assertEquals(net1.paramTable(), net2.paramTable());
    }

    @Test
    @DisplayName("Test Compare Layer Methods")
    void testCompareLayerMethods() {
        // Simple test: compare .layer(int, Layer) and .layer(Layer) are identical
        MultiLayerConfiguration conf1 = new NeuralNetConfiguration.Builder().seed(123).list().layer(0, new DenseLayer.Builder().nIn(4).nOut(3).weightInit(WeightInit.XAVIER).activation(Activation.TANH).build()).layer(1, new DenseLayer.Builder().nIn(3).nOut(2).weightInit(WeightInit.XAVIER).activation(Activation.TANH).build()).layer(2, new LSTM.Builder().nIn(2).nOut(2).build()).layer(3, new RnnOutputLayer.Builder(LossFunctions.LossFunction.MCXENT).weightInit(WeightInit.XAVIER).activation(Activation.SOFTMAX).nIn(2).nOut(3).build()).build();
        MultiLayerConfiguration conf2 = new NeuralNetConfiguration.Builder().seed(123).list().layer(new DenseLayer.Builder().nIn(4).nOut(3).weightInit(WeightInit.XAVIER).activation(Activation.TANH).build()).layer(new DenseLayer.Builder().nIn(3).nOut(2).weightInit(WeightInit.XAVIER).activation(Activation.TANH).build()).layer(new LSTM.Builder().nIn(2).nOut(2).build()).layer(new RnnOutputLayer.Builder(LossFunctions.LossFunction.MCXENT).weightInit(WeightInit.XAVIER).activation(Activation.SOFTMAX).nIn(2).nOut(3).build()).build();
        assertEquals(conf1, conf2);
    }

    @Test
    @DisplayName("Test Epoch Counter")
    void testEpochCounter() throws Exception {
        MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder().list().layer(new OutputLayer.Builder().nIn(4).nOut(3).activation(Activation.SOFTMAX).build()).build();
        MultiLayerNetwork net = new MultiLayerNetwork(conf);
        net.init();
        assertEquals(0, net.getLayerWiseConfigurations().getEpochCount());
        DataSetIterator iter = new IrisDataSetIterator(150, 150);
        for (int i = 0; i < 4; i++) {
            assertEquals(i, net.getLayerWiseConfigurations().getEpochCount());
            net.fit(iter);
            assertEquals(i + 1, net.getLayerWiseConfigurations().getEpochCount());
        }
        assertEquals(4, net.getLayerWiseConfigurations().getEpochCount());
        MultiLayerNetwork restored = TestUtils.testModelSerialization(net);
        assertEquals(4, restored.getLayerWiseConfigurations().getEpochCount());
    }

    @Test
    @DisplayName("Test Input Clearance")
    void testInputClearance() throws Exception {
        // Activations should be cleared - if not, it's possible for out of (workspace) scope arrays to be around
        // which can cause a crash
        MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder().convolutionMode(ConvolutionMode.Same).list().layer(new ConvolutionLayer.Builder().kernelSize(2, 2).stride(1, 1).nIn(1).nOut(1).build()).layer(new SubsamplingLayer.Builder().kernelSize(2, 2).stride(1, 1).build()).layer(new DenseLayer.Builder().nOut(10).build()).layer(new OutputLayer.Builder().nOut(10).activation(Activation.SOFTMAX).build()).setInputType(InputType.convolutional(28, 28, 1)).build();
        MultiLayerNetwork net = new MultiLayerNetwork(conf);
        net.init();
        INDArray content = Nd4j.create(1, 1, 28, 28);
        // Check output:
        net.output(content);
        for (org.deeplearning4j.nn.api.Layer l : net.getLayers()) {
            assertNull(l.input());
        }
        // Check feedForward:
        net.feedForward(content, false);
        for (org.deeplearning4j.nn.api.Layer l : net.getLayers()) {
            assertNull(l.input());
        }
    }

    @Test
    @DisplayName("Test External Errors")
    void testExternalErrors() {
        // Simple test: same network, but in one case: one less layer (the OutputLayer), where the epsilons are passed in externally
        // instead. Should get identical results
        for (WorkspaceMode ws : WorkspaceMode.values()) {
            log.info("Workspace mode: " + ws);
            Nd4j.getRandom().setSeed(12345);
            INDArray inData = Nd4j.rand(3, 10);
            INDArray outData = Nd4j.rand(3, 10);
            Nd4j.getRandom().setSeed(12345);
            MultiLayerConfiguration standard = new NeuralNetConfiguration.Builder().updater(new Sgd(0.1)).trainingWorkspaceMode(ws).inferenceWorkspaceMode(ws).seed(12345).list().layer(new DenseLayer.Builder().nIn(10).nOut(10).build()).layer(new OutputLayer.Builder().lossFunction(LossFunctions.LossFunction.MSE).nIn(10).nOut(10).build()).build();
            MultiLayerNetwork s = new MultiLayerNetwork(standard);
            s.init();
            Nd4j.getRandom().setSeed(12345);
            MultiLayerConfiguration external = new NeuralNetConfiguration.Builder().updater(new Sgd(0.1)).trainingWorkspaceMode(ws).inferenceWorkspaceMode(ws).seed(12345).list().layer(new DenseLayer.Builder().nIn(10).nOut(10).build()).build();
            MultiLayerNetwork e = new MultiLayerNetwork(external);
            e.init();
            s.setInput(inData);
            s.setLabels(outData);
            s.computeGradientAndScore();
            Gradient sGrad = s.gradient();
            s.setInput(inData);
            // FF without clearing inputs as we need them later
            s.feedForward(true, false);
            e.setInput(inData);
            // FF without clearing inputs as we need them later
            e.feedForward(true, false);
            org.deeplearning4j.nn.layers.OutputLayer ol = (org.deeplearning4j.nn.layers.OutputLayer) s.getLayer(1);
            Pair<Gradient, INDArray> olPairStd = ol.backpropGradient(null, LayerWorkspaceMgr.noWorkspaces());
            INDArray olEpsilon = olPairStd.getSecond().detach();
            e.setInput(inData);
            e.feedForward(true, false);
            Pair<Gradient, INDArray> extErrorGrad = e.backpropGradient(olEpsilon, LayerWorkspaceMgr.noWorkspaces());
            int nParamsDense = 10 * 10 + 10;
            assertEquals(sGrad.gradient().get( NDArrayIndex.interval(0, nParamsDense)), extErrorGrad.getFirst().gradient());
            Nd4j.getWorkspaceManager().destroyAllWorkspacesForCurrentThread();
        }
    }

    @Test
    @DisplayName("Test External Errors 2")
    void testExternalErrors2() {
        Nd4j.getExecutioner().setProfilingMode(OpExecutioner.ProfilingMode.SCOPE_PANIC);
        int nIn = 4;
        int nOut = 3;
        for (WorkspaceMode ws : WorkspaceMode.values()) {
            // System.out.println("***** WORKSPACE: " + ws);
            MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder().updater(new Adam(0.01)).trainingWorkspaceMode(ws).inferenceWorkspaceMode(ws).list().layer(new DenseLayer.Builder().nIn(nIn).nOut(nOut).activation(Activation.RELU).build()).layer(new ActivationLayer.Builder().activation(Activation.IDENTITY).build()).inputPreProcessor(0, new RnnToFeedForwardPreProcessor()).inputPreProcessor(1, new FeedForwardToRnnPreProcessor()).build();
            MultiLayerNetwork graph = new MultiLayerNetwork(conf);
            graph.init();
            final int minibatch = 5;
            final int seqLen = 6;
            INDArray param = Nd4j.create(new double[] { 0.54, 0.31, 0.98, -0.30, -0.66, -0.19, -0.29, -0.62, 0.13, -0.32, 0.01, -0.03, 0.00, 0.00, 0.00 }).reshape(1, -1);
            graph.setParams(param);
            INDArray input = Nd4j.rand(new int[] { minibatch, nIn, seqLen }, 12);
            INDArray expected = Nd4j.ones(minibatch, nOut, seqLen);
            graph.setInput(input);
            INDArray output = graph.feedForward(false, false).get(2);
            INDArray error = output.sub(expected);
            for (org.deeplearning4j.nn.api.Layer l : graph.getLayers()) {
                assertNotNull(l.input());
                assertFalse(l.input().isAttached());
            }
            // Compute Gradient
            Pair<Gradient, INDArray> gradient = graph.backpropGradient(error, LayerWorkspaceMgr.noWorkspaces());
            graph.getUpdater().update(graph, gradient.getFirst(), 0, 0, minibatch, LayerWorkspaceMgr.noWorkspaces());
            Nd4j.getWorkspaceManager().destroyAllWorkspacesForCurrentThread();
        }
        Nd4j.getExecutioner().setProfilingMode(OpExecutioner.ProfilingMode.DISABLED);
    }

    @Test
    @DisplayName("Test Layer Size")
    void testLayerSize() {
        MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder().list().layer(new ConvolutionLayer.Builder().kernelSize(2, 2).nOut(6).build()).layer(new SubsamplingLayer.Builder().kernelSize(2, 2).build()).layer(new DenseLayer.Builder().nOut(30).build()).layer(new OutputLayer.Builder().nOut(13).activation(Activation.SOFTMAX).build()).setInputType(InputType.convolutional(28, 28, 3)).build();
        MultiLayerNetwork net = new MultiLayerNetwork(conf);
        net.init();
        assertEquals(6, net.layerSize(0));
        assertEquals(0, net.layerSize(1));
        assertEquals(30, net.layerSize(2));
        assertEquals(13, net.layerSize(3));
        assertEquals(3, net.layerInputSize(0));
        assertEquals(0, net.layerInputSize(1));
        assertEquals(((FeedForwardLayer) net.getLayer(2).conf().getLayer()).getNIn(), net.layerInputSize(2));
        assertEquals(30, net.layerInputSize(3));
    }

    @Test
    @DisplayName("Test Zero Param Net")
    void testZeroParamNet() throws Exception {
        MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder().list().layer(new SubsamplingLayer.Builder().kernelSize(2, 2).stride(2, 2).build()).layer(new LossLayer.Builder().activation(Activation.SIGMOID).lossFunction(LossFunctions.LossFunction.MSE).build()).setInputType(InputType.convolutionalFlat(28, 28, 1)).build();
        MultiLayerNetwork net = new MultiLayerNetwork(conf);
        net.init();
        DataSet ds = new MnistDataSetIterator(16, true, 12345).next();
        INDArray out = net.output(ds.getFeatures());
        INDArray labelTemp = Nd4j.create(out.shape());
        ds.setLabels(labelTemp);
        net.fit(ds);
        MultiLayerNetwork net2 = TestUtils.testModelSerialization(net);
        INDArray out2 = net2.output(ds.getFeatures());
        assertEquals(out, out2);
    }

    @Test
    @DisplayName("Test Input Activation Gradient")
    void testInputActivationGradient() {
        MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder()
                .dataType(DataType.DOUBLE).seed(12345).activation(Activation.TANH)
                .list().layer(new DenseLayer.Builder().nIn(10).nOut(10).build())
                .layer(new OutputLayer.Builder().nIn(10).nOut(10).lossFunction(LossFunctions.LossFunction.MSE).build()).build();
        MultiLayerNetwork net = new MultiLayerNetwork(conf);
        net.init();
        INDArray in = Nd4j.rand(1, 10).castTo(DataType.DOUBLE);
        INDArray label = Nd4j.rand(1, 10).castTo(DataType.DOUBLE);
        Pair<Gradient, INDArray> p = net.calculateGradients(in, label, null, null);
        // Quick gradient check:
        double eps = 1e-6;
        double maxRelError = 1e-5;
        for (int i = 0; i < 10; i++) {
            double orig = in.getDouble(i);
            in.putScalar(i, orig + eps);
            double scorePlus = net.score(new DataSet(in, label));
            in.putScalar(i, orig - eps);
            double scoreMinus = net.score(new DataSet(in, label));
            in.putScalar(i, orig);
            double expGrad = (scorePlus - scoreMinus) / (2.0 * eps);
            double actGrad = p.getSecond().getDouble(i);
            double relError = (Math.abs(expGrad - actGrad)) / (Math.abs(expGrad) + Math.abs(actGrad));
            String str = i + " - " + relError + " - exp=" + expGrad + ", act=" + actGrad;
            assertTrue(relError < maxRelError,str);
        }
    }

    @Test
    @DisplayName("Test Multi Layer Configuration Activation Types")
    void testMultiLayerConfigurationActivationTypes() {
        NeuralNetConfiguration.ListBuilder builder = new NeuralNetConfiguration.Builder().list().layer(new LSTM.Builder().nOut(6).build()).layer(new LSTM.Builder().nOut(7).build()).layer(new GlobalPoolingLayer()).layer(new OutputLayer.Builder().nOut(8).activation(Activation.SOFTMAX).build()).setInputType(InputType.recurrent(10));
        MultiLayerConfiguration conf = builder.build();
        List<InputType> outBuilder = builder.getLayerActivationTypes();
        List<InputType> outConf = conf.getLayerActivationTypes(InputType.recurrent(10));
        List<InputType> exp = Arrays.asList(InputType.recurrent(6), InputType.recurrent(7), InputType.recurrent(7), InputType.feedForward(8,RNNFormat.NCW));
        assertEquals(exp, outBuilder);
        assertEquals(exp, outConf);
    }

    @Test
    @DisplayName("Test Multiple Epochs Simple")

    void testMultipleEpochsSimple() {
        // Mainly a simple sanity check on the preconditions in the method...
        DataSetIterator iter = new IrisDataSetIterator(10, 150);
        MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder().list().layer(new OutputLayer.Builder().nIn(4).nOut(3).activation(Activation.SOFTMAX).build()).build();
        MultiLayerNetwork net = new MultiLayerNetwork(conf);
        net.init();
        net.fit(iter, 3);
        ComputationGraph g = net.toComputationGraph();
        g.fit(iter, 3);
    }

    @Test
    @DisplayName("Test Pretrain Fit Methods")
    void testPretrainFitMethods() {
        // The fit methods should *not* do layerwise pretraining:
        MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder().list().layer(new VariationalAutoencoder.Builder().nIn(10).nOut(10).encoderLayerSizes(10).decoderLayerSizes(10).build()).layer(new OutputLayer.Builder().nIn(10).nOut(10).activation(Activation.SOFTMAX).build()).build();
        MultiLayerNetwork net = new MultiLayerNetwork(conf);
        net.init();
        Set<Class<?>> exp = new HashSet<>();
        exp.add(MultiLayerNetwork.class);
        CheckModelsListener listener = new CheckModelsListener();
        net.setListeners(listener);
        INDArray f = Nd4j.create(1, 10);
        INDArray l = Nd4j.create(1, 10);
        DataSet ds = new DataSet(f, l);
        MultiDataSet mds = new org.nd4j.linalg.dataset.MultiDataSet(f, l);
        DataSetIterator iter = new ExistingDataSetIterator(Collections.singletonList(ds));
        net.fit(iter);
        assertEquals(exp, listener.getModelClasses());
        net.fit(ds);
        assertEquals(exp, listener.getModelClasses());
        net.fit(f, l);
        assertEquals(exp, listener.getModelClasses());
        net.fit(f, l, null, null);
        assertEquals(exp, listener.getModelClasses());
        net.fit(mds);
        assertEquals(exp, listener.getModelClasses());
        net.fit(new SingletonMultiDataSetIterator(mds));
        assertEquals(exp, listener.getModelClasses());
    }

    @Test
    @DisplayName("Test IND Array Config Cloning")
    void testINDArrayConfigCloning() {
        // INDArrays in config should be cloned to avoid threading issues
        int mb = 3;
        int b = 4;
        int c = 3;
        int depth = b * (5 + c);
        int w = 6;
        int h = 6;
        INDArray bbPrior = Nd4j.rand(b, 2).muliRowVector(Nd4j.create(new double[] { w, h }).castTo(Nd4j.defaultFloatingPointType()));
        MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder().l2(0.01).list()
                .layer(new ConvolutionLayer.Builder().nIn(depth).nOut(depth).kernelSize(1, 1).build())
                .layer(new Yolo2OutputLayer.Builder().boundingBoxPriors(bbPrior).build()).build();
        MultiLayerConfiguration conf2 = conf.clone();
        INDArray bb1 = ((Yolo2OutputLayer) conf.getConf(1).getLayer()).getBoundingBoxes().castTo(Nd4j.defaultFloatingPointType());
        INDArray bb2 = ((Yolo2OutputLayer) conf2.getConf(1).getLayer()).getBoundingBoxes().castTo(Nd4j.defaultFloatingPointType());
        assertFalse(bb1 == bb2);
        assertEquals(bb1, bb2);
    }

    @Data
    @DisplayName("Check Models Listener")
    public static class CheckModelsListener extends BaseTrainingListener {

        private Set<Class<?>> modelClasses = new HashSet<>();

        @Override
        public void iterationDone(Model model, int iteration, int epoch) {
            modelClasses.add(model.getClass());
        }
    }

    @Test
    @DisplayName("Test MLN Updater Blocks")
    void testMLNUpdaterBlocks() {
        // Check that setting learning rate results in correct rearrangement of updater state within updater blocks
        // https://github.com/eclipse/deeplearning4j/issues/6809#issuecomment-463892644
        double lr = 1e-3;
        MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder().seed(12345).weightInit(WeightInit.XAVIER).updater(new Adam(lr)).list().layer(new DenseLayer.Builder().nIn(5).nOut(3).build()).layer(new DenseLayer.Builder().nIn(3).nOut(2).build()).layer(new OutputLayer.Builder(LossFunctions.LossFunction.XENT).nIn(2).nOut(1).activation(Activation.SIGMOID).build()).build();
        MultiLayerNetwork net = new MultiLayerNetwork(conf);
        net.init();
        INDArray in = Nd4j.rand(1, 5);
        INDArray lbl = Nd4j.rand(1, 1);
        net.fit(new DataSet(in, lbl));
        INDArray viewArray = net.getUpdater().getStateViewArray();
        INDArray viewArrayCopy = viewArray.dup();
        // Initially updater view array is set out like:
        // [m0w, m0b, m1w, m1b, m2w, m2b][v0w, v0b, v1w, v1b, v2w, v2b]
        long soFar = 0;
        // m0w
        INDArray m0w = viewArray.get( NDArrayIndex.interval(soFar, soFar + 5 * 3)).assign(0);
        soFar += 5 * 3;
        // m0b
        INDArray m0b = viewArray.get( NDArrayIndex.interval(soFar, soFar + 3)).assign(1);
        soFar += 3;
        // m1w
        INDArray m1w = viewArray.get(NDArrayIndex.interval(soFar, soFar + 3 * 2)).assign(2);
        soFar += 3 * 2;
        // m1b
        INDArray m1b = viewArray.get(NDArrayIndex.interval(soFar, soFar + 2)).assign(3);
        soFar += 2;
        // m2w
        INDArray m2w = viewArray.get(NDArrayIndex.interval(soFar, soFar + 2 * 1)).assign(4);
        soFar += 2 * 1;
        // m2b
        INDArray m2b = viewArray.get( NDArrayIndex.interval(soFar, soFar + 1)).assign(5);
        soFar += 1;
        // v0w
        INDArray v0w = viewArray.get( NDArrayIndex.interval(soFar, soFar + 5 * 3)).assign(6);
        soFar += 5 * 3;
        // v0b
        INDArray v0b = viewArray.get( NDArrayIndex.interval(soFar, soFar + 3)).assign(7);
        soFar += 3;
        // v1w
        INDArray v1w = viewArray.get( NDArrayIndex.interval(soFar, soFar + 3 * 2)).assign(8);
        soFar += 3 * 2;
        // v1b
        INDArray v1b = viewArray.get( NDArrayIndex.interval(soFar, soFar + 2)).assign(9);
        soFar += 2;
        // v2w
        INDArray v2w = viewArray.get( NDArrayIndex.interval(soFar, soFar + 2 * 1)).assign(10);
        soFar += 2 * 1;
        // v2b
        INDArray v2b = viewArray.get( NDArrayIndex.interval(soFar, soFar + 1)).assign(11);
        soFar += 1;
        net.setLearningRate(0, 0.0);
        // Expect new updater state to look like:
        // [m0w, m0b][v0w,v0b], [m1w, m1b, m2w, m2b][v1w, v1b, v2w, v2b]
        INDArray exp = Nd4j.concat(0, m0w, m0b, v0w, v0b, m1w, m1b, m2w, m2b, v1w, v1b, v2w, v2b);
        INDArray act = net.getUpdater().getStateViewArray();
        // System.out.println(exp);
        // System.out.println(act);
        assertEquals(exp, act);
        // And set layer 1 LR:
        net.setLearningRate(1, 0.2);
        exp = Nd4j.concat(0, m0w, m0b, v0w, v0b, m1w, m1b, v1w, v1b, m2w, m2b, v2w, v2b);
        assertEquals(exp, net.getUpdater().getStateViewArray());
        // Set all back to original LR and check again:
        net.setLearningRate(1, lr);
        net.setLearningRate(0, lr);
        exp = Nd4j.concat(0, m0w, m0b, m1w, m1b, m2w, m2b, v0w, v0b, v1w, v1b, v2w, v2b);
        assertEquals(exp, net.getUpdater().getStateViewArray());
        // Finally, training sanity check (if things are wrong, we get -ve values in adam V, which causes NaNs)
        net.getUpdater().getStateViewArray().assign(viewArrayCopy);
        net.setLearningRate(0, 0.0);
        Nd4j.getExecutioner().setProfilingMode(OpExecutioner.ProfilingMode.NAN_PANIC);
        net.fit(new DataSet(in, lbl));
        Nd4j.getExecutioner().setProfilingMode(OpExecutioner.ProfilingMode.SCOPE_PANIC);
    }
}
