package huisken.network;

import ij.IJ;
import ij.ImagePlus;
import ij.plugin.filter.PlugInFilter;
import ij.process.ImageProcessor;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.zip.Deflater;

public class ImageProvider implements PlugInFilter {

	protected ImagePlus image;
	protected int w, h;
	protected byte[] compressed;
	protected int compressedLength;
	protected Deflater compresser;

	public ImageProvider() {}

	public ImageProvider(ImagePlus image) {
		setup("", image);
	}

	public void init() {
		compresser = new Deflater();
		w = image.getWidth();
		h = image.getHeight();
		compressed = new byte[w * h];
	}

	public void run(int port) throws Exception {
		int w = image.getWidth();
		int h = image.getHeight();
		ServerSocket serverSocket = new ServerSocket(port);
		Socket clientSocket = serverSocket.accept();
		OutputStream out = clientSocket.getOutputStream();
		BufferedReader in = new BufferedReader(new InputStreamReader(
				clientSocket.getInputStream()));

		String inputLine;
		while ((inputLine = in.readLine()) != null) {
			if (inputLine.equals("close")) {
				break;
			}
			if (inputLine.equals("getImage")) {
				writeInt(out, w);
				writeInt(out, h);
				writeInt(out, compressedLength);
				out.write(compressed, 0, compressedLength);
				out.flush();
			}
		}
		System.out.println("Shutting down server");
		out.close();
		clientSocket.close();
	}

	private static void writeInt(OutputStream out, int i) throws IOException {
		out.write((i >> 24) & 0xFF);
		out.write((i >> 16) & 0xFF);
		out.write((i >> 8) & 0xFF);
		out.write((i >> 0) & 0xFF);
	}

	public synchronized void setImage(ImagePlus image) {
		this.image = image;
		byte[] decompressed = (byte[])image.getProcessor().getPixels();
		compresser.reset();
		compresser.setInput(decompressed);
		compresser.finish();
		compressedLength = compresser.deflate(compressed);
	}

	public synchronized ImagePlus getImage() {
		return image;
	}

	@Override
	public void run(ImageProcessor arg0) {
		double n = IJ.getNumber("Port", 4444);
		if (n == IJ.CANCELED)
			return;
		try {
			run((int) n);
		} catch (Exception e) {
			IJ.error(e.getMessage());
			e.printStackTrace();
		}
	}

	@Override
	public int setup(String arg, ImagePlus imp) {
		this.image = imp;
		init();
		return DOES_8G;
	}
}
