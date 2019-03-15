package com.baidu.idl.sample.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
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
import android.text.Editable;
import android.util.Log;
import android.view.Gravity;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.baidu.idl.sample.R;
import com.baidu.idl.sample.api.FaceApi;
import com.baidu.idl.sample.callback.IFaceRegistCalllBack;
import com.baidu.idl.sample.callback.ILivenessCallBack;
import com.baidu.idl.sample.manager.FaceLiveness;
import com.baidu.idl.sample.manager.FaceSDKManager;
import com.baidu.idl.sample.model.LivenessModel;
import com.baidu.idl.sample.utils.DensityUtil;
import com.baidu.idl.sample.utils.ToastUtils;
import com.baidu.idl.sample.view.BaseCameraView;
import com.orbbec.Native.DepthUtils;
import com.orbbec.obDepth2.HomeKeyListener;
import com.serenegiant.usb.IFrameCallback;
import com.serenegiant.usb.USBMonitor;
import com.serenegiant.usb.UVCCamera;

import org.openni.android.OpenNIHelper;

import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;

/**
 * 奥比中光pro RGB+depth 注册页面
 * Created by v_liujialu01 on 2019/1/7.
 */

public class UsbRegisterActivity extends BaseActivity implements
        OpenNIHelper.DeviceOpenListener, ActivityCompat.OnRequestPermissionsResultCallback,
        ILivenessCallBack {
    private static final String TAG = "OrbbecProRegister";
    private static final int MSG_WHAT = 5;
    private static final String MSG_KEY = "YUV";
    private final int depthNeedPermission = 33;

    // 摄像头预览相关控件
    private BaseCameraView mLayoutOne;

    // 注册相关控件
    private RelativeLayout mLayoutInput;
    private EditText mNickView;
    private View registResultView;
    private TextView mTextBatchRegister;
    private RelativeLayout mCameraLayout;

    private TextureView mTextureView;
    private SurfaceTexture mSurfaceTexture;
    private Matrix matrix = new Matrix();
    private Context mContext;

    private int mWidth = com.orbbec.utils.GlobalDef.RESOLUTION_X;
    private int mHeight = com.orbbec.utils.GlobalDef.RESOLUTION_Y;

    private USBMonitor mUSBMonitor;

    private HomeKeyListener mHomeListener;
    private MyHandler mHandler;

    private byte[] yuv = new byte[mWidth * mHeight * 3 / 2];
    private String mNickName;
    private Handler handler = new Handler();
    private Runnable runnable = new Runnable() {
        @Override
        public void run() {
            faceRegistCalllBack.onRegistCallBack(1, null, null);
        }
    };
    private UVCCamera mUVCCamera;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);
        mContext = this;
        initView();
        initCamera();
        setAction();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mHandler = new MyHandler(this);
        // 注意此处的注册和反注册  注册后会有相机usb设备的回调
        if (mUSBMonitor != null) {
            mUSBMonitor.register();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        releaseCamera();
        // 摄像头重启情况之前记录状态
        FaceSDKManager.getInstance().getFaceLiveness().clearInfo();
        finish();
    }

    @Override
    public void onDestroy() {
        Log.v("lkdong", "onDestroy:");
        super.onDestroy();
        FaceSDKManager.getInstance().getFaceLiveness().removeRegistCallBack(faceRegistCalllBack);
        // 重置状态为默认状态
        FaceSDKManager.getInstance().getFaceLiveness()
                .setCurrentTaskType(FaceLiveness.TaskType.TASK_TYPE_ONETON);
        unRegisterHomeListener();
    }

    private void initView() {
        mLableTxt = findViewById(R.id.title);
        mLableTxt.setText(R.string.face_regiseter);

        mLayoutInput = findViewById(R.id.layout_input);
        mTextBatchRegister = findViewById(R.id.text_batch_register);
        mNickView = findViewById(R.id.nick_name);
        registResultView = findViewById(R.id.regist_result);
        mCameraLayout = findViewById(R.id.layout_camera);

        mLayoutInput.setVisibility(View.GONE);
        mTextBatchRegister.setVisibility(View.GONE);
        mCameraLayout.setVisibility(View.VISIBLE);
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

    private void registerHomeListener() {
        mHomeListener = new HomeKeyListener(this);
        mHomeListener.setOnHomePressedListener(new HomeKeyListener.OnHomePressedListener() {
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

    private void setAction() {
        FaceSDKManager.getInstance().getFaceLiveness()
                .setCurrentTaskType(FaceLiveness.TaskType.TASK_TYPE_REGIST);
        FaceSDKManager.getInstance().getFaceLiveness().setLivenessCallBack(this);

        // 注册人脸注册事件
        FaceSDKManager.getInstance().getFaceLiveness().addRegistCallBack(faceRegistCalllBack);
        mNickName = "测试" + (FaceSDKManager.getInstance().setFeature() + 1);
        FaceSDKManager.getInstance().getFaceLiveness().setRegistNickName(mNickName);
        // 设置完成事件
        registResultView.findViewById(R.id.complete).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        handler.postDelayed(runnable, 1000 * 30);
    }

    private void unRegisterHomeListener() {
        if (mHomeListener != null) {
            mHomeListener.stopWatch();
        }
    }

    @Override
    public void onDeviceOpened(UsbDevice device) {
    }

    @Override
    public void onDeviceOpenFailed(String msg) {
        showAlertAndExit("Open Device failed: " + msg);
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

    private final USBMonitor.OnDeviceConnectListener mOnDeviceConnectListener = new USBMonitor.OnDeviceConnectListener() {

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
            ToastUtils.toast(mContext, "USB_DEVICE_DETACHED");
        }

        @Override
        public void onCancel(final UsbDevice device) {
        }
    };

    // 判断人脸识别是否已经完成了，如果没有完成，则不会进行下一次人脸识别。
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

    private static class MyHandler extends Handler {
        private WeakReference<UsbRegisterActivity> mWeakReference;
        private Bitmap mRgbBitmap = null;
        Bitmap bitmap = null;
        int[] rgba = null;

        public MyHandler(UsbRegisterActivity pActivity) {
            mWeakReference = new WeakReference<>(pActivity);
            rgba = new int[mWeakReference.get().mWidth * mWeakReference.get().mHeight];
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
                    if (data == null) {
                        return;
                    }
                    bitmap = mWeakReference.get().cameraByte2Bitmap(data, rgba,
                            mWeakReference.get().mWidth, mWeakReference.get().mHeight);
                    if (bitmap != null) {
                        mRgbBitmap = Bitmap.createBitmap(bitmap, 0, 0,
                                bitmap.getWidth(), bitmap.getHeight(), mWeakReference.get().matrix, true);
                        bitmap.recycle();
                        bitmap = null;

                        // 传入rgb人脸识别bitmap数据
                        FaceSDKManager.getInstance().getFaceLiveness().setRgbBitmap(mRgbBitmap);
                        FaceSDKManager.getInstance().getFaceLiveness().livenessCheck(mWeakReference.get().mWidth,
                                mWeakReference.get().mHeight, 0X0101);
                        mRgbBitmap.recycle();
                        mRgbBitmap = null;
                    }
                }
            }
        }
    }

    // 注册结果
    private IFaceRegistCalllBack faceRegistCalllBack = new IFaceRegistCalllBack() {

        @Override
        public void onRegistCallBack(int code, LivenessModel livenessModel, final Bitmap cropBitmap) {
            handler.removeCallbacks(runnable);
            // 停止摄像头采集
            registResultView.post(new Runnable() {
                @Override
                public void run() {
                    registResultView.setVisibility(View.VISIBLE);
                    mCameraLayout.setVisibility(View.GONE);
                    releaseCamera();
                }
            });
            switch (code) {
                case 0:
                    // 注册成功，显示注册信息
                    registResultView.post(new Runnable() {
                        @Override
                        public void run() {
                            (registResultView.findViewById(R.id.ic_right))
                                    .setBackground(getDrawable(R.mipmap.ic_success));
                            if (cropBitmap != null) {
                                ((ImageView) registResultView.findViewById(R.id.ic_portrait))
                                        .setImageBitmap(cropBitmap);
                            }
                            ((TextView) registResultView.findViewById(R.id.nick_name))
                                    .setText(mNickName);
                            (registResultView.findViewById(R.id.result))
                                    .setVisibility(View.VISIBLE);
                            ((TextView) registResultView.findViewById(R.id.complete))
                                    .setText("确定");
                        }
                    });
                    break;
                case 1: // 注册超时
                    registResultView.post(new Runnable() {
                        @Override
                        public void run() {
                            (registResultView.findViewById(R.id.ic_right))
                                    .setVisibility(View.INVISIBLE);
                            (registResultView.findViewById(R.id.ic_portrait))
                                    .setBackground(getDrawable(R.mipmap.ic_track));
                            ((TextView) registResultView.findViewById(R.id.nick_name))
                                    .setText("注册超时");
                            (registResultView.findViewById(R.id.result))
                                    .setVisibility(View.GONE);
                            ((TextView) registResultView.findViewById(R.id.complete))
                                    .setText("确定");
                            registResultView.setVisibility(View.VISIBLE);
                        }
                    });
                    break;
                default:
                    break;
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
    public void onCallback(int code, LivenessModel livenessModel) {

    }

    private void releaseCamera() {
        if (mHandler != null) {
            mHandler.removeMessages(MSG_WHAT);
            mHandler = null;
        }
        if (mUVCCamera != null) {
            mUVCCamera.stopPreview();
            mUVCCamera.close();
        }
        if (mUSBMonitor != null) {
            mUSBMonitor.unregister();
            mUSBMonitor.destroy();
            mUSBMonitor = null;
        }

    }
}
