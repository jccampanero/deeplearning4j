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

import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.commons.io.IOUtils;
import org.bytedeco.javacpp.Loader;
import org.bytedeco.javacpp.indexer.UByteIndexer;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.bytedeco.javacv.OpenCVFrameConverter;
import org.bytedeco.leptonica.PIX;
import org.bytedeco.leptonica.PIXCMAP;
import org.bytedeco.opencv.opencv_core.Mat;
import org.datavec.image.data.Image;
import org.datavec.image.data.ImageWritable;
import org.datavec.image.loader.Java2DNativeImageLoader;
import org.datavec.image.loader.NativeImageLoader;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nd4j.common.io.ClassPathResource;
import org.nd4j.common.resources.Resources;
import org.nd4j.common.tests.tags.NativeTag;
import org.nd4j.common.tests.tags.TagNames;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;

import java.awt.image.BufferedImage;
import java.io.*;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.Random;

import static org.bytedeco.leptonica.global.lept.*;
import static org.bytedeco.opencv.global.opencv_core.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author saudet
 */
@Slf4j
@NativeTag
@Tag(TagNames.FILE_IO)
@Tag(TagNames.LARGE_RESOURCES)
@Tag(TagNames.LONG_TEST)
public class TestNativeImageLoader {
    static final long seed = 10;
    static final Random rng = new Random(seed);


    @Test
    public void testConvertPix() throws Exception {
        PIX pix;
        Mat mat;

        pix = pixCreate(11, 22, 1);
        mat = NativeImageLoader.convert(pix);
        assertEquals(11, mat.cols());
        assertEquals(22, mat.rows());
        assertEquals(CV_8UC1, mat.type());

        pix = pixCreate(33, 44, 2);
        mat = NativeImageLoader.convert(pix);
        assertEquals(33, mat.cols());
        assertEquals(44, mat.rows());
        assertEquals(CV_8UC1, mat.type());

        pix = pixCreate(55, 66, 4);
        mat = NativeImageLoader.convert(pix);
        assertEquals(55, mat.cols());
        assertEquals(66, mat.rows());
        assertEquals(CV_8UC1, mat.type());

        pix = pixCreate(77, 88, 8);
        mat = NativeImageLoader.convert(pix);
        assertEquals(77, mat.cols());
        assertEquals(88, mat.rows());
        assertEquals(CV_8UC1, mat.type());

        pix = pixCreate(99, 111, 16);
        mat = NativeImageLoader.convert(pix);
        assertEquals(99, mat.cols());
        assertEquals(111, mat.rows());
        assertEquals(CV_16UC(1), mat.type());

        pix = pixCreate(222, 333, 24);
        mat = NativeImageLoader.convert(pix);
        assertEquals(222, mat.cols());
        assertEquals(333, mat.rows());
        assertEquals(CV_8UC(3), mat.type());

        pix = pixCreate(444, 555, 32);
        mat = NativeImageLoader.convert(pix);
        assertEquals(444, mat.cols());
        assertEquals(555, mat.rows());
        assertEquals(CV_32FC1, mat.type());

        // a GIF file, for example
        pix = pixCreate(32, 32, 8);
        PIXCMAP cmap = pixcmapCreateLinear(8, 256);
        pixSetColormap(pix, cmap);
        mat = NativeImageLoader.convert(pix);
        assertEquals(32, mat.cols());
        assertEquals(32, mat.rows());
        assertEquals(CV_8UC4, mat.type());

        int w4 = 100, h4 = 238, ch4 = 1, pages = 1, depth = 1;
        String path2MitosisFile = "datavec-data-image/testimages2/mitosis.tif";
        NativeImageLoader loader5 = new NativeImageLoader(h4, w4, ch4, NativeImageLoader.MultiPageMode.FIRST);
        INDArray array6 = null;
        try {
            array6 = loader5.asMatrix(new ClassPathResource(path2MitosisFile).getFile().getAbsolutePath());
        } catch (IOException e) {
            log.error("",e);
            fail();
        }
        assertEquals(5, array6.rank());
        assertEquals(pages, array6.size(0));
        assertEquals(ch4, array6.size(1));
        assertEquals(depth, array6.size(2));
        assertEquals(h4, array6.size(3));
        assertEquals(w4, array6.size(4));

//        int ch5 = 4, pages1 = 1;
//        NativeImageLoader loader6 = new NativeImageLoader(h4, w4, 1, NativeImageLoader.MultiPageMode.CHANNELS);
//        loader6.direct = false; // simulate conditions under Android
//        INDArray array7 = null;
//        try {
//            array7 = loader6.asMatrix(
//                  new ClassPathResource(path2MitosisFile).getFile());
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//        assertEquals(5, array7.rank());
//        assertEquals(pages1, array7.size(0));
//        assertEquals(ch5, array7.size(1));
//        assertEquals(depth1, array7.size(2));
//        assertEquals(h4, array7.size(3));
//        assertEquals(w4, array7.size(4));

        int ch6 = 1, pages2 = 4, depth1 = 1;
        NativeImageLoader loader7 = new NativeImageLoader(h4, w4, ch6, NativeImageLoader.MultiPageMode.MINIBATCH);
        INDArray array8 = null;
        try {
            array8 = loader7.asMatrix(new ClassPathResource(path2MitosisFile).getFile().getAbsolutePath());
        } catch (IOException e) {
            log.error("",e);
        }
        assertEquals(5, array8.rank());
        assertEquals(pages2, array8.size(0));
        assertEquals(ch6, array8.size(1));
        assertEquals(depth1, array8.size(2));
        assertEquals(h4, array8.size(3));
        assertEquals(w4, array8.size(4));

        int w5 = 256, h5 = 256, pages3 = 2;
        String braintiff = "datavec-data-image/testimages2/3d.tiff"; // this is a 16-bit 3d image
        NativeImageLoader loader8 = new NativeImageLoader(h5, w5, ch6, NativeImageLoader.MultiPageMode.MINIBATCH);
        INDArray array9 = null;
        try {
            array9 = loader8.asMatrix(new ClassPathResource(braintiff).getFile().getAbsolutePath());
        } catch (IOException e) {
            log.error("",e);
            fail();
        }
        assertEquals(5, array9.rank());
        assertEquals(pages3, array9.size(0));
        assertEquals(ch6, array9.size(1));
        assertEquals(depth1, array9.size(2));
        assertEquals(h5, array9.size(3));
        assertEquals(w5, array9.size(4));

//        int ch8 = 5, pages4 = 1;
//        NativeImageLoader loader9 = new NativeImageLoader(h5, w5, ch8, NativeImageLoader.MultiPageMode.CHANNELS);
//        INDArray array10 = null;
//        try {
//            array10 = loader9.asMatrix(new ClassPathResource(braintiff).getFile().getAbsolutePath());
//        } catch (IOException e) {
//            e.printStackTrace();
//            fail();
//        }
//        assertEquals(5, array10.rank());
//        assertEquals(pages4, array10.size(0));
//        assertEquals(ch8, array10.size(1));
//        assertEquals(depth1, array10.size(2));
//        assertEquals(h5, array10.size(3));
//        assertEquals(w5, array10.size(4));
    }

