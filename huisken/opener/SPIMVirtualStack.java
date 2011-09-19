package huisken.opener;

import ij.process.ImageProcessor;
import ij.process.ShortProcessor;

import java.util.ArrayList;
import java.util.List;

public class SPIMVirtualStack extends SPIMStack {

	protected List<String> paths = new ArrayList<String>();
	private final int x0, x1, y0, y1, orgW, orgH;

	/** Creates a new, empty virtual stack. */
	public SPIMVirtualStack(int orgW, int orgH, int x0, int x1, int y0, int y1) {
		super(x1 - x0, y1 - y0);
		this.x0 = x0;
		this.x1 = x1;
		this.y0 = y0;
		this.y1 = y1;
		this.orgW = orgW;
		this.orgH = orgH;
	}

	 /** Adds an image to the end of the stack. */
	public void addSlice(String path, boolean lastZ) {
		if (path == null)
			throw new IllegalArgumentException("path is null!");

		paths.add(path);
	}

	/** Does nothing. */
	public void addSlice(String sliceLabel, Object pixels) {
	}

	/** Does nothing.. */
	public void addSlice(String sliceLabel, ImageProcessor ip) {
	}

	/** Does noting. */
	public void addSlice(String sliceLabel, ImageProcessor ip, int n) {
	}

	/** Deletes the specified slice, were 1<=n<=nslices. */
	public void deleteSlice(int n) {
		if(n < 1 || n > paths.size())
			throw new IllegalArgumentException("Argument out of range: " + n);
		paths.remove(n - 1);
	}

	/** Deletes the last slice in the stack. */
	public void deleteLastSlice() {
		if(paths.size() > 0)
			deleteSlice(paths.size());
	}

	/** Returns the pixel array for the specified slice, were 1<=n<=nslices. */
	public Object getPixels(int n) {
		ImageProcessor ip = getProcessor(n);
		return ip == null ? null : ip.getPixels();
	}

	/**
	 * Assigns a pixel array to the specified slice,
	 * were 1<=n<=nslices.
	 */
	public void setPixels(Object pixels, int n) {
	}

	/**
	 * Returns an ImageProcessor for the specified slice,
	 *  were 1<=n<=nslices. Returns null if the stack is empty.
	 */
	public ImageProcessor getProcessor(int n) {
		ImageProcessor ip = null;
		try {
			ip = SPIMExperiment.openRaw(paths.get(n - 1), orgW, orgH, x0, x1, y0, y1);
		} catch(Exception e) {
			e.printStackTrace();
			return null;
		}
		if(!(ip instanceof ShortProcessor))
			ip = ip.convertToShort(true);
		return ip;
	}

	/** Currently not implemented */
	public int saveChanges(int n) {
		return -1;
	}

	 /** Returns the number of slices in this stack. */
	public int getSize() {
		return paths.size();
	}

	/** Returns the label of the Nth image. */
	public String getSliceLabel(int n) {
		return "";
	}

	/** Returns null. */
	public Object[] getImageArray() {
		return null;
	}

	/** Does nothing. */
	public void setSliceLabel(String label, int n) {
	}

	/** Always return true. */
	public boolean isVirtual() {
		return true;
	}

	/** Does nothing. */
	public void trim() {
	}
}
