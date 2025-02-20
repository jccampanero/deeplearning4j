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

package org.deeplearning4j.spark.datavec;

import com.sun.jna.Platform;
import lombok.val;
import org.apache.commons.io.FilenameUtils;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.lib.output.SequenceFileOutputFormat;
import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.input.PortableDataStream;
import org.datavec.api.io.labels.ParentPathLabelGenerator;
import org.datavec.api.records.reader.SequenceRecordReader;
import org.datavec.api.records.reader.impl.csv.CSVRecordReader;
import org.datavec.api.records.reader.impl.csv.CSVSequenceRecordReader;
import org.datavec.api.split.FileSplit;
import org.datavec.api.split.InputSplit;
import org.datavec.api.split.NumberedFileInputSplit;
import org.datavec.api.writable.Writable;
import org.datavec.image.recordreader.ImageRecordReader;
import org.datavec.spark.functions.SequenceRecordReaderFunction;
import org.datavec.spark.functions.pairdata.*;
import org.datavec.spark.transform.misc.StringToWritablesFunction;
import org.datavec.spark.util.DataVecSparkUtil;
import org.deeplearning4j.datasets.datavec.RecordReaderDataSetIterator;
import org.deeplearning4j.datasets.datavec.SequenceRecordReaderDataSetIterator;
import org.deeplearning4j.spark.BaseSparkTest;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nd4j.common.io.ClassPathResource;
import org.nd4j.common.tests.tags.NativeTag;
import org.nd4j.common.tests.tags.TagNames;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.factory.Nd4j;
import scala.Tuple2;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
@Tag(TagNames.FILE_IO)
@Tag(TagNames.SPARK)
@Tag(TagNames.DIST_SYSTEMS)
@NativeTag
public class TestDataVecDataSetFunctions extends BaseSparkTest {



    @Test
    public void testDataVecDataSetFunction(@TempDir Path testDir) throws Exception {
        if(Platform.isWindows()) {
            //Spark tests don't run on windows
            return;
        }
        JavaSparkContext sc = getContext();

        File f = testDir.toFile();
        ClassPathResource cpr = new ClassPathResource("dl4j-spark/imagetest/");
        cpr.copyDirectory(f);

        //Test Spark record reader functionality vs. local
        List<String> labelsList = Arrays.asList("0", "1"); //Need this for Spark: can't infer without init call

        String path = f.getPath() + "/*";

        JavaPairRDD<String, PortableDataStream> origData = sc.binaryFiles(path);
        assertEquals(4, origData.count()); //4 images

        ImageRecordReader rr = new ImageRecordReader(28, 28, 1, new ParentPathLabelGenerator());
        rr.setLabels(labelsList);
        org.datavec.spark.functions.RecordReaderFunction rrf = new org.datavec.spark.functions.RecordReaderFunction(rr);
        JavaRDD<List<Writable>> rdd = origData.map(rrf);
        JavaRDD<DataSet> data = rdd.map(new DataVecDataSetFunction(1, 2, false));
        List<DataSet> collected = data.collect();

        //Load normally (i.e., not via Spark), and check that we get the same results (order not withstanding)
        InputSplit is = new FileSplit(f, new String[] {"bmp"}, true);
        ImageRecordReader irr = new ImageRecordReader(28, 28, 1, new ParentPathLabelGenerator());
        irr.initialize(is);

        RecordReaderDataSetIterator iter = new RecordReaderDataSetIterator(irr, 1, 1, 2);
        List<DataSet> listLocal = new ArrayList<>(4);
        while (iter.hasNext()) {
            listLocal.add(iter.next());
        }


        //Compare:
        assertEquals(4, collected.size());
        assertEquals(4, listLocal.size());

        //Check that results are the same (order not withstanding)
        boolean[] found = new boolean[4];
        for (int i = 0; i < 4; i++) {
            int foundIndex = -1;
            DataSet ds = collected.get(i);
            for (int j = 0; j < 4; j++) {
                if (ds.equals(listLocal.get(j))) {
                    if (foundIndex != -1)
                        fail(); //Already found this value -> suggests this spark value equals two or more of local version? (Shouldn't happen)
                    foundIndex = j;
                    if (found[foundIndex])
                        fail(); //One of the other spark values was equal to this one -> suggests duplicates in Spark list
                    found[foundIndex] = true; //mark this one as seen before
                }
            }
        }
        int count = 0;
        for (boolean b : found)
            if (b)
                count++;
        assertEquals(4, count); //Expect all 4 and exactly 4 pairwise matches between spark and local versions
    }

