package huisken.projection.acquisition;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.Socket;

public class LabView {

	public static String read(String name) {
		try {
			Socket s = new Socket("10.1.199.6", 1235);
			new PrintStream(s.getOutputStream()).println(name);
			String l = "";
			StringBuffer buf = new StringBuffer();
			BufferedReader r = new BufferedReader(new InputStreamReader(s.getInputStream()));
			while((l = r.readLine()) != null)
				buf.append(l).append("\n");
			s.close();

			return buf.toString();
		} catch(Exception e) {
			throw new RuntimeException("Cannot read " + name, e);
		}
	}

	public static int readInt(String name) {
		return (int)readDouble(name);
	}

	public static double readDouble(String name) {
		return Double.parseDouble(read(name));
	}

	public static void main(String[] args) {
		System.out.println(read("Positions"));
	}
}
