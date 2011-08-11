package huisken.opener;

import ij.process.ImageProcessor;
import ij.process.ShortProcessor;

public class SPIMRegularStack extends SPIMStack {

	public SPIMRegularStack(int w, int h) {
		super(w, h);
	}

	public void addSlice(String path, boolean lastZ) {
		ImageProcessor ip = null;
		try {
			ip = SPIMExperiment.openRaw(path, getWidth(), getHeight());
		} catch(Exception e) {
			e.printStackTrace();
			return;
		}
		if(!(ip instanceof ShortProcessor))
			ip = ip.convertToShort(true);
		super.addSlice("", ip);
	}
}