    @Test
    public void testAsRowVector() throws Exception {
        org.opencv.core.Mat img1 = makeRandomOrgOpenCvCoreMatImage(0, 0, 1);
        Mat img2 = makeRandomImage(0, 0, 3);

        int w1 = 35, h1 = 79, ch1 = 3;
        NativeImageLoader loader1 = new NativeImageLoader(h1, w1, ch1);

        INDArray array1 = loader1.asRowVector(img1);
        assertEquals(2, array1.rank());
        assertEquals(1, array1.rows());
        assertEquals(h1 * w1 * ch1, array1.columns());
        assertNotEquals(0.0, array1.sum().getDouble(0), 0.0);

        INDArray array2 = loader1.asRowVector(img2);
        assertEquals(2, array2.rank());
        assertEquals(1, array2.rows());
        assertEquals(h1 * w1 * ch1, array2.columns());
        assertNotEquals(0.0, array2.sum().getDouble(0), 0.0);

        int w2 = 103, h2 = 68, ch2 = 4;
        NativeImageLoader loader2 = new NativeImageLoader(h2, w2, ch2);
        loader2.direct = false; // simulate conditions under Android

        INDArray array3 = loader2.asRowVector(img1);
        assertEquals(2, array3.rank());
        assertEquals(1, array3.rows());
        assertEquals(h2 * w2 * ch2, array3.columns());
        assertNotEquals(0.0, array3.sum().getDouble(0), 0.0);

        INDArray array4 = loader2.asRowVector(img2);
        assertEquals(2, array4.rank());
        assertEquals(1, array4.rows());
        assertEquals(h2 * w2 * ch2, array4.columns());
        assertNotEquals(0.0, array4.sum().getDouble(0), 0.0);
    }

