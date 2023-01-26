package com.example.mobedsr;

import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.MediaController;
import android.widget.VideoView;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.arthenica.ffmpegkit.FFmpegKit;
import com.arthenica.ffmpegkit.FFmpegSession;
import com.arthenica.ffmpegkit.ReturnCode;
import com.example.mobedsr.databinding.ActivityVideosrBinding;

import org.checkerframework.checker.units.qual.A;
import org.tensorflow.lite.DataType;
import org.tensorflow.lite.support.common.ops.NormalizeOp;
import org.tensorflow.lite.support.image.ImageProcessor;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.image.ops.ResizeOp;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;

public class VideoSRActivity extends AppCompatActivity {

    private int width;
    private int height;
    private Bitmap bitmap_lr;
    private Bitmap bitmap_sr;

    private final String TAG = "MobedSR";
    private final String VIDEO_NAME = "input.mp4";
    private final String SR_VIDEO_NAME = "output.mp4";
    private final String INPUT_DIR = "DCIM";
    private final String FRAMES_DIR = "frames";
    private final String SR_DIR = "SRFrames";

    private View decorView;
    private ImageView img_frame;
    private VideoView video_lr;

    private Uri uri;
    private Button btn_run_sr;
    private Button btn_lr_video;
    private Toolbar toolbar;
    private ActivityVideosrBinding binding;


    private ArrayList<String> videoFramePaths = new ArrayList<>();
    private ArrayList<String> srFramePaths = new ArrayList<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_videosr);

        setComponentById();
        hideSoftKeys(decorView);


        btn_run_sr.setOnClickListener(v -> {
            if (video_lr.getDuration() != 0) {
                try {
                    runVideoSR();
                } catch (IOException | InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });


        btn_lr_video.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_PICK);
            intent.setType("video/*");
            startActivityResult.launch(intent);
            video_lr.setMediaController(new MediaController(this));
        });
    }

    // launcher for getting low resolution video
    ActivityResultLauncher<Intent> startActivityResult = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            new ActivityResultCallback<ActivityResult>() {
                @Override
                public void onActivityResult(ActivityResult result) {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        uri = result.getData().getData();

                        video_lr.setVideoURI(uri);
                    }
                }
            }
    );



    /** @brief  Set the component by view
     *  @date   23/01/25
     */
    private void setComponentById() {
        decorView = getWindow().getDecorView();
        img_frame = findViewById(R.id.img_frame);
        video_lr = findViewById(R.id.video_lr);

        btn_run_sr = findViewById(R.id.btn_run_sr);
        btn_lr_video = findViewById(R.id.btn_video);
    }


    /** @brief  Hide the soft-keys
     *  @date   23/01/25
     *  @param  decorView: background of the root view
     */
    private void hideSoftKeys(View decorView) {
        int uiOption = decorView.getSystemUiVisibility();

        uiOption |= View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
        uiOption |= View.SYSTEM_UI_FLAG_FULLSCREEN;
        uiOption |= View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;

        decorView.setSystemUiVisibility(uiOption);
    }


    /** @brief  Get the frame rate of video
     *  @date   23/01/26
     *  TODO: Refactor the code more generally
     */
    private String getFPS() {
        String inputPath = INPUT_DIR + "/" + VIDEO_NAME;

        FFmpegSession fFmpegSession = FFmpegKit.execute("-i " + inputPath + " ");
        if (ReturnCode.isSuccess(fFmpegSession.getReturnCode()))
            return null;
        else {
            String out = fFmpegSession.getOutput();
            String fpsLine = out.substring(out.lastIndexOf("/s"));
            String fps = fpsLine.substring(fpsLine.lastIndexOf("/s"), fpsLine.indexOf("fps"));

            fps = fps.substring(3).replaceAll("\\s", "");
            fps = String.valueOf(Math.round(Float.parseFloat(fps)));
            return fps;
        }
    }

    /**
     * @brief   Get the real path from uri
     * @date    23/01/26
     * @param   contentUri uri of video
     * @reference https://asukim.tistory.com/68
     */
    private String getRealPathFromURI(Uri contentUri) {
        Cursor cursor = getContentResolver().query(uri, null, null, null, null );
        cursor.moveToNext();
        String path = cursor.getString( cursor.getColumnIndex( "_data" ) );
        cursor.close();
        return path;
    }


    /**
     * @brief   Get the list of frames
     * @date    23/01/26
     */
    private void fillFrames() {
        File inputDir = new File(getExternalFilesDir(INPUT_DIR).getAbsolutePath() + FRAMES_DIR);
        File[] frames = inputDir.listFiles();

        videoFramePaths = new ArrayList<String>();
        for (File frame : frames) {
            videoFramePaths.add(frame.getName());
        }
        Collections.sort(videoFramePaths);
    }


    /**
     * @brief   Read low resolution frames from video
     * @date    23/01/26
     * @param   fps: FPS of video by getFPS()
     */
    private void readFrames(String fps) {
        File path = new File(getExternalFilesDir(INPUT_DIR), FRAMES_DIR);

        if (!path.exists()) {
            if (!path.mkdirs())
                Log.d(TAG, "Directory not created");
        }

        String inputPath = getRealPathFromURI(uri);
        String outputPath = getExternalFilesDir(INPUT_DIR).getAbsolutePath() + "/frames/frame_%04d.png";
        Log.d(TAG, outputPath);

        FFmpegSession ffmpegSession = FFmpegKit.execute("-i " + inputPath + " -vf fps=" + fps + " -preset ultrafast " + outputPath);
        if (ReturnCode.isSuccess(ffmpegSession.getReturnCode())) {
            Log.i("Log", "Extracted frames successfully");
        } else {
            // Failure
            Log.d("Error", String.format("Extracting frames failed with state %s and rc %s.%s", ffmpegSession.getState(),
                    ffmpegSession.getReturnCode(), ffmpegSession.getFailStackTrace()));
        }
        fillFrames();
    }



    /** @brief  Prepare the input tensor from low resolution image
     *  @date   23/01/25
     */
    private TensorImage prepareInputTensor() {
        TensorImage inputImage = TensorImage.fromBitmap(bitmap_lr);
        height = bitmap_lr.getHeight();
        width = bitmap_lr.getWidth();

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
    private TensorImage prepareOutputTensor() {
        TensorImage srImage = new TensorImage(DataType.FLOAT32);
        int[] srShape = new int[]{1080, 1920, 3};
        srImage.load(TensorBuffer.createFixedSize(srShape, DataType.FLOAT32));

        return srImage;
    }


    /** @brief  Run video super resolution
     *  @date 23/01/26
     *  @throws IOException
     *  @throws InterruptedException
     */
    private void runVideoSR() throws IOException, InterruptedException {
        Log.d(TAG, "Run video super resolution");

        // Get low resolution frames from the video
//        String fps = getFPS();
        String fps = "556";
        readFrames(fps);

        // Prepare the TF Lite Interpreter, Delegate

        // Run super resolution
        int frame_index = 0;

        // Save the SR image

        // Convert the image sequence to video

    }

}
