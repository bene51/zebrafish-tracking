package huisken.projection;

import ij.ImagePlus;

import ij.process.FloatProcessor;

public class SimpleUpWeighter implements FusionWeight {

	private float d2;

	public SimpleUpWeighter(float d2) {
		this.d2 = d2;
	}

	@Override
	public float getWeight(float x, float y, float z) {
		if(z < d2 - 12) return 1f;
		if(z > d2 + 12) return 0f;
		return (float)(1.0 / (1 + Math.exp(0.5 * (z - d2))));
	}
}
