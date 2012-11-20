package huisken.network;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.GenericDialog;
import ij.plugin.PlugIn;
import ij.process.ByteProcessor;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.util.zip.Inflater;

public class ImageReceiver implements PlugIn {

	private Socket socket;
	private PrintWriter out;
	private byte[] compressed;
	private InputStream in;
	private ImagePlus image;
	private byte[] decompressed;
	private Inflater decompresser;

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
				if(image == null) {
					image = getImage();
					image.show();
				}
				else {
					// image.setProcessor(getImage().getProcessor());
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
		in = socket.getInputStream();
		decompresser = new Inflater();
	}

	private static final int readInt(InputStream in) throws IOException {
		int ch1 = in.read();
		int ch2 = in.read();
		int ch3 = in.read();
		int ch4 = in.read();
		if ((ch1 | ch2 | ch3 | ch4) < 0)
			throw new EOFException();
		return ((ch1 << 24) + (ch2 << 16) + (ch3 << 8) + (ch4 << 0));
	}

	public ImagePlus getImage() throws Exception {
		out.println("getImage");
		int w = readInt(in);
		int h = readInt(in);
		int compressedLength = readInt(in);
		if(image == null) {
			compressed = new byte[w * h];
			decompressed = new byte[w * h];
			image = new ImagePlus("Received", new ByteProcessor(w, h, decompressed, null));
		}

		int read = 0;
		while(read < compressedLength)
			read += in.read(compressed, read, compressedLength - read);

		decompresser.reset();
		decompresser.setInput(compressed);
		int n = decompresser.inflate(decompressed);
		System.out.println("inflate returned " + n);

		return image;
	}

	public void stop() throws Exception {
		out.println("close");
		socket.close();
		socket = null;
		in = null;
		out = null;
	}
}
