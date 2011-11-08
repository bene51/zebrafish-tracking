package huisken.various;

import fiji.util.gui.GenericDialogPlus;
import ij.IJ;
import ij.ImagePlus;
import ij.plugin.PlugIn;
import ij.process.ColorProcessor;
import ij.process.ShortProcessor;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public class Dynamic_Color_Merge implements PlugIn {

	public static final int COUNT = 100;
	public static final String FORMAT = "%03d.dat";

	@Override
	public void run(String arg) {
		GenericDialogPlus gd = new GenericDialogPlus("Select a directory for each channel");
		gd.addDirectoryField("Green", "");
		gd.addDirectoryField("Red", "");
		gd.addNumericField("Width", 0, 0);
		gd.addNumericField("Height", 0, 0);
		gd.addNumericField("Min", 0, 0);
		gd.addNumericField("Max", 0, 0);
		gd.showDialog();
		if(gd.wasCanceled())
			return;

		File green = new File(gd.getNextString());
		File red = new File(gd.getNextString());
		int w = (int)gd.getNextNumber();
		int h = (int)gd.getNextNumber();
		int min = (int)gd.getNextNumber();
		int max = (int)gd.getNextNumber();


		dynamicMerge(green, red, w, h, min, max);
	}

	public void dynamicMerge(File gDir, File rDir, int w, int h, int min, int max) {
		if(!gDir.exists())
			gDir.mkdirs();
		if(!rDir.exists())
			rDir.mkdirs();
		if(!gDir.isDirectory())
			throw new IllegalArgumentException(gDir + " must be a directory");
		if(!rDir.isDirectory())
			throw new IllegalArgumentException(rDir + " must be a directory");

		ShortProcessor rIP = new ShortProcessor(w, h);
		ShortProcessor gIP = new ShortProcessor(w, h);
		ColorProcessor mIP = new ColorProcessor(w, h);

		byte[] bytes = new byte[2 * w * h];

		ImagePlus imp = new ImagePlus("Merge", mIP);
		imp.show();

		int i = 0;
		while(!IJ.escapePressed()) {
			try {
				Thread.sleep(50);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			String filename = String.format(FORMAT, i);
			File rNext = new File(rDir, filename);
			File gNext = new File(gDir, filename);
			if(!rNext.exists() || !gNext.exists())
				continue;
			load(rNext, rIP, bytes);
			load(gNext, gIP, bytes);
			if(!rNext.delete())
				System.out.println("Could not delete " + rNext);
			if(gNext.delete())
				System.out.println("Could not delete " + gNext);
			merge(rIP, gIP, mIP, min, max);
			imp.updateAndDraw();
			i = ++i / COUNT;
		}
		IJ.resetEscape();
	}

	public static void merge(ShortProcessor r, ShortProcessor g, ColorProcessor merged, int min, int max) {
		int w = r.getWidth();
		int h = r.getHeight();
		int wh = w * h;
		double scale = 256.0 / (max - min + 1);
		for(int i = 0; i < wh; i++) {
			int red   = r.get(i);
			red = (int)(red * scale + 0.5);
			int green = g.get(i);
			green = (int)(green * scale + 0.5);

			int merge = 0xff000000 + (red << 16) + (green << 8);
			merged.set(i, merge);
		}
	}

	public static void load(File f, ShortProcessor ip, byte[] tmp) {
		short[] pixels = (short[])ip.getPixels();
		FileInputStream in = null;
		try {
			in = new FileInputStream(f);
			int read = 0;
			while(read < tmp.length)
				read += in.read(tmp, read, tmp.length - read);
			in.close();
		} catch(IOException e) {
			throw new RuntimeException("Cannot load " + f, e);
		}

		for(int i = 0; i < pixels.length; i++) {
			int low  = 0xff & tmp[2 * i];
			int high = 0xff & tmp[2 * i + 1];
			pixels[i] = (short)((high << 8) | low);
		}
	}
}
