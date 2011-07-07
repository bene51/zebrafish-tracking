package huisken.projection.viz;

import fiji.util.gui.GenericDialogPlus;

import ij.IJ;

import ij.gui.GenericDialog;

import ij.plugin.PlugIn;

import ij3d.*;

import ij3d.behaviors.InteractiveBehavior;

import java.awt.Scrollbar;

import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.File;

import java.util.ArrayList;
import java.util.Scanner;

import javax.vecmath.Point3f;


public class SphereProjectionViewer implements PlugIn {
	public void run(String arg) {
		GenericDialogPlus gd = new GenericDialogPlus("Sphere Projection Viewer");
		gd.addDirectoryField("Data directory", "");
		gd.showDialog();
		if(gd.wasCanceled())
			return;
		String dir = gd.getNextString();
		
		String objfile = dir + File.separator + "Sphere.obj";
		if(dir == null || objfile == null)
			return;
		try {
			show(objfile, dir);
		} catch(Exception e) {
			IJ.error(e.getMessage());
			e.printStackTrace();
		}
	}

	public void show(String objfile, String vertexDir) {

		// load mesh
		CustomContent content = null;
		try {
			content = readMesh(objfile, vertexDir);
		} catch(Exception e) {
			throw new RuntimeException("Cannot load " + objfile, e);
		}

		Image3DUniverse univ = new Image3DUniverse();
		univ.addInteractiveBehavior(new CustomBehavior(univ, content));
		univ.show();

		univ.addContent(content);
	}

	public CustomContent readMesh(String objpath, String vertexDir) throws IOException {
		BufferedReader in = new BufferedReader(new FileReader(objpath));
		ArrayList<Point3f> points = new ArrayList<Point3f>();
		ArrayList<Integer> faces = new ArrayList<Integer>();
		String line = in.readLine();
		while(line != null && !line.startsWith("v "))
			line = in.readLine();

		while(line != null && line.startsWith("v ")) {
			Scanner s = new Scanner(line);
			s.next();
			points.add(new Point3f(s.nextFloat(), s.nextFloat(), s.nextFloat()));
			line = in.readLine();
		}

		while(line != null && !line.startsWith("f "))
			line = in.readLine();

		while(line != null && line.startsWith("f ")) {
			Scanner s = new Scanner(line);
			s.next();
			faces.add(s.nextInt());
			faces.add(s.nextInt());
			faces.add(s.nextInt());
			line = in.readLine();
		}

		Point3f[] vertices = new Point3f[points.size()];
		points.toArray(vertices);

		int[] f = new int[faces.size()];
		for(int i = 0; i < f.length; i++)
			f[i] = faces.get(i);
		return new CustomContent(vertices, f, vertexDir);
	}

	private static class CustomBehavior extends InteractiveBehavior {

		private CustomContent cc;
		
		CustomBehavior(Image3DUniverse univ, CustomContent cc) {
			super(univ);
			this.cc = cc;
		}
	
		public void doProcess(KeyEvent e) {
			if(e.isControlDown() && e.getKeyCode() == KeyEvent.VK_C) {
				final float oldMin = cc.getCurrentMinimum();
				final float oldMax = cc.getCurrentMaximum();
				final GenericDialog gd = new GenericDialog("Adjust contrast");
				gd.addSlider("Minimum", 0, 1 << 14, oldMin);
				gd.addSlider("Maximum", 0, 1 << 14, oldMax);
				gd.setModal(false);
				gd.addWindowListener(new WindowAdapter() {
					public void windowClosed(WindowEvent e) {
						if(gd.wasCanceled()) {
							System.out.println("closing");
							cc.setDisplayedMinimum(oldMin);
							cc.setDisplayedMaximum(oldMax);
						}
					}
				});
				final Scrollbar minChoice = (Scrollbar)gd.getSliders().get(0);
				final Scrollbar maxChoice = (Scrollbar)gd.getSliders().get(1);
				minChoice.addAdjustmentListener(new AdjustmentListener() {
					public void adjustmentValueChanged(AdjustmentEvent e) {
						System.out.println("setMinimum: " + minChoice.getValue());
						cc.setDisplayedMinimum(minChoice.getValue());
					}
				});
				maxChoice.addAdjustmentListener(new AdjustmentListener() {
					public void adjustmentValueChanged(AdjustmentEvent e) {
						System.out.println("setMaximum: " + maxChoice.getValue());
						cc.setDisplayedMaximum(maxChoice.getValue());
					}
				});
				gd.showDialog();
			}
		}
	}
}