    @Test
    public void testDataVecDataSetFunctionMultiLabelRegression() throws Exception {
        JavaSparkContext sc = getContext();

        List<String> stringData = new ArrayList<>();
        int n = 6;
        for (int i = 0; i < 10; i++) {
            StringBuilder sb = new StringBuilder();
            boolean first = true;
            for (int j = 0; j < n; j++) {
                if (!first)
                    sb.append(",");
                sb.append(10 * i + j);
                first = false;
            }
            stringData.add(sb.toString());
        }

        JavaRDD<String> stringList = sc.parallelize(stringData);
        JavaRDD<List<Writable>> writables = stringList.map(new StringToWritablesFunction(new CSVRecordReader()));
        JavaRDD<DataSet> dataSets = writables.map(new DataVecDataSetFunction(3, 5, -1, true, null, null));

        List<DataSet> ds = dataSets.collect();
        assertEquals(10, ds.size());

        boolean[] seen = new boolean[10];
        for (DataSet d : ds) {
            INDArray f = d.getFeatures();
            INDArray l = d.getLabels();
            assertEquals(3, f.length());
            assertEquals(3, l.length());

            int exampleIdx = ((int) f.getDouble(0)) / 10;
            seen[exampleIdx] = true;

            for (int j = 0; j < 3; j++) {
                assertEquals(10 * exampleIdx + j, (int) f.getDouble(j));
                assertEquals(10 * exampleIdx + j + 3, (int) l.getDouble(j));
            }
        }

        int seenCount = 0;
        for (boolean b : seen)
            if (b)
                seenCount++;
        assertEquals(10, seenCount);
    }

    @Test
    public void testDataVecSequenceDataSetFunction(@TempDir Path testDir) throws Exception {
        if(Platform.isWindows()) {
            //Spark tests don't run on windows
            return;
        }
        JavaSparkContext sc = getContext();
        //Test Spark record reader functionality vs. local
        File dir = testDir.toFile();
        ClassPathResource cpr = new ClassPathResource("dl4j-spark/csvsequence/");
        cpr.copyDirectory(dir);

        JavaPairRDD<String, PortableDataStream> origData = sc.binaryFiles(dir.getAbsolutePath());
        assertEquals(3, origData.count()); //3 CSV sequences



        SequenceRecordReader seqRR = new CSVSequenceRecordReader(1, ",");
        SequenceRecordReaderFunction rrf = new SequenceRecordReaderFunction(seqRR);
        JavaRDD<List<List<Writable>>> rdd = origData.map(rrf);
        JavaRDD<DataSet> data = rdd.map(new DataVecSequenceDataSetFunction(2, -1, true, null, null));
        List<DataSet> collected = data.collect();

        //Load normally (i.e., not via Spark), and check that we get the same results (order not withstanding)
        InputSplit is = new FileSplit(dir, new String[] {"txt"}, true);
        SequenceRecordReader seqRR2 = new CSVSequenceRecordReader(1, ",");
        seqRR2.initialize(is);

        SequenceRecordReaderDataSetIterator iter = new SequenceRecordReaderDataSetIterator(seqRR2, 1, -1, 2, true);
        List<DataSet> listLocal = new ArrayList<>(3);
        while (iter.hasNext()) {
            listLocal.add(iter.next());
        }


        //Compare:
        assertEquals(3, collected.size());
        assertEquals(3, listLocal.size());

        //Check that results are the same (order not withstanding)
        boolean[] found = new boolean[3];
        for (int i = 0; i < 3; i++) {
            int foundIndex = -1;
            DataSet ds = collected.get(i);
            for (int j = 0; j < 3; j++) {
                if (ds.equals(listLocal.get(j))) {
                    if (foundIndex != -1)
                        fail(); //Already found this value -> suggests this spark value equals two or more of local version? (Shouldn't happen)
                    foundIndex = j;
                    if (found[foundIndex])
                        fail(); //One of the other spark values was equal to this one -> suggests duplicates in Spark list
                    found[foundIndex] = true; //mark this one as seen before
                }
            }
        }
        int count = 0;
        for (boolean b : found)
            if (b)
                count++;
        assertEquals(3, count); //Expect all 3 and exactly 3 pairwise matches between spark and local versions
    }

