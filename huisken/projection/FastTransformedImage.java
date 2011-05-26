package huisken.projection;

import vib.TransformedImage;
import vib.InterpolatedImage;

import ij.ImagePlus;

import java.util.Arrays;

public class FastTransformedImage extends TransformedImage {

	public static final int TILEX = 64, TILEY = 64, TILEZ = 32;
	public static final float RATIO = 0.1f;

	private Range[] ranges;

	public FastTransformedImage(ImagePlus orig, ImagePlus transform) {
		super(orig, transform);
		findInterestingRanges();
	}

	private FastTransformedImage(InterpolatedImage orig, InterpolatedImage transform) {
		super(orig.getImage(), transform.getImage());
	}

	// get 10% of the ranges with the highest entropy
	private void findInterestingRanges() {

		int nx = (int)Math.ceil(orig.w / (double)TILEX);
		int ny = (int)Math.ceil(orig.h / (double)TILEY);
		int nz = (int)Math.ceil(orig.d / (double)TILEZ);
		
		Range[] ranges = new Range[nx * ny * nz];
		int index = 0;
		
		for(int z = 0; z < nz; z++) {
			int z0 = z * TILEZ;
			int z1 = Math.min(orig.d, z0 + TILEZ);
			for(int y = 0; y < ny; y++) {
				int y0 = y * TILEY;
				int y1 = Math.min(orig.h, y0 + TILEY);
				for(int x = 0; x < nx; x++) {
					int x0 = x * TILEX;
					int x1 = Math.min(orig.w, x0 + TILEX);
					
					InterpolatedImage.Iterator it = orig.iterator(
						false, x0, y0, z0, x1, y1, z1);
					int[] histo = new int[256];
					int sum = 0;
					while(it.next() != null) {
						histo[orig.getNoCheck(it.i, it.j, it.k)]++;
						sum++;
					}
					float measure = HistogramFeatures.getEntropy(histo, sum);

					ranges[index++] = new Range(x0, y0, z0, x1, y1, z1, measure);
				}
			}
		}

		Arrays.sort(ranges);
		this.ranges = ranges;
		int n = (int)Math.ceil(RATIO * nx * ny * nz);
		this.ranges = new Range[n];
		System.arraycopy(ranges, 0, this.ranges, 0, n);
	}

	@Override
	public FastTransformedImage resample(int factor) {
		TransformedImage t = super.resample(factor);
		FastTransformedImage res = new FastTransformedImage(t.orig, t.transform);
		res.measure = t.measure;

		res.ranges = new Range[this.ranges.length];
		for(int r = 0; r < ranges.length; r++) {
			res.ranges[r] = new Range(
				ranges[r].x0 / factor,
				ranges[r].y0 / factor,
				ranges[r].z0 / factor,
				(ranges[r].x1 + factor - 1) / factor,
				(ranges[r].y1 + factor - 1) / factor,
				(ranges[r].z1 + factor - 1) / factor,
				ranges[r].measure);
		}
		return res;
	}

	@Override
	public float getDistance() {
		float d = 0;
		for(Range r : ranges)
			d += getDistance(r);
		return d;
	}

	public float getDistance(Range r) {
		measure.reset();
		Iterator iter = new Iterator(false, r.x0, r.y0, r.z0, r.x1, r.y1, r.z1);
		while (iter.next() != null) {
			float v1 = orig.getNoCheck(iter.i, iter.j, iter.k);
			float v2 = (float)transform.interpol.get(
				iter.x, iter.y, iter.z);
			measure.add(v1, v2);
		}
		return measure.distance();

	}

	private class Range implements Comparable<Range> {
		int x0, y0, z0;
		int x1, y1, z1;
		float measure;

		Range(int x0, int y0, int z0, int x1, int y1, int z1, float measure) {
			this.x0 = x0;
			this.x1 = x1;
			this.y0 = y0;
			this.y1 = y1;
			this.z0 = z0;
			this.z1 = z1;
			this.measure = measure;
		}

		/* reverse sorting */
		public int compareTo(Range r) {
			if(this.measure < r.measure) return +1;
			if(this.measure > r.measure) return -1;
			return 0;
		}

		public String toString() {
			return "(" + x0 + ", " + y0 + ", " + z0 + ") - (" + x1 + ", " + y1 + ", " + z1 + ")";
		}
	}
}
