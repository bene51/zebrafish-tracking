package huisken.opener;

import ij.ImageStack;

public abstract class SPIMStack extends ImageStack {

	public SPIMStack(int width, int height) {
		super(width, height);
	}

	public abstract void addSlice(String path, boolean lastZ);

}
