package huisken.fusion;

import ij.process.ImageProcessor;

public class HistogramFeatures {

	public static int calculateHistogram(ImageProcessor ip, int[] histo, int x, int y, int rx, int ry) {
		for(int i = 0; i < histo.length; i++)
			histo[i] = 0;
		int sum = 0;
		for(int ix = x - rx; ix <= x + rx; ix++) {
			if(ix < 0 || ix >= ip.getWidth())
				continue;
			for(int iy = y - ry; iy <= y + ry; iy++) {
				if(iy < 0 || iy >= ip.getHeight())
					continue;
				histo[ip.get(ix, iy)]++;
				sum++;
			}
		}
		return sum;
	}

	public static int getSum(int[] histo) {
		int sum = 0;
		for(int i = 0; i < histo.length; i++)
			sum =+ histo[i];
		return sum;
	}


	// The different filters come here
	public static float getEntropy(int[] histo, int sum) {
		double e = 0;
		for(int i = 0; i < histo.length; i++) {
			if(histo[i] > 0) {
				double p = histo[i] / (double)sum;
				e += (p * Math.log(p));
			}
		}
		return (float)-e;
	}

	public static float getMin(int[] histo, int sum) {
		for(int i = 0; i < histo.length; i++)
			if(histo[i] > 0)
				return i;
		return 0;
	}

	public static float getMax(int[] histo, int sum) {
		for(int i = histo.length - 1; i >= 0; i--) {
			if(histo[i] > 0)
				return i;
		}
		return 0;
	}

	// e.g. q = 0.25, 0.5, 0.75 etc
	public static float getQuantile(int[] histo, int sum, float q) {
		float quant = q * sum;
		int cumulative = 0;
		for(int i = 0; i < histo.length; i++) {
			if(cumulative + histo[i] <  quant) {
				cumulative = cumulative + histo[i];
				continue;
			}
			// interpolate: cumulative + x * histo[i] = quant
			return i + (quant - cumulative) / (float)histo[i];	
		}
		return 0;
	}

	public static float getContrast(int[] histo, int sum) {
		float min = getMin(histo, sum);//getQuantile(histo, sum, 0.2f);
		float max = getMax(histo, sum);//getQuantile(histo, sum, 0.8f);
		if(max == min)
			return 0f;

		return (max - min);// / (max + min);
	}

	public static float getMean(int[] histo, int sum) {
		float mean = 0;
		for(int i = 0; i < histo.length; i++)
			mean += histo[i] * i;

		return mean / histo.length;
	}

	public static float getVariance(int[] histo, int sum) {
		float mean = getMean(histo, sum);
		float variance = 0;
		for(int i = 0; i < histo.length; i++) {
			float t = i - mean;
			variance += histo[i] * t * t;
		}
		return variance / histo.length;
	}

	// Sturrok 2008: Analysis of Bimodality in Histograms Formed from GALLEX
	// and GNO Solar Neutrino Data
	public static float getBimodality(int[] histo, int sum) {
		double m = 2f;
		double c = 0, s = 0;
		double[] cdf = new double[histo.length + 1];
		for(int i = 0; i < histo.length; i++) {
			cdf[i + 1] = cdf[i] + histo[i] / (double)sum;
		}

		for(int n = 0; n < cdf.length; n++) {
			double arg = 2 * Math.PI * m * cdf[n];
			c += Math.cos(arg);
			s += Math.sin(arg);
		}
		return (float) (((c * c) + (s * s)) / cdf.length);
	}

	public static int getFirstMode(int[] histo) {
		int prev = histo[0];
		for(int i = 1; i < histo.length; i++) {
			if(histo[i] < prev)
				return i - 1;
		}
		return histo.length - 1;
	}

	public static int[] smooth(int[] histo, int r) {
		int[] newHisto = new int[histo.length];
		for(int i = 0; i < histo.length; i++) {
			double sum = 0;
			for(int j = -r; j <= r; j++)
				sum += getvalue(histo, i + j);
			newHisto[i] = (int)(sum / r + 0.5);
		}
		return newHisto;
	}

	private static int getvalue(int[] histo, int i) {
		if(i >= 0 && i < histo.length)
			return histo[i];
		return 0;
	}
}