    @Test
    @Disabled
    public void testDataVecSequencePairDataSetFunction(@TempDir Path testDir) throws Exception {
        if(Platform.isWindows()) {
            //Spark tests don't run on windows
            return;
        }
        JavaSparkContext sc = getContext();

        File f = new File(testDir.toFile(),"f");
        ClassPathResource cpr = new ClassPathResource("dl4j-spark/csvsequence/");
        cpr.copyDirectory(f);
        String path = f.getAbsolutePath() + "/*";

        PathToKeyConverter pathConverter = new PathToKeyConverterFilename();
        JavaPairRDD<Text, BytesPairWritable> toWrite =
                        DataVecSparkUtil.combineFilesForSequenceFile(sc, path, path, pathConverter);

        Path p = new File(testDir.toFile(),"dl4j_testSeqPairFn").toPath();
        p.toFile().deleteOnExit();
        String outPath = p.toString() + "/out";
        new File(outPath).deleteOnExit();
        toWrite.saveAsNewAPIHadoopFile(outPath, Text.class, BytesPairWritable.class, SequenceFileOutputFormat.class);

        //Load from sequence file:
        JavaPairRDD<Text, BytesPairWritable> fromSeq = sc.sequenceFile(outPath, Text.class, BytesPairWritable.class);

        SequenceRecordReader srr1 = new CSVSequenceRecordReader(1, ",");
        SequenceRecordReader srr2 = new CSVSequenceRecordReader(1, ",");
        PairSequenceRecordReaderBytesFunction psrbf = new PairSequenceRecordReaderBytesFunction(srr1, srr2);
        JavaRDD<Tuple2<List<List<Writable>>, List<List<Writable>>>> writables = fromSeq.map(psrbf);

        //Map to DataSet:
        DataVecSequencePairDataSetFunction pairFn = new DataVecSequencePairDataSetFunction();
        JavaRDD<DataSet> data = writables.map(pairFn);
        List<DataSet> sparkData = data.collect();


        //Now: do the same thing locally (SequenceRecordReaderDataSetIterator) and compare
        String featuresPath = FilenameUtils.concat(f.getAbsolutePath(), "csvsequence_%d.txt");

        SequenceRecordReader featureReader = new CSVSequenceRecordReader(1, ",");
        SequenceRecordReader labelReader = new CSVSequenceRecordReader(1, ",");
        featureReader.initialize(new NumberedFileInputSplit(featuresPath, 0, 2));
        labelReader.initialize(new NumberedFileInputSplit(featuresPath, 0, 2));

        SequenceRecordReaderDataSetIterator iter =
                        new SequenceRecordReaderDataSetIterator(featureReader, labelReader, 1, -1, true);

        List<DataSet> localData = new ArrayList<>(3);
        while (iter.hasNext())
            localData.add(iter.next());

        assertEquals(3, sparkData.size());
        assertEquals(3, localData.size());

        for (int i = 0; i < 3; i++) {
            //Check shapes etc. data sets order may differ for spark vs. local
            DataSet dsSpark = sparkData.get(i);
            DataSet dsLocal = localData.get(i);

            assertNull(dsSpark.getFeaturesMaskArray());
            assertNull(dsSpark.getLabelsMaskArray());

            INDArray fSpark = dsSpark.getFeatures();
            INDArray fLocal = dsLocal.getFeatures();
            INDArray lSpark = dsSpark.getLabels();
            INDArray lLocal = dsLocal.getLabels();

            val s = new long[] {1, 3, 4}; //1 example, 3 values, 3 time steps
            assertArrayEquals(s, fSpark.shape());
            assertArrayEquals(s, fLocal.shape());
            assertArrayEquals(s, lSpark.shape());
            assertArrayEquals(s, lLocal.shape());
        }


        //Check that results are the same (order not withstanding)
        boolean[] found = new boolean[3];
        for (int i = 0; i < 3; i++) {
            int foundIndex = -1;
            DataSet ds = sparkData.get(i);
            for (int j = 0; j < 3; j++) {
                if (ds.equals(localData.get(j))) {
                    if (foundIndex != -1)
                        fail(); //Already found this value -> suggests this spark value equals two or more of local version? (Shouldn't happen)
                    foundIndex = j;
                    if (found[foundIndex])
                        fail(); //One of the other spark values was equal to this one -> suggests duplicates in Spark list
                    found[foundIndex] = true; //mark this one as seen before
                }
            }
        }
        int count = 0;
        for (boolean b : found)
            if (b)
                count++;
        assertEquals(3, count); //Expect all 3 and exactly 3 pairwise matches between spark and local versions
    }

