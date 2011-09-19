package huisken.opener;

import ij.process.Blitter;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;

import java.io.File;

public class SPIM_ProjectedVirtualStack extends SPIMVirtualStack {

	private String tempdir;
	private final int proj;
	private final int x0, y0, x1, y1, orgW, orgH;

	public SPIM_ProjectedVirtualStack(int orgW, int orgH, int x0, int x1, int y0, int y1, int proj) {
		super(orgW, orgH, x0, x1, y0, y1);
		makeTempDir();
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
		if (path == null)
			throw new IllegalArgumentException("path is null!");

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
			String projectionPath = tempdir + File.separator + String.format("%05d.dat", getSize());
			SPIMExperiment.saveRaw(projection, projectionPath);
			paths.add(projectionPath);
			projection = null;
		}
	}

	public ImageProcessor getProcessor(int n) {
		ImageProcessor ip = null;
		try {
			ip = SPIMExperiment.openRaw(paths.get(n - 1), getWidth(), getHeight());
		} catch(Exception e) {
			e.printStackTrace();
			return null;
		}
		if(!(ip instanceof ShortProcessor))
			ip = ip.convertToShort(true);
		return ip;
	}

	private void makeTempDir() {
		String tmp = System.getProperty("java.io.tmpdir");
		int i = 0;
		File f = null;
		while((f = new File(tmp, String.format("SPIM_MaxProjection_%05d", i))).exists())
			i++;
		f.mkdir();
		tempdir = f.getAbsolutePath();
	}
}