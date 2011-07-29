package huisken.opener;

import ij.plugin.PlugIn;
import ij.io.OpenDialog;
import ij.IJ;

import ij.plugin.frame.Recorder;

public class SPIM_Opener implements PlugIn {

	public void run(String args) {
		OpenDialog od = new OpenDialog("Open experiment xml", "");
		String filename = od.getFileName();
		String directory = od.getDirectory();
		if(filename == null || directory == null)
			return;

		Experiment exp = null;
		int dimsWithMoreThanOne = 0;
		boolean zProject = false;
		boolean virtual = true;
		do {
			try {
				exp = new Experiment(directory + filename);
			} catch(Exception e) {
				IJ.error(e.getMessage());
				e.printStackTrace();
				return;
			}

			OpenerGenericDialog gd = new OpenerGenericDialog("Open SPIM experiment");
			gd.addChoice("Sample", exp.samples);
			gd.addDoubleSlider("Time points", exp.timepointStart, exp.timepointEnd);
			gd.addChoice("Region", exp.regions);
			gd.addChoice("Angle", exp.angles);
			gd.addChoice("Channel", exp.channels);
			gd.addDoubleSlider("Planes",  exp.planeStart, exp.planeEnd);
			gd.addDoubleSlider("Frames",  exp.frameStart, exp.frameEnd);
			gd.addCheckbox("Maximum projection", false);
			gd.addCheckbox("Use Virtual Stack", true);
			gd.showDialog();
			if(gd.wasCanceled())
				return;

			exp.sampleStart  = exp.sampleEnd  = Integer.parseInt(gd.getNextChoice().substring(1));
			exp.regionStart  = exp.regionEnd  = Integer.parseInt(gd.getNextChoice().substring(1));
			exp.angleStart   = exp.angleEnd   = Integer.parseInt(gd.getNextChoice().substring(1));
			exp.channelStart = exp.channelEnd = Integer.parseInt(gd.getNextChoice().substring(1));

			DoubleSlider slider = gd.getNextDoubleSlider();
			exp.timepointStart = slider.getCurrentMin();
			exp.timepointEnd   = slider.getCurrentMax();
			slider = gd.getNextDoubleSlider();
			exp.planeStart = slider.getCurrentMin();
			exp.planeEnd   = slider.getCurrentMax();
			slider = gd.getNextDoubleSlider();
			exp.frameStart = slider.getCurrentMin();
			exp.frameEnd   = slider.getCurrentMax();
			zProject = gd.getNextBoolean();
			virtual = gd.getNextBoolean();

			int nTimepoints = exp.timepointEnd - exp.timepointStart + 1;
			int nPlanes     = exp.planeEnd - exp.planeStart + 1;
			int nFrames     = exp.frameEnd - exp.frameStart + 1;

			if(zProject && (nFrames > 1 || nPlanes < 2)) {
				IJ.error("Maximum projection is only possible for non-movie stacks");
				zProject = false;
			}

			dimsWithMoreThanOne = 0;
			if(nTimepoints > 1)          dimsWithMoreThanOne++;
			if(!zProject && nPlanes > 1) dimsWithMoreThanOne++;
			if(nFrames > 1)              dimsWithMoreThanOne++;

			if(dimsWithMoreThanOne > 1)
				IJ.error("Only one dimension may contain more than one entry");
		} while(dimsWithMoreThanOne > 1);

		exp.open(virtual, zProject).show();

		String command = "call(\"huisken.opener.SPIM_Opener.open\",\n";
		command += "\t\"" + directory + filename + "\", // path to xml\n";
		command += "\t\"" + exp.sampleStart      + "\", // sample\n";
		command += "\t\"" + exp.timepointStart   + "\", // first timepoint\n";
		command += "\t\"" + exp.timepointEnd     + "\", // last timepoint\n";
		command += "\t\"" + exp.regionStart      + "\", // region\n";
		command += "\t\"" + exp.angleStart       + "\", // angle\n";
		command += "\t\"" + exp.channelStart     + "\", // channel\n";
		command += "\t\"" + exp.planeStart       + "\", // first plane\n";
		command += "\t\"" + exp.planeEnd         + "\", // last plane\n";
		command += "\t\"" + exp.frameStart       + "\", // first frame\n";
		command += "\t\"" + exp.frameEnd         + "\", // last frame\n";
		command += "\t\"" + zProject             + "\", // zProjection?\n";
		command += "\t\"" + virtual              + "\"); // virtual?";

		if(Recorder.record)
			Recorder.recordString(command);
	}

	public static void open(String xmlpath,
				String sample,
				String tpMin, String tpMax,
				String region,
				String angle,
				String channel,
				String zMin, String zMax,
				String fMin, String fMax,
				String projection,
				String virtual) {
		open(xmlpath,
			Integer.parseInt(sample),
			Integer.parseInt(tpMin),
			Integer.parseInt(tpMax),
			Integer.parseInt(region),
			Integer.parseInt(angle),
			Integer.parseInt(channel),
			Integer.parseInt(zMin),
			Integer.parseInt(zMax),
			Integer.parseInt(fMin),
			Integer.parseInt(fMax),
			Boolean.parseBoolean(projection),
			Boolean.parseBoolean(virtual));
	}

	public static void open(String xmlpath,
				int sample,
				int tpMin, int tpMax,
				int region,
				int angle,
				int channel,
				int zMin, int zMax,
				int fMin, int fMax,
				boolean projection,
				boolean virtual) {

		Experiment exp = null;
		try {
			exp = new Experiment(xmlpath);
		} catch(Exception e) {
			throw new RuntimeException("Cannot load experiment " + xmlpath, e);
		}
		exp.sampleStart  = exp.sampleEnd  = sample;
		exp.regionStart  = exp.regionEnd  = region;
		exp.angleStart   = exp.angleEnd   = angle;
		exp.channelStart = exp.channelEnd = channel;

		exp.timepointStart = tpMin;
		exp.timepointEnd   = tpMax;
		exp.planeStart = zMin;
		exp.planeEnd   = zMax;
		exp.frameStart = fMin;
		exp.frameEnd   = fMax;

		int nTimepoints = exp.timepointEnd - exp.timepointStart + 1;
		int nPlanes     = exp.planeEnd - exp.planeStart + 1;
		int nFrames     = exp.frameEnd - exp.frameStart + 1;

		if(projection && (nFrames > 1 || nPlanes < 2))
			throw new RuntimeException("Maximum projection is only possible for non-movie stacks");

		int dimsWithMoreThanOne = 0;
		if(nTimepoints > 1)            dimsWithMoreThanOne++;
		if(!projection && nPlanes > 1) dimsWithMoreThanOne++;
		if(nFrames > 1)                dimsWithMoreThanOne++;

		if(dimsWithMoreThanOne > 1)
			throw new RuntimeException("Only one dimension may contain more than one entry");

		exp.open(virtual, projection).show();
	}
}
