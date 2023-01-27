# MobedSR: Practice of Super Resolution on the Android

## TODO
+ ~~Implement the frame of app~~ (23/01/25)
+ ~~Implement TF Lite Interpreter by Java~~ (23/01/25)
+ Implement TF Lite Interpreter by C++
<br/>
  
+ Build DNN model using the python a to z
+ Add evaluation indicators i.e. PSNR, SSIM 
+ Expanding the subject from Image Super Resolution to other applications
+ Implement the CPU, GPU co-execution for DNN task (GOAL!)
<br/>

+ Implement Video Super Resolution by using FFmpeg
+ Refactor the VSR code more generally
+ Solve the issue of storage -> it's hardcoded now


## Progress log
23/01/25:  
+ Start Toy Project. 
+ Finish setting up basic frame of app. 
+ Finish java implementation of simple super resolution app  

23/01/26:
+ Expand the project to video super resolution
+ Create new activity for video super resolutio
+ Capture frames from video by using FFmpeg

23/01/27:
+ Implement split the video by fps
+ Ongoing on super resolution the frames and convert them to the super resolution video

## References
+ https://github.com/arthenica/ffmpeg-kit/tree/main/android
+ https://github.com/cd-athena/MoViDNN
+ https://openaccess.thecvf.com/content/CVPR2021W/MAI/papers/Liu_EVSRNet_Efficient_Video_Super-Resolution_With_Neural_Architecture_Search_CVPRW_2021_paper.pdf

### Low resolution video
https://commons.wikimedia.org/wiki/Category:Video_display_resolution_480_x_270