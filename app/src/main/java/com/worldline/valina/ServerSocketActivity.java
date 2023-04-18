//package com.worldline.valina;
//
//
//
//import android.app.Activity;
//import android.os.Bundle;
//import android.util.Log;
//
//import java.io.BufferedReader;
//import java.io.IOException;
//import java.io.InputStreamReader;
//import java.net.ServerSocket;
//import java.net.Socket;
//
//public class ServerSocketActivity extends Activity {
//    /** Called when the activity is first created. */
//    private static String TAG = "ServerSocketTest";
//
//    private ServerSocket server;
//
//    Runnable conn = new Runnable() {
//        public void run() {
//            try {
//                server = new ServerSocket(53000);
//
//                while (true) {
//                    Socket socket = server.accept();
//                    BufferedReader in = new BufferedReader(
//                            new InputStreamReader(socket.getInputStream()));
//
//                    String str = in.readLine();
//
//                    Log.i("received response from server", str);
//
//                    in.close();
//                    socket.close();
//                }
//            } catch (IOException e) {
//                Log.e(TAG, e.getMessage());
//            } catch (Exception e) {
//                Log.e(TAG, e.getMessage());
//            }
//        }
//    };
//    @Override
//    public void onCreate(Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//        setContentView(R.layout.activity_main);
//
//        new Thread(conn).start();
//    }
//
//    @Override
//    protected void onPause() {
//        super.onPause();
//        if (server != null) {
//            try {
//                server.close();
//            } catch (IOException e) {
//                e.printStackTrace();
//            }
//        }
//    }
//}