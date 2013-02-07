package huisken.util;

import fiji.plugin.timelapsedisplay.TimeLapseDisplay;
import fiji.util.gui.GenericDialogPlus;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;

import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Date;
import java.util.Properties;

import javax.swing.JButton;
import javax.swing.JPanel;
import javax.vecmath.AxisAngle4f;
import javax.vecmath.Matrix3f;
import javax.vecmath.Matrix4f;
import javax.vecmath.Point3f;
import javax.vecmath.Point4f;
import javax.vecmath.Tuple3f;
import javax.vecmath.Vector3f;

import math3d.Point3d;
import mpicbg.spim.Reconstruction;
import mpicbg.spim.io.ConfigurationParserException;
import mpicbg.spim.io.IOFunctions;
import mpicbg.spim.io.SPIMConfiguration;
import neo.BaseCameraApplication;
import spimopener.SPIMExperiment;
import vib.FastMatrix;


@SuppressWarnings("serial")
public class Stage_Calibration extends BaseCameraApplication {

	public static final int PORT = 1236;
	public static final String DIR = System.getProperty("java.io.tmpdir") + File.separator + "SPIM_Calibration";
	public static final int D = 150;
	public static final double DZ = 4;
	public static final double DX = 0.65;
	public static final int N_POSITIONS = 7;
	public static final Point3f REF_POS = new Point3f(122.3f, 4f, 19.02f);

	public static final String CALIBRATION_FILE = System.getProperty("user.home") + File.separator + ".spim2_stage_calibration";

	private JButton calibrate;

	public Stage_Calibration() {
		super();
	}

