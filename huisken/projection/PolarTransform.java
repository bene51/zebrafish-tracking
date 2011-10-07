package huisken.projection;

import ij.process.FloatProcessor;
import ij.process.ImageProcessor;

import javax.vecmath.Point3f;

public class PolarTransform implements MapProjection {

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
		double dx = 2 * Math.PI / w;

		this.h = w / 2;
		int h2 = h / 2;



		float[] sinLongs = new float[w];
		float[] cosLongs = new float[w];
		for(int x = 0; x < w; x++) {
			double fx = x * dx;
			sinLongs[x] = (float)Math.sin(fx);
			cosLongs[x] = (float)Math.cos(fx);
		}
		
		float[] sinLats = new float[h];
		float[] cosLats = new float[h];
		for(int y = 0; y < h; y++) {
			double fy = dx * (y - h2);
			sinLats[y] = (float)Math.sin(fy);
			cosLats[y] = (float)Math.cos(fy);
		}

		vIndices = new int[w * h][3];
		vertexWeights = new float[w * h][3];
		Point3f p = new Point3f();
		Point3f[] vertices = smp.getSphere().getVertices();
		for(int y = 0; y < h; y++) {
			for(int x = 0; x < w; x++) {
				int index = y * w + x;

				p.x = (float)(smp.center.x + smp.radius * cosLats[y] * cosLongs[x]);
				p.y = (float)(smp.center.y + smp.radius * cosLats[y] * sinLongs[x]);
				p.z = (float)(smp.center.z + smp.radius * sinLats[y]);

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
