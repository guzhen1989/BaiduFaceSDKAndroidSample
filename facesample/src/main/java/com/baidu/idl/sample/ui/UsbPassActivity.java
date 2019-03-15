package com.baidu.idl.sample.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.hardware.usb.UsbDevice;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.baidu.idl.facesdk.model.Feature;
import com.baidu.idl.sample.R;
import com.baidu.idl.sample.callback.ILivenessCallBack;
import com.baidu.idl.sample.manager.FaceSDKManager;
import com.baidu.idl.sample.model.LivenessModel;
import com.baidu.idl.sample.utils.DensityUtil;
import com.baidu.idl.sample.utils.FileUtils;
import com.baidu.idl.sample.utils.Utils;
import com.baidu.idl.sample.view.BaseCameraView;
import com.baidu.idl.sample.view.CircleImageView;
import com.baidu.idl.sample.view.CirclePercentView;
import com.orbbec.Native.DepthUtils;
import com.orbbec.obDepth2.HomeKeyListener;
import com.serenegiant.usb.IFrameCallback;
import com.serenegiant.usb.USBMonitor;
import com.serenegiant.usb.UVCCamera;

import org.openni.android.OpenNIHelper;

import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;

import static com.baidu.idl.sample.utils.Utils.TAG;

/**
 * Created by litonghui on 2018/11/17.
 */