	@Override
	public JPanel getPanel() {
		calibrate = new JButton("Start aquisition");
		calibrate.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				new Thread() {
					@Override
					public void run() {
						try {
							calibrate();
						} catch (IOException e) {
							IJ.error(e.getMessage());
							e.printStackTrace();
						}
					}
				}.start();
			}
		});

		// Initialize the GUI
		JPanel spoolPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
		spoolPanel.add(calibrate);
		spoolPanel.setName("Calibration");
		return spoolPanel;
	}

	public void test() throws IOException {
		GenericDialogPlus gd = new GenericDialogPlus("test");
		gd.addDirectoryField("directory", "");
		gd.addFileField("positions", "");
		gd.addNumericField("d", 151, 0);
		gd.addNumericField("dx", 0.65, 4);
		gd.addNumericField("dz", 4, 4);
		gd.addNumericField("timepoints", 1, 0);
		gd.addCheckbox("Do_acquisition", true);
		gd.addCheckbox("Do_registration", true);
		gd.showDialog();
		if(gd.wasCanceled())
			return;
		String dir = gd.getNextString();
		String posfile = gd.getNextString();
		int d = (int)gd.getNextNumber();
		double dx = gd.getNextNumber();
		double dz = gd.getNextNumber();
		int timepoints = (int)gd.getNextNumber();
		boolean doAcquisition = gd.getNextBoolean();
		boolean doRegistration = gd.getNextBoolean();

		ArrayList<Point4f> positions = readPositions(posfile);
		int npos = positions.size();

		File errorfile = new File(dir, "errors.txt");
		PrintStream out = new PrintStream(errorfile);

		for(int t = 0; t < timepoints; t++) {
			long start = System.currentTimeMillis();
			String tdir = new File(dir, String.format("tp%04d", t)).getAbsolutePath();

			if(doAcquisition)
				acquire(tdir, npos, d, dx, dz);

			if(doRegistration) {
				try {
				 	register(tdir, npos, dx, dz);
				} catch(Exception e) {
					e.printStackTrace();
				}
			}

			File registrationFolder = new File(tdir, "registration");
			File rf = new File(registrationFolder, "pos0.tif.beads.txt");
			Point4f refpos = positions.get(0);

			for(int i = 1; i < positions.size(); i++) {
				File mf = new File(registrationFolder, "pos" + i + ".tif.beads.txt");
				Matrix4f mat = getRegistration(refpos, positions.get(i));
				float meanerror = checkTransformation(rf.getAbsolutePath(), mf.getAbsolutePath(), mat);
				out.println(meanerror);
			}
			long end = System.currentTimeMillis();
			System.out.println("Needed " + (end - start) + " ms");
		}
		out.close();
	}

	public static ArrayList<Point4f> readPositions(String posfile) throws IOException {
		BufferedReader in = new BufferedReader(new FileReader(posfile));
		String line;
		ArrayList<Point4f> pos = new ArrayList<Point4f>();
		// skip the first two lines
		in.readLine();
		in.readLine();

		while((line = in.readLine()) != null) {
			String[] tok = line.split("\t");
			Point4f p = new Point4f();
			p.w = (float)Double.parseDouble(tok[1]);
			p.x = (float)Double.parseDouble(tok[2]);
			p.y = (float)Double.parseDouble(tok[3]);
			p.z = (float)Double.parseDouble(tok[4]);
			pos.add(p);
		}
		in.close();
		return pos;
	}

	public static ArrayList<Point4f> readPositionsFromString(String s) {
		String[] lines = s.split("\n");
		System.out.println("***");
		ArrayList<Point4f> pos = new ArrayList<Point4f>();
		for(int i = 2; i < lines.length; i++) {
			String l = lines[i];
			String[] tok = l.split("\t");
			Point4f p = new Point4f();
			p.w = (float)Double.parseDouble(tok[1]);
			p.x = (float)Double.parseDouble(tok[2]);
			p.y = (float)Double.parseDouble(tok[3]);
			p.z = (float)Double.parseDouble(tok[4]);
			pos.add(p);
		}
		return pos;
	}

	public void acquire(String dir, int npos, int d, double dx, double dz) throws IOException {
		File folder = new File(dir);
		if(!folder.exists())
			folder.mkdir();

		int w = at.AT_GetInt("AOIWidth");
		int h = at.AT_GetInt("AOIHeight");

		String xmlfile = "properties.xml";

		// create xml file
		PrintWriter xml = new PrintWriter(new FileWriter(new File(dir, xmlfile)));
		xml.println("<?xml version=\"1.0\" encoding=\"utf-8\" standalone=\"yes\"?>");
		xml.println("<?xml-stylesheet href=\"jan.xsl\" type=\"text/xsl\"?>");
		xml.println("<EXPERIMENT>");
		xml.println("	<Width>"     + w + "</Width>");
		xml.println("	<Height>"    + h + "</Height>");
		xml.println("	<NrPlanes>"  + d + "</NrPlanes>");
		xml.println("	<MagObj>10</MagObj>");
		xml.println("	<PixelSize>" + dx + "</PixelSize>");
		xml.println("	<dZ>"        + dz + "</dZ>");
		xml.println("</EXPERIMENT>");
		xml.close();


		// stream data
		System.out.println("Starting acquisition");
		for(int p = 0; p < npos; p++) {
			File pf = new File(folder, "pos" + p);
			System.out.println("... position + " + p);
			pf.mkdir();
			at.AT_Spool(pf.getAbsolutePath() + File.separator, 2 * d, "plane_%010d.dat");
		}
		System.out.println("Acquisition done");

		// make stacks
		for(int p = 0; p < npos; p++) {
			File pf = new File(folder, "pos" + p);
			pf.mkdir();
			ImageStack stack = new ImageStack(w, h);
			for(int z = 0; z < d; z++) {
				String file = String.format("plane_%010d.dat", 2 * z); // skip right ill
				stack.addSlice("", SPIMExperiment.openRaw(new File(pf, file).getAbsolutePath(), w, h));
			}
			ImagePlus image = new ImagePlus("", stack);
			image.getCalibration().pixelWidth = dx;
			image.getCalibration().pixelHeight = dx;
			image.getCalibration().pixelDepth = dz;
			IJ.save(image, new File(folder, "pos" + p + ".tif").getAbsolutePath());
		}
	}

	public static void register(String direc, int npos, double dx, double dz) throws IOException {

		File folder = new File(direc);

		SPIMConfiguration conf = new SPIMConfiguration();

		if ( conf.initialSigma == null || conf.initialSigma.length != 1 )
			conf.initialSigma = new float[]{ 1.8f };

		if ( conf.minPeakValue == null || conf.minPeakValue.length != 1 )
			conf.minPeakValue = new float[]{ 0.01f };

		conf.minPeakValue[ 0 ] = 0.008f; // bead brightness

		conf.minInitialPeakValue = new float[]{ conf.minPeakValue[ 0 ]/4 };

		conf.timepointPattern = "1";
		conf.channelPattern = "";
		conf.channelsToRegister = "";
		conf.channelsToFuse = "";
		conf.anglePattern = "0-" + (npos - 1) + ":1";
		conf.inputFilePattern = "pos{a}.tif";

		conf.inputdirectory = folder.getAbsolutePath();

		conf.overrideImageZStretching = true;
		conf.zStretching = dz / dx;

		conf.readSegmentation = false;
		conf.readRegistration = false;
		int defaultModel = 2;

		if ( defaultModel == 0 )
		{
			conf.transformationModel = "Translation";
			conf.max_epsilon = 10;
			conf.numIterations = 10000;
		}
		else if ( defaultModel == 1 )
		{
			conf.transformationModel = "Rigid";
			conf.max_epsilon = 7;
			conf.numIterations = 10000;
		}
		else
		{
			conf.transformationModel = "Affine";
		}

		conf.registerOnly = true;
		conf.timeLapseRegistration = false;

		// check the directory string
		conf.inputdirectory = conf.inputdirectory.replace('\\', '/');
		conf.inputdirectory = conf.inputdirectory.replaceAll( "//", "/" );

		conf.inputdirectory = conf.inputdirectory.trim();
		if (conf.inputdirectory.length() > 0 && !conf.inputdirectory.endsWith("/"))
			conf.inputdirectory = conf.inputdirectory + "/";

		conf.outputdirectory = conf.inputdirectory + "output/";
		conf.registrationFiledirectory = conf.inputdirectory + "registration/";

		// variable specific verification
		if (conf.numberOfThreads < 1)
			conf.numberOfThreads = Runtime.getRuntime().availableProcessors();

		if (conf.scaleSpaceNumberOfThreads < 1)
			conf.scaleSpaceNumberOfThreads = Runtime.getRuntime().availableProcessors();

		try {
			conf.getFileNames();
		}
		catch ( ConfigurationParserException e ) {
			IJ.error( "Cannot parse input: " + e );
			return;
		}

		// set interpolator stuff
		conf.interpolatorFactorOutput.setOutOfBoundsStrategyFactory( conf.strategyFactoryOutput );

		// check if directories exist
		File dir = new File(conf.outputdirectory, "");
		if (!dir.exists()) {
			IOFunctions.println("(" + new Date(System.currentTimeMillis()) + "): Creating directory '" + conf.outputdirectory + "'.");
			boolean success = dir.mkdirs();
			if (!success) {
				if (!dir.exists()) {
					IOFunctions.printErr("(" + new Date(System.currentTimeMillis()) + "): Cannot create directory '" + conf.outputdirectory + "', quitting.");
					return;
				}
			}
		}

		dir = new File(conf.registrationFiledirectory, "");
		if (!dir.exists()) {
			IOFunctions.println("(" + new Date(System.currentTimeMillis()) + "): Creating directory '" + conf.registrationFiledirectory + "'.");
			boolean success = dir.mkdirs();
			if (!success) {
				if (!dir.exists()) {
					IOFunctions.printErr("(" + new Date(System.currentTimeMillis()) + "): Cannot create directory '" + conf.registrationFiledirectory + "', quitting.");
					return;
				}
			}
		}



		// this is only registration
		conf.registerOnly = true;

		// if we do not do timelapseregistration we can just go ahead and
		// display the result if wanted
		conf.timeLapseRegistration = false;
		conf.collectRegistrationStatistics = true;

		final Reconstruction reconstruction = new Reconstruction( conf );

		if ( reconstruction.getSPIMConfiguration().file.length > 1)
			TimeLapseDisplay.plotData( reconstruction.getRegistrationStatistics(), -1, false );
	}

	public void calibrate() throws IOException {
		System.out.println(DIR);

		acquire(DIR, N_POSITIONS, D, DX, DZ);

		register(DIR, N_POSITIONS, DX, DZ);

		System.out.println();
		System.out.println("Analyzing 300 um stage movement in x-direction:");
		Vector3f xStep = analyzeTranslation(1);
		System.out.println("   ...corresponds to " + xStep + " um in image coordinates");

		System.out.println();
		System.out.println("Analyzing 300 um stage movement in y-direction:");
		Vector3f yStep = analyzeTranslation(2);
		System.out.println("   ...corresponds to " + yStep + " um in image coordinates");

		System.out.println();
		System.out.println("Analyzing 300 um stage movement in z-direction:");
		Vector3f zStep = analyzeTranslation(3);
		System.out.println("   ...corresponds to " + zStep + " um in image coordinates");

		Vector3f axis = new Vector3f();
		Vector3f center = new Vector3f();

		Vector3f atemp = new Vector3f(), ctemp = new Vector3f();
		System.out.println();
		System.out.println("Analyzing rotation about 60 degrees:");
		float angle = analyzeRotation(4, atemp, ctemp);
		System.out.println("   ...axis = " + atemp);
		System.out.println("   ...center = " + ctemp);
		System.out.println("   ...angle = " + angle);
		axis.add(atemp);
		center.add(ctemp);

		System.out.println();
		System.out.println("Analyzing rotation about 90 degrees:");
		angle = analyzeRotation(5, atemp, ctemp);
		System.out.println("   ...axis = " + atemp);
		System.out.println("   ...center = " + ctemp);
		System.out.println("   ...angle = " + angle);
		axis.add(atemp);
		center.add(ctemp);

		System.out.println();
		System.out.println("Analyzing rotation about 120 degrees:");
		angle = analyzeRotation(6, atemp, ctemp);
		System.out.println("   ...axis = " + atemp);
		System.out.println("   ...center = " + ctemp);
		System.out.println("   ...angle = " + angle);
		axis.add(atemp);
		center.add(ctemp);

		System.out.println();
		System.out.println("Rotation summary:");
		axis.scale(1/3f);
		center.scale(1/3f);
		System.out.println("   ...average axis = " + axis);
		System.out.println("   ...average center = " + center);

		// write calibration file
		xStep.scale(1 / 300f);
		yStep.scale(1 / 300f);
		zStep.scale(1 / 300f);

		saveCalibration(xStep, yStep, zStep, axis, center, REF_POS);
	}

	public static float checkTransformation(String beadfileRef, String beadFileMod, Matrix4f mat) throws IOException {
		ArrayList<Point3f> ref = new ArrayList<Point3f>();
		ArrayList<Point3f> mod = new ArrayList<Point3f>();
		File rf = new File(beadfileRef);
		File mf = new File(beadFileMod);

		getBeadCorrespondences(rf, mf, ref, mod);

		Point3f pr = new Point3f(), pm = new Point3f();
		int N = ref.size();
		double distance = 0.0;
		for(int i = 0; i < N; i++) {
			pr.set(ref.get(i));
			pm.set(mod.get(i));

			pr.x *= (float)DX;
			pr.y *= (float)DX;
			pr.z *= (float)DZ;

			pm.x *= (float)DX;
			pm.y *= (float)DX;
			pm.z *= (float)DZ;

			mat.transform(pm);

			distance += (pr.distance(pm)) / N;
		}
		return (float)distance;
	}

	public static void checkTransformation(String beadfileRef, String beadFileMod, Matrix4f mat, double[] minmaxavg) throws IOException {
		ArrayList<Point3f> ref = new ArrayList<Point3f>();
		ArrayList<Point3f> mod = new ArrayList<Point3f>();
		File rf = new File(beadfileRef);
		File mf = new File(beadFileMod);

		getBeadCorrespondences(rf, mf, ref, mod);

		Point3f pr = new Point3f(), pm = new Point3f();
		int N = ref.size();
		double avg = 0.0;
		double min = Double.MAX_VALUE;
		double max = 0;
		for(int i = 0; i < N; i++) {
			pr.set(ref.get(i));
			pm.set(mod.get(i));

			pr.x *= (float)DX;
			pr.y *= (float)DX;
			pr.z *= (float)DZ;

			pm.x *= (float)DX;
			pm.y *= (float)DX;
			pm.z *= (float)DZ;

			mat.transform(pm);
			double dist = pr.distance(pm);
			if(dist < min)
				min = dist;
			if(dist > max)
				max = dist;
			avg += dist / N;
		}
		minmaxavg[0] = min;
		minmaxavg[1] = max;
		minmaxavg[2] = avg;
	}

	public static Matrix4f readTransformation(String path) throws IOException {
		BufferedReader in = new BufferedReader(new FileReader(path));
		float[] d = new float[16];
		for(int i = 0; i < 16; i++) {
			String line = in.readLine();
			String[] tok = line.split(": ");
			d[i] = (float)Double.parseDouble(tok[1]);
		}
		in.close();
		Matrix4f m = new Matrix4f(d);
		return m;
	}

	public static Matrix4f getRegistration(Point4f stageref, Point4f stagepos) {
		/*
		 * we have
		 *                    T0           T1
		 *        stageref  <---- REF_POS ---->  stagepos
		 *
		 * so T(stagepos -> stageref) = T0 T1^(-1)
		 */
		Matrix4f T0 = getTransformation(stageref);
		Matrix4f T1 = getTransformation(stagepos);
		T1.invert();
		T0.mul(T1);
		return T0;
	}

	/*
	 * Calculates the transformation from refpos to stagepos,
	 * angle is assumed to be in degrees,
	 */
	public static Matrix4f getTransformation(Point4f stagepos) {
		float angle = stagepos.w;

System.out.println();
System.out.println("getTransformation(" + stagepos + ")");
		Vector3f dx = new Vector3f();
		Vector3f dy = new Vector3f();
		Vector3f dz = new Vector3f();
		Vector3f axis = new Vector3f();
		Vector3f center = new Vector3f();
		Point3f ref = new Point3f();
		try {
			loadCalibration(dx, dy, dz, axis, center, ref);
		} catch (IOException e) {
			throw new RuntimeException("Cannot load calibration", e);
		}

		AxisAngle4f aa = new AxisAngle4f(axis, (float)(angle * Math.PI / 180));

/*
		float d = (stagepos.x - ref.x) * 1000;
		center.scaleAdd(-d, dx, center);
		d = (stagepos.y - ref.y) * 1000;
		center.scaleAdd(-d, dy, center);
		d = (stagepos.z - ref.z) * 1000;
		center.scaleAdd(-d, dz, center);
*/
		// calculate C * R * (-C)
		Matrix4f c = new Matrix4f();
		c.set(center);
		Matrix4f m = new Matrix4f();
		m.set(aa);
		m.mul(c, m);
		c.invert();
		m.mul(m, c);
System.out.println("crc = " + m);

		Vector3f transl = new Vector3f();
		float d = (stagepos.x - ref.x) * 1000;
		transl.scaleAdd(d, dx, transl);
		d = (stagepos.y - ref.y) * 1000;
		transl.scaleAdd(d, dy, transl);
		d = (stagepos.z - ref.z) * 1000;
		transl.scaleAdd(d, dz, transl);
System.out.println("transl = " + transl);

		Matrix4f translation = new Matrix4f();
		translation.set(transl);
		m.mul(translation, m);

		return m;
	}

	private static void loadCalibration(Vector3f dx, Vector3f dy, Vector3f dz, Vector3f axis, Vector3f center, Point3f reference) throws IOException {
		Properties props = new Properties();
		props.load(new FileReader(CALIBRATION_FILE));
		String value;
		value = props.getProperty("dx");
		if(value != null) parse(value, dx);        else throw new RuntimeException("dx not found in " + CALIBRATION_FILE);
		value = props.getProperty("dy");
		if(value != null) parse(value, dy);        else throw new RuntimeException("dy not found in " + CALIBRATION_FILE);
		value = props.getProperty("dz");
		if(value != null) parse(value, dz);        else throw new RuntimeException("dz not found in " + CALIBRATION_FILE);
		value = props.getProperty("axis");
		if(value != null) parse(value, axis);      else throw new RuntimeException("axis not found in " + CALIBRATION_FILE);
		value = props.getProperty("center");
		if(value != null) parse(value, center);    else throw new RuntimeException("center not found in " + CALIBRATION_FILE);
		value = props.getProperty("reference");
		if(value != null) parse(value, reference); else throw new RuntimeException("reference not found in " + CALIBRATION_FILE);
	}

	private static void saveCalibration(Vector3f dx, Vector3f dy, Vector3f dz, Vector3f axis, Vector3f center, Point3f reference) throws IOException {
		Properties props = new Properties();
		props.setProperty("dx", toString(dx));
		props.setProperty("dy", toString(dy));
		props.setProperty("dz", toString(dz));
		props.setProperty("axis", toString(axis));
		props.setProperty("center", toString(center));
		props.setProperty("reference", toString(reference));
		props.store(new FileWriter(CALIBRATION_FILE), "");
	}

	public static String toString(Tuple3f t) {
		return t.x + ", " + t.y + ", " + t.z;
	}

	public static void parse(String text, Tuple3f t) {
		String[] toks = text.split(",");
		t.x = (float)Double.parseDouble(toks[0]);
		t.y = (float)Double.parseDouble(toks[1]);
		t.z = (float)Double.parseDouble(toks[2]);
	}

	public static Vector3f analyzeTranslation(int pos) throws IOException {
		File registrationFolder = new File(DIR, "registration");
		ArrayList<Point3f> ref = new ArrayList<Point3f>();
		ArrayList<Point3f> mod = new ArrayList<Point3f>();
		File rf = new File(registrationFolder, "pos0.tif.beads.txt");
		File mf = new File(registrationFolder, "pos" + pos + ".tif.beads.txt");

		getBeadCorrespondences(rf, mf, ref, mod);
		Vector3f transl = new Vector3f();
		int N = ref.size();
		for(int i = 0; i < N; i++) {
			Point3f rp = ref.get(i);
			Point3f mp = mod.get(i);
			transl.x += mp.x - rp.x;
			transl.y += mp.y - rp.y;
			transl.z += mp.z - rp.z;
		}
		transl.x /= N;
		transl.y /= N;
		transl.z /= N;

		transl.x *= DX;
		transl.y *= DX;
		transl.z *= DZ;
		return transl;
	}

	public static float analyzeRotation(int pos, Vector3f axis, Vector3f center) throws IOException {
		File registrationFolder = new File(DIR, "registration");
		File rf = new File(registrationFolder, "pos0.tif.beads.txt");
		File mf = new File(registrationFolder, "pos" + pos + ".tif.beads.txt");

		return analyzeRotation(rf, mf, DX, DZ, axis, center);
	}

	public static float analyzeRotation(File rf, File mf, double dx, double dz, Vector3f axis, Vector3f center) throws IOException {
		ArrayList<Point3f> ref = new ArrayList<Point3f>();
		ArrayList<Point3f> mod = new ArrayList<Point3f>();

		getBeadCorrespondences(rf, mf, ref, mod);
		int N = ref.size();
		Point3d[] pr = new math3d.Point3d[N];
		Point3d[] pm = new math3d.Point3d[N];
		for(int i = 0; i < N; i++) {
			Point3f p3fr = ref.get(i);
			Point3f p3fm = mod.get(i);
			pr[i] = new Point3d((float)(dx * p3fr.x), (float)(dx * p3fr.y), (float)(dz * p3fr.z));
			pm[i] = new Point3d((float)(dx * p3fm.x), (float)(dx * p3fm.y), (float)(dz * p3fm.z));
		}
		FastMatrix fm = FastMatrix.bestRigid(pr, pm);
		double[] d = fm.rowwise16();
		float[] f = new float[16];
		for(int i = 0; i < 16; i++)
			f[i] = (float)d[i];

		Matrix4f matrix = new Matrix4f(f);
System.out.println("matrix = " + matrix);
		AxisAngle4f aa = new AxisAngle4f();
		aa.set(matrix);
		Vector3f trans = new Vector3f();
		matrix.get(trans);

		Matrix3f identity = new Matrix3f();
		identity.setIdentity();
		Matrix3f rotation = new Matrix3f();
		matrix.get(rotation);
		rotation.sub(identity, rotation);
		rotation.invert();
		Point3f ctr = new Point3f();
		ctr.set(trans);
		rotation.transform(ctr);

		axis.set(aa.x, aa.y, aa.z);
		float a = ctr.x / aa.x;
		center.scaleAdd(-a, axis, ctr);
		return aa.angle;
	}

	public static void getBeadCorrespondences(File f1, File f2, ArrayList<Point3f> l1, ArrayList<Point3f> l2) throws IOException {
		// load all beads of view 1
		BufferedReader buf = new BufferedReader(new FileReader(f1));
		String line;
		int viewid1 = 0;
		ArrayList<Point3f> beads1 = new ArrayList<Point3f>();
		buf.readLine();
		while((line = buf.readLine()) != null) {
			String[] toks = line.split("\t");
			viewid1 = Integer.parseInt(toks[1]);
			Point3f p = new Point3f();
			p.x = Float.parseFloat(toks[5]);
			p.y = Float.parseFloat(toks[6]);
			p.z = Float.parseFloat(toks[7]);
			beads1.add(p);
		}
		buf.close();

		l1.clear();
		l2.clear();
		buf = new BufferedReader(new FileReader(f2));
		buf.readLine();
		while((line = buf.readLine()) != null) {
			String[] toks = line.split("\t");
			if(toks[10].trim().equals("0"))
				continue;
			String[] corrString = toks[10].split(";");
			for(String c : corrString) {
				String[] match = c.split(":");
				int otherviewid = Integer.parseInt(match[1]);
				if(otherviewid == viewid1) {
					Point3f p = new Point3f();
					p.x = Float.parseFloat(toks[5]);
					p.y = Float.parseFloat(toks[6]);
					p.z = Float.parseFloat(toks[7]);
					l2.add(p);
					l1.add(beads1.get(Integer.parseInt(match[0])));
				}
			}
		}
		buf.close();
	}
}
