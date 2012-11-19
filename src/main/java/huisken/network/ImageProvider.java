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

	public ImageProvider() {}

	public ImageProvider(ImagePlus image) {
		this.image = image;
	}

	public void run(int port) throws Exception {
		int w = image.getWidth();
		int h = image.getHeight();
		ServerSocket serverSocket = new ServerSocket(port);
		Socket clientSocket = serverSocket.accept();
		OutputStream out = clientSocket.getOutputStream();
		BufferedReader in = new BufferedReader(new InputStreamReader(
				clientSocket.getInputStream()));

		byte[] compressed = new byte[w * h];
		String inputLine;
		Deflater compresser = new Deflater();
		while ((inputLine = in.readLine()) != null) {
			if (inputLine.equals("close")) {
				break;
			}
			if (inputLine.equals("getImage")) {
				double mean = getImage().getProcessor().getStatistics().mean;
				System.out.println("writing image to socket: mean = " + mean);
				byte[] decompressed = (byte[])image.getProcessor().getPixels();
				compresser.reset();
				compresser.setInput(decompressed);
				int compressedLength = compresser.deflate(compressed);
				writeInt(out, w);
				writeInt(out, h);
				writeInt(out, compressedLength);
				out.write(compressed, 0, compressedLength);
				compresser.finish();
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
		return DOES_8G;
	}
}
