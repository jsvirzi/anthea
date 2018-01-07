package com.mam.lambo.modules.server;

import android.app.Activity;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Pair;

import com.mam.lambo.modules.utils.Utils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/**
 * Created by jsvirzi on 12/2/16.
 */

/* to test
 * curl --data "param1=value1&param2=value2" 192.168.1.13:8080/sendData.html
 */

public class SimpleServer {

    static String TAG = "SimpleServer";
    HandlerThread thread;
    Handler handler;
    ServerSocket serverSocket;
    int port;
    boolean run;
    boolean done;
    private List<ISimpleServerListener> listeners;

    public void addListener(ISimpleServerListener listener) {
        listeners.add(listener);
    }

    public SimpleServer(int port) {

        listeners = new ArrayList<>();

        run = false; /* changes later */
        done = true;

        this.port = port;
        thread = new HandlerThread("server");
        thread.start();
        handler = new Handler(thread.getLooper());

        List<InetAddress> ipAddresses = Utils.getIpAddresses();
        if (ipAddresses.size() == 0) {
            Log.d(TAG, "unable to find a local ip address");
            return;
        }

        try {
//            serverSocket = new ServerSocket(port, 0, ipAddresses.get(0));
            serverSocket = new ServerSocket(port);
        } catch (IOException ex) {
            ex.printStackTrace();
        }

        if (serverSocket == null) {
            Log.d(TAG, "unable to establish server");
            destroy();
            return;
        }

        String msg = String.format("server at %s listening on port %d", ipAddresses.get(0), port);
        Log.d(TAG, msg);

        run = true;
        done = false;
        handler.post(workerRunnable);
    }

    private Runnable workerRunnable = new Runnable() {
        @Override
        public void run() {
            Log.d(TAG, "welcome to the worker!");
            while(run) {
                SocketAddress socketAddress = serverSocket.getLocalSocketAddress();
                Log.d(TAG, String.format("%s waiting ...", socketAddress.toString()));
                Socket socket = null;
                InetAddress incomingInetAddress = null;
                try {
                    socket = serverSocket.accept();
                } catch (IOException ex) {
                    ex.printStackTrace();
                }

                if (socket == null) {
                    Log.d(TAG, "null socket. continue...");
                    continue;
                }

                incomingInetAddress = socket.getInetAddress();
                String msg = String.format("incoming ip/port = %s/%s", incomingInetAddress, socket.getPort());
                Log.d(TAG, msg);

                InputStream inputStream = null;
                try {
                    inputStream = socket.getInputStream();
                } catch (IOException ex) {
                    ex.printStackTrace();
                }

                int inputSize = 0;
                byte[] inputBuffer = new byte[1024];
                try {
                    int nAvailable = inputStream.available();
                    while (nAvailable != 0) {
                        int nRead = inputStream.read(inputBuffer, inputSize, nAvailable);
                        inputSize += nRead;
                        nAvailable = inputStream.available();
                    }
                } catch (IOException ex) {
                    Log.e(TAG, "IOException caught on socket", ex);
                }

                msg = String.format("%d bytes read from port", inputSize);
                Log.d(TAG, msg);

//                ServerTest serverTest = (ServerTest) activity;
                String incomingRawData = new String(inputBuffer, 0, inputSize);
                boolean sentHttpResponse = false; /* whether we should send out http response */
                if (listeners != null) {
                    for (ISimpleServerListener listener : listeners) {
                        sentHttpResponse = listener.processRawPacket(socket, incomingRawData, sentHttpResponse);
                    }
                }
                String[] incomingHttpFields = incomingRawData.split("\r\n\r\n");
                String requestedUrl = null;
                List<Pair<String, String>> keyValuePairs = new ArrayList<>();
                if (incomingHttpFields.length > 0) {
                    String[] httpFields = incomingHttpFields[0].split("\r\n");
                    String[] urlFields = httpFields[0].split(" ");
                    if (urlFields.length > 1) {
                        requestedUrl = urlFields[1];
                        msg = String.format("requesting page = [%s]", requestedUrl);
                        Log.d(TAG, msg);
                    }

                    if (listeners != null) {
                        for (ISimpleServerListener listener : listeners) {
                            sentHttpResponse = listener.processRawPacketHeader(socket, incomingHttpFields[0], sentHttpResponse);
                        }
                    }
                } else { /* something went wrong. mercy-kill and continue */
                    if (sentHttpResponse == false) {
                        sendMinimalHttpReply(socket);
                        sentHttpResponse = true;
                    }
                    continue;
                }

//                serverTest.displayText(incomingHttpFields[0]);
                if(incomingHttpFields.length == 2) {
                    if (listeners != null) {
                        for (ISimpleServerListener listener : listeners) {
                            sentHttpResponse = listener.processRawPacketData(socket, incomingHttpFields[1], sentHttpResponse);
                        }
                    }
                    String[] keyValuePairsRaw = incomingHttpFields[1].split("&");
                    for (String keyValuePairRaw : keyValuePairsRaw) {
                        String[] keyValueFields = keyValuePairRaw.split("=");
                        if (keyValueFields.length == 2) {
                            String key = keyValueFields[0];
                            String value = keyValueFields[1];
                            Pair<String, String> keyValuePair = new Pair<>(key, value);
                            keyValuePairs.add(keyValuePair);
                            msg = String.format("KEY=%s/VALUE=%s", key, value);
                            Log.d(TAG, msg);
                        }
                    }
                }

                if (listeners != null) { /* if someone is going to listen to requests */
                    for (ISimpleServerListener listener : listeners) {
                        sentHttpResponse = listener.processSimpleServerRequest(socket, incomingInetAddress, requestedUrl, keyValuePairs, sentHttpResponse);
                    }
                } else { /* noone is going to listen. mercy-kill */
                    if (sentHttpResponse == false) {
                        sendMinimalHttpReply(socket);
                        sentHttpResponse = true;
                    }
                }
            }
            done = true;
        }
    };

    public void destroy() {
        if (run) {
            run = false;
        }
        while(done == false) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ex) {
                ex.printStackTrace();
            }
            Log.d(TAG, "waiting for server to finish...");
        }

        Utils.goodbyeThread(thread);

        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException ex) {
                ex.printStackTrace();
            }

            Log.d(TAG, "socket closed");
        }
    }

    public static boolean sendMinimalHttpReply(Socket socket) {
        return sendMinimalHttpReply(socket, true);
    }

    public static boolean sendMinimalHttpReply(Socket socket, boolean closeSocket) {
        byte[] buffer = new byte[2];
        buffer[0] = '\n';
        buffer[1] = 0;
        String msg = "How do you do?\n";
        return sendMinimalHttpReply(socket, msg.getBytes(), closeSocket);
    }

    public static boolean sendMinimalHttpReply(Socket socket, byte[] buffer) {
        return sendMinimalHttpReply(socket, buffer, true);
    }

    public static boolean sendMinimalHttpReply(Socket socket, byte[] buffer, boolean closeSocket) {
        String reply = String.format("HTTP/1.1 200 OK\nServer: mamlambo_server/1.0\nContent-Length: %d\nConnection: close\nContent-Type: text/html\n\n", buffer.length);
        OutputStream outputStream = null;
        try {
            outputStream = socket.getOutputStream();
            outputStream.write(reply.getBytes());
            outputStream.write(buffer);
            outputStream.close();
        } catch (IOException ex) {
            ex.printStackTrace();
            return false;
        }
        if (closeSocket) {
            try {
                socket.close();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
        return true;
    }
}
