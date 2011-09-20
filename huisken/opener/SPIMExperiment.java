package huisken.opener;

import ij.IJ;
import ij.ImagePlus;

import ij.process.Blitter;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;

import java.io.File;

import java.util.ArrayList;
import java.util.Arrays;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

public class SPIMExperiment {

	public final int sampleStart, sampleEnd;
	public final int timepointStart, timepointEnd;
	public final int regionStart, regionEnd;
	public final int angleStart, angleEnd;
	public final int channelStart, channelEnd;
	public final int planeStart, planeEnd;
	public final int frameStart, frameEnd;
	public final String pathFormatString;
	public final double pw, ph, pd;
	public final int w, h, d;
	public final String[] samples, regions, angles, channels;

	public static final int NO_PROJECTION = 0;
	public static final int MAX_PROJECTION = 1;
	public static final int MIN_PROJECTION = 2;

	public SPIMExperiment(String xmlfile) {
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
		int zMin       = getMin(planes);
		int zMax       = getMax(planes);
		int fMin       = getMin(frames);
		int fMax       = getMax(frames);

		if(frames[0].startsWith("plane_")) {
			pathFormatString = experimentFolder.getAbsolutePath() + File.separator +
				"s%03d/t%05d/r%03d/a%03d/c%03d/z0000/plane_%010d.dat";
			zMin = fMin;
			zMax = fMax;
			fMin = fMax = 0;
		} else {
			pathFormatString = experimentFolder.getAbsolutePath() + File.separator +
				"s%03d/t%05d/r%03d/a%03d/c%03d/z%04d/%010d.dat";
		}

		planeStart = zMin;
		planeEnd   = zMax;
		frameStart = fMin;
		frameEnd   = fMax;

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

	public ImagePlus open(int sample,
				int tpMin, int tpMax,
				int region,
				int angle,
				int channel,
				int zMin, int zMax,
				int fMin, int fMax,
				int projection,
				boolean virtual) {
		return open(sample, tpMin, tpMax, region, angle, channel, zMin, zMax, fMin, fMax, 0, h, 0, w, projection, virtual);
	}

	public ImagePlus open(int sample,
				int tpMin, int tpMax,
				int region,
				int angle,
				int channel,
				int zMin, int zMax,
				int fMin, int fMax,
				int yMin, int yMax,
				int xMin, int xMax,
				int projection,
				boolean virtual) {
		int nX          = xMax - xMin;
		int nY          = yMax - yMin;
		int nTimepoints = tpMax - tpMin + 1;
		int nPlanes     = zMax - zMin + 1;
		int nFrames     = fMax - fMin + 1;
		int nFiles      = nTimepoints * nPlanes * nFrames;
		int i = 0;

		SPIMStack stack = null;
		switch(projection) {
			case NO_PROJECTION:  stack = virtual ? new SPIMVirtualStack(w, h, xMin, xMax, yMin, yMax) : new SPIMRegularStack(w, h, xMin, xMax, yMin, yMax); break;
			case MAX_PROJECTION: stack = virtual ? new SPIM_ProjectedVirtualStack(w, h, xMin, xMax, yMin, yMax, Blitter.MAX) : new SPIM_ProjectedRegularStack(w, h, xMin, xMax, yMin, yMax, Blitter.MAX); break;
			case MIN_PROJECTION: stack = virtual ? new SPIM_ProjectedVirtualStack(w, h, xMin, xMax, yMin, yMax, Blitter.MIN) : new SPIM_ProjectedRegularStack(w, h, xMin, xMax, yMin, yMax, Blitter.MIN); break;
			default: throw new IllegalArgumentException("Unsupported projection type");
		}

		outer: for(int tp = tpMin; tp <= tpMax; tp++) {
			for(int p = zMin; p <= zMax; p++) {
				for(int f = fMin; f <= fMax; f++) {
					if(IJ.escapePressed()) {
						IJ.resetEscape();
						break outer;
					}
					String path = getPath(sample, tp, region, angle, channel, p, f);
					stack.addSlice(path, p == zMax);
					IJ.showProgress(i++, nFiles);
				}
			}
		}
		IJ.showProgress(1);
		ImagePlus ret = new ImagePlus("SPIM", stack);
		ret.getCalibration().pixelWidth = pw;
		ret.getCalibration().pixelWidth = ph;
		ret.getCalibration().pixelWidth = pd;
		return ret;
	}

	public static ImageProcessor openRaw(String path, int orgW, int orgH, int xMin, int xMax, int yMin, int yMax) {
		if(xMin == 0 && xMax == orgW && yMin == 0 && yMax == orgH)
			return openRaw(path, orgW, orgH);

		int ws = xMax - xMin;
		int hs = yMax - yMin;

		byte[] bytes = new byte[ws * hs * 2];
		short[] pixels = new short[ws * hs];

		FileInputStream in = null;
		try {
			in = new FileInputStream(path);

			// skip the top
			int toSkip = 2 * (yMin * orgW + xMin);
			while(toSkip > 0)
				toSkip -= in.skip(toSkip);

			// read through it line by line
			int offs = 0;
			for(int r = 0; r < hs; r++) {
				// read the data
				int read = 0;
				while(read < ws)
					read += in.read(bytes, offs + read, 2 * ws - read);
				offs += 2 * ws;

				// skip to next line
				toSkip = 2 * (orgW - xMax + xMin);
				while(toSkip > 0)
					toSkip -= in.skip(toSkip);
			}
			in.close();
		} catch(IOException e) {
			throw new RuntimeException("Cannot load " + path, e);
		}

		for(int i = 0; i < pixels.length; i++) {
			int low  = 0xff & bytes[2 * i];
			int high = 0xff & bytes[2 * i + 1];
			pixels[i] = (short)((high << 8) | low);
		}

		return new ShortProcessor(ws, hs, pixels, null);
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