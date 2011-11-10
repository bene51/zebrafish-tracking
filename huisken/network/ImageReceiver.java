package huisken.network;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.GenericDialog;
import ij.plugin.PlugIn;

import java.io.ObjectInputStream;
import java.io.PrintWriter;
import java.net.Socket;

public class ImageReceiver implements PlugIn {

	private Socket socket;
	private PrintWriter out;
	private ObjectInputStream in;

	@Override
	public void run(String args) {
		GenericDialog gd = new GenericDialog("ImageReceiver");
		gd.addStringField("Host", "localhost");
		gd.addNumericField("Port", 4444, 0);
		gd.showDialog();
		if(gd.wasCanceled())
			return;
		String host = gd.getNextString();
		int port = (int)gd.getNextNumber();
		try {
			start(host, port);
			getImage().show();
			stop();
		} catch(Exception e) {
			IJ.error(e.getMessage());
			e.printStackTrace();
		}
	}

	public void start(String host, int port) throws Exception {
		if(socket != null)
			throw new RuntimeException("Already running");
		socket = new Socket(host, port);
		out = new PrintWriter(socket.getOutputStream(), true);
		in = new ObjectInputStream(socket.getInputStream());
	}

	public ImagePlus getImage() throws Exception {
		out.println("getImage");
		return ((ImageWrapper)in.readObject()).getImage();
	}

	public void stop() throws Exception {
		out.println("close");
		socket.close();
		socket = null;
		in = null;
		out = null;
	}
}
