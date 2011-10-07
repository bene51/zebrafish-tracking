package huisken.projection;

import huisken.util.XMLReader;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ImageProcessor;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.TreeSet;

public class OldTimelapseOpener extends Opener {

	private final String parentdir;
	
	private final int w, h, d, angleStart, angleInc, nAngles, timepointStart, timepointInc, nTimepoints;
	private final double pw, ph, pd;

	public OldTimelapseOpener(String parentdir, boolean doublesided) {
		if(!parentdir.endsWith(File.separator))
			parentdir += File.separator;
		this.parentdir = parentdir;
		String xmlpath = new File(new File(parentdir).getParentFile(), "recording_002_e0000.xml").getAbsolutePath();
		XMLReader xml = null;
		try {
			xml = new XMLReader(xmlpath);
		} catch(Exception e) {
			throw new RuntimeException("Cannot parse xml file: " + xmlpath, e);
		}
		this.w = xml.width;
		this.h = xml.height;
		this.d = doublesided ? 2 * xml.depth : xml.depth;
		this.pw = xml.pw;
		this.ph = xml.ph;
		this.pd = xml.pd;

		TreeSet<Integer> timepoints = new TreeSet<Integer>();
		TreeSet<Integer> angles = new TreeSet<Integer>();
		String[] files = new File(parentdir).list();
		for(String file : files) {
			if(!file.startsWith("tp"))
				continue;
			int tp = Integer.parseInt(file.substring(2, 6));
			int angle = Integer.parseInt(file.substring(18, 21));
			timepoints.add(tp);
			angles.add(angle);
		}

		java.util.Iterator<Integer> angleIt = angles.iterator();
		java.util.Iterator<Integer> timepointsIt = timepoints.iterator();
		this.angleStart = angleIt.next();
		this.angleInc   = angleIt.next() - angleStart;
		this.nAngles    = angles.size();
		this.timepointStart = timepointsIt.next();
		this.timepointInc   = timepointsIt.next() - timepointStart;
		this.nTimepoints    = timepoints.size();	
	}

	public void print() {
		System.out.println("parentdir = " + parentdir);
		System.out.println("w = " + getWidth());
		System.out.println("h = " + getHeight());
		System.out.println("d = " + getDepth());
		System.out.println("pw = " + getPixelWidth());
		System.out.println("ph = " + getPixelHeight());
		System.out.println("pd = " + getPixelDepth());
		System.out.println("angleStart = " + getAngleStart());
		System.out.println("angleInc = " + getAngleInc());
		System.out.println("nAngles = " + getNAngles());
		System.out.println("timepointStart = " + getTimepointStart());
		System.out.println("timepointInc = " + getTimepointInc());
		System.out.println("nTimepoints = " + getNTimepoints());
	}

	public ImagePlus openStack(int timepoint, int angle, int planeStart, int planeInc, int nPlanes) {
		if(nPlanes < 0)
			nPlanes = (getDepth() - planeStart) / planeInc;
	
		String folderPattern = "tp%04d_view%d_angle%03d/";
		String slicePattern = "0001_%04d.tif";

		int view = 1 + (angle - getAngleStart()) / getAngleInc();
		String folder = parentdir + String.format(folderPattern, timepoint, view, angle);
	
		ImageStack stack = new ImageStack(getWidth(), getHeight());

		for(int i = 0; i < nPlanes; i ++) {
			int plane = planeStart + i * planeInc;	
			String filename = String.format(slicePattern, plane);
			ImagePlus imp = IJ.openImage(folder + filename);
			if(imp == null)
				throw new RuntimeException("Cannot open " + (folder + filename));
			stack.addSlice(filename, imp.getProcessor());
			IJ.showProgress(i, nPlanes);
		}
		ImagePlus imp = new ImagePlus(folder, stack);
		imp.getCalibration().pixelWidth  = getPixelWidth();
		imp.getCalibration().pixelHeight = getPixelHeight();
		imp.getCalibration().pixelDepth  = getPixelDepth();
		return imp;
	}

	public void checkOpenable() {
		String folderPattern = "tp%04d_view%d_angle%03d/";
		String slicePattern = "0001_%04d.tif";

		Iterator it = iterator();
		while(it.next() != null) {
			String folder = parentdir + String.format(folderPattern, it.timepoint, it.view, it.angle);
			System.out.println(folder);

			for(int z = 0; z < getDepth(); z++) {
				int plane = z;
				String filename = String.format(slicePattern, plane);
				try {
					ImagePlus imp = IJ.openImage(folder + filename);
					if(imp == null) {
						IJ.log(folder + filename);
					}
				} catch(Exception e) {
					IJ.log(folder + filename);
				}	
			}
		}
	}

