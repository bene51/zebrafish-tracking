package huisken.network;

import ij.IJ;
import ij.ImagePlus;
import ij.plugin.filter.PlugInFilter;
import ij.process.ImageProcessor;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.zip.GZIPOutputStream;

public class ImageProvider implements PlugInFilter {

	protected ImagePlus image;

	public void run(int port) throws Exception {
		ServerSocket serverSocket = new ServerSocket(port);
		Socket clientSocket = serverSocket.accept();
		DataOutputStream out = new DataOutputStream(new GZIPOutputStream(clientSocket.getOutputStream()));
		BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
		String inputLine;
		while ((inputLine = in.readLine()) != null) {
			if(inputLine.equals("close")) {
				break;
			}
			if(inputLine.equals("getImage")) {
				double mean = getImage().getProcessor().getStatistics().mean;
				System.out.println("writing image to socket: mean = " + mean);
				ImagePlus im = getImage();
				out.writeInt(im.getWidth());
				out.writeInt(im.getHeight());
				out.write((byte[])im.getProcessor().getPixels());
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
		if(n == IJ.CANCELED)
			return;
		try {
			run((int)n);
		} catch(Exception e) {
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
