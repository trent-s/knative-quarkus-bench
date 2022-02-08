package com.ibm.trl.funqyTest;

import java.util.HashMap;
import java.util.List;
import java.util.stream.Stream;

import com.ibm.trl.funqyTest.cloudevent.IBMCOS_HMAC;

import java.io.FileWriter;
import java.io.IOException;
import java.io.UnsupportedEncodingException;

import java.net.InetSocketAddress;
import java.net.DatagramSocket;
import java.net.DatagramPacket;
import java.net.SocketTimeoutException;
import java.net.SocketException;

import io.quarkus.funqy.Funq;

public class NetworkBenchmark {
    @Funq("Hello")
    public String helloOnly() {
        return "Hello!";
    }

    @Funq
    public String hello(String msg) {
        return "Hello " + msg;
    }

    @Funq
    public HashMap<String, String> network_benchmark(MySocket s) {
        HashMap<String, String> retVal = new HashMap<String, String>();
        String key = "filename_tmp";
    
        String request_id = s.getRequest_id();
        String address = s.getServer_address();
        int port = Integer.valueOf(s.getServer_port());
        int repetitions = Integer.valueOf(s.getRepetitions());
        String output_bucket = s.getOutput_bucket();

        List<Long[]> times = List.of();
        int i = 0;

        try {
            DatagramSocket socket = new DatagramSocket();
            socket.setSoTimeout(3000);
            socket.setReuseAddress(true);
            socket.bind(new InetSocketAddress("", 0));

            byte[] message = new byte[0];
            try {
                message = request_id.getBytes("UTF-8");
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }

            DatagramPacket packet = new DatagramPacket(
                message,
                message.length,
                new InetSocketAddress(address, port)
            );

            int consecutive_failures = 0;
            long send_begin = 0;
            long recv_end = 0;
            // Timestamp timestamp = new Timestamp(System.currentTimeMillis());

            while (i < repetitions + 1) {
                try {
                    send_begin = System.nanoTime();
                    socket.send(packet);
                    socket.receive(packet);
                    recv_end = System.nanoTime();
                } catch (SocketTimeoutException e) {
                    i += 1;
                    consecutive_failures += 1;
                    if (consecutive_failures == 5) {
                        System.out.println("Can't setup the connection");
                        break;
                    }
                    continue;
                } catch (IOException e) {
                    e.printStackTrace();
                }
                if (i > 0)
                    times.add( new Long[] { Long.valueOf(i), send_begin, recv_end } );

                i += 1;
                consecutive_failures = 0;
                socket.setSoTimeout(2000);
            }
            socket.close();

            if (consecutive_failures != 5) {
                try {
                    FileWriter writer = new FileWriter("data.csv");
                    String header = String.join(",", "id", "client_send", "client_rcv");
                    writer.append(header);
                    for (Long[] row : times) {
                        String[] strRow = Stream.of(row).map(r -> r.toString()).toArray(String[]::new);
                        writer.append(String.join(",", strRow));
                    }
                    writer.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }

                key = String.format("000/out/020-results-%s.csv", request_id);

                try {
                    final IBMCOS_HMAC cos = new IBMCOS_HMAC();
                    cos.uploadFile("trl-knative-benchmark-bucket", key, "data.csv");
                } catch (java.lang.Exception e) {
                    e.printStackTrace();
                }
            }
        } catch (SocketException e) {
            e.printStackTrace();
        }

        retVal.put( "result", key );
        return retVal;
    }
}