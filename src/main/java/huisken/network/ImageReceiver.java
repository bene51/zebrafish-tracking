package huisken.network;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.GenericDialog;
import ij.plugin.PlugIn;
import ij.process.ByteProcessor;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.util.zip.GZIPInputStream;

public class ImageReceiver implements PlugIn {

	private Socket socket;
	private PrintWriter out;
	private DataInputStream in;
	private ImagePlus image;

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
		in = new DataInputStream(socket.getInputStream());
	}
	
	private byte[] compressed;
	private MyByteArrayOutputStream decompressed;

	public ImagePlus getImage() throws Exception {
		out.println("getImage");
		int w = in.readInt();
		int h = in.readInt();
		int compressedLength = in.readInt();
		if(image == null) {
			compressed = new byte[w * h];
			decompressed = new MyByteArrayOutputStream(w * h);
			image = new ImagePlus("Received", new ByteProcessor(w, h, decompressed.getBuffer(), null));
		}
		decompressed.reset();

		int read = 0;
		while(read < compressedLength)
			read += in.read(compressed, read, compressedLength - read);
		decompress(compressed, compressedLength, decompressed);
		image.updateAndDraw();
		return image;
	}

	public void stop() throws Exception {
		out.println("close");
		socket.close();
		socket = null;
		in = null;
		out = null;
	}
	
	private static byte[] tmp = new byte[1024];
	private static void decompress(byte[] compressedBytes, int l, ByteArrayOutputStream decompressed) {
		try {
			GZIPInputStream zipIn = new GZIPInputStream(
					new ByteArrayInputStream(compressedBytes));

			int bytesRead;
			while((bytesRead = zipIn.read(tmp)) != -1) {
				decompressed.write(tmp, 0, bytesRead);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	private static class MyByteArrayOutputStream extends ByteArrayOutputStream {
		
		MyByteArrayOutputStream(int n) {
			super(n);
		}

		byte[] getBuffer() {
			return this.buf;
		}
	}

}
