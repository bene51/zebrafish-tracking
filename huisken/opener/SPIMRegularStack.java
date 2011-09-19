package huisken.opener;

import ij.process.ImageProcessor;
import ij.process.ShortProcessor;

public class SPIMRegularStack extends SPIMStack {

	private final int x0, x1, y0, y1, orgW, orgH;

	public SPIMRegularStack(int orgW, int orgH, int x0, int x1, int y0, int y1) {
		super(x1 - x0, y1 - y0);
		this.x0 = x0;
		this.x1 = x1;
		this.y0 = y0;
		this.y1 = y1;
		this.orgW = orgW;
		this.orgH = orgH;
	}

	public void addSlice(String path, boolean lastZ) {
		ImageProcessor ip = null;
		try {
			ip = SPIMExperiment.openRaw(path, orgW, orgH, x0, x1, y0, y1);
		} catch(Exception e) {
			e.printStackTrace();
			return;
		}
		if(!(ip instanceof ShortProcessor))
			ip = ip.convertToShort(true);
		super.addSlice("", ip);
	}
}