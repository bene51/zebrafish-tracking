package huisken.projection.viz;

import fiji.util.gui.GenericDialogPlus;
import ij.IJ;
import ij.gui.GenericDialog;
import ij.io.OpenDialog;
import ij.io.SaveDialog;
import ij.plugin.LutLoader;
import ij.plugin.PlugIn;
import ij3d.Image3DUniverse;
import ij3d.behaviors.InteractiveBehavior;

import java.awt.TextField;
import java.awt.event.KeyEvent;
import java.awt.event.TextEvent;
import java.awt.event.TextListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.IndexColorModel;
import java.io.File;
import java.io.IOException;


public class SphereProjectionViewer implements PlugIn {
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

	public static Image3DUniverse show(String objfile, String vertexDir, String filenameContains) {

		// load mesh
		CustomContent content = null;
		try {
			content = readMesh(objfile, vertexDir, filenameContains);
		} catch(Exception e) {
			throw new RuntimeException("Cannot load " + objfile, e);
		}

		Image3DUniverse univ = new Image3DUniverse();
		univ.addInteractiveBehavior(new CustomBehavior(univ, content));
		univ.show();

		univ.addContent(content);

		return univ;
	}

	public static CustomContent readMesh(String objpath, String vertexDir, String filenameContains) throws IOException {
		return new CustomContent(objpath, vertexDir, filenameContains);
	}

	private static class CustomBehavior extends InteractiveBehavior {

		private final CustomContent cc;

		CustomBehavior(Image3DUniverse univ, CustomContent cc) {
			super(univ);
			this.cc = cc;
		}

		@Override
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
					@Override
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
					@Override
					public void textValueChanged(TextEvent e) {
						try {
							cc.setDisplayedMinimum(Integer.parseInt(minTF.getText()));
						} catch(NumberFormatException ex) {}
					}
				});
				maxTF.addTextListener(new TextListener() {
					@Override
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
			else if(e.isControlDown() && e.getKeyCode() == KeyEvent.VK_F) {
				double f = IJ.getNumber("Elevation factor", cc.getElevationFactor());
				if(f != IJ.CANCELED)
					cc.setElevationFactor((float)f);
			}
			else if(e.isControlDown() && e.getKeyCode() == KeyEvent.VK_M) {
				if(!cc.areMaximaShown()) {
					float th = cc.getMaximaThreshold();
					th = (float)IJ.getNumber("Threshold for detected maxima", th);
					if(th != IJ.CANCELED)
						cc.setMaximaThreshold(th);
				}
				cc.toggleShowMaxima();
			}
			else if(e.isControlDown() && e.getKeyCode() == KeyEvent.VK_N) {
				IJ.showMessage(cc.getCurrentFile());
			}
			else if(e.isControlDown() && e.getKeyCode() == KeyEvent.VK_T) {
				float angleFactor = (float)IJ.getNumber("Angle factor", 0.7);
				try {
					cc.scaleForAngle(angleFactor);
				} catch (IOException e1) {
					e1.printStackTrace();
				}
			}
			else if(e.isControlDown() && e.getKeyCode() == KeyEvent.VK_O) {
				cc.toggleShowAsColor();
			}
			else if(e.isControlDown() && e.getKeyCode() == KeyEvent.VK_E) {
				OpenDialog od = new OpenDialog("Open LUT", "luts", "elevation.lut");
				String path = new File(od.getDirectory(), od.getFileName()).getAbsolutePath();
				IndexColorModel cm = null;
				try {
					cm = LutLoader.open(path);
				} catch (IOException e1) {
					e1.printStackTrace();
				}
				byte[] r = new byte[256];
				byte[] g = new byte[256];
				byte[] b = new byte[256];
				cm.getReds(r);
				cm.getGreens(g);
				cm.getBlues(b);
				byte[][] lut = new byte[][] {r, g, b};
				cc.setLUT(lut);
			}
			else if(e.isControlDown() && e.getKeyCode() == KeyEvent.VK_P) {
				SaveDialog od = new SaveDialog("Save as PLY", "", ".ply");
				String path = new File(od.getDirectory(), od.getFileName()).getAbsolutePath();
				try {
					cc.exportToPLY(path);
				} catch(Exception ex) {
					ex.printStackTrace();
					IJ.error(ex.getMessage());
				}
			}
		}
	}
}
