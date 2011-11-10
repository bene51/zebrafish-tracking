package huisken.various;

import huisken.network.ImageProvider;
import huisken.network.ImageReceiver;
import ij.ImagePlus;
import ij.process.ColorProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;

import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.JTextField;

import neo.AbstractCameraApplication;


@SuppressWarnings("serial")
public class DynamicColorMerge extends AbstractCameraApplication {

	private JButton merge;
	private JCheckBox serverCB;
	private JTextField serverTF;
	private JTextField portTF;

	private Thread thread;
	private boolean running = false;

	public DynamicColorMerge() {
		super();
	}

	@Override
	public JPanel getPanel() {
		merge = new JButton("Merge");
		merge.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				toggleMerge();
			}
		});
		serverTF = new JTextField("localhost");
		portTF = new JTextField("4444");
		serverCB = new JCheckBox("Server mode?");



		// Initialize the GUI
		JPanel mergePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
		mergePanel.add(serverCB);
		mergePanel.add(serverTF);
		mergePanel.add(portTF);
		mergePanel.add(merge);
		mergePanel.setName("Merging");
		return mergePanel;
	}

	public void toggleMerge() {
		if(!running) {
			thread = new Thread() {
				@Override
				public void run() {
					running = true;
					merge.setText("Stop");
					String host = serverTF.getText();
					int port = Integer.parseInt(portTF.getText());
					boolean isServer = serverCB.isSelected();
					if(isServer)
						startServer(port);
					else
						startClient(host, port);
				}
			};
			thread.start();
		} else {
			running = false;
			try {
				thread.join();
			} catch(Exception e) {
				e.printStackTrace();
			}
			merge.setText("Merge");
		}
	}

	public void startServer(final int port) {
		// start camera
		final ImageProvider provider = new ImageProvider();
		new Thread() {
			@Override
			public void run() {
				try {
					provider.run(port);
				} catch(Exception e) {
					e.printStackTrace();
				}
			}
		}.start();

		// start camera, each time an image is received, call provider.setImage();
		int aw = at.AT_GetInt("AOIWidth");
		int ah = at.AT_GetInt("AOIHeight");
		int nPixels = aw * ah;
		short[] pixels = new short[nPixels];
		ImageProcessor ip = new ShortProcessor(aw, ah, pixels, null);
		ImagePlus image = new ImagePlus("", ip);

		at.startPreview();
		while(running) {
			at.nextPreviewImage(pixels);
			ip.resetMinAndMax();
			provider.setImage(image);
		}
		at.finishPreview();
	}

	public void startClient(final String host, final int port) {
		// start camera
		final ImageReceiver receiver = new ImageReceiver();
		new Thread() {
			@Override
			public void run() {
				try {
					receiver.start(host, port);
				} catch(Exception e) {
					e.printStackTrace();
				}
			}
		}.start();

		// start camera, each time an image is received, call provider.setImage();
		int aw = at.AT_GetInt("AOIWidth");
		int ah = at.AT_GetInt("AOIHeight");
		int nPixels = aw * ah;
		short[] pixels = new short[nPixels];
		ImageProcessor ip = new ShortProcessor(aw, ah, pixels, null);
		ImagePlus image = new ImagePlus("", ip);

		ImageProcessor merged = new ColorProcessor(aw, ah);
		ImagePlus result = new ImagePlus("Merge", merged);
		result.show();

		at.startPreview();
		while(running) {
			at.nextPreviewImage(pixels);
			ip.resetMinAndMax();
			ImagePlus second = null;
			try {
				second = receiver.getImage();
			} catch (Exception e) {
				e.printStackTrace();
			}
			ShortProcessor r = (ShortProcessor)image.getProcessor();
			ShortProcessor g = (ShortProcessor)second.getProcessor();
			int w = r.getWidth();
			int h = r.getHeight();
			int wh = w * h;
			double scale1 = 256.0 / (r.getMax() - r.getMin() + 1);
			double scale2 = 256.0 / (g.getMax() - g.getMin() + 1);
			for(int i = 0; i < wh; i++) {
				int red   = r.get(i);
				red = (int)(red * scale1 + 0.5);
				int green = g.get(i);
				green = (int)(green * scale2 + 0.5);

				int merge = 0xff000000 + (red << 16) + (green << 8);
				merged.set(i, merge);
			}
		}
		try {
			receiver.stop();
		} catch(Exception e) {
			e.printStackTrace();
		}
		at.finishPreview();
	}
}
