package huisken.opener;

import ij.process.Blitter;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;

import java.io.File;

public class SPIM_ProjectedVirtualStack extends SPIMVirtualStack {

	private String tempdir;

	public SPIM_ProjectedVirtualStack(int w, int h) {
		super(w, h);
		makeTempDir();
	}

	private ImageProcessor projection = null;
	public void addSlice(String path, boolean lastZ) {
		if (path == null)
			throw new IllegalArgumentException("path is null!");

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
			String projectionPath = tempdir + File.separator + String.format("%05d.dat", getSize());
			Experiment.saveRaw(projection, projectionPath);
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