    @Test
    public void testDataTypes_1() throws Exception {
        val dtypes = new DataType[]{DataType.FLOAT, DataType.HALF, DataType.SHORT, DataType.INT};

        val dt = Nd4j.dataType();

        for (val dtype: dtypes) {
            Nd4j.setDataType(dtype);
            int w3 = 123, h3 = 77, ch3 = 3;
            val loader = new NativeImageLoader(h3, w3, ch3);
            File f3 = new ClassPathResource("datavec-data-image/testimages/class0/2.jpg").getFile();
            ImageWritable iw3 = loader.asWritable(f3);

            val array = loader.asMatrix(iw3);

            assertEquals(dtype, array.dataType());
        }

        Nd4j.setDataType(dt);
    }

    @Test
    public void testDataTypes_2() throws Exception {
        val dtypes = new DataType[]{DataType.FLOAT, DataType.HALF, DataType.SHORT, DataType.INT};

        val dt = Nd4j.dataType();

        for (val dtype: dtypes) {
            Nd4j.setDataType(dtype);
            int w3 = 123, h3 = 77, ch3 = 3;
            val loader = new NativeImageLoader(h3, w3, 1);
            File f3 = new ClassPathResource("datavec-data-image/testimages/class0/2.jpg").getFile();
            val array = loader.asMatrix(f3);

            assertEquals(dtype, array.dataType());
        }

        Nd4j.setDataType(dt);
    }

    @Test
    public void testAsMatrix() throws Exception {
        BufferedImage img1 = makeRandomBufferedImage(0, 0, 3);
        Mat img2 = makeRandomImage(0, 0, 4);

        int w1 = 33, h1 = 77, ch1 = 1;
        NativeImageLoader loader1 = new NativeImageLoader(h1, w1, ch1);

        INDArray array1 = loader1.asMatrix(img1);
        assertEquals(4, array1.rank());
        assertEquals(1, array1.size(0));
        assertEquals(1, array1.size(1));
        assertEquals(h1, array1.size(2));
        assertEquals(w1, array1.size(3));
        assertNotEquals(0.0, array1.sum().getDouble(0), 0.0);

        INDArray array2 = loader1.asMatrix(img2);
        assertEquals(4, array2.rank());
        assertEquals(1, array2.size(0));
        assertEquals(1, array2.size(1));
        assertEquals(h1, array2.size(2));
        assertEquals(w1, array2.size(3));
        assertNotEquals(0.0, array2.sum().getDouble(0), 0.0);

        int w2 = 111, h2 = 66, ch2 = 3;
        NativeImageLoader loader2 = new NativeImageLoader(h2, w2, ch2);
        loader2.direct = false; // simulate conditions under Android

        INDArray array3 = loader2.asMatrix(img1);
        assertEquals(4, array3.rank());
        assertEquals(1, array3.size(0));
        assertEquals(3, array3.size(1));
        assertEquals(h2, array3.size(2));
        assertEquals(w2, array3.size(3));
        assertNotEquals(0.0, array3.sum().getDouble(0), 0.0);

        INDArray array4 = loader2.asMatrix(img2);
        assertEquals(4, array4.rank());
        assertEquals(1, array4.size(0));
        assertEquals(3, array4.size(1));
        assertEquals(h2, array4.size(2));
        assertEquals(w2, array4.size(3));
        assertNotEquals(0.0, array4.sum().getDouble(0), 0.0);

        int w3 = 123, h3 = 77, ch3 = 3;
        NativeImageLoader loader3 = new NativeImageLoader(h3, w3, ch3);
        File f3 = new ClassPathResource("datavec-data-image/testimages/class0/2.jpg").getFile();
        ImageWritable iw3 = loader3.asWritable(f3);

        INDArray array5 = loader3.asMatrix(iw3);
        assertEquals(4, array5.rank());
        assertEquals(1, array5.size(0));
        assertEquals(3, array5.size(1));
        assertEquals(h3, array5.size(2));
        assertEquals(w3, array5.size(3));
        assertNotEquals(0.0, array5.sum().getDouble(0), 0.0);

        Mat mat = loader3.asMat(array5);
        assertEquals(w3, mat.cols());
        assertEquals(h3, mat.rows());
        assertEquals(ch3, mat.channels());
        assertTrue(mat.type() == CV_32FC(ch3) || mat.type() == CV_64FC(ch3));
        assertNotEquals(0.0, sumElems(mat).get(), 0.0);

        Frame frame = loader3.asFrame(array5, Frame.DEPTH_UBYTE);
        assertEquals(w3, frame.imageWidth);
        assertEquals(h3, frame.imageHeight);
        assertEquals(ch3, frame.imageChannels);
        assertEquals(Frame.DEPTH_UBYTE, frame.imageDepth);

        Java2DNativeImageLoader loader4 = new Java2DNativeImageLoader();
        BufferedImage img12 = loader4.asBufferedImage(array1);
        assertEquals(array1, loader4.asMatrix(img12));

        NativeImageLoader loader5 = new NativeImageLoader(0, 0, 0);
        loader5.direct = false; // simulate conditions under Android
        INDArray array7 = loader5.asMatrix(f3);
        assertEquals(4, array7.rank());
        assertEquals(1, array7.size(0));
        assertEquals(3, array7.size(1));
        assertEquals(32, array7.size(2));
        assertEquals(32, array7.size(3));
        assertNotEquals(0.0, array7.sum().getDouble(0), 0.0);
    }

