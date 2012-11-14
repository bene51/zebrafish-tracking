package huisken.projection.acquisition;

import fiji.util.gui.GenericDialogPlus;
import huisken.projection.processing.Fit_Sphere;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.io.FileInfo;
import ij.io.FileOpener;
import ij.plugin.FolderOpener;
import ij.plugin.PlugIn;
import ij.plugin.filter.GaussianBlur;
import ij.plugin.filter.RankFilters;
import ij.process.Blitter;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;

import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Properties;

import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.vecmath.Point3f;

import neo.AT;
import neo.BaseCameraApplication;
import vib.NaiveResampler;

public class FitSphereToTransmission implements PlugIn {

	private static final String DATA_DIR = "D:\\SPIMdata\\";
	private static final double PW = 0.65;

	@Override
	public void run(String arg) {
		new CameraApp();
	}

	@SuppressWarnings("serial")
	private final class CameraApp extends BaseCameraApplication {

		private JButton spool;
		private JTextField frameTF;

		@Override
		public JPanel getPanel() {
			frameTF = new JTextField("420");
			frameTF.setColumns(6);

			spool = new JButton("Go");
			spool.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					new Thread() {
						@Override
						public void run() {
							go();
						}
					}.start();
				}
			});

			// Initialize the GUI
			JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
			buttons.add(frameTF);
			buttons.add(spool);
			buttons.setName("Transmission");
			return buttons;
		}

		void go() {
			String date = new java.text.SimpleDateFormat("yyyyMMdd" + File.separator + "HH-mm").format(new java.util.Date());
			File dir = new File(DATA_DIR + date);
			if(!dir.exists())
				dir.mkdirs();
			try {
				FileOutputStream fos = new FileOutputStream(new File(dir, "camera.xml"));
				getCameraSettings().storeToXML(fos, "Andor sCMOS settings");
				fos.close();
			} catch (IOException e) {
				e.printStackTrace();
			}

			int nFrames = Integer.parseInt(frameTF.getText());
			AT at = getAT();
			at.AT_Flush();
			at.AT_SetEnumString("CycleMode", "Fixed");
			at.AT_CreateBuffers();
			at.AT_SetInt("FrameCount", nFrames);
			int w = at.AT_GetInt("AOIWidth");
			int h = at.AT_GetInt("AOIHeight");
			ImageStack stack = new ImageStack(w, h);
			at.AT_Command("AcquisitionStart");
			for(int f = 0; f < nFrames; f++) {
				short[] data = new short[w * h];
				at.AT_NextFrame(data);
				stack.addSlice("", new ShortProcessor(w, h, data, null));
			}
			at.AT_Command("AcquisitionStop");

			double pd = -1;
			try {
				pd = LabView.readDouble("z spacing") * 1000;
			} catch(Exception e) {
				e.printStackTrace();
				pd = IJ.getNumber("z spacing", 1);
			}

			ImagePlus imp = new ImagePlus("", stack);
			imp.getCalibration().pixelWidth = PW;
			imp.getCalibration().pixelHeight = PW;
			imp.getCalibration().pixelDepth = pd;

			try {
				doit(imp, dir);
			} catch (IOException e) {
				IJ.handleException(e);
			}
		}
	}

	private File getDefaultDir() {
		File defaultdir = new File(DATA_DIR);
		File[] tmp = defaultdir.listFiles();
		if(tmp != null && tmp.length != 0) {
			Arrays.sort(tmp);
			defaultdir = tmp[tmp.length - 1];
		}
		tmp = defaultdir.listFiles();
		if(tmp != null && tmp.length != 0) {
			Arrays.sort(tmp);
			defaultdir = tmp[tmp.length - 1];
		}
		return defaultdir;
	}

	public void doit() throws IOException {

		File folder = getDefaultDir();
		double dz = 0;
		try{
			dz = LabView.readDouble("z spacing") * 1000;
			System.out.println("dz = " + dz);
		} catch(Exception err) {
			err.printStackTrace();
		}


		GenericDialogPlus gd = new GenericDialogPlus("");
		gd.addDirectoryField("Directory", folder.getAbsolutePath());
		gd.addNumericField("z spacing", dz, 5);
		gd.showDialog();
		if(gd.wasCanceled())
			return;

		folder = new File(gd.getNextString());
		double pw = PW;
		double pd = gd.getNextNumber();

		Properties properties = new Properties();
		properties.loadFromXML(new FileInputStream(new File(folder, "camera.xml")));
		int w = Integer.parseInt(properties.getProperty("AOIWidth"));
		int h = Integer.parseInt(properties.getProperty("AOIHeight"));
		System.out.println(w + "x" + h);

		ImagePlus imp = openRaw(new File(folder, "data"), w, h);
		imp.getCalibration().pixelWidth = pw;
		imp.getCalibration().pixelHeight = pw;
		imp.getCalibration().pixelDepth = pd;
		doit(imp, folder);
	}

	public void doit(ImagePlus imp, File folder) throws IOException {
		int w = imp.getWidth();
		int h = imp.getHeight();
		int d = imp.getStackSize();
		double pw = imp.getCalibration().pixelWidth;
		double ph = imp.getCalibration().pixelHeight;
		double pd = imp.getCalibration().pixelDepth;
		imp = NaiveResampler.resample(imp, 4, 4, 1);

		GaussianBlur gauss = new GaussianBlur();
		RankFilters rank = new RankFilters();
		double min = Double.MAX_VALUE;
		double max = 0;
		for(int z = 0; z < imp.getStackSize(); z++) {
			ImageProcessor ip = imp.getStack().getProcessor(z + 1);
			ImageProcessor blurred = ip.duplicate();
			gauss.blurGaussian(blurred, 1, 1, 0.01);
			ip.copyBits(blurred, 0, 0, Blitter.DIFFERENCE);
			rank.rank(ip, 1, RankFilters.MEDIAN);
			ip.resetMinAndMax();
			if (ip.getMin()<min) min = ip.getMin();
			if (ip.getMax()>max) max = ip.getMax();
		}
		imp.getProcessor().setMinAndMax(min, max);
		imp.show();

		Fit_Sphere fs = new Fit_Sphere(imp);
		double threshold = IJ.getNumber("Threshold", 15);
		if(threshold == IJ.CANCELED)
			return;
		fs.fitMixture(threshold);
		fs.getControlImage().show();

		Point3f center = new Point3f();
		fs.getCenter(center);
		double radius = fs.getRadius();
		IJ.log("radius = " + radius);

		Properties props = new Properties();
		props.setProperty("w", Integer.toString(w));
		props.setProperty("h", Integer.toString(h));
		props.setProperty("d", Integer.toString(d));
		props.setProperty("pw", Double.toString(pw));
		props.setProperty("ph", Double.toString(ph));
		props.setProperty("pd", Double.toString(pd));
		props.setProperty("centerx", Float.toString(center.x));
		props.setProperty("centery", Float.toString(center.y));
		props.setProperty("centerz", Float.toString(center.z));
		props.setProperty("radius", Double.toString(radius));

		props.storeToXML(new FileOutputStream(new File(folder, "SMP.xml")), "SphericalMaximumProjection");

		String otherfolder = folder.getAbsolutePath().replaceAll("\\\\", "/").replace("D:/SPIMdata", "U:");
		File file = new File(otherfolder);
		if(!file.exists())
			file.mkdirs();
		props.storeToXML(new FileOutputStream(new File(otherfolder, "SMP.xml")), "SphericalMaximumProjection");
	}

	/** Opens all the images in the directory. */
	private ImagePlus openRaw(File dir, int w, int h) {
		String[] list = dir.list();

		// String imageType = types[choiceSelection];
		FileInfo fi = new FileInfo();
		fi.fileFormat = FileInfo.RAW;
		fi.fileName = "";
		fi.directory = dir.getAbsolutePath();
		fi.width = w;
		fi.height = h;
		fi.longOffset = 0;
		fi.offset = 0;
		fi.nImages = 1;
		fi.gapBetweenImages = 0;
		fi.intelByteOrder = true;
		fi.whiteIsZero = false;
		fi.fileType = FileInfo.GRAY16_UNSIGNED;

		//StringSorter.sort(list);
		FolderOpener fo = new FolderOpener();
		list = fo.trimFileList(list);
		list = fo.sortFileList(list);
		if (list==null) return null;
		ImageStack stack=null;
		ImagePlus imp=null;
		double min = Double.MAX_VALUE;
		double max = -Double.MAX_VALUE;
		for (int i=0; i<list.length; i++) {
			if (list[i].startsWith("."))
				continue;
			fi.fileName = list[i];
			imp = new FileOpener(fi).open(false);
			if (imp==null)
				IJ.log(list[i] + ": unable to open");
			else {
				if (stack==null)
					stack = imp.createEmptyStack();
				try {
					ImageProcessor ip = imp.getProcessor();
					if (ip.getMin()<min) min = ip.getMin();
					if (ip.getMax()>max) max = ip.getMax();
					stack.addSlice(list[i], ip);
				} catch(OutOfMemoryError e) {
					IJ.outOfMemory("OpenAll");
					stack.trim();
					break;
				}
				IJ.showStatus((stack.getSize()+1) + ": " + list[i]);
			}
		}
		if (stack!=null) {
			imp = new ImagePlus("Imported Stack", stack);
			if (imp.getBitDepth()==16 || imp.getBitDepth()==32)
				imp.getProcessor().setMinAndMax(min, max);
			return imp;
		}
		return null;
	}
}
