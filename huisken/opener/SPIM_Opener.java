package huisken.opener;

import ij.plugin.PlugIn;
import ij.io.OpenDialog;
import ij.IJ;

import ij.plugin.frame.Recorder;

import java.awt.Checkbox;
import java.awt.Choice;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import java.util.List;
import java.util.Vector;



public class SPIM_Opener implements PlugIn {

	public void run(String args) {
		OpenDialog od = new OpenDialog("Open experiment xml", "");
		final String filename = od.getFileName();
		final String directory = od.getDirectory();
		if(filename == null || directory == null)
			return;

		SPIMExperiment tmp = null;

		try {
			tmp = new SPIMExperiment(directory + filename);
		} catch(Exception e) {
			IJ.error(e.getMessage());
			e.printStackTrace();
			return;
		}

		final SPIMExperiment exp = tmp;

		final OpenerGenericDialog gd = new OpenerGenericDialog("Open SPIM experiment");
		gd.addChoice("Sample", exp.samples);
		gd.addDoubleSlider("Time points", exp.timepointStart, exp.timepointEnd);
		gd.addChoice("Region", exp.regions);
		gd.addChoice("Angle", exp.angles);
		gd.addChoice("Channel", exp.channels);
		gd.addDoubleSlider("Planes",  exp.planeStart, exp.planeEnd);
		gd.addDoubleSlider("Frames",  exp.frameStart, exp.frameEnd);
		String[] projMethods = new String[] {"None", "Maximum", "Minimum"};
		gd.addChoice("Projection Method", projMethods, "None");
		gd.addCheckbox("Use Virtual Stack", true);
		gd.setModal(false);
		gd.setActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				Vector choices = gd.getChoices();
				int sample   = Integer.parseInt(((Choice)choices.get(0)).getSelectedItem().substring(1));
				int region   = Integer.parseInt(((Choice)choices.get(1)).getSelectedItem().substring(1));
				int angle    = Integer.parseInt(((Choice)choices.get(2)).getSelectedItem().substring(1));
				int channel  = Integer.parseInt(((Choice)choices.get(3)).getSelectedItem().substring(1));
				int zProject = ((Choice)choices.get(4)).getSelectedIndex();

				List<DoubleSlider> sliders = gd.getDoubleSliders();
				DoubleSlider slider = sliders.get(0);
				int tpMin = slider.getCurrentMin();
				int tpMax = slider.getCurrentMax();
				slider = sliders.get(1);
				int zMin = slider.getCurrentMin();
				int zMax = slider.getCurrentMax();
				slider = sliders.get(2);
				int fMin = slider.getCurrentMin();
				int fMax = slider.getCurrentMax();

				Vector checkboxes = gd.getCheckboxes();
				boolean virtual = ((Checkbox)checkboxes.get(0)).getState();
	
				int nTimepoints = tpMax - tpMin + 1;
				int nPlanes     = zMax - zMin + 1;
				int nFrames     = fMax - fMin + 1;
	
				if(zProject != SPIMExperiment.NO_PROJECTION && (nFrames > 1 || nPlanes < 2)) {
					IJ.error("Maximum projection is only possible for non-movie stacks");
					return;
				}
	
				int dimsWithMoreThanOne = 0;
				if(nTimepoints > 1)          dimsWithMoreThanOne++;
				if(zProject == SPIMExperiment.NO_PROJECTION && nPlanes > 1) dimsWithMoreThanOne++;
				if(nFrames > 1)              dimsWithMoreThanOne++;
	
				if(dimsWithMoreThanOne > 1) {
					IJ.error("Only one dimension may contain more than one entry");
					return;
				}
		
				exp.open(sample, tpMin, tpMax, region, angle, channel, zMin, zMax, fMin, fMax, zProject, virtual).show();
		
				String command = "call(\"huisken.opener.SPIM_Opener.open\",\n";
				command += "\t\"" + directory + filename + "\",  // path to xml\n";
				command += "\t\"" + sample               + "\",  // sample\n";
				command += "\t\"" + tpMin                + "\",  // first timepoint\n";
				command += "\t\"" + tpMax                + "\",  // last timepoint\n";
				command += "\t\"" + region               + "\",  // region\n";
				command += "\t\"" + angle                + "\",  // angle\n";
				command += "\t\"" + channel              + "\",  // channel\n";
				command += "\t\"" + zMin                 + "\",  // first plane\n";
				command += "\t\"" + zMax                 + "\",  // last plane\n";
				command += "\t\"" + fMin                 + "\",  // first frame\n";
				command += "\t\"" + fMax                 + "\",  // last frame\n";
				command += "\t\"" + zProject             + "\",  // zProjection?\n";
				command += "\t\"" + virtual              + "\"); // virtual?";
		
				if(Recorder.record)
					Recorder.recordString(command);
			}
		});
		gd.showDialog();
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
			Integer.parseInt(projection),
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
				int projection,
				boolean virtual) {

		SPIMExperiment exp = null;
		try {
			exp = new SPIMExperiment(xmlpath);
		} catch(Exception e) {
			throw new RuntimeException("Cannot load experiment " + xmlpath, e);
		}

		int nTimepoints = tpMax - tpMin + 1;
		int nPlanes     = zMax - zMin + 1;
		int nFrames     = fMax - fMin + 1;

		if(projection != SPIMExperiment.NO_PROJECTION && (nFrames > 1 || nPlanes < 2))
			throw new RuntimeException("Maximum projection is only possible for non-movie stacks");

		int dimsWithMoreThanOne = 0;
		if(nTimepoints > 1)            dimsWithMoreThanOne++;
		if(projection == SPIMExperiment.NO_PROJECTION && nPlanes > 1) dimsWithMoreThanOne++;
		if(nFrames > 1)                dimsWithMoreThanOne++;

		if(dimsWithMoreThanOne > 1)
			throw new RuntimeException("Only one dimension may contain more than one entry");

		exp.open(sample, tpMin, tpMax, region, angle, channel, zMin, zMax, fMin, fMax, projection, virtual).show();
	}
}
