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

package org.datavec.image.loader;

import org.apache.commons.io.FilenameUtils;
import org.datavec.api.records.reader.RecordReader;
import org.datavec.image.loader.CifarLoader;
import org.datavec.image.loader.LFWLoader;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.nd4j.common.tests.tags.NativeTag;
import org.nd4j.common.tests.tags.TagNames;
import org.nd4j.linalg.dataset.DataSet;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 *
 */
@NativeTag
@Tag(TagNames.FILE_IO)
@Tag(TagNames.LARGE_RESOURCES)
@Tag(TagNames.LONG_TEST)
public class LoaderTests {

    private static void ensureDataAvailable(){
        //Ensure test resources available by initializing CifarLoader and relying on auto download
        boolean preProcessCifar = false;
        int numExamples = 10;
        int row = 28;
        int col = 28;
        int channels = 1;
        for( boolean train : new boolean[]{true, false}){
            CifarLoader loader = new CifarLoader(row, col, channels, train, preProcessCifar);
            loader.next(numExamples);
        }
        new LFWLoader(new long[] {250, 250, 3}, true).getRecordReader(1, 1, 1, new Random(42)).next();
    }

    @Test
    public void testLfwReader() throws Exception {
        RecordReader rr = new LFWLoader(new long[] {250, 250, 3}, true).getRecordReader(1, 1, 1, new Random(42));
        List<String> exptedLabel = rr.getLabels();

        RecordReader rr2 = new LFWLoader(new long[] {250, 250, 3}, true).getRecordReader(1, 1, 1, new Random(42));

        assertEquals(exptedLabel.get(0), rr2.getLabels().get(0));
    }

    @Test
    public void testCifarLoader() {
        ensureDataAvailable();
        File dir = new File(FilenameUtils.concat(System.getProperty("user.home"), "cifar/cifar-10-batches-bin"));
        CifarLoader cifar = new CifarLoader(false, dir);
        assertTrue(dir.exists());
        assertTrue(cifar.getLabels() != null);
    }

    @Test
    public void testCifarInputStream() throws Exception {
        ensureDataAvailable();
        // check train
        String subDir = "cifar/cifar-10-batches-bin/data_batch_1.bin";
        String path = FilenameUtils.concat(System.getProperty("user.home"), subDir);
        byte[] fullDataExpected = new byte[3073];
        FileInputStream inExpected = new FileInputStream(path);
        inExpected.read(fullDataExpected);

        byte[] fullDataActual = new byte[3073];
        CifarLoader cifarLoad = new CifarLoader(true);
        InputStream inActual = cifarLoad.getInputStream();
        inActual.read(fullDataActual);
        assertEquals(fullDataExpected[0], fullDataActual[0]);

        // check test
        subDir = "cifar/cifar-10-batches-bin/test_batch.bin";
        path = FilenameUtils.concat(System.getProperty("user.home"), subDir);
        fullDataExpected = new byte[3073];
        inExpected = new FileInputStream(path);
        inExpected.read(fullDataExpected);

        fullDataActual = new byte[3073];
        cifarLoad = new CifarLoader(false);
        inActual = cifarLoad.getInputStream();
        inActual.read(fullDataActual);
        assertEquals(fullDataExpected[0], fullDataActual[0]);

    }

    @Test
    public void testCifarLoaderNext() throws Exception {
        boolean train = true;
        boolean preProcessCifar = false;
        int numExamples = 10;
        int row = 28;
        int col = 28;
        int channels = 1;
        CifarLoader loader = new CifarLoader(row, col, channels, train, preProcessCifar);
        DataSet data = loader.next(numExamples);
        assertEquals(numExamples, data.getLabels().size(0));
        assertEquals(channels, data.getFeatures().size(1));

        train = true;
        preProcessCifar = true;
        row = 32;
        col = 32;
        channels = 3;
        loader = new CifarLoader(row, col, channels, train, preProcessCifar);
        data = loader.next(1);
        assertEquals(1, data.getFeatures().size(0));
        assertEquals(channels * row * col, data.getFeatures().ravel().length());

        train = false;
        preProcessCifar = false;
        loader = new CifarLoader(row, col, channels, train, preProcessCifar);
        data = loader.next(numExamples);
        assertEquals(row, data.getFeatures().size(2));

        train = false;
        preProcessCifar = true;
        loader = new CifarLoader(row, col, channels, train, preProcessCifar);
        data = loader.next(numExamples);
        assertEquals(numExamples, data.getLabels().size(0));
        assertEquals(col, data.getFeatures().size(3));

    }

    @Test
    public void testCifarLoaderReset() throws Exception {
        int numExamples = 50;
        int row = 28;
        int col = 28;
        int channels = 3;
        CifarLoader loader = new CifarLoader(row, col, channels, null, false, false, false);
        DataSet data;
        for (int i = 0; i < CifarLoader.NUM_TEST_IMAGES / numExamples; i++) {
            loader.next(numExamples);
        }
        data = loader.next(numExamples);
        assertEquals(new DataSet(), data);
        loader.reset();
        data = loader.next(numExamples);
        assertEquals(numExamples, data.getLabels().size(0));
    }

    @Test
    public void testCifarLoaderWithBiggerImage() {
        boolean train = true;
        boolean preProcessCifar = false;
        int row = 128;
        int col = 128;
        int channels = 3;
        int numExamples = 10;

        CifarLoader loader = new CifarLoader(row, col, channels, train, preProcessCifar);
        DataSet data = loader.next(numExamples);
        long shape[] = data.getFeatures().shape();
        assertEquals(shape.length, 4);
        assertEquals(shape[0], numExamples);
        assertEquals(shape[1], channels);
        assertEquals(shape[2], row);
        assertEquals(shape[2], col);

    }


    @Disabled // Use when confirming data is getting stored
    @Test
    public void testProcessCifar() {
        int row = 32;
        int col = 32;
        int channels = 3;
        CifarLoader cifar = new CifarLoader(row, col, channels, null, true, true, false);
        DataSet result = cifar.next(1);
        assertEquals(result.getFeatures().length(), 32 * 32 * 3, 0.0);
    }



    @Test
    public void testCifarLoaderExpNumExamples() throws Exception {
        boolean train = true;
        boolean preProcessCifar = false;
        int numExamples = 10;
        int row = 28;
        int col = 28;
        int channels = 1;
        CifarLoader loader = new CifarLoader(row, col, channels, train, preProcessCifar);

        int minibatch = 100;
        int nMinibatches = 50000 / minibatch;

        for( int i=0; i < nMinibatches; i++) {
            DataSet ds = loader.next(minibatch);
            String s = String.valueOf(i);
            assertNotNull(ds.getFeatures(),s);
            assertNotNull(ds.getLabels(),s);

            assertEquals(minibatch, ds.getFeatures().size(0),s);
            assertEquals(minibatch, ds.getLabels().size(0),s);
            assertEquals(10, ds.getLabels().size(1),s);
        }

    }
}
