package com.example.qoli.myapplication;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;

public class MyAccessibility extends AccessibilityService {
    private static final String TAG = "MyAccessibility";
    private static final String Hosts = "http://192.168.1.104:3002";
    private int Pong = 0;
    private Socket mSocket;

    /**
     * pidcat com.example.qoli.myapplication -l I
     * terminal 指令
     */

    /**
     * Socket
     */
    private void initSocketHttp() {

        // TODO https://github.com/socketio/socket.io-client-java/issues/123#issuecomment-96066333
        // 休眠后會斷線…

        try {
            IO.Options options = new IO.Options();
            options.timeout = 60 * 1000;
            options.reconnection = true;
            mSocket = IO.socket(Hosts,options);
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }

        mSocket.on(Socket.EVENT_CONNECT, onConnect);
        mSocket.on(Socket.EVENT_DISCONNECT, onDisconnect);// 断开连接
        mSocket.on(Socket.EVENT_CONNECT_ERROR, onConnectError);// 连接异常
        mSocket.on(Socket.EVENT_CONNECT_TIMEOUT, onConnectTimeoutError);// 连接超时

        mSocket.on("update", onUpdate);
        mSocket.on("Ping", onPing);

        mSocket.connect();
    }

    /**
     * Socket 相關函數
     */

    private Emitter.Listener onConnect = new Emitter.Listener() {
        @Override
        public void call(Object... args) {
            mSocket.emit("android", "onAccessibilityEvent ONLINE");
        }
    };

    private Emitter.Listener onUpdate = new Emitter.Listener() {
        @Override
        public void call(Object... args) {

            Log.i(TAG, "> Call: android action.");
            wakeAndUnlock(true);

            JSONObject obj = (JSONObject) args[0];
            try {
                Log.i(TAG, "updateDevice: " + obj.get("updateDevice"));
                Log.i(TAG, "status: " + obj.get("status"));

                boolean onView = gotoView("AndroidAPI");

                if (onView) {
                    nodeAction(obj.get("updateDevice").toString(), obj.get("status").toString());
                } else {
                    Log.i(TAG, "> Call: No");
                }

            } catch (JSONException e) {
                e.printStackTrace();
            }

            wakeAndUnlock(false);
        }
    };

    private Emitter.Listener onDisconnect = new Emitter.Listener() {
        @Override
        public void call(Object... args) {
            Log.i(TAG, "断开连接 " + args[0]);
            mSocket.emit("android", "onAccessibilityEvent OFFLINE");
            onSocketFail();
        }
    };

    private Emitter.Listener onConnectError = new Emitter.Listener() {
        @Override
        public void call(final Object... args) {
            Log.i(TAG, "连接失败" + args[0]);
            networkTest();
            onSocketFail();
        }
    };

    private Emitter.Listener onConnectTimeoutError = new Emitter.Listener() {
        @Override
        public void call(final Object... args) {
            Log.i(TAG, "连接超时" + args[0]);
            networkTest();
            onSocketFail();
        }
    };

    private Emitter.Listener onPing = new Emitter.Listener() {
        @Override
        public void call(final Object... args) {
            Thread socketThread = new Thread(new Runnable() {
                public void run() {
                    Pong++;
                    System.out.println("Socket.io send Pong ... " + Pong);
                    mSocket.emit("Pong", "Ping");
                }
            });
            socketThread.start();
        }
    };

    private void onSocketFail() {
        wakeAndUnlock(true);
        startApp("com.xiaomi.smarthome");
        initSocketHttp();
        wakeAndUnlock(false);
    }


    //锁屏、唤醒相关
    private PowerManager powerManager;
    private PowerManager.WakeLock wakeLock;

    // TODO 尋找良好的鎖屏代碼
    private void wakeAndUnlock(boolean b) {
        if (b) {
            //获取电源管理器对象
            powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
            //获取PowerManager.WakeLock对象，后面的参数|表示同时传入两个值，最后的是调试用的Tag
            wakeLock = powerManager.newWakeLock(PowerManager.ACQUIRE_CAUSES_WAKEUP | PowerManager.SCREEN_BRIGHT_WAKE_LOCK, TAG);

            //点亮屏幕
            wakeLock.acquire();
        } else {
            //释放wakeLock，关灯
            wakeLock.release();
        }
    }

    /**
     * 當服務成功激活
     */

    @Override
    protected void onServiceConnected() {
        Log.i(TAG, "> 無障礙設定已經激活！");
        tellUser("MiHomePlus 服務已經激活.");
        startApp("com.xiaomi.smarthome");
        initSocketHttp();
        networkTest();
    }

    /**
     * 網絡信息獲取
     */

    private boolean networkTest() {
        return false;
    }

