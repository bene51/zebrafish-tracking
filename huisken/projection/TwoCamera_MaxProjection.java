package huisken.projection;

import fiji.util.gui.GenericDialogPlus;
import huisken.util.Stage_Calibration;
import ij.IJ;
import ij.ImagePlus;
import ij.Prefs;
import ij.plugin.PlugIn;
import ij.process.ShortProcessor;

import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.swing.JButton;
import javax.swing.JPanel;
import javax.vecmath.Matrix4f;
import javax.vecmath.Point3f;
import javax.vecmath.Point4f;

import neo.AT;
import neo.BaseCameraApplication;

public class TwoCamera_MaxProjection implements PlugIn {

	private static final int PORT = 1236;

	@Override
	public void run(String arg) {
		File defaultdir = new File("D:\\SPIMdata");
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

		double pw = 0, ph = 0, pd = 0;
		Point3f center = new Point3f();
		float radius = 0;
		int timepoints = 0;
		int angleInc = 0;

		GenericDialogPlus gd = new GenericDialogPlus("Spherical_Max_Projection");
		String[] cChoice = new String[2];
		cChoice[TwoCameraSphericalMaxProjection.CAMERA1] = "Camera 1";
		cChoice[TwoCameraSphericalMaxProjection.CAMERA2] = "Camera 2";
		int lastCamera = (int)Prefs.get("sphere_proj.camera", 0);
		System.out.println("lastCamera = " + lastCamera);
		gd.addDirectoryField("Output directory", defaultdir.getAbsolutePath());
		gd.addChoice("Camera", cChoice, cChoice[lastCamera]);
		gd.showDialog();
		if(gd.wasCanceled())
			return;

		File outputdir = new File(gd.getNextString());
		int camera = gd.getNextChoiceIndex();
		Prefs.set("sphere_proj.camera", camera);
		Prefs.savePreferences();

		if(!outputdir.exists() || !outputdir.isDirectory())
			throw new RuntimeException("Output directory must be a folder");

		nSamples = 0;
		while(new File(outputdir, "sample" + nSamples).exists())
			nSamples++;

		try {
			String s = LabView.read("Positions");
			ArrayList<Point4f> positions = Stage_Calibration.readPositionsFromString(s);

			timepoints = LabView.readInt("n timepoints");

			nAngles = positions.size() / nSamples;
			if(nAngles > 1)
				angleInc = Math.round(positions.get(1).w - positions.get(0).w);

			gd = new GenericDialogPlus("Spherical_Max_Projection");
			gd.addNumericField("Timepoints", timepoints, 0);
			gd.addNumericField("Angle Increment", angleInc, 0);
			gd.addNumericField("#Angles", nAngles, 0);
			gd.showDialog();
			if(gd.wasCanceled())
				return;


			timepoints = (int)gd.getNextNumber();
			angleInc = (int)gd.getNextNumber();
			nAngles = (int)gd.getNextNumber();


			int timepointStart = 0;
			int timepointInc   = 1;
			nTimepoints    = timepoints;

			mmsmp = new TwoCameraSphericalMaxProjection[nSamples];
			for(int sample = 0; sample < nSamples; sample++) {
				File sampledir = new File(outputdir, "sample" + sample);
				FileInputStream config = new FileInputStream(new File(sampledir, "SMP.xml"));
				Properties props = new Properties();
				props.loadFromXML(config);
				config.close();

				w = Integer.parseInt(props.getProperty("w", "0"));
				h = Integer.parseInt(props.getProperty("h", "0"));
				d = Integer.parseInt(props.getProperty("d", "0"));
				pw = Double.parseDouble(props.getProperty("pw", "0"));
				ph = Double.parseDouble(props.getProperty("ph", "0"));
				pd = Double.parseDouble(props.getProperty("pd", "0"));
				center.set(
					Float.parseFloat(props.getProperty("centerx")),
					Float.parseFloat(props.getProperty("centery")),
					Float.parseFloat(props.getProperty("centerz")));
				radius = (float)Double.parseDouble(props.getProperty("radius"));

				// account for double-sided illumination:
				d /= 2;
				pd *= 2;

				Matrix4f[] trans = new Matrix4f[nAngles];
				Point4f refpos = positions.get(sample * nAngles);
				for(int i = 1; i < nAngles; i++) {
					Point4f sampos = positions.get(sample * nAngles + i);
					trans[i] = Stage_Calibration.getRegistration(refpos, sampos);
					trans[i].invert();
				}

				mmsmp[sample] = new TwoCameraSphericalMaxProjection(
						sampledir.getAbsolutePath(),
						timepointStart, timepointInc, nTimepoints,
						camera,
						angleInc, nAngles,
						w, h, d,
						pw, ph, pd,
						center, radius,
						trans);
			}
		} catch(Exception e) {
			e.printStackTrace();
		}

		toProcess = new short[w * h];
		cameraApp = new CameraApp(); // double-sided illumination} catch(Exception e) {
	}

