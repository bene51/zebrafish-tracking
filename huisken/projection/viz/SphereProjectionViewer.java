package huisken.projection.viz;

import fiji.util.gui.GenericDialogPlus;

import ij.IJ;

import ij.gui.GenericDialog;

import ij.plugin.PlugIn;

import ij3d.*;

import ij3d.behaviors.InteractiveBehavior;

import java.awt.TextField;

import java.awt.event.TextEvent;
import java.awt.event.TextListener;
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
		return new CustomContent(objpath, vertexDir);
	}

	private static class CustomBehavior extends InteractiveBehavior {

		private CustomContent cc;
		
		CustomBehavior(Image3DUniverse univ, CustomContent cc) {
			super(univ);
			this.cc = cc;
		}
	
		public void doProcess(KeyEvent e) {
			if(e.getID() != KeyEvent.KEY_PRESSED)
				return;

			if(e.isControlDown() && e.getKeyCode() == KeyEvent.VK_C) {
				final float oldMin = cc.getDisplayedMinimum();
				final float oldMax = cc.getDisplayedMaximum();
				final GenericDialog gd = new GenericDialog("Adjust contrast");
				gd.addSlider("Minimum", 0, 1 << 14, oldMin);
				gd.addSlider("Maximum", 0, 1 << 14, oldMax);
				gd.setModal(false);
				gd.addWindowListener(new WindowAdapter() {
					public void windowClosed(WindowEvent e) {
						if(gd.wasCanceled()) {
							cc.setDisplayedMinimum(oldMin);
							cc.setDisplayedMaximum(oldMax);
						}
					}
				});
				final TextField minTF = (TextField)gd.getNumericFields().get(0);
				final TextField maxTF = (TextField)gd.getNumericFields().get(1);
				minTF.addTextListener(new TextListener() {
					public void textValueChanged(TextEvent e) {
						try {
							cc.setDisplayedMinimum(Integer.parseInt(minTF.getText()));
						} catch(NumberFormatException ex) {}
					}
				});
				maxTF.addTextListener(new TextListener() {
					public void textValueChanged(TextEvent e) {
						try {
							cc.setDisplayedMaximum(Integer.parseInt(maxTF.getText()));
						} catch(NumberFormatException ex) {}
					}
				});

				gd.showDialog();
			}
			else if(e.isControlDown() && e.getKeyCode() == KeyEvent.VK_S) {
				cc.smooth();
			}
			else if(e.isControlDown() && e.getKeyCode() == KeyEvent.VK_M) {
				cc.toggleShowMaxima();
			}
		}
	}
}

