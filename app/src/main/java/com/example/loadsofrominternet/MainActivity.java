package com.example.loadsofrominternet;

import android.Manifest;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.RequiresApi;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.alibaba.fastjson.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Arrays;

import okhttp3.OkHttpClient;


public class MainActivity extends AppCompatActivity {

    // Used to load the 'native-lib' library on application startup.
    static {
        System.loadLibrary("native-lib");
    }

    //日志记录区
    private TextView dialogBoard;
    //测试按钮
    private Button downloadButton,loadSoFileButton;

    //日志缓存区
    private String Buffer="操作记录\n";


    //日期转换器
    private SimpleDateFormat simpleDateFormat=null;

    //下载完成监听器
    private CompleteReceiver completeReceiver;

    //主Handler
    private  MyHandler handler=new MyHandler(this);
    private static class MyHandler extends Handler {
        WeakReference weakReference;
        Context context;

        MyHandler(MainActivity activity) {
            context = activity;
            weakReference = new WeakReference(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what){
                case 0:{
                    MainActivity activity=(MainActivity)context;
                    activity.showThisDialog("最新插件版本: "+activity.latestVersion);
                }
            }
        }
    }

    //so库加载完成标志
    private static boolean LoadCompleted=false;

    //应当加载的so文件版本.0=armeabi(默认),1=armeabi-v7a,2=arm64-v8a
    private int soPlatform=0;

    //最新插件版本号,默认为"lib_1_0_0"
    private String latestVersion="lib_1_0_0";

    //下载文件名记录
    private String fileName="";

    //okhttp客户端
    private OkHttpClient client=null;

    class CompleteReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            // get complete download id
            long completeDownloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
            // to do here

