package huisken.network;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.GenericDialog;
import ij.plugin.PlugIn;
import ij.process.ByteProcessor;

import java.io.DataInputStream;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.util.zip.ZipInputStream;

public class ImageReceiver implements PlugIn {

	private Socket socket;
	private PrintWriter out;
	private DataInputStream in;
	private ZipInputStream zip;

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
		ImagePlus image = null;
		try {
			start(host, port);
			while(!IJ.escapePressed()) {
				Thread.sleep(50);
				if(image == null) {
					image = getImage();
					image.show();
				}
				else {
					image.setProcessor(getImage().getProcessor());
					image.updateAndDraw();
				}
			}
			stop();
		} catch(Exception e) {
			IJ.error(e.getMessage());
			e.printStackTrace();
		}
	}

	public void start(String host, int port) throws Exception {
		if(socket != null)
			throw new RuntimeException("Already running");
		socket = new Socket(InetAddress.getByName(host), port);
		out = new PrintWriter(socket.getOutputStream(), true);
		zip = new ZipInputStream(socket.getInputStream());
		in = new DataInputStream(zip);
	}

	public ImagePlus getImage() throws Exception {
		out.println("getImage");
		int w = in.readInt();
		int h = in.readInt();
		byte[] data = new byte[w * h];
		int read = 0;
		zip.getNextEntry();
		while(read < data.length)
			read += in.read(data, read, data.length - read);
		ByteProcessor ip = new ByteProcessor(w, h, data, null);
		double mean = ip.getStatistics().mean;
		System.out.println("reading image from socket mean = " + mean);
		return new ImagePlus("Received", ip);
	}

	public void stop() throws Exception {
		out.println("close");
		socket.close();
		socket = null;
		in = null;
		out = null;
	}
}