	public void correctNonOpenable(int inc) {
		String folderPattern = "tp%04d_view%d_angle%03d/";
		String slicePattern = "0001_%04d.tif";

		Iterator it = iterator();
		while(it.next() != null) {
			String folder = parentdir + String.format(folderPattern, it.timepoint, it.view, it.angle);

			for(int z = 0; z < getDepth(); z++) {
				int plane = z;
				String filename = String.format(slicePattern, plane);
				ImagePlus imp = null;
				try {
					imp = IJ.openImage(folder + filename);
				} catch(Exception e) {
					imp = null;
				}
				if(imp != null)
					continue;

				IJ.log(folder + filename);
				if(new File(folder, filename).renameTo(new File(folder, filename + ".orig"))) {
					if(plane >= inc)
						copyFile(new File(folder, String.format(slicePattern, plane - inc)), new File(folder, filename));
					else
						copyFile(new File(folder, String.format(slicePattern, plane + inc)), new File(folder, filename));
				} else {
					IJ.log("Could not rename " + (folder + filename));
				}
			}
		}
	}

	public void checkPixels() {
		String folderPattern = "tp%04d_view%d_angle%03d/";
		String slicePattern = "0001_%04d.tif";

		Iterator it = iterator();
		while(it.next() != null) {
			String folder = parentdir + String.format(folderPattern, it.timepoint, it.view, it.angle);

			for(int z = 0; z < getDepth(); z++) {
				int plane = z;
				String filename = String.format(slicePattern, plane);
				ImageProcessor ip = null;
				try {
					ip = IJ.openImage(folder + filename).getProcessor();
					double max = ip.getMax();
					if(max > 1 << 14)
						IJ.log(folder + filename + ": " + max);
				} catch(Exception e) {
					IJ.log("Cannot open " + folder + filename);
				}
			}
		}
	}
	
	public void correctPixels(int inc) {
		String folderPattern = "tp%04d_view%d_angle%03d/";
		String slicePattern = "0001_%04d.tif";

		Iterator it = iterator();
		while(it.next() != null) {
			String folder = parentdir + String.format(folderPattern, it.timepoint, it.view, it.angle);

			for(int z = 0; z < getDepth(); z++) {
				int plane = z;
				String filename = String.format(slicePattern, plane);
				String alternate = String.format(slicePattern, plane - inc);
				Correct.correct(folder + filename, folder + alternate, 1 << 15);
			}
		}
	}

	public Iterator iterator() {
		return new Iterator(getTimepointStart(), getTimepointInc(), getNTimepoints(),
				getAngleStart(), getAngleInc(), getNAngles());
	}

	// iterates outer: time and inner: angle
	public class Iterator implements java.util.Iterator<Iterator> {
		public final int t0, nt, tinc, a0, na, ainc;
		public int timepoint, angle, view;

		public Iterator(int t0, int tinc, int nt, int a0, int ainc, int na) {
			this.t0 = t0;
			this.nt = nt;
			this.tinc = tinc;
			this.a0 = a0;
			this.na = na;
			this.ainc = ainc;
			reset();
		}

		public void reset() {
			view = 0;
			angle = a0 - ainc;
			timepoint = t0;
		}

		public boolean hasNext() {
			return (angle + ainc) < (a0 + ainc * na) || (timepoint + tinc) < (t0 + tinc * nt);
		}

		public void remove() {
			throw new UnsupportedOperationException();
		}

		public Iterator next() {
			view++;
			angle += ainc;
			if(angle >= (a0 + ainc * na)) {
				angle = a0;
				view = 1;
				timepoint += tinc;
				if(timepoint >= (t0 + tinc * nt))
					return null;
			}
			return this;
		}
	}

	private static void copyFile(File in, File out) {
		try {
			FileInputStream fis = new FileInputStream(in);
			FileOutputStream fos = new FileOutputStream(out);
			int read = 0;
			byte[] buffer = new byte[1024];
			while((read = fis.read(buffer)) != -1) {
				fos.write(buffer, 0, read);
			}
			fis.close();
			fos.close();
		} catch(Exception e) {
			throw new RuntimeException("Error copying " + in, e);
		}
	}

	public static void main(String[] args) {
		String parentdir = "/Users/huiskenlab/Documents/SPIMdata/Jan/4view_sox17/recording_002_tif/";
		OldTimelapseOpener to = new OldTimelapseOpener(parentdir, true);
		to.print();

/*
		// open an example stack
		to.openStack(40, 0, 0, 2, -1).show();

		// check correct iteration
		Iterator it = to.iterator();
		while(it.next() != null)
			System.out.println("timepoint = " + it.timepoint + " angle = " + it.angle + " view = " + it.view);

		// check if all files can be opened
		to.checkOpenable();


		// check if all files have a correct pixel range
		to.checkPixels();
*/
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
}