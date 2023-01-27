package com.example.mobedsr;

import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.graphics.Bitmap;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.gpu.CompatibilityList;
import org.tensorflow.lite.gpu.GpuDelegate;
import org.tensorflow.lite.support.common.ops.NormalizeOp;
import org.tensorflow.lite.support.image.ImageProcessor;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.image.ops.ResizeOp;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;


/** @brief  Super Resolution Model class
 *  @date   23/01/27
 */
public class SRModel {
    private boolean useGpu;

    public Interpreter interpreter;
    private Interpreter.Options options;
    private GpuDelegate gpuDelegate;
    private AssetManager assetManager;

    private final String MODEL_NAME = "evsrnet_x4.tflite";

    SRModel(AssetManager assetManager, boolean useGpu) throws IOException {
        interpreter = null;
        gpuDelegate = null;

        this.assetManager = assetManager;
        this.useGpu = useGpu;

        // Initialize the TF Lite interpreter
        init();
    }

    private void init() throws IOException {
        options = new Interpreter.Options();

        // Set gpu delegate
        if (useGpu) {
            CompatibilityList compatList = new CompatibilityList();
            GpuDelegate.Options delegateOptions = compatList.getBestOptionsForThisDevice();
            gpuDelegate = new GpuDelegate(delegateOptions);
            options.addDelegate(gpuDelegate);
        }

        // Set TF Lite interpreter
        interpreter = new Interpreter(loadModelFile(), options);
    }

    /** @brief  Load .tflite model file to ByteBuffer
     *  @date   23/01/25
     */
    private ByteBuffer loadModelFile() throws IOException {
        AssetFileDescriptor assetFileDescriptor = assetManager.openFd(MODEL_NAME);
        FileInputStream fileInputStream = new FileInputStream(assetFileDescriptor.getFileDescriptor());

        FileChannel fileChannel = fileInputStream.getChannel();
        long startOffset = assetFileDescriptor.getStartOffset();
        long declaredLength = assetFileDescriptor.getDeclaredLength();

        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    public void run(Object a, Object b) {
        interpreter.run(a, b);
    }


    /** @brief  Prepare the input tensor from low resolution image
     *  @date   23/01/25
     */
    public TensorImage prepareInputTensor(Bitmap bitmap_lr) {
        TensorImage inputImage = TensorImage.fromBitmap(bitmap_lr);
        int height = bitmap_lr.getHeight();
        int width = bitmap_lr.getWidth();

        ImageProcessor imageProcessor = new ImageProcessor.Builder()
                .add(new ResizeOp(height, width, ResizeOp.ResizeMethod.NEAREST_NEIGHBOR))
                .add(new NormalizeOp(0.0f, 255.0f))
                .build();
        inputImage = imageProcessor.process(inputImage);

        return inputImage;
    }


    /** @brief  Prepare the output tensor for super resolution
     *  @date   23/01/25
     */
    public TensorImage prepareOutputTensor() {
        TensorImage srImage = new TensorImage(DataType.FLOAT32);
//        int[] srShape = new int[]{1080, 1920, 3};
        int[] srShape = new int[]{1920, 1080, 3};
        srImage.load(TensorBuffer.createFixedSize(srShape, DataType.FLOAT32));

        return srImage;
    }


    /** @brief  Convert tensor to bitmap image
     *  @date   23/01/25
     *  @param outputTensor super resolutioned image
     */
    public Bitmap tensorToImage(TensorImage outputTensor) {
        ByteBuffer srOut = outputTensor.getBuffer();
        srOut.rewind();

        int height = outputTensor.getHeight();
        int width = outputTensor.getWidth();

        Bitmap bmpImage = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        int[] pixels = new int[width * height];

        for (int i = 0; i < width * height; i++) {
            int a = 0xFF;
            float r = srOut.getFloat() * 255.0f;
            float g = srOut.getFloat() * 255.0f;
            float b = srOut.getFloat() * 255.0f;

            pixels[i] = a << 24 | ((int) r << 16) | ((int) g << 8) | ((int) b);
        }

        bmpImage.setPixels(pixels, 0, width, 0, 0, width, height);

        return bmpImage;
    }


}