public class UsbPassActivity extends BaseActivity implements
        ILivenessCallBack, OpenNIHelper.DeviceOpenListener,
        ActivityCompat.OnRequestPermissionsResultCallback {

    private static final int MSG_WHAT = 5;
    private static final String MSG_KEY = "YUV";
    private final int depthNeedPermission = 33;

    private Context mContext;

    private CircleImageView mImage;
    private TextView mNickNameTv;
    private TextView mSimilariryTv;
    private TextView mNumTv;
    private TextView mDetectTv;
    private TextView mFeatureTv;
    private TextView mLiveTv;
    private TextView mAllTv;
    private RelativeLayout mCameraLayout;

    private TextureView mTextureView;
    private SurfaceTexture mSurfaceTexture;
    private BaseCameraView mLayoutOne;

    private CirclePercentView mRgbCircleView;
    private CirclePercentView mNirCircleView;
    private CirclePercentView mDepthCircleView;

    private int mWidth = com.orbbec.utils.GlobalDef.RESOLUTION_X;
    private int mHeight = com.orbbec.utils.GlobalDef.RESOLUTION_Y;

    private USBMonitor mUSBMonitor;
    private Matrix matrix = new Matrix();

    private HomeKeyListener mHomeListener;
    private MyHandler mHandler;

    private byte[] yuv = new byte[mWidth * mHeight * 3 / 2];

    private Bitmap mBitmap;
    private String mUserName;
    private RelativeLayout mLayoutInfo;
    private ImageView mImageTrack;
    private UVCCamera mUVCCamera;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pass);
        mContext = this;
        initView();
        initData();
    }

    private void initView() {
        mLableTxt = findViewById(R.id.title);
        mLableTxt.setText(R.string.pass_1_n);

        mImage = findViewById(R.id.image);
        mNickNameTv = findViewById(R.id.tv_nick_name);
        mSimilariryTv = findViewById(R.id.tv_similarity);
        mNumTv = findViewById(R.id.tv_num);
        mDetectTv = findViewById(R.id.tv_detect);
        mFeatureTv = findViewById(R.id.tv_feature);
        mLiveTv = findViewById(R.id.tv_live);
        mAllTv = findViewById(R.id.tv_all);

        mImageTrack = findViewById(R.id.image_track);
        mRgbCircleView = findViewById(R.id.circle_rgb_live);
        mNirCircleView = findViewById(R.id.circle_nir_live);
        mDepthCircleView = findViewById(R.id.circle_depth_live);

        mLayoutInfo = findViewById(R.id.layout_info);
        mCameraLayout = findViewById(R.id.layout_camera);
        initCamera();
    }

    private void initCamera() {
        // 计算并适配显示图像容器的宽高
        String newPix = DensityUtil.calculateCameraOrbView(mContext);
        String[] newPixs = newPix.split(" ");
        int newWidth = Integer.parseInt(newPixs[0]);
        int newHeight = Integer.parseInt(newPixs[1]);
        ViewGroup.LayoutParams lp = new ViewGroup.LayoutParams(newWidth, newHeight);


        mLayoutOne = new BaseCameraView(mContext);
        mLayoutOne.setGravity(Gravity.CENTER);

        mTextureView = new TextureView(mContext);
        mTextureView.setOpaque(false);
        mTextureView.setKeepScreenOn(true);
        // 创建一个布局放入mTextureView
        RelativeLayout rl = new RelativeLayout(mContext);
        rl.setGravity(Gravity.CENTER);
        rl.addView(mTextureView, lp);
        // 将创建的布局放入mLayoutOne
        mLayoutOne.addView(rl, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        // 调用BaseCameraView添加人脸识别框
        mLayoutOne.initFaceFrame(mContext);
        // mTextureView的绘制完毕监听，用于将左边距传入BaseCameraView
        mTextureView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                mLayoutOne.leftDisparity(mTextureView.getLeft());
                mLayoutOne.topDisparity(mTextureView.getTop());
            }
        });
        mCameraLayout.setGravity(Gravity.CENTER);
        mCameraLayout.addView(mLayoutOne);

        matrix.postScale(-1, 1);   // 镜像水平翻转
        initUvc();
        registerHomeListener();

    }

    private void initData() {
        int num = FaceSDKManager.getInstance().setFeature();
        mNumTv.setText(String.format("底库人脸数: %s 个", num));
        FaceSDKManager.getInstance().getFaceLiveness().setLivenessCallBack(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.e("lkdong", "onStart:");
        mHandler = new MyHandler(this, mImageTrack);
        // 注意此处的注册和反注册  注册后会有相机usb设备的回调
        if (mUSBMonitor != null) {
            mUSBMonitor.register();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        mHandler.removeMessages(MSG_WHAT);
        mHandler = null;
        if (mUSBMonitor != null) {
            mUSBMonitor.unregister();
            mUSBMonitor.destroy();
        }
        if (mUVCCamera != null) {
            mUVCCamera.stopPreview();
            mUVCCamera.close();
        }
        // 摄像头重启情况之前记录状态
        FaceSDKManager.getInstance().getFaceLiveness().clearInfo();
        finish();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mUVCCamera != null) {
            mUVCCamera.stopPreview();
            mUVCCamera.close();
        }
        if (mUSBMonitor != null) {
            mUSBMonitor.destroy();
            mUSBMonitor = null;
        }
        unRegisterHomeListener();
    }

    private void registerHomeListener() {
        mHomeListener = new HomeKeyListener(this);
        mHomeListener
                .setOnHomePressedListener(new HomeKeyListener.OnHomePressedListener() {

                    @Override
                    public void onHomePressed() {
                        finish();
                    }

                    @Override
                    public void onHomeLongPressed() {
                    }
                });
        mHomeListener.startWatch();
    }

    private void unRegisterHomeListener() {
        if (mHomeListener != null) {
            mHomeListener.stopWatch();
        }
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        if (mUVCCamera != null) {
            mUVCCamera.stopPreview();
            mUVCCamera.close();
        }
        if (mUSBMonitor != null) {
            mUSBMonitor.destroy();
            mUSBMonitor = null;
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    private void initUvc() {
        mUSBMonitor = new USBMonitor(getApplicationContext(), mOnDeviceConnectListener); // 创建
        mTextureView.setRotationY(180); // 旋转90度
        mTextureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                mSurfaceTexture = surface;
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                if (mUVCCamera != null) {
                    mUVCCamera.stopPreview();
                }
                mSurfaceTexture = null;
                return true;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surface) {

            }
        });
    }

    private final USBMonitor.OnDeviceConnectListener mOnDeviceConnectListener
            = new USBMonitor.OnDeviceConnectListener() {
        @Override
        public void onAttach(final UsbDevice device) {
            if (device.getDeviceClass() == 239 && device.getDeviceSubclass() == 2) {
                mUSBMonitor.requestPermission(device);
            }
        }


        @Override
        public void onConnect(final UsbDevice device, final USBMonitor.UsbControlBlock ctrlBlock,
                              final boolean createNew) {
            mUVCCamera = new UVCCamera();
            mUVCCamera.open(ctrlBlock);
            if (mSurfaceTexture != null) {
                mUVCCamera.setPreviewTexture(mSurfaceTexture);
                mUVCCamera.setPreviewSize(mWidth, mHeight);
//                设置回调 和回调数据类型
                mUVCCamera.setFrameCallback(iFrameCallback, UVCCamera.PIXEL_FORMAT_YUV420SP);
                mUVCCamera.startPreview();
            }
        }

        @Override
        public void onDisconnect(final UsbDevice device, final USBMonitor.UsbControlBlock ctrlBlock) {
            ctrlBlock.close();
            if (mUVCCamera != null) {
                mUVCCamera.stopPreview();
                mUVCCamera.close();
            }
        }

        @Override
        public void onDettach(final UsbDevice device) {
            Toast.makeText(UsbPassActivity.this, "USB_DEVICE_DETACHED", Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onCancel(final UsbDevice device) {
        }
    };

    //判断人脸识别是否已经完成了，如果没有完成，则不会进行下一次人脸识别。
    IFrameCallback iFrameCallback = new IFrameCallback() {
        @Override
        public void onFrame(final ByteBuffer byteBuffer) {
            final int len = byteBuffer.capacity();
            if (len > 0) {
                byteBuffer.get(yuv);
                if (mHandler != null) {
                    mHandler.removeMessages(MSG_WHAT);
                    Message message = mHandler.obtainMessage();
                    message.getData().putByteArray(MSG_KEY, yuv);
                    message.what = MSG_WHAT;
                    mHandler.sendMessage(message);
                }
            }
        }
    };

    public Bitmap cameraByte2Bitmap(byte[] data, int[] rgba, int width, int height) {
        DepthUtils.cameraByte2Bitmap(data, rgba, width, height);
        Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
        bmp.setPixels(rgba, 0, width, 0, 0, width, height);
        return bmp;
    }

    @Override
    public void onTip(int code, String msg) {

    }

    @Override
    public void onCanvasRectCallback(LivenessModel livenessModel) {

    }


    @Override
    public void onCallback(final int code, final LivenessModel livenessModel) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mDetectTv.setText(String.format("人脸检测耗时: %s ms", livenessModel == null
                        ? 0 : livenessModel.getRgbDetectDuration()));
                mFeatureTv.setText(String.format("特征提取耗时: %s ms", livenessModel == null
                        ? 0 : livenessModel.getFeatureDuration()));
                mLiveTv.setText(String.format("活体检测耗时: %s ms", livenessModel == null
                        ? 0 : livenessModel.getLiveDuration()));
                mAllTv.setText(String.format("1:N人脸检索耗时: %s ms", livenessModel == null
                        ? 0 : livenessModel.getCheckDuration()));

                mRgbCircleView.setCurPercent(livenessModel == null
                        ? 0 : livenessModel.getRgbLivenessScore());

                mNirCircleView.setCurPercent(livenessModel == null
                        ? 0 : livenessModel.getIrLivenessScore());

                mDepthCircleView.setCurPercent(livenessModel == null
                        ? 0 : livenessModel.getDepthLivenessScore());

                if (livenessModel == null) {
                    mLayoutInfo.setVisibility(View.INVISIBLE);

                } else {
                    mLayoutInfo.setVisibility(View.VISIBLE);
                    if (code == 0) {
                        Feature feature = livenessModel.getFeature();
                        mSimilariryTv.setText(String.format("相似度: %s", livenessModel.getFeatureScore()));
                        mNickNameTv.setText(String.format("%s，你好！", feature.getUserName()));

                        if (!TextUtils.isEmpty(mUserName) && feature.getUserName().equals(mUserName)) {
                            mImage.setImageBitmap(mBitmap);
                        } else {
                            String imgPath = FileUtils.getFaceCropPicDirectory().getAbsolutePath()
                                    + "/" + feature.getCropImageName();
                            Bitmap bitmap = Utils.getBitmapFromFile(imgPath);
                            mImage.setImageBitmap(bitmap);
                            mBitmap = bitmap;
                            mUserName = feature.getUserName();
                        }
                    } else {
                        mSimilariryTv.setText("未匹配到相似人脸");
                        mNickNameTv.setText("陌生访客，请先注册");
                        mImage.setImageResource(R.mipmap.preview_image_angle);
                    }
                }
            }
        });
    }

    private static class MyHandler extends Handler {
        private WeakReference<UsbPassActivity> mWeakReference;
        private Bitmap RgbBitmap = null;
        Bitmap bitmap = null;
        int[] rgba = null;
        private ImageView imageView;

        public MyHandler(UsbPassActivity pActivity, ImageView imageView) {
            mWeakReference = new WeakReference<>(pActivity);
            rgba = new int[mWeakReference.get().mWidth * mWeakReference.get().mHeight];
            this.imageView = imageView;
        }

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            if (msg.what == MSG_WHAT) {
                if (mWeakReference.get() != null) {
                    if (rgba == null) {
                        rgba = new int[mWeakReference.get().mWidth * mWeakReference.get().mHeight];
                    }
                    byte[] data = msg.getData().getByteArray(MSG_KEY);
                    if (data == null)
                        return;
                    bitmap = mWeakReference.get().cameraByte2Bitmap(data, rgba,
                            mWeakReference.get().mWidth, mWeakReference.get().mHeight);
                    if (bitmap != null) {
                        RgbBitmap = Bitmap.createBitmap(bitmap, 0, 0,
                                bitmap.getWidth(), bitmap.getHeight(), mWeakReference.get().matrix, true);
                        bitmap.recycle();
                        bitmap = null;

                        // 用于测试送检图片的显示，使用时建议注释掉
//                        if (imageView != null) {
//                            imageView.setImageBitmap(Utils.getBitmap(rgba, mWeakReference.get().mHeight, mWeakReference.get().mWidth));
//                        }
                        //传入rgb人脸识别bitmap数据
                        FaceSDKManager.getInstance().getFaceLiveness().setRgbBitmap(RgbBitmap);
                        FaceSDKManager.getInstance().getFaceLiveness().livenessCheck(mWeakReference.get().mWidth, mWeakReference.get().mHeight, 0X0101);
                        RgbBitmap.recycle();
                        RgbBitmap = null;
                    }
                }
            }
        }
    }


    @Override
    public void onDeviceOpened(UsbDevice device) {
    }

    @Override
    public void onDeviceOpenFailed(String msg) {
        showAlertAndExit("Open Device failed: " + msg);
    }


    private void showAlertAndExit(String message) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(message);
        builder.setNeutralButton("OK", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                finish();
            }
        });
        builder.show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == depthNeedPermission) {

            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Permission Grant");
                Toast.makeText(mContext, "Permission Grant", Toast.LENGTH_SHORT).show();
            } else {
                Log.d(TAG, "Permission Denied");
                Toast.makeText(mContext, "Permission Denied", Toast.LENGTH_SHORT).show();
            }


        }
    }
}
