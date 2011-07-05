package huisken.projection;

import ij.ImagePlus;

import ij.process.FloatProcessor;

public class SimpleLeftWeighter implements FusionWeight {

	private float w2;

	public SimpleLeftWeighter(float w2) {
		this.w2 = w2;
	}

	@Override
	public float getWeight(float x, float y, float z) {
		if(x < w2 - 12) return 1f;
		if(x > w2 + 12) return 0f;
		return (float)(1.0 / (1 + Math.exp(0.5 * (x - w2))));
	}

	public static void main(String[] args) {
		int w = 200, h = 200;
		FloatProcessor fp = new FloatProcessor(w, h);
		SimpleLeftWeighter slw = new SimpleLeftWeighter(w / 2);
		for(int y = 0; y < h; y++)
			for(int x = 0; x < w; x++)
				fp.setf(x, y, slw.getWeight(x, y, 0));
		new ImagePlus("slf", fp).show();
	}
}
