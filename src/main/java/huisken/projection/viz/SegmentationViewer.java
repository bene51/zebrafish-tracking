package huisken.projection.viz;

import fiji.util.gui.GenericDialogPlus;
import ij.IJ;
import ij.plugin.PlugIn;
import ij3d.Image3DUniverse;
import ij3d.ImageCanvas3D;
import ij3d.behaviors.InteractiveBehavior;

import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.io.File;

public class SegmentationViewer implements PlugIn {

	@Override
	public void run(String arg) {
		GenericDialogPlus gd = new GenericDialogPlus("Sphere Projection Viewer");
		gd.addDirectoryField("Data directory", "");
		gd.addStringField("File name contains", "");
		gd.showDialog();
		if(gd.wasCanceled())
			return;
		String dir = gd.getNextString();
		String pattern = gd.getNextString();

		String objfile = dir + File.separator + "Sphere.obj";
		if(dir == null || objfile == null)
			return;
		try {
			show(objfile, dir, pattern);
		} catch(Exception e) {
			IJ.error(e.getMessage());
			e.printStackTrace();
		}
	}

	public Image3DUniverse show(String objfile, String vertexDir, String filenameContains) {

		// load mesh
		CustomContent content = null;
		try {
			content = SphereProjectionViewer.readMesh(objfile, vertexDir, filenameContains);
		} catch(Exception e) {
			throw new RuntimeException("Cannot load " + objfile, e);
		}

		Image3DUniverse univ = new Image3DUniverse();
		univ.addInteractiveBehavior(new SphereProjectionViewer.CustomBehavior(univ, content));
		univ.addInteractiveBehavior(new CustomBehavior(univ, content));
		univ.show();

		univ.addContent(content);

		return univ;
	}

	protected static class CustomBehavior extends InteractiveBehavior {

		private final CustomContent cc;

		public CustomBehavior(Image3DUniverse univ, CustomContent cc) {
			super(univ);
			this.cc = cc;
		}

		@Override
		public void doProcess(KeyEvent e) {
			if(e.getID() != KeyEvent.KEY_PRESSED)
				return;
			ImageCanvas3D canvas = (ImageCanvas3D)univ.getCanvas();
			if(!canvas.isKeyDown(KeyEvent.VK_G))
				return;

			if(e.isControlDown() && e.getKeyCode() == KeyEvent.VK_S) {
				cc.smooth();
			}
		}

		@Override
		public void doProcess(MouseEvent e) {

		}
	}
}
