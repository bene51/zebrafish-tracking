package huisken.network;

import ij.IJ;
import ij.ImagePlus;
import ij.plugin.filter.PlugInFilter;
import ij.process.ImageProcessor;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;

public class ImageProvider implements PlugInFilter {

	protected ImagePlus image;

	public ImageProvider() {}

	public ImageProvider(ImagePlus image) {
		this.image = image;
	}

	public void run(int port) throws Exception {
		int w = image.getWidth();
		int h = image.getHeight();
		byte[] header = new byte[8];
		header[0] = (byte)((w >> 24) & 0xff);
		header[1] = (byte)((w >> 16) & 0xff);
		header[2] = (byte)((w >>  8) & 0xff);
		header[3] = (byte)((w      ) & 0xff);
		header[4] = (byte)((w >> 24) & 0xff);
		header[5] = (byte)((w >> 16) & 0xff);
		header[6] = (byte)((w >>  8) & 0xff);
		header[7] = (byte)((w      ) & 0xff);
		ServerSocket serverSocket = new ServerSocket(port);
		Socket clientSocket = serverSocket.accept();
		CompressedBlockOutputStream out = new CompressedBlockOutputStream(
				clientSocket.getOutputStream(), w * h + 8);
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
				out.write(header);
				out.write(data);
				out.flush();
			}
		}
		System.out.println("Shutting down server");
		out.close();
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

	@Override
	public int setup(String arg, ImagePlus imp) {
		this.image = imp;
		return DOES_8G;
	}
}
