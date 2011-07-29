package huisken.opener;

import ij.process.Blitter;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;

public class SPIM_ProjectedRegularStack extends SPIMRegularStack {

	public SPIM_ProjectedRegularStack(int w, int h) {
		super(w, h);
	}

	private ImageProcessor projection = null;
	public void addSlice(String path, boolean lastZ) {
		ImageProcessor ip = null;
		try {
			ip = Experiment.openRaw(path, getWidth(), getHeight());
		} catch(Exception e) {
			e.printStackTrace();
			return;
		}
		if(!(ip instanceof ShortProcessor))
			ip = ip.convertToShort(true);

		if(projection == null)
			projection = ip;
		else
			projection.copyBits(ip, 0, 0, Blitter.MAX);

		if(lastZ) {
			addSlice("", projection);
			projection = null;
		}
	}
}