    @Test
    public void testScalingIfNeed() throws Exception {
        Mat img1 = makeRandomImage(0, 0, 1);
        Mat img2 = makeRandomImage(0, 0, 3);

        int w1 = 60, h1 = 110, ch1 = 1;
        NativeImageLoader loader1 = new NativeImageLoader(h1, w1, ch1);

        Mat scaled1 = loader1.scalingIfNeed(img1);
        assertEquals(h1, scaled1.rows());
        assertEquals(w1, scaled1.cols());
        assertEquals(img1.channels(), scaled1.channels());
        assertNotEquals(0.0, sumElems(scaled1).get(), 0.0);

        Mat scaled2 = loader1.scalingIfNeed(img2);
        assertEquals(h1, scaled2.rows());
        assertEquals(w1, scaled2.cols());
        assertEquals(img2.channels(), scaled2.channels());
        assertNotEquals(0.0, sumElems(scaled2).get(), 0.0);

        int w2 = 70, h2 = 120, ch2 = 3;
        NativeImageLoader loader2 = new NativeImageLoader(h2, w2, ch2);
        loader2.direct = false; // simulate conditions under Android

        Mat scaled3 = loader2.scalingIfNeed(img1);
        assertEquals(h2, scaled3.rows());
        assertEquals(w2, scaled3.cols());
        assertEquals(img1.channels(), scaled3.channels());
        assertNotEquals(0.0, sumElems(scaled3).get(), 0.0);

        Mat scaled4 = loader2.scalingIfNeed(img2);
        assertEquals(h2, scaled4.rows());
        assertEquals(w2, scaled4.cols());
        assertEquals(img2.channels(), scaled4.channels());
        assertNotEquals(0.0, sumElems(scaled4).get(), 0.0);
    }

    @Test
    public void testCenterCropIfNeeded() throws Exception {
        int w1 = 60, h1 = 110, ch1 = 1;
        int w2 = 120, h2 = 70, ch2 = 3;

        Mat img1 = makeRandomImage(h1, w1, ch1);
        Mat img2 = makeRandomImage(h2, w2, ch2);

        NativeImageLoader loader = new NativeImageLoader(h1, w1, ch1, true);

        Mat cropped1 = loader.centerCropIfNeeded(img1);
        assertEquals(85, cropped1.rows());
        assertEquals(60, cropped1.cols());
        assertEquals(img1.channels(), cropped1.channels());
        assertNotEquals(0.0, sumElems(cropped1).get(), 0.0);

        Mat cropped2 = loader.centerCropIfNeeded(img2);
        assertEquals(70, cropped2.rows());
        assertEquals(95, cropped2.cols());
        assertEquals(img2.channels(), cropped2.channels());
        assertNotEquals(0.0, sumElems(cropped2).get(), 0.0);
    }


    BufferedImage makeRandomBufferedImage(int height, int width, int channels) {
        Mat img = makeRandomImage(height, width, channels);

        OpenCVFrameConverter.ToMat c = new OpenCVFrameConverter.ToMat();
        Java2DFrameConverter c2 = new Java2DFrameConverter();

        return c2.convert(c.convert(img));
    }

    org.opencv.core.Mat makeRandomOrgOpenCvCoreMatImage(int height, int width, int channels) {
        Mat img = makeRandomImage(height, width, channels);

        Loader.load(org.bytedeco.opencv.opencv_java.class);
        OpenCVFrameConverter.ToOrgOpenCvCoreMat c = new OpenCVFrameConverter.ToOrgOpenCvCoreMat();

        return c.convert(c.convert(img));
    }