	private TwoCameraSphericalMaxProjection[] mmsmp;
	private CameraApp cameraApp;


	private int w, h, d, nTimepoints, nSamples, nAngles;
	private short[] toProcess = null;
	private static final boolean SAVE_RAW = false;
	private boolean cameraAcquiring = false;
	private long[] millis;

	private void startAcq() {
		exec.execute(new Runnable() {
			@Override
			public void run() {
				millis = new long[nTimepoints];
				int d2 = 2 * d;
				AT at = cameraApp.getAT();
				for(int t = 0; t < nTimepoints; t++) {
					for(int s = 0; s < nSamples; s++) {
						for(int a = 0; a < nAngles; a++) {
							at.AT_SetInt("FrameCount", d2);
							at.AT_Command("AcquisitionStart");
							File tpDir = null;
							if(SAVE_RAW) {
								tpDir = new File(mmsmp[s].getOutputDirectory(), String.format("tp%04d_a%03d", t, a));
								tpDir.mkdir();
							}
							long start = 0;
							for(int f = 0; f < d2; f++) {
								at.AT_NextFrame(toProcess);
								cameraAcquiring = true;
								if(f == 0) {
									start = System.currentTimeMillis();
									if(s == 0 && a == 0)
										millis[t] = start;
								}


								mmsmp[s].process(toProcess);
								if(SAVE_RAW)
									IJ.save(new ImagePlus("", new ShortProcessor(w, h, toProcess, null)), new File(tpDir, String.format("%04d.tif", f)).getAbsolutePath());
							}
							at.AT_Command("AcquisitionStop");
							cameraAcquiring = false;
							long end = System.currentTimeMillis();
							System.out.println("Needed " + (end - start) + "ms  " + 1000f * d2 / (end - start) + " fps");
						}
					}
				}
				try {
					File times = new File(new File(mmsmp[0].getOutputDirectory()).getParentFile(), "times.csv");
					PrintStream out = new PrintStream(new FileOutputStream(times));
					for(int i = 0; i < millis.length; i++)
						out.println(i + "\t" + millis[i]);
					out.close();
				} catch (FileNotFoundException e) {
					e.printStackTrace();
				}
			}
		});
	}

	public void waitForCamera() {
		while(cameraAcquiring) {
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

	public void setup() throws IOException {
		AT at = cameraApp.getAT();
		at.AT_Flush();
		// at.AT_SetEnumString("CycleMode", "Continuous");
		at.AT_SetEnumString("CycleMode", "Fixed");
		at.AT_SetEnumString("TriggerMode", "External Start");

		at.AT_CreateBuffers();

		startAcq();

		ServerSocket server = new ServerSocket(PORT);

		Socket client = server.accept();
		BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
		String line = null;
		System.out.println("listening");
		while((line = in.readLine()) != null) {
			System.out.println("***" + line + "***");
			if(line.equals("WAIT")) {
				waitForCamera();
				client.getOutputStream().write("done\r\n".getBytes());
				System.out.println("done");
			}
		}
		System.out.println("closing");
		in.close();
		client.close();
		server.close();
	}

	public void done() {
		cameraApp.getAT().AT_DeleteBuffers();
	}

	private final ExecutorService exec = Executors.newSingleThreadExecutor();

	@SuppressWarnings("serial")
	private final class CameraApp extends BaseCameraApplication {

		private JButton process;

		@Override
		public JPanel getPanel() {
			process = new JButton("Go!");
			process.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					new Thread() {
						@Override
						public void run() {
							try {
								setup();
							} catch(Exception ex) {
								ex.printStackTrace();
								IJ.error(ex.getMessage());
								return;
							}
						}
					}.start();
				}
			});

			// Initialize the GUI
			JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
			panel.add(process);
			panel.setName("Projection");
			return panel;
		}
	}
}
