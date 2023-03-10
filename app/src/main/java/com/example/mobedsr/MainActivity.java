package com.example.mobedsr;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.example.mobedsr.databinding.ActivityMainBinding;

import org.tensorflow.lite.support.image.TensorImage;

import java.io.IOException;

public class MainActivity extends AppCompatActivity {

    private int width;
    private int height;
    private Bitmap bitmap_lr;
    private Bitmap bitmap_sr;

    private final String TAG = "MobedSR";
    private boolean useGpu = false;

    // View variables
    private View decorView;
    private TextView text_sr;
    private TextView text_gpu;
    private TextView text_time;
    private ImageView img_lr;
    private ImageView img_hr;

    private Uri uri;
    private Switch switch_gpu;
    private Button btn_resolution;
    private Button btn_lr_img;

    private Toolbar toolbar;


    // Used to load the 'mobedsr' library on application startup.
    static {
        System.loadLibrary("mobedsr");
    }

    private ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Get the components
        setComponentById();

        // Hide the soft-keys
        hideSoftKeys(decorView);

        // switch listener
        switch_gpu.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                useGpu = true;
                text_gpu.setText("Enable GPU");
            }
            else {
                useGpu = false;
                text_gpu.setText("Disable GPU");
            }
        });

        // button click listener
        btn_resolution.setOnClickListener(v -> {
            // Start Resolution when low resolution image is set
            if (img_lr.getDrawable() != null) {
                Log.d(TAG, "Resolution Start!");
                try {
                    runSR();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            else {
                Toast.makeText(this, "No low resolution image!", Toast.LENGTH_SHORT).show();
            }
        });

        // Get the image from gallary
        btn_lr_img.setOnClickListener(v -> {

            /* Intent: Request jobs to other app components*/
            Intent intent = new Intent(Intent.ACTION_PICK);
            intent.setType("image/*");
            startActivityResult.launch(intent);
        });

    }


    // Launcher for setting the low resolution image
    ActivityResultLauncher<Intent> startActivityResult = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            new ActivityResultCallback<ActivityResult>() {
                @Override
                public void onActivityResult(ActivityResult result) {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        uri = result.getData().getData();

                        try {
                            // Set the low resolution image
                            Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), uri);
                            img_lr.setImageBitmap(bitmap);

                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            });


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

    /** @brief  Set the component by view
     *  @date   23/01/25
     */
    private void setComponentById() {
        decorView = getWindow().getDecorView();
        switch_gpu = findViewById(R.id.useGpu);

        text_time = findViewById(R.id.text_time);
        text_gpu = findViewById(R.id.gpu_status);
        text_sr = findViewById(R.id.text_sr);

        img_lr = findViewById(R.id.img_lr);
        img_hr = findViewById(R.id.img_hr);

        btn_resolution = findViewById(R.id.btn_resolution);
        btn_lr_img = findViewById(R.id.btn_lr);

        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
    }


    /** @brief  Reflect menu resource xml to Appbar
     *  @date   23/01/26
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.toolbar, menu);
        return true;
    }


    /** @brief  Listener when the toolbar menu clicked
     *  @date   23/01/26
     *  @param  item: selected toolbar item
     */
    @SuppressLint("NonConstantResourceId")
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        hideSoftKeys(decorView);
        switch (item.getItemId()) {
            case R.id.imageSR:
                startActivity(new Intent(this, MainActivity.class));
                return true;
            case R.id.videoSR:
                startActivity(new Intent(this, VideoSRActivity.class));
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }


    /** @brief  Run Super Resolution
     *  @date   23/01/25
     */
    private void runSR() throws IOException {
        // Get the image by bitmap
        BitmapDrawable drawable = (BitmapDrawable) img_lr.getDrawable();
        bitmap_lr = drawable.getBitmap();

        SRModel srModel = new SRModel(getAssets(), useGpu);

        // Prepare image by TensorImage
        TensorImage inputTensor = srModel.prepareInputTensor(bitmap_lr);
        TensorImage outputTensor = srModel.prepareOutputTensor();

//        // Set GPU delegate
//        CompatibilityList compatList = new CompatibilityList();
//        GpuDelegate.Options delegateOptions = compatList.getBestOptionsForThisDevice();
//        GpuDelegate gpuDelegate = new GpuDelegate(delegateOptions);
//
//        // Create the interpreter
//        Interpreter.Options options = new Interpreter.Options();
//        options.addDelegate(gpuDelegate);
//        Interpreter interpreter = new Interpreter(loadModelFile(), options);


        // Debug: Get input size
        // Tensor input = interpreter.getInputTensor(0);
        // int[] inputShape = input.shape();
        // Log.d(TAG, String.format("input shape %d %d", width, height));

        // Run the interpreter
        long startTime = System.currentTimeMillis();
        srModel.run(inputTensor.getBuffer(), outputTensor.getBuffer());

        text_time.setText(String.format("Spent time: %dms", (System.currentTimeMillis()-startTime)));
        text_sr.setVisibility(View.GONE);

        // Show the result
        bitmap_sr = srModel.tensorToImage(outputTensor);
        img_hr.setImageBitmap(bitmap_sr);
    }



    /**
     * A native method that is implemented by the 'mobedsr' native library,
     * which is packaged with this application.
     */
    public native String stringFromJNI();
}