package huisken.network;

import ij.ImagePlus;

import java.io.Serializable;

public class ImageWrapper implements Serializable {

	private byte[] data;
	public final int w, h;

	public ImageWrapper(ImagePlus image) {
		if(image.getType() != ImagePlus.GRAY8)
			throw new IllegalArgumentException("Only 8-bit images are supported at the moment");
		this.data = (byte[])image.getProcessor().getPixels();
		this.w = image.getWidth();
		this.h = image.getHeight();
	}

	public byte[] getData() {
		return data;
	}

	private static final long serialVersionUID = 424050848692912672L;
}
