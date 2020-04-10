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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;


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

    //so库加载完成标志
    private static boolean LoadCompleted=false;


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

        // Example of a call to a native method
        final TextView tv = (TextView) findViewById(R.id.sample_text);

        dialogBoard=(TextView)findViewById(R.id.textview_dialogBoard);
        downloadButton=(Button)findViewById(R.id.button_startToLoadSoFile);
        loadSoFileButton=(Button)findViewById(R.id.button_testSoMethod);
        completeReceiver = new CompleteReceiver();
        //注册下载完成监听器
        registerReceiver(completeReceiver, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
        downloadButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(!getPermissions()){
                    showThisDialog("请先获取权限.");
                    return;
                }

                if(new File("/storage/emulated/0/Download/libloadLibraryTest.so").exists()){
                    showThisDialog("so文件已经下载至本地. 路径:/storage/emulated/0/Download/libloadLibraryTest.so");
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
                File dir=getDir("libs",Context.MODE_PRIVATE),soPath=new File(dir.getPath()+"/libloadLibraryTest.so");
                try {
                    if (!soPath.exists()) {
                        showThisDialog("so文件不存在，正在复制.");
                        soPath.createNewFile();
                        copyFile("/storage/emulated/0/Download/libloadLibraryTest.so",
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
                tv.setText(getMyFlag());
                showThisDialog("成功加载so文件内flag信息.");
            }
        });


    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
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
    private void showThisDialog(String dialog){
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
    申请必要权限
     */
    private boolean getPermissions() {
        boolean cando=true;
        String[] neededPermissions = {Manifest.permission.INTERNET,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE};
        showThisDialog("检查权限，如有未获得权限则自动申请，请同意以进行测试.");
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
    将文件下载至目录 /Download 下
     */
    @RequiresApi(api = Build.VERSION_CODES.GINGERBREAD)
    private void downloadSoFile(){
        String URL="https://solibrarytest-1257936688.cos.ap-beijing.myqcloud.com/libloadLibraryTest.so";
        Uri webUri=Uri.parse(URL);
        DownloadManager.Request request=new DownloadManager.Request(webUri);
        request.setDestinationInExternalPublicDir("Download","libloadLibraryTest.so");
        request.setVisibleInDownloadsUi(true);
        DownloadManager mDownloadManager = (DownloadManager)getSystemService(DOWNLOAD_SERVICE);
        mDownloadManager.enqueue(request);
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