package huisken.network;

import ij.ImagePlus;

import java.io.Serializable;

public class ImageWrapper implements Serializable {

<<<<<<< .merge_file_eQgyYK
	private short[] data;
	public final int w, h;

	public ImageWrapper(ImagePlus image) {
		if(image.getType() != ImagePlus.GRAY16)
			throw new IllegalArgumentException("Only 16bit images are supported at the moment");
		this.data = (short[])image.getProcessor().getPixels();
		this.w = image.getWidth();
		this.h = image.getHeight();
	}

	public short[] getData() {
		return data;
	}

	private static final long serialVersionUID = 424050848692912672L;
=======
	private ImagePlus image;

	public ImageWrapper(ImagePlus image) {
		this.image = image;
	}

	public ImagePlus getImage() {
		return image;
	}

	private static final long serialVersionUID = 424050848692912672L;

>>>>>>> .merge_file_xpc6q4
}
