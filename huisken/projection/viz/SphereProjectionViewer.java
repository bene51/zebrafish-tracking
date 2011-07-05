package huisken.projection.viz;

import customnode.CustomTriangleMesh;
import customnode.WavefrontLoader;

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

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.File;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.vecmath.Point3f;
import javax.vecmath.Color3f;


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

	CustomContent readMesh(String objFile, String vertexDir) throws IOException {
		CustomTriangleMesh ctm = (CustomTriangleMesh)WavefrontLoader
				.load(objFile).get("Sphere");

		// indexify mesh
		Map<Point3f, Integer> vertexToIndex =
				new HashMap<Point3f, Integer>();

		List<Point3f> meshlist = ctm.getMesh();
		int nFaces = meshlist.size();
		int[] faces = new int[nFaces];
		List<Point3f> v = new ArrayList<Point3f>();

		for(int i = 0; i < nFaces; i++) {
			Point3f p = meshlist.get(i);

			if(!vertexToIndex.containsKey(p)) {
				Point3f newp = new Point3f(p);
				vertexToIndex.put(newp, v.size());
				v.add(newp);
			}
			faces[i] = vertexToIndex.get(p);
		}
		int nVertices = v.size();
		Point3f[] vertices = new Point3f[nVertices];
		v.toArray(vertices);


		return new CustomContent(vertices, faces, vertexDir);
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

