package huisken.opener;

import ij.process.Blitter;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;

public class SPIM_ProjectedRegularStack extends SPIMRegularStack {

	private final int proj;
	private final int x0, y0, x1, y1, orgW, orgH;

	public SPIM_ProjectedRegularStack(int orgW, int orgH, int x0, int x1, int y0, int y1, int proj) {
		super(orgW, orgH, x0, x1, y0, y1);
		this.x0 = x0;
		this.x1 = x1;
		this.y0 = y0;
		this.y1 = y1;
		this.orgW = orgW;
		this.orgH = orgH;
		this.proj = proj;
	}

	private ImageProcessor projection = null;
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

		if(projection == null)
			projection = ip;
		else
			projection.copyBits(ip, 0, 0, proj);

		if(lastZ) {
			addSlice("", projection);
			projection = null;
		}
	}
}