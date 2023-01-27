package com.example.mobedsr;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
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

import org.tensorflow.lite.support.image.TensorImage;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;

public class VideoSRActivity extends AppCompatActivity {

    private int width;
    private int height;

    private final String TAG = "MobedSR";
    private final String VIDEO_NAME = "input.mp4";
    private final String SR_VIDEO_NAME = "output.mp4";
    private final String INPUT_DIR = "DCIM";
    private final String FRAMES_DIR = "frames";
    private final String SR_DIR = "SRFrames";
    private String absoluteFramesDir;
    private String absoluteSRFramesDir;

    private View decorView;
    private ImageView img_frame;
    private VideoView video_lr;

    private Uri uri;
    private Button btn_run_sr;
    private Button btn_lr_video;
    private Toolbar toolbar;
    private ActivityVideosrBinding binding;

    private SRModel srModel;
    private ArrayList<String> videoFramePaths = new ArrayList<>();
    private ArrayList<String> srFramePaths = new ArrayList<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_videosr);

        setComponentById();
        hideSoftKeys(decorView);

        absoluteFramesDir = getExternalFilesDir(INPUT_DIR) + "/" + FRAMES_DIR;
        absoluteSRFramesDir = getExternalFilesDir(INPUT_DIR) + "/" + SR_DIR;

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
     *  TODO: Refactor the code more generally & solve the issue of read-only file system
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
     * @brief   Get the list of frames and sort
     * @date    23/01/26
     */
    private void fillFrames() {
        // inputDir: External Storage of app
        File inputDir = new File(absoluteFramesDir);
        File[] frames = inputDir.listFiles();

        videoFramePaths = new ArrayList<String>();
        for (File frame : frames) {
            videoFramePaths.add(frame.getName());
        }
        Collections.sort(videoFramePaths);
        Log.d(TAG, videoFramePaths.get(0));
    }


    /**
     * @brief   Read low resolution frames from video
     * @date    23/01/26
     * @param   fps: FPS of video by getFPS()
     */
    private void readFrames(String fps) {
        // External storage path to store frames
        // path: com.example.mobedsr/ ... /DCIM/frames
        File path = new File(getExternalFilesDir(INPUT_DIR), FRAMES_DIR);

        if (!path.exists()) {
            if (!path.mkdirs())
                Log.d(TAG, "Directory not created");
        }

        // Get real path from Uri of MediaStore
        String inputPath = getRealPathFromURI(uri);
        String outputPath = absoluteFramesDir + "/frame_%04d.png";
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


    /** @brief Save the super resolution image
     *  @date 23/01/27
     *  @param bitmap_sr super resolution bitmap
     *  @param name      name of resulted image
     */
    private void saveImage(Bitmap bitmap_sr, String name) {
        OutputStream fOut = null;
        File file = new File(absoluteSRFramesDir, name);

        try {
            fOut = new FileOutputStream(file);
            bitmap_sr.compress(Bitmap.CompressFormat.JPEG, 100, fOut);
            fOut.flush();
            fOut.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void saveImage(Bitmap bitmap_lr) {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, "example.JPG");
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/*");

        ContentResolver contentResolver = getContentResolver();
        Uri item = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);

        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        bitmap_lr.compress(Bitmap.CompressFormat.JPEG, 100, stream);
        byte[] bytearray = stream.toByteArray();

        try {
            ParcelFileDescriptor pdf = contentResolver.openFileDescriptor(item, "w", null);
            FileOutputStream fos = new FileOutputStream(pdf.getFileDescriptor());
            fos.write(bytearray);
            fos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    /** @brief Save the super resolution video
     *  @date 23/01/27
     */
    private void saveSrVideo(String fps) {
        String inputPath = absoluteSRFramesDir + "/srframe_%04d.jpeg";
//        String outputPath =
    }



    /** @brief  Run video super resolution
     *  @date 23/01/26
     *  @throws IOException
     *  @throws InterruptedException
     */
    private void runVideoSR() throws IOException, InterruptedException {
        Log.d(TAG, "Run video super resolution");

        // Make the folder of SR frames
        File srPath = new File(getExternalFilesDir(INPUT_DIR), SR_DIR);
        if (!srPath.exists()) {
            if (!srPath.mkdirs())
                Log.d(TAG, "Directory not created");
        }

        // Get low resolution frames from the video
        // String fps = getFPS();
        String fps = "15";
        readFrames(fps);

        // Prepare the TF Lite Interpreter, Delegate
        srModel = new SRModel(getAssets(), true);
        Log.d(TAG, absoluteFramesDir);
        // Run super resolution
        int frame_index = 0;
        Bitmap bitmap_sr = null;
        for (String frame : videoFramePaths) {
            Bitmap bitmap_lr = BitmapFactory.decodeFile(absoluteFramesDir + "/" + frame);
            Log.d(TAG, absoluteFramesDir + "/" + frame);
            TensorImage lrImage = srModel.prepareInputTensor(bitmap_lr);
            TensorImage srImage = srModel.prepareOutputTensor();


            if (lrImage != null && srImage != null) {
                // Super Resolution
                srModel.interpreter.run(lrImage.getBuffer(), srImage.getBuffer());
            }
            bitmap_sr = srModel.tensorToImage(srImage);

            String img_name = String.format("srframe_%04d.jpeg", frame_index);
            frame_index += 1;

            // Save the SR image
            saveImage(bitmap_sr, img_name);
            srFramePaths.add(absoluteSRFramesDir + "/" + img_name);
        }
        saveImage(bitmap_sr);   // debug


        // Convert the image sequence to video
        saveSrVideo(fps);

    }

}
