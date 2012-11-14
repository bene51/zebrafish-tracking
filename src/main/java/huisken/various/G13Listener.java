package huisken.various;

import ij.plugin.PlugIn;

import java.io.IOException;
import java.io.OutputStream;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

public class G13Listener {
	
	public static void main(String[] args) {
		
		try {
			start("localhost", 1234, args[0]);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static void start(String host, int port, String args) throws Exception {
		Socket socket = new Socket(InetAddress.getByName(host), port);
		OutputStream out = socket.getOutputStream();
		out.write((args + "\r\n").getBytes());
		out.flush();
		out.close();
		socket.close();
	}
}
