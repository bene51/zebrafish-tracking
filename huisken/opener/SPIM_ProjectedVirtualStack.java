package huisken.opener;

import ij.process.Blitter;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;

import java.io.File;

public class SPIM_ProjectedVirtualStack extends SPIMVirtualStack {

	private String tempdir;
	private final int proj;

	public SPIM_ProjectedVirtualStack(int w, int h, int proj) {
		super(w, h);
		makeTempDir();
		this.proj = proj;
	}

	private ImageProcessor projection = null;
	public void addSlice(String path, boolean lastZ) {
		if (path == null)
			throw new IllegalArgumentException("path is null!");

		ImageProcessor ip = null;
		try {
			ip = SPIMExperiment.openRaw(path, getWidth(), getHeight());
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