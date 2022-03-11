package com.ibm.trl.serverlessbench;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.DatagramSocket;
import java.net.DatagramPacket;
import java.net.SocketTimeoutException;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Stream;

import javax.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.quarkus.funqy.Funq;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.core.sync.RequestBody;


public class Network {
    @Inject
    S3Client s3;

    @ConfigProperty(name = "serverlessbench.network.output_bucket")
    String output_bucket;

    @Funq
    public HashMap<String, String> network(Param s) {
        HashMap<String, String> retVal = new HashMap<String, String>();
        String key = "filename_tmp";
    
        String request_id = s.getRequest_id();
        String address = s.getServer_address();
        int port = Integer.valueOf(s.getServer_port());
        int repetitions = Integer.valueOf(s.getRepetitions());
	boolean skipUploading = s.getSkipUploading();

        List<Long[]> times = new ArrayList<Long[]>();
        int i = 0;

        try {
            DatagramSocket sendSocket = new DatagramSocket(null);
            sendSocket.setSoTimeout(3000);
            sendSocket.setReuseAddress(true);
            sendSocket.bind(new InetSocketAddress("", 0));

            DatagramSocket recvSocket = new DatagramSocket(port);

            byte[] message = new byte[0];
            try {
                message = request_id.getBytes("UTF-8");
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }

            DatagramPacket packet = new DatagramPacket(message, message.length, new InetSocketAddress(address, port));
            DatagramPacket packet2 = new DatagramPacket(message, message.length);

            int consecutive_failures = 0;
            long send_begin = 0;
            long recv_end = 0;

            while (i < repetitions + 1) {
                try {
                    send_begin = System.nanoTime();
                    sendSocket.send(packet);
                    recvSocket.receive(packet2);
                    recv_end = System.nanoTime();
                } catch (SocketTimeoutException e) {
                    ++i;
                    ++consecutive_failures;
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

                ++i;
                consecutive_failures = 0;
                sendSocket.setSoTimeout(2000);
            }
            sendSocket.close();
            recvSocket.close();

            if (consecutive_failures != 5 && !skipUploading) {
                File upload_file = new File("/tmp/data.csv");
                try {
                    FileWriter writer = new FileWriter("/tmp/data.csv");
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

                key = String.format("network-benchmark-results-%s.csv", request_id);

                try {
                    PutObjectRequest objectRequest = PutObjectRequest.builder().bucket(output_bucket).key(key).build();
                    s3.putObject(objectRequest, RequestBody.fromFile(new File("/tmp/data.csv").toPath()));
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

    public static class Param {
        String request_id;
        String server_address;
        int server_port;
        int repetitions;
        String output_bucket;
        String income_timestamp;
	boolean skipUploading;

        public String getRequest_id() { return request_id; }
        public void setRequest_id(String request_id) { this.request_id = request_id; }
        public String getServer_address() { return server_address; }
        public void setServer_address(String server_address) { this.server_address = server_address; }
        public int getServer_port() { return server_port; }
        public void setServer_port(int server_port) { this.server_port = server_port; }
        public int getRepetitions() { return repetitions; }
        public void setRepetitions(int repetitions) { this.repetitions = repetitions; }
        public String getOutput_bucket() { return output_bucket; }
        public void setOutput_bucket(String output_bucket) { this.output_bucket = output_bucket; }
        public String getIncome_timestamp() { return income_timestamp; }
        public void setIncome_timestamp(String income_timestamp) { this.income_timestamp = income_timestamp; }
	public boolean getSkipUploading() { return skipUploading; }
	public void setSkipUploading(boolean skipUploading) { this.skipUploading = skipUploading; }
    }
}