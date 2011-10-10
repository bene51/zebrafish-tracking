package huisken.projection;

import huisken.util.XMLReader;
import ij.IJ;
import ij.process.ImageProcessor;

import java.io.File;
import java.util.TreeSet;

public class NewTimelapseOpener extends Opener {

	private final int w, h, d;
	private double pw, ph, pd;
	private final String parentdir;
	private final boolean doublesided;
	private final int angleStart, angleInc, nAngles;
	private final int timepointStart, timepointInc, nTimepoints;

	public NewTimelapseOpener(String parentdir, boolean doublesided) {
		this.doublesided = doublesided;
		if(!doublesided)
			throw new UnsupportedOperationException("Only double-sided illumination is supported at the moment");

		if(!parentdir.endsWith(File.separator))
			parentdir += File.separator;
		this.parentdir = parentdir;
		File tmp = new File(parentdir);
		String xmlpath = new File(tmp.getParentFile(), tmp.getName() + ".xml").getAbsolutePath();
		XMLReader xml = null;
		try {
			xml = new XMLReader(xmlpath);
		} catch(Exception e) {
			throw new RuntimeException("Cannot parse xml file: " + xmlpath, e);
		}
		this.w = xml.width;
		this.h = xml.height;
		this.d = xml.depth;
		this.pw = xml.pw;
		this.ph = xml.ph;
		this.pd = xml.pd;

		TreeSet<Integer> samples    = new TreeSet<Integer>();
		TreeSet<Integer> timepoints = new TreeSet<Integer>();
		TreeSet<Integer> angles     = new TreeSet<Integer>();
		File[] files = new File(parentdir).listFiles();
		for(File file : files)
			if(file.getName().startsWith("s"))
				samples.add(Integer.parseInt(file.getName().substring(1)));
		// check hierarchy in first sample folder
		files = files[0].listFiles();
		for(File file : files)
			if(file.getName().startsWith("t"))
				timepoints.add(Integer.parseInt(file.getName().substring(1)));
		// go into first region folder and check angles
		files = new File(files[0], "r000").listFiles();
		for(File file : files)
			if(file.getName().startsWith("a"))
				angles.add(Integer.parseInt(file.getName().substring(1)));


		java.util.Iterator<Integer> angleIt = angles.iterator();
		java.util.Iterator<Integer> timepointsIt = timepoints.iterator();
		this.angleStart = angleIt.next();
		this.angleInc   = angleIt.next() - angleStart;
		this.nAngles    = angles.size();
		this.timepointStart = timepointsIt.next();
		this.timepointInc   = timepointsIt.next() - timepointStart;
		this.nTimepoints    = timepoints.size();
	}

	@Override
	public int getWidth() {
		return w;
	}

	@Override
	public int getHeight() {
		return h;
	}

	@Override
	public int getDepth() {
		return d;
	}

	@Override
	public int getAngleStart() {
		return angleStart;
	}

	@Override
	public int getAngleInc() {
		return angleInc;
	}

	@Override
	public int getNAngles() {
		return nAngles;
	}

	@Override
	public int getTimepointStart() {
		return timepointStart;
	}

	@Override
	public int getTimepointInc() {
		return timepointInc;
	}

	@Override
	public int getNTimepoints() {
		return nTimepoints;
	}

	@Override
	public double getPixelWidth() {
		return pw;
	}

	@Override
	public double getPixelHeight() {
		return ph;
	}

	@Override
	public double getPixelDepth() {
		return pd;
	}

	/*
	 * Sth like "s000/t00001/r000/a000/c001/z0000/plane_0000000000.dat"
	 */
	@Override
	public ImageProcessor openPlane(int timepoint, int angle, int plane, int illumination) {
		String file = parentdir + "s%03d/t%05d/r000/a%03d/c001/z0000/plane_%010d.dat";
		int s = (angle - angleStart) / angleInc;
		int p = doublesided ? 2 * plane + illumination : plane;
		String path = String.format(file, s, timepoint, angle, p);
		return IJ.openImage(path).getProcessor();
	}
}