    Mat makeRandomImage(int height, int width, int channels) {
        if (height <= 0) {
            height = rng.nextInt() % 100 + 100;
        }
        if (width <= 0) {
            width = rng.nextInt() % 100 + 100;
        }

        Mat img = new Mat(height, width, CV_8UC(channels));
        UByteIndexer idx = img.createIndexer();
        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                for (int k = 0; k < channels; k++) {
                    idx.put(i, j, k, rng.nextInt());
                }
            }
        }
        return img;
    }

    @Test
    public void testAsWritable() throws Exception {
        String f0 = new ClassPathResource("datavec-data-image/testimages/class0/0.jpg").getFile().getAbsolutePath();

        NativeImageLoader imageLoader = new NativeImageLoader();
        ImageWritable img = imageLoader.asWritable(f0);

        assertEquals(32, img.getFrame().imageHeight);
        assertEquals(32, img.getFrame().imageWidth);
        assertEquals(3, img.getFrame().imageChannels);

        BufferedImage img1 = makeRandomBufferedImage(0, 0, 3);
        Mat img2 = makeRandomImage(0, 0, 4);

        int w1 = 33, h1 = 77, ch1 = 1;
        NativeImageLoader loader1 = new NativeImageLoader(h1, w1, ch1);

        INDArray array1 = loader1.asMatrix(f0);
        assertEquals(4, array1.rank());
        assertEquals(1, array1.size(0));
        assertEquals(1, array1.size(1));
        assertEquals(h1, array1.size(2));
        assertEquals(w1, array1.size(3));
        assertNotEquals(0.0, array1.sum().getDouble(0), 0.0);
    }

    @Test
    public void testBufferRealloc() throws Exception {
        Field f = NativeImageLoader.class.getDeclaredField("buffer");
        Field m = NativeImageLoader.class.getDeclaredField("bufferMat");
        f.setAccessible(true);
        m.setAccessible(true);

        File f1 = new ClassPathResource("datavec-data-image/voc/2007/JPEGImages/000005.jpg").getFile();
        String f2 = new ClassPathResource("datavec-data-image/voc/2007/JPEGImages/000007.jpg").getFile().getAbsolutePath();

        //Start with a large buffer
        byte[] buffer = new byte[20*1024*1024];
        Mat bufferMat = new Mat(buffer);

        NativeImageLoader loader = new NativeImageLoader(28, 28, 1);
        f.set(loader, buffer);
        m.set(loader, bufferMat);

        INDArray img1LargeBuffer = loader.asMatrix(f1);
        INDArray img2LargeBuffer = loader.asMatrix(f2);

        //Check multiple reads:
        INDArray img1LargeBuffer2 = loader.asMatrix(f1);
        INDArray img1LargeBuffer3 = loader.asMatrix(f1);
        assertEquals(img1LargeBuffer2, img1LargeBuffer3);

        INDArray img2LargeBuffer2 = loader.asMatrix(f1);
        INDArray img2LargeBuffer3 = loader.asMatrix(f1);
        assertEquals(img2LargeBuffer2, img2LargeBuffer3);

        //Clear the buffer and re-read:
        f.set(loader, null);
        INDArray img1NoBuffer1 = loader.asMatrix(f1);
        INDArray img1NoBuffer2 = loader.asMatrix(f1);
        assertEquals(img1LargeBuffer, img1NoBuffer1);
        assertEquals(img1LargeBuffer, img1NoBuffer2);

        f.set(loader, null);
        INDArray img2NoBuffer1 = loader.asMatrix(f2);
        INDArray img2NoBuffer2 = loader.asMatrix(f2);
        assertEquals(img2LargeBuffer, img2NoBuffer1);
        assertEquals(img2LargeBuffer, img2NoBuffer2);

        //Assign much too small buffer:
        buffer = new byte[10];
        bufferMat = new Mat(buffer);
        f.set(loader, buffer);
        m.set(loader, bufferMat);
        INDArray img1SmallBuffer1 = loader.asMatrix(f1);
        INDArray img1SmallBuffer2 = loader.asMatrix(f1);
        assertEquals(img1LargeBuffer, img1SmallBuffer1);
        assertEquals(img1LargeBuffer, img1SmallBuffer2);

        f.set(loader, buffer);
        m.set(loader, bufferMat);
        INDArray img2SmallBuffer1 = loader.asMatrix(f2);
        INDArray img2SmallBuffer2 = loader.asMatrix(f2);
        assertEquals(img2LargeBuffer, img2SmallBuffer1);
        assertEquals(img2LargeBuffer, img2SmallBuffer2);

        //Assign an exact buffer:
        try(InputStream is = new FileInputStream(f1)){
            byte[] temp = IOUtils.toByteArray(is);
            buffer = new byte[temp.length];
            bufferMat = new Mat(buffer);
        }
        f.set(loader, buffer);
        m.set(loader, bufferMat);

        INDArray img1ExactBuffer = loader.asMatrix(f1);
        assertEquals(img1LargeBuffer, img1ExactBuffer);
    }


    @Test
    public void testNativeImageLoaderEmptyStreams(@TempDir Path testDir) throws Exception {
        File dir = testDir.toFile();
        File f = new File(dir, "myFile.jpg");
        f.createNewFile();

        NativeImageLoader nil = new NativeImageLoader(32, 32, 3);

        try(InputStream is = new FileInputStream(f)){
            nil.asMatrix(is);
            fail("Expected exception");
        } catch (IOException e){
            String msg = e.getMessage();
            assertTrue(msg.contains("decode image"),msg);
        }

        try(InputStream is = new FileInputStream(f)){
            nil.asImageMatrix(is);
            fail("Expected exception");
        } catch (IOException e){
            String msg = e.getMessage();
            assertTrue(msg.contains("decode image"),msg);
        }

        try(InputStream is = new FileInputStream(f)){
            nil.asRowVector(is);
            fail("Expected exception");
        } catch (IOException e){
            String msg = e.getMessage();
            assertTrue(msg.contains("decode image"),msg);
        }

        try(InputStream is = new FileInputStream(f)){
            INDArray arr = Nd4j.create(DataType.FLOAT, 1, 3, 32, 32);
            nil.asMatrixView(is, arr);
            fail("Expected exception");
        } catch (IOException e){
            String msg = e.getMessage();
            assertTrue( msg.contains("decode image"),msg);
        }
    }

    @Test
    public void testNCHW_NHWC() throws Exception {
        File f = Resources.asFile("datavec-data-image/voc/2007/JPEGImages/000005.jpg");

        NativeImageLoader il = new NativeImageLoader(32, 32, 3);

        //asMatrix(File, boolean)
        INDArray a_nchw = il.asMatrix(f);
        INDArray a_nchw2 = il.asMatrix(f, true);
        INDArray a_nhwc = il.asMatrix(f, false);

        assertEquals(a_nchw, a_nchw2);
        assertEquals(a_nchw, a_nhwc.permute(0,3,1,2));


        //asMatrix(InputStream, boolean)
        try(InputStream is = new BufferedInputStream(new FileInputStream(f))){
            a_nchw = il.asMatrix(is);
        }
        try(InputStream is = new BufferedInputStream(new FileInputStream(f))){
            a_nchw2 = il.asMatrix(is, true);
        }
        try(InputStream is = new BufferedInputStream(new FileInputStream(f))){
            a_nhwc = il.asMatrix(is, false);
        }
        assertEquals(a_nchw, a_nchw2);
        assertEquals(a_nchw, a_nhwc.permute(0,3,1,2));


        //asImageMatrix(File, boolean)
        Image i_nchw = il.asImageMatrix(f);
        Image i_nchw2 = il.asImageMatrix(f, true);
        Image i_nhwc = il.asImageMatrix(f, false);

        assertEquals(i_nchw.getImage(), i_nchw2.getImage());
        assertEquals(i_nchw.getImage(), i_nhwc.getImage().permute(0,3,1,2));        //NHWC to NCHW


        //asImageMatrix(InputStream, boolean)
        try(InputStream is = new BufferedInputStream(new FileInputStream(f))){
            i_nchw = il.asImageMatrix(is);
        }
        try(InputStream is = new BufferedInputStream(new FileInputStream(f))){
            i_nchw2 = il.asImageMatrix(is, true);
        }
        try(InputStream is = new BufferedInputStream(new FileInputStream(f))){
            i_nhwc = il.asImageMatrix(is, false);
        }
        assertEquals(i_nchw.getImage(), i_nchw2.getImage());
        assertEquals(i_nchw.getImage(), i_nhwc.getImage().permute(0,3,1,2));        //NHWC to NCHW
    }

}
