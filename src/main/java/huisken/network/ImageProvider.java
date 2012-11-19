package huisken.network;

import ij.IJ;
import ij.ImagePlus;
import ij.plugin.filter.PlugInFilter;
import ij.process.ImageProcessor;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.zip.GZIPOutputStream;

public class ImageProvider implements PlugInFilter {

	protected ImagePlus image;
	private ByteArrayOutputStream intermediate;

	public ImageProvider() {}

	public ImageProvider(ImagePlus image) {
		this.image = image;
	}

	public void run(int port) throws Exception {
		intermediate = new ByteArrayOutputStream(((byte[])image.getProcessor().getPixels()).length);
		ServerSocket serverSocket = new ServerSocket(port);
		Socket clientSocket = serverSocket.accept();
		DataOutputStream out = new DataOutputStream(
				clientSocket.getOutputStream());
		BufferedReader in = new BufferedReader(new InputStreamReader(
				clientSocket.getInputStream()));
		String inputLine;
		while ((inputLine = in.readLine()) != null) {
			if (inputLine.equals("close")) {
				break;
			}
			if (inputLine.equals("getImage")) {
				double mean = getImage().getProcessor().getStatistics().mean;
				System.out.println("writing image to socket: mean = " + mean);
				byte[] data = (byte[])image.getProcessor().getPixels();
				intermediate.reset();
				compress(data, intermediate);
				out.writeInt(image.getWidth());
				out.writeInt(image.getHeight());
				out.writeInt(intermediate.size());
				intermediate.writeTo(out);
				out.flush();
			}
		}
		System.out.println("Shutting down server");
		clientSocket.close();
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

	private static void compress(byte[] data, ByteArrayOutputStream out) {
		try {
			GZIPOutputStream zipOut = new GZIPOutputStream(out);
			zipOut.write(data);
			zipOut.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	public int setup(String arg, ImagePlus imp) {
		this.image = imp;
		return DOES_8G;
	}
}
