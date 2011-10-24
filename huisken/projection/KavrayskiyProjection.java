package huisken.projection;

import ij.process.FloatProcessor;
import ij.process.ImageProcessor;

import javax.vecmath.Point3f;

public class KavrayskiyProjection implements MapProjection {

	private SphericalMaxProjection smp;
	private int w;
	private int h;

	private int[][] vIndices;
	private float[][] vertexWeights;

	@Override
	public void prepareForProjection(SphericalMaxProjection smp, int w) {
		this.smp = smp;
		this.w = w;

		// calculate pixel width in rad
		double dx = Math.sqrt(3) * Math.PI / w;
		// calculate maximum y for a max angle of 90 degree
		double r90 = 90 * Math.PI / 180.0;

		double maxy = r90;
		int h2 = (int)Math.round(maxy / dx);
		h = 2 * h2;


		float[] sinLongs = new float[w * h];
		float[] cosLongs = new float[w * h];
		float[] sinLats  = new float[w * h];
		float[] cosLats  = new float[w * h];

		for(int y = 0; y < h; y++) {
			for(int x = 0; x < w; x++) {
				int index = y * w + x;
				double fx = dx * (x - w/2);
				double fy = -dx * (y - h2);

				double lat = fy;
				double lon = (2 * Math.PI * fx) / (3 * Math.sqrt(
					Math.PI * Math.PI / 3 - lat * lat));

				if(lat > Math.PI / 2 || lat < -Math.PI / 2 ||
						lon > Math.PI || lon < -Math.PI)
					continue;

				sinLongs[index] = (float)Math.sin(lon);
				cosLongs[index] = (float)Math.cos(lon);
				sinLats[index]  = (float)Math.sin(lat);
				cosLats[index]  = (float)Math.cos(lat);
			}
		}

		vIndices = new int[w * h][3];
		vertexWeights = new float[w * h][3];
		Point3f p = new Point3f();
		Point3f[] vertices = smp.getSphere().getVertices();
		for(int y = 0; y < h; y++) {
			for(int x = 0; x < w; x++) {
				int index = y * w + x;

				if(sinLongs[index] == 0 && sinLats[index] == 0 &&
					cosLongs[index] == 0 && cosLats[index] == 0)
					continue;

				smp.getPoint(sinLongs[index], cosLongs[index], sinLats[index], cosLats[index], p);

				smp.getThreeNearestVertexIndices(p, vIndices[index]);

				// interpolate according to distance
				float d0 = 1 / p.distance(vertices[vIndices[index][0]]);
				float d1 = 1 / p.distance(vertices[vIndices[index][1]]);
				float d2 = 1 / p.distance(vertices[vIndices[index][2]]);
				float sum = d0 + d1 + d2;
				vertexWeights[index][0] = d0 / sum;
				vertexWeights[index][1] = d1 / sum;
				vertexWeights[index][2] = d2 / sum;
			}
		}
	}

	@Override
	public ImageProcessor project() {
		FloatProcessor ip = new FloatProcessor(w, h);
		float[] maxima = smp.getMaxima();
		for(int y = 0; y < h; y++) {
			for(int x = 0; x < w; x++) {
				int index = y * w + x;

				float v0 = vertexWeights[index][0] * maxima[vIndices[index][0]];
				float v1 = vertexWeights[index][1] * maxima[vIndices[index][1]];
				float v2 = vertexWeights[index][2] * maxima[vIndices[index][2]];

				ip.setf(x, y, v0 + v1 + v2);
			}
		}
		return ip;
	}
}
