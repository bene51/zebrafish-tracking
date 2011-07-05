package huisken.util;

import ij.IJ;
import ij.ImagePlus;

import ij.plugin.PlugIn;
import ij.WindowManager;

import ij.gui.GenericDialog;

import ij.process.ImageProcessor;

import java.awt.*;
import java.awt.event.*;

import java.util.ArrayList;

import javax.swing.JColorChooser;


public class Advanced_Color_Merge implements PlugIn {

	private ArrayList<ImagePanel> imagepanels;
	
	public void run(String arg) {

		final int[] ids = WindowManager.getIDList();
		if(ids.length == 0) {
			IJ.noImage();
			return;
		}
		final String[] titles = new String[ids.length + 1];
		titles[titles.length - 1] = "*None*";
		for(int i = 0; i < ids.length; i++)
			titles[i] = WindowManager.getImage(ids[i]).getTitle();

		final Panel parent = new Panel();
		parent.setLayout(new GridBagLayout());
		final GridBagConstraints c = new GridBagConstraints();
			
		imagepanels = new ArrayList<ImagePanel>();
		
		final GenericDialog gd = new GenericDialog("Advanced Color Merge");
		for(int i = 0; i < 4; i++) {
			ImagePanel ip = new ImagePanel(titles, i < ids.length ? i : ids.length);
			c.gridy++;
			parent.add(ip, c);
			imagepanels.add(ip);
		}

		gd.addPanel(parent);
		gd.setInsets(15, 5, 0);
		gd.addMessage("Add image");
		Label l = (Label)gd.getMessage();

		l.setForeground(Color.BLUE);
		l.addMouseListener(new MouseAdapter() {
			public void mouseClicked(MouseEvent e) {
				int i = imagepanels.size();
				ImagePanel ip = new ImagePanel(titles, i < ids.length ? i : ids.length);
				c.gridy++;
				parent.add(ip, c);
				imagepanels.add(ip);
				gd.pack();
			}
		});
		gd.showDialog();



		if(gd.wasCanceled())
			return;

		ArrayList<ImagePlus> images = new ArrayList<ImagePlus>();
		ArrayList<Color> colors = new ArrayList<Color>();
		for(ImagePanel ip : imagepanels) {
			String title = ip.getImage();
			ImagePlus imp = WindowManager.getImage(title);
			if(imp != null) {
				images.add(WindowManager.getImage(ip.getImage()));
				colors.add(ip.getColor());
			}
		}

		try {
			merge(images, colors).show();
		} catch(Exception e) {
			IJ.error(e.getMessage());
		}
	}

	private class ImagePanel extends Panel {
		private final Choice choice;
		private final Button button;
		private Color color = Color.RED;

		public ImagePanel(String[] titles, int sel) {
			super();
			setLayout(new FlowLayout());

			choice = new Choice();
			for(String title : titles)
				choice.add(title);
			choice.select(sel);
			add(choice);

			button = new Button("Color");
			button.setForeground(color);
			add(button);

			button.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					Color newColor = JColorChooser.showDialog(
						null,
						"Choose Image Color",
						color);
					color = newColor;
					button.setForeground(newColor);

				}
			});
		}

		public String getImage() {
			return choice.getSelectedItem();
		}

		public Color getColor() {
			return color;
		}
	}

	public ImagePlus merge(ArrayList<ImagePlus> images, ArrayList<Color> colors) {

		int w = images.get(0).getWidth();
		int h = images.get(0).getHeight();
		int d = images.get(0).getStackSize();

		// check that all images are 8-bit and of the same size
		for(ImagePlus imp : images) {
			if(imp.getType() != ImagePlus.GRAY8)
				throw new IllegalArgumentException("Only 8-bit grayscale images are supported");
			if(imp.getWidth() != w || imp.getHeight() != h || imp.getStackSize() != d)
				throw new IllegalArgumentException("Images must be of the same size");
		}

		ImagePlus result = IJ.createImage("Advanced color merge", "rgb black", w, h, d);
		int wh = w * h;
		for(int z = 0; z < d; z++) {
			ImageProcessor rp = result.getStack().getProcessor(z + 1);
			for(int i = 0; i < images.size(); i++) {
				Color c = colors.get(i);
				ImagePlus imp = images.get(i);
				ImageProcessor ip = imp.getStack().getProcessor(z + 1);
				for(int k = 0; k < wh; k++) {
					int org = rp.get(k);
					int red   = (org & 0xff0000) >> 16;
					int green = (org & 0xff00) >> 8;
					int blue  = (org & 0xff);

					int add = ip.get(k);
					red   += (int)Math.round(add * c.getRed()   / 255.0);
					green += (int)Math.round(add * c.getGreen() / 255.0);
					blue  += (int)Math.round(add * c.getBlue()  / 255.0);

					if(red > 255)   red = 255;
					if(green > 255) green = 255;
					if(blue > 255)  blue = 255;
					
					int t = (red << 16) + (green << 8) + blue;
					rp.set(k, t);
				}
			}
		}
		return result;
	}
}