    @Test
    @Disabled("Permissions issues")
    public void testDataVecSequencePairDataSetFunctionVariableLength(@TempDir Path testDir) throws Exception {
        //Same sort of test as testDataVecSequencePairDataSetFunction() but with variable length time series (labels shorter, align end)
        if(Platform.isWindows()) {
            //Spark tests don't run on windows
            return;
        }
        File dirFeatures = new File(testDir.toFile(),"dirFeatures");
        ClassPathResource cpr = new ClassPathResource("dl4j-spark/csvsequence/");
        cpr.copyDirectory(dirFeatures);

        File dirLabels =  new File(testDir.toFile(),"dirLables");
        ClassPathResource cpr2 = new ClassPathResource("dl4j-spark/csvsequencelabels/");
        cpr2.copyDirectory(dirLabels);


        PathToKeyConverter pathConverter = new PathToKeyConverterNumber(); //Extract a number from the file name
        JavaPairRDD<Text, BytesPairWritable> toWrite =
                        DataVecSparkUtil.combineFilesForSequenceFile(sc, dirFeatures.getAbsolutePath(), dirLabels.getAbsolutePath(), pathConverter);

        Path p = new File(testDir.toFile(),"dl4j_testSeqPairFnVarLength").toPath();
        p.toFile().deleteOnExit();
        String outPath = p.toFile().getAbsolutePath() + "/out";
        new File(outPath).deleteOnExit();
        toWrite.saveAsNewAPIHadoopFile(outPath, Text.class, BytesPairWritable.class, SequenceFileOutputFormat.class);

        //Load from sequence file:
        JavaPairRDD<Text, BytesPairWritable> fromSeq = sc.sequenceFile(outPath, Text.class, BytesPairWritable.class);

        SequenceRecordReader srr1 = new CSVSequenceRecordReader(1, ",");
        SequenceRecordReader srr2 = new CSVSequenceRecordReader(1, ",");
        PairSequenceRecordReaderBytesFunction psrbf = new PairSequenceRecordReaderBytesFunction(srr1, srr2);
        JavaRDD<Tuple2<List<List<Writable>>, List<List<Writable>>>> writables = fromSeq.map(psrbf);

        //Map to DataSet:
        DataVecSequencePairDataSetFunction pairFn = new DataVecSequencePairDataSetFunction(4, false,
                        DataVecSequencePairDataSetFunction.AlignmentMode.ALIGN_END);
        JavaRDD<DataSet> data = writables.map(pairFn);
        List<DataSet> sparkData = data.collect();


        //Now: do the same thing locally (SequenceRecordReaderDataSetIterator) and compare
        String featuresPath = FilenameUtils.concat(dirFeatures.getAbsolutePath(), "csvsequence_%d.txt");
        String labelsPath = FilenameUtils.concat(dirLabels.getAbsolutePath(), "csvsequencelabelsShort_%d.txt");

        SequenceRecordReader featureReader = new CSVSequenceRecordReader(1, ",");
        SequenceRecordReader labelReader = new CSVSequenceRecordReader(1, ",");
        featureReader.initialize(new NumberedFileInputSplit(featuresPath, 0, 2));
        labelReader.initialize(new NumberedFileInputSplit(labelsPath, 0, 2));

        SequenceRecordReaderDataSetIterator iter = new SequenceRecordReaderDataSetIterator(featureReader, labelReader,
                        1, 4, false, SequenceRecordReaderDataSetIterator.AlignmentMode.ALIGN_END);

        List<DataSet> localData = new ArrayList<>(3);
        while (iter.hasNext())
            localData.add(iter.next());

        assertEquals(3, sparkData.size());
        assertEquals(3, localData.size());

        val fShapeExp = new long[] {1, 3, 4}; //1 example, 3 values, 4 time steps
        val lShapeExp = new long[] {1, 4, 4}; //1 example, 4 values/classes, 4 time steps (after padding)
        for (int i = 0; i < 3; i++) {
            //Check shapes etc. data sets order may differ for spark vs. local
            DataSet dsSpark = sparkData.get(i);
            DataSet dsLocal = localData.get(i);

            assertNotNull(dsSpark.getLabelsMaskArray()); //Expect mask array for labels

            INDArray fSpark = dsSpark.getFeatures();
            INDArray fLocal = dsLocal.getFeatures();
            INDArray lSpark = dsSpark.getLabels();
            INDArray lLocal = dsLocal.getLabels();


            assertArrayEquals(fShapeExp, fSpark.shape());
            assertArrayEquals(fShapeExp, fLocal.shape());
            assertArrayEquals(lShapeExp, lSpark.shape());
            assertArrayEquals(lShapeExp, lLocal.shape());
        }


        //Check that results are the same (order not withstanding)
        boolean[] found = new boolean[3];
        for (int i = 0; i < 3; i++) {
            int foundIndex = -1;
            DataSet ds = sparkData.get(i);
            for (int j = 0; j < 3; j++) {
                if (dataSetsEqual(ds, localData.get(j))) {
                    if (foundIndex != -1)
                        fail(); //Already found this value -> suggests this spark value equals two or more of local version? (Shouldn't happen)
                    foundIndex = j;
                    if (found[foundIndex])
                        fail(); //One of the other spark values was equal to this one -> suggests duplicates in Spark list
                    found[foundIndex] = true; //mark this one as seen before
                }
            }
        }
        int count = 0;
        for (boolean b : found) {
            if (b) {
                count++;
            }
        }
        assertEquals(3, count); //Expect all 3 and exactly 3 pairwise matches between spark and local versions


        //-------------------------------------------------
        //NOW: test same thing, but for align start...
        DataVecSequencePairDataSetFunction pairFnAlignStart = new DataVecSequencePairDataSetFunction(4, false,
                        DataVecSequencePairDataSetFunction.AlignmentMode.ALIGN_START);
        JavaRDD<DataSet> rddDataAlignStart = writables.map(pairFnAlignStart);
        List<DataSet> sparkDataAlignStart = rddDataAlignStart.collect();

        featureReader.initialize(new NumberedFileInputSplit(featuresPath, 0, 2)); //re-initialize to reset
        labelReader.initialize(new NumberedFileInputSplit(labelsPath, 0, 2));
        SequenceRecordReaderDataSetIterator iterAlignStart = new SequenceRecordReaderDataSetIterator(featureReader,
                        labelReader, 1, 4, false, SequenceRecordReaderDataSetIterator.AlignmentMode.ALIGN_START);

        List<DataSet> localDataAlignStart = new ArrayList<>(3);
        while (iterAlignStart.hasNext())
            localDataAlignStart.add(iterAlignStart.next());

        assertEquals(3, sparkDataAlignStart.size());
        assertEquals(3, localDataAlignStart.size());

        for (int i = 0; i < 3; i++) {
            //Check shapes etc. data sets order may differ for spark vs. local
            DataSet dsSpark = sparkDataAlignStart.get(i);
            DataSet dsLocal = localDataAlignStart.get(i);

            assertNotNull(dsSpark.getLabelsMaskArray()); //Expect mask array for labels

            INDArray fSpark = dsSpark.getFeatures();
            INDArray fLocal = dsLocal.getFeatures();
            INDArray lSpark = dsSpark.getLabels();
            INDArray lLocal = dsLocal.getLabels();


            assertArrayEquals(fShapeExp, fSpark.shape());
            assertArrayEquals(fShapeExp, fLocal.shape());
            assertArrayEquals(lShapeExp, lSpark.shape());
            assertArrayEquals(lShapeExp, lLocal.shape());
        }


        //Check that results are the same (order not withstanding)
        found = new boolean[3];
        for (int i = 0; i < 3; i++) {
            int foundIndex = -1;
            DataSet ds = sparkData.get(i);
            for (int j = 0; j < 3; j++) {
                if (dataSetsEqual(ds, localData.get(j))) {
                    if (foundIndex != -1)
                        fail(); //Already found this value -> suggests this spark value equals two or more of local version? (Shouldn't happen)
                    foundIndex = j;
                    if (found[foundIndex])
                        fail(); //One of the other spark values was equal to this one -> suggests duplicates in Spark list
                    found[foundIndex] = true; //mark this one as seen before
                }
            }
        }
        count = 0;
        for (boolean b : found)
            if (b)
                count++;
        assertEquals(3, count); //Expect all 3 and exactly 3 pairwise matches between spark and local versions
    }


    private static boolean dataSetsEqual(DataSet d1, DataSet d2) {

        if (!d1.getFeatures().equals(d2.getFeatures())) {
            return false;
        }
        if (d1.getLabels() == null && d2.getLabels() != null || d1.getLabels() != null && d2.getLabels() == null) {
            return false;
        }
        if (d1.getLabels() != null && !d1.getLabels().equals(d2.getLabels())) {
            return false;
        }

        return masksEqual(d1.getFeatures(), d2.getFeatures())
                        && masksEqual(d1.getLabelsMaskArray(), d2.getLabelsMaskArray());
    }

    private static boolean masksEqual(INDArray m1, INDArray m2) {
        if (m1 == null && m2 == null) {
            return true;
        }
        if (m1 != null && m2 != null) {
            return m1.equals(m2);
        }
        //One is null, other is not. Null and ones mask arrays are equal though
        if (m1 != null && !m1.equals(Nd4j.ones(m1.shape()))) {
            return false;
        }
        if (m2 != null && !m2.equals(Nd4j.ones(m2.shape()))) {
            return false;
        }

        return true;
    }

}
