package huisken.opener;

import ij.IJ;
import ij.ImagePlus;

import ij.process.ImageProcessor;
import ij.process.ShortProcessor;

import java.io.File;

import java.util.ArrayList;
import java.util.Arrays;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

public class Experiment {

	int sampleStart, sampleEnd;
	int timepointStart, timepointEnd;
	int regionStart, regionEnd;
	int angleStart, angleEnd;
	int channelStart, channelEnd;
	int planeStart, planeEnd;
	int frameStart, frameEnd;
	String pathFormatString;
	double pw, ph, pd;
	int w, h, d;
	String[] samples, regions, angles, channels;

	public Experiment(String xmlfile) {
		if(!xmlfile.endsWith(".xml"))
			throw new IllegalArgumentException("Please select an xml file");
		File experimentFolder = new File(xmlfile.substring(0, xmlfile.length() - 4));
		experimentFolder = new File(experimentFolder.getParent(), experimentFolder.getName());
		if(!experimentFolder.exists() || !experimentFolder.isDirectory())
			throw new IllegalArgumentException("No directory " + experimentFolder.getAbsolutePath());

		File tmp = new File(experimentFolder.getAbsolutePath());
		System.out.println(tmp.getAbsolutePath());
		samples             = filter(tmp.list(), "s\\d{3}?"); Arrays.sort(samples);    tmp = new File(tmp, samples[0]);
		String[] timepoints = filter(tmp.list(), "t\\d{5}?"); Arrays.sort(timepoints); tmp = new File(tmp, timepoints[0]);
		regions             = filter(tmp.list(), "r\\d{3}?"); Arrays.sort(regions);    tmp = new File(tmp, regions[0]);
		angles              = filter(tmp.list(), "a\\d{3}?"); Arrays.sort(angles);     tmp = new File(tmp, angles[0]);
		channels            = filter(tmp.list(), "c\\d{3}?"); Arrays.sort(channels);   tmp = new File(tmp, channels[0]);
		String[] planes     = filter(tmp.list(), "z\\d{4}?"); Arrays.sort(planes);     tmp = new File(tmp, planes[0]);
		String[] frames     = tmp.list();                     Arrays.sort(frames);

		sampleStart    = getMin(samples);
		sampleEnd      = getMax(samples);
		timepointStart = getMin(timepoints);
		timepointEnd   = getMax(timepoints);
		regionStart    = getMin(regions);
		regionEnd      = getMax(regions);
		angleStart     = getMin(angles);
		angleEnd       = getMax(angles);
		channelStart   = getMin(channels);
		channelEnd     = getMax(channels);
		planeStart     = getMin(planes);
		planeEnd       = getMax(planes);
		frameStart     = getMin(frames);
		frameEnd       = getMax(frames);

		if(frames[0].startsWith("plane_")) {
			pathFormatString = experimentFolder.getAbsolutePath() + File.separator +
				"s%03d/t%05d/r%03d/a%03d/c%03d/z0000d/plane_%10d.dat";
			planeStart = frameStart;
			planeEnd = frameEnd;
			frameStart = frameEnd = 0;
		} else {
			pathFormatString = experimentFolder.getAbsolutePath() + File.separator +
				"s%03d/t%05d/r%03d/a%03d/c%03d/z%04d/%010d.dat";
		}

		try {
			XMLReader xmlreader = new XMLReader(xmlfile);
			pw = xmlreader.pw;
			ph = xmlreader.ph;
			pd = xmlreader.pd;
			w = xmlreader.width;
			h = xmlreader.height;
			d = xmlreader.depth;
		} catch(Exception e) {
			throw new IllegalArgumentException("Error reading xml file: " + xmlfile, e);
		}
	}

	public String getPath(int sample, int timepoint, int region, int angle, int channel, int plane, int frame) {
		return String.format(pathFormatString, sample, timepoint, region, angle, channel, plane, frame);
	}

	public ImagePlus open(boolean virtual, boolean doZProjection) {
		int nTimepoints = timepointEnd - timepointStart + 1;
		int nPlanes     = planeEnd - planeStart + 1;
		int nFrames     = frameEnd - frameStart + 1;
		int nFiles      = nTimepoints * nPlanes * nFrames;
		int i = 0;

		SPIMStack stack = null;
		if(!doZProjection)
			stack = virtual ? new SPIMVirtualStack(w, h) : new SPIMRegularStack(w, h);
		else
			stack = virtual ? new SPIM_ProjectedVirtualStack(w, h) : new SPIM_ProjectedRegularStack(w, h);
		outer: for(int tp = timepointStart; tp <= timepointEnd; tp++) {
			for(int p = planeStart; p <= planeEnd; p++) {
				for(int f = frameStart; f <= frameEnd; f++) {
					if(IJ.escapePressed()) {
						IJ.resetEscape();
						break outer;
					}
					String path = getPath(sampleStart, tp, regionStart, angleStart, channelStart, p, f);
					stack.addSlice(path, p == planeEnd);
					IJ.showProgress(i++, nFiles);

				}
			}
		}
		IJ.showProgress(1);
		return new ImagePlus("SPIM", stack);
	}

	public static ImageProcessor openRaw(String path, int w, int h) {
		byte[] bytes = new byte[w * h * 2];
		short[] pixels = new short[w * h];

		FileInputStream in = null;
		try {
			in = new FileInputStream(path);
			int read = 0;
			while(read < bytes.length)
				read += in.read(bytes, read, bytes.length - read);
			in.close();
		} catch(IOException e) {
			throw new RuntimeException("Cannot load " + path, e);
		}

		for(int i = 0; i < pixels.length; i++) {
			int low  = 0xff & bytes[2 * i];
			int high = 0xff & bytes[2 * i + 1];
			pixels[i] = (short)((high << 8) | low);
		}

		return new ShortProcessor(w, h, pixels, null);
	}

	public static void saveRaw(ImageProcessor ip, String path) {
		short[] pixels = (short[])ip.getPixels();
		byte[] bytes = new byte[pixels.length * 2];

		for(int i = 0; i < pixels.length; i++) {
			short pixel = pixels[i];
			bytes[2 * i] = (byte)pixel;
			bytes[2 * i + 1] = (byte)(pixel >> 8);
		}

		try {
			FileOutputStream out = new FileOutputStream(path);
			out.write(bytes);
			out.close();
		} catch(IOException e) {
			throw new RuntimeException("Cannot save to " + path, e);
		}
	}

	private static String[] filter(String[] in, String pattern) {
		ArrayList<String> all = new ArrayList(in.length);
		for(String s : in)
			if(s.matches(pattern))
				all.add(s);
		String[] out = new String[all.size()];
		all.toArray(out);
		return out;
	}

	private static int getMin(String[] s) {
		int idx = 0;
		int start = s[idx].startsWith("plane_") ? 6 : 1;
		int stop = s[idx].indexOf('.') >= 0 ? s[idx].length() - 4 : s[idx].length();
		return Integer.parseInt(s[idx].substring(start, stop));
	}

	private static int getMax(String[] s) {
		int idx = s.length - 1;
		int start = s[idx].startsWith("plane_") ? 6 : 1;
		int stop = s[idx].indexOf('.') >= 0 ? s[idx].length() - 4 : s[idx].length();
		return Integer.parseInt(s[idx].substring(start, stop));
	}
}