    /*
     * 打開一個 App
     */
    public void startApp(String appPackageName) {
        try {
            Intent intent = this.getPackageManager().getLaunchIntentForPackage(appPackageName);
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "没有安装", Toast.LENGTH_LONG).show();
        }
    }


    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {

        int eventType = event.getEventType();

        if (eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            preProcess(event);
            Log.i(TAG, "onAccessibilityEvent");
        }


    }

    /*
     * 處理……
     */
    private void preProcess(AccessibilityEvent event) {

        int nextNumber = 0;

        if (event.getSource() != null) {
            nextNumber = nextNumber + 1;
        }

        if (event.getPackageName().equals("com.xiaomi.smarthome")) {
            nextNumber = nextNumber + 1;
        }

        if (nextNumber == 2) {

            // TODO View 支持指定名稱，配置檔
            boolean onView = gotoView("AndroidAPI");

            if (onView) {
                tellUser("MiHomePlus");
                // TODO 改進為配置檔形式
                nodeAction("空調伴侶", "read");
                nodeAction("電腦燈", "read");
                nodeAction("落地燈", "read");
                nodeAction("客廳空氣淨化器", "read");
            } else {
                tellUser("< Mi >");
            }

        }

    }

    /*
     * 切換到正確的頁面
     */
    private boolean gotoView(String lookingTitle) {
        // TODO 如果 app 沒有在前台需要兩次發送才成功

        AccessibilityNodeInfo source = getRootInActiveWindow();

        if (!source.getPackageName().equals("com.xiaomi.smarthome")) {
            startApp("com.xiaomi.smarthome");
        }

        List<AccessibilityNodeInfo> viewTitle = source.findAccessibilityNodeInfosByViewId("com.xiaomi.smarthome:id/module_a_2_more_title");

        if (!titleCheck(lookingTitle, viewTitle)) {
            List<AccessibilityNodeInfo> menuBtn = source.findAccessibilityNodeInfosByViewId("com.xiaomi.smarthome:id/drawer_btn");
            doClick(menuBtn);
            List<AccessibilityNodeInfo> backBtn = source.findAccessibilityNodeInfosByViewId("com.xiaomi.plugseat:id/title_bar_return");
            doClick(backBtn);
            List<AccessibilityNodeInfo> apiBtn = getRootInActiveWindow().findAccessibilityNodeInfosByText(lookingTitle);
            if (apiBtn != null)
                for (AccessibilityNodeInfo n : apiBtn) {
                    n.getParent().performAction(AccessibilityNodeInfo.ACTION_CLICK);
                }
            return false;
        } else {
            return true;
        }


    }

    /**
     * 標題檢查，配合
     */
    private boolean titleCheck(String title, List<AccessibilityNodeInfo> viewTitle) {

        if (viewTitle != null && !viewTitle.isEmpty()) {
            AccessibilityNodeInfo node;
            for (int i = 0; i < viewTitle.size(); i++) {
                node = viewTitle.get(i);
                Log.i(TAG, "> Title Check: " + title + " / " + node.getText() + ", index: " + i);

                Pattern pattern = Pattern.compile("^" + title + ".*");
                Matcher matcher = pattern.matcher(node.getText());
                if (matcher.matches()) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 執行點擊
     *
     * @param infos
     */
    private void doClick(List<AccessibilityNodeInfo> infos) {
        if (infos != null)
            for (AccessibilityNodeInfo info : infos) {
                if (info.isEnabled() && info.isClickable()) {
                    Log.i(TAG, "> doClick: " + info.getText());
                    info.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                }

            }
    }

    /**
     * 節點動作
     *
     * @param lookingName
     * @param action
     */
    private void nodeAction(String lookingName, String action) {

        // 查找基於關鍵字的設備
        List<AccessibilityNodeInfo> looking = getRootInActiveWindow().findAccessibilityNodeInfosByText(lookingName);

        if (looking != null && !looking.isEmpty()) {

            AccessibilityNodeInfo node;
            Log.i(TAG, "> " + lookingName + " now. Search Total: " + looking.size());

            for (int i = 0; i < looking.size(); i++) {
                node = looking.get(i);

                // 查找設備狀態
                List<AccessibilityNodeInfo> parent = node.getParent().findAccessibilityNodeInfosByViewId("com.xiaomi.smarthome:id/info_value");

                if (parent != null && !parent.isEmpty()) {

                    AccessibilityNodeInfo nodeParent;
                    for (int j = 0; j < parent.size(); j++) {
                        nodeParent = parent.get(j);
                        Log.i(TAG, "> " + node.getText() + " 狀態: " + nodeParent.getText() + " 操作: " + action);

                        // 點擊或者讀取按鈕
                        if (action.equals("read")) {
                            sync(node.getText().toString(), nodeParent.getText().toString());
                        } else {
                            if (!nodeParent.getText().toString().equals(action)) {
                                nodeParent.getParent().performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                sync(node.getText().toString(), nodeParent.getText().toString());
                            }
                        }

                    }
                }

                break;
            }
        }

    }

    /**
     * 與伺服器同步函數
     *
     * @param name
     * @param status
     * @return
     */
    private boolean sync(final String name, final String status) {

        Thread t1 = new Thread(new Runnable() {
            public void run() {

                System.out.println("Server Sync ... ");
                URL url;
                HttpURLConnection urlConnection = null;
                try {
                    url = new URL(Hosts + "/sync/" + URLEncoder.encode(name, "UTF-8") + "/" + URLEncoder.encode(status, "UTF-8"));

                    urlConnection = (HttpURLConnection) url.openConnection();

                    urlConnection.setRequestProperty("connection", "Keep-Alive");
                    urlConnection.setRequestProperty("user-agent", "HomeKitProxy/1.0 (Android)");

                    BufferedReader in = new BufferedReader(new InputStreamReader(urlConnection.getInputStream()));
                    String line;
                    for (; (line = in.readLine()) != null; ) {
                        System.out.println("> on Server: " + line);
                    }

                    System.out.println("> on Local: " + name + " => " + status);
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    if (urlConnection != null) {
                        urlConnection.disconnect();
                    }
                }

            }
        });
        t1.start();

        return false;

    }

    /**
     * toast 顯示函數
     *
     * @param s
     */
    private void tellUser(String s) {
        Context context = getApplicationContext();
        CharSequence text = s;
        int duration = Toast.LENGTH_SHORT;

        Toast toast = Toast.makeText(context, text, duration);
        toast.show();
    }

    @Override
    public void onInterrupt() {
    }


}


