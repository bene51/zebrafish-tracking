package huisken.network;

import ij.ImagePlus;

import java.io.Serializable;

public class ImageWrapper implements Serializable {

	private ImagePlus image;

	public ImageWrapper(ImagePlus image) {
		this.image = image;
	}

	public ImagePlus getImage() {
		return image;
	}

	private static final long serialVersionUID = 424050848692912672L;

}