            //提示下载完成
            showThisDialog("so文件下载完成.");
            Toast.makeText(MainActivity.this,"文件下载完成!",Toast.LENGTH_LONG).show();
            loadSoFileButton.setEnabled(true);
        }
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        final TextView tv = (TextView) findViewById(R.id.sample_text);
        dialogBoard=(TextView)findViewById(R.id.textview_dialogBoard);
        downloadButton=(Button)findViewById(R.id.button_startToLoadSoFile);
        loadSoFileButton=(Button)findViewById(R.id.button_testSoMethod);
        completeReceiver = new CompleteReceiver();
        //检查支持的CPU平台，并且选定最适合的
        //选定规则:如果支持arm64-v8a,就使用arm64-v8a.如果不支持arm64-v8a,但支持armeabi-v7a,就使用armeabi-v7a.否则就使用armeabi.
        String[] Platfroms=getCPUPlatform();
        showThisDialog("受支持的CPU平台类型:"+Arrays.toString(Platfroms));
        for(String str : Platfroms) {
            if (str.equals("arm64-v8a")){
                this.soPlatform=2;
                break;
            }
            if(str.equals("armeabi-v7a")){
                this.soPlatform=1;
            }
        }
        showThisDialog("so文件加载优先级：arm64-v8a>armeabi-v7a>armeabi.");
        switch (this.soPlatform){
            case 1:{
                showThisDialog("选定平台:armeabi-v7a");
                break;
            }
            case 2:{
                showThisDialog("选定平台:arm64-v8a");
                break;
            }
            default: {
                showThisDialog("选定平台:armeabi");
                break;
            }
        }

        checkForLatestVersionCode();

        //注册下载完成监听器
        registerReceiver(completeReceiver, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));

        downloadButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(!getPermissions()){
                    showThisDialog("请先获取权限.");
                    return;
                }
                File soFile=new File(TCloudResources.downloadAddress+
                        latestVersion+
                        "_"+
                        convertARIsIntToString(soPlatform)+".so");
                if(soFile.exists()){
                    showThisDialog("so文件已经下载到路径:"+soFile.getAbsolutePath());
                    loadSoFileButton.setEnabled(true);
                    return;
                }
                showThisDialog("开始下载so文件");
                downloadSoFile();
            }
        });

        loadSoFileButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //把so文件从下载目录转移到应用目录下面去,注意文件较大时必须移步操作
                File dir=getDir("libs",Context.MODE_PRIVATE),soPath=new File(dir.getPath()+
                        "/"+latestVersion+"_"+convertARIsIntToString(soPlatform)+".so");
                try {
                    if (!soPath.exists()) {
                        showThisDialog("so文件不存在，正在复制.");
                        soPath.createNewFile();
                        copyFile(TCloudResources.downloadAddress+latestVersion+"_"+convertARIsIntToString(soPlatform)+".so",
                                soPath.getAbsolutePath());
                    }
                }catch (IOException e){
                    e.printStackTrace();
                }

                //加载so文件，防止重复加载
                if(!LoadCompleted) {
                    System.load(soPath.getAbsolutePath());
                    LoadCompleted=true;
                }
                //获取so文件内flag信息
                tv.setText(getMyFlag()+"\nCurrent Platform:"+convertARIsIntToString(soPlatform));
                showThisDialog("成功加载so文件内flag信息.");
            }
        });


    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        this.client=null;
        unregisterReceiver(completeReceiver);
    }

    /**
     * A native method that is implemented by the 'native-lib' native library,
     * which is packaged with this application.
     */

    //public native String stringFromJNI();
    public native String getMyFlag();


    /*
    在显示栏中追加日志记录
     */
    public void showThisDialog(String dialog){
        this.Buffer+=(getTime()+"   "+dialog+"\n");
        dialogBoard.setText(this.Buffer);
    }

    /*
    获取当前时间，精确到毫秒
     */
    private String getTime(){
        if(this.simpleDateFormat==null){
            //实例化
            this.simpleDateFormat=new SimpleDateFormat("yyyy-MM-dd HH:mm:ss:SSSS");
        }
        return simpleDateFormat.format(System.currentTimeMillis());
    }

    /*
    检查最新版本号
     */
    public void checkForLatestVersionCode(){
        if(client==null){
        client=new OkHttpClient();
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                showThisDialog("检查插件版本中");
                try {
                    JSONObject jsonObject = JSONObject.parseObject(TCloudResources.run(client,
                            TCloudResources.bucketAddress + "pointer.json"));
                    latestVersion=jsonObject.getString("release");

                }catch (Exception e){
                    e.printStackTrace();
                }
                Message message=new Message();
                message.what=0;
                handler.sendMessage(message);
            }
        }).start();
        }


    /*
    申请必要权限
     */
    private boolean getPermissions() {
        boolean cando=true;
        String[] neededPermissions = {Manifest.permission.INTERNET,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE};
        for (String permission : neededPermissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED ||
                    ActivityCompat.shouldShowRequestPermissionRationale(this, permission)) {
                //说明有权限没有获得
                cando=false;
                ActivityCompat.requestPermissions(this, new String[] {permission}, 0);
            }
        }
        return cando;
    }
    /*
    将文件下载至目录 /Download 下,文件名: 版本号_平台名.so
     */
    @RequiresApi(api = Build.VERSION_CODES.GINGERBREAD)
    private void downloadSoFile(){
        String URL=TCloudResources.bucketAddress+this.latestVersion+"/"+convertARIsIntToString(this.soPlatform)+".so";
        Uri webUri=Uri.parse(URL);
        DownloadManager.Request request=new DownloadManager.Request(webUri);
        request.setDestinationInExternalPublicDir("Download",this.latestVersion+"_"+convertARIsIntToString(this.soPlatform)+".so");
        request.setVisibleInDownloadsUi(true);
        DownloadManager mDownloadManager = (DownloadManager)getSystemService(DOWNLOAD_SERVICE);
        mDownloadManager.enqueue(request);
    }

    /*
    检查设备CPU支持类型,参考文章：https://blog.csdn.net/qq_25412055/article/details/53884582
     */
    public String[] getCPUPlatform(){
        String[] abis = new String[]{};
        if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.LOLLIPOP)
        {
            abis = Build.SUPPORTED_ABIS;
        } else {
            abis = new String[]{Build.CPU_ABI,Build.CPU_ABI2};
        }
        StringBuilder abiStr = new StringBuilder();
        for(String abi:abis)
        {
            abiStr.append(abi);
            abiStr.append(',');
        }
        return abis;
    }

    /*
    转换CPU类型代码为字符串
     */
    private String convertARIsIntToString(int i){
        switch (i){
            case 1:{
                return "armeabi-v7a";
            }
            case 2:{
                return "arm64-v8a";
            }
            default:{
                return "armeabi";
            }
        }
    }




    /*
    把so文件从绝对路径转移到另一路径
     */
    public boolean copyFile(String oldPath$Name, String newPath$Name) {
        try {
            File oldFile = new File(oldPath$Name);
            if (!oldFile.exists()) {
                Log.e("--Method--", "copyFile:  oldFile not exist.");
                return false;
            } else if (!oldFile.isFile()) {
                Log.e("--Method--", "copyFile:  oldFile not file.");
                return false;
            } else if (!oldFile.canRead()) {
                Log.e("--Method--", "copyFile:  oldFile cannot read.");
                return false;
            }

            /* 如果不需要打log，可以使用下面的语句
            if (!oldFile.exists() || !oldFile.isFile() || !oldFile.canRead()) {
                return false;
            }
            */

            FileInputStream fileInputStream = new FileInputStream(oldPath$Name);
            FileOutputStream fileOutputStream = new FileOutputStream(newPath$Name);
            byte[] buffer = new byte[1024];
            int byteRead;
            while (-1 != (byteRead = fileInputStream.read(buffer))) {
                fileOutputStream.write(buffer, 0, byteRead);
            }
            fileInputStream.close();
            fileOutputStream.flush();
            fileOutputStream.close();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

}