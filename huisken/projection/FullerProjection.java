package huisken.projection;

import ij.ImagePlus;

import ij.process.FloatProcessor;
import ij.process.ImageProcessor;

import javax.vecmath.Point3f;
import javax.vecmath.Vector3f;

import meshtools.IndexedTriangleMesh;

public class FullerProjection implements MapProjection {

	private SphericalMaxProjection smp;
	private int w;
	private int h;

	private int[][] vIndices;
	private float[][] vertexWeights;

	private int[][][] table = new int[6][5][3];

	public FullerProjection() {
		initTable();
	}

	private final double BLA = 0.5 * Math.sqrt(3);

	private int getTriangle(int x, int y, double s) {
		double h = BLA * s;

		double aint =     y / (2 * BLA) - s;
		double bint = s - y / (2 * BLA);
		double cint = 0;

		int aind = (int)Math.floor((x - aint) / s);
		int bind = (int)Math.floor((x - bint) / s);
		int cind = (int)Math.floor((y - cint) / h);

		int face = -1;
		if(aind >= 0 && aind < table.length &&
			bind >= 0 && bind < table[aind].length &&
			cind >= 0 && cind < table[aind][bind].length) {
			face = table[aind][bind][cind];
		}

		return face;
	}

	@Override
	public void prepareForProjection(SphericalMaxProjection smp, int w) {
		this.smp = smp;
		this.w = w;

		double s = w / 5.5;
		this.h = (int)Math.round(3 * BLA * s);

		float tao = 1.61803399f;
		Icosahedron icosa = new Icosahedron(tao, smp.radius);
		for(Point3f p : icosa.getVertices())
			p.add(smp.center);
		IndexedTriangleMesh flatIcosa = icosa.createFlatVersion((float)s);

		vIndices = new int[w * h][3];
		vertexWeights = new float[w * h][3];

		Point3f[] flatVertices = flatIcosa.getVertices();
		int[] flatFaces = flatIcosa.getFaces();

		Point3f[] icosaVertices = icosa.getVertices();
		int[] icosaFaces = icosa.getFaces();

		Point3f[] vertices = smp.getSphere().getVertices();

		for(int y = 0; y < h; y++) {
			for(int x = 0; x < w; x++) {
				int index = y * w + x;
				int t = getTriangle(x, y, s);
				if(t < 0) {
					vIndices[index][0] = vIndices[index][1] = vIndices[index][2] = -1;
					continue;
				}

				Point3f pf1 = flatVertices[flatFaces[3 * t]];
				Point3f pf2 = flatVertices[flatFaces[3 * t + 1]];
				Point3f pf3 = flatVertices[flatFaces[3 * t + 2]];

				Vector3f v1 = new Vector3f(); v1.sub(pf2, pf1); v1.normalize();
				Vector3f v2 = new Vector3f(); v2.sub(pf3, pf1); v2.normalize();
				Vector3f p  = new Vector3f((x - pf1.x), (y - pf1.y), 0);

				// solve d1.x * v1.x + d2.x * v2.x = p.x
				//       d1.y * v1.y + d2.y * v2.y = p.y
				float d2 = (v1.x * p.y - v1.y * p.x) / (v1.x * v2.y - v1.y * v2.x);
				float d1 = (p.x - d2 * v2.x) / v1.x;
				d1 /= pf1.distance(pf2);
				d2 /= pf1.distance(pf3);

				Point3f p1 = icosaVertices[icosaFaces[3 * t]];
				Point3f p2 = icosaVertices[icosaFaces[3 * t + 1]];
				Point3f p3 = icosaVertices[icosaFaces[3 * t + 2]];

				Vector3f nearest = new Vector3f(p1);
				v1.sub(p2, p1); v1.normalize();
				v2.sub(p3, p1); v2.normalize();
				nearest.scaleAdd(d1 * p1.distance(p2), v1, nearest);
				nearest.scaleAdd(d2 * p1.distance(p3), v2, nearest);

				// project onto sphere
				nearest.sub(nearest, smp.center);
				nearest.normalize();
				Point3f ptmp = new Point3f();
				ptmp.scaleAdd((float)smp.radius, nearest, smp.center);


				smp.getThreeNearestVertexIndices(ptmp, vIndices[index]);

				// interpolate according to distance
				float w0 = 1 / ptmp.distance(vertices[vIndices[index][0]]);
				float w1 = 1 / ptmp.distance(vertices[vIndices[index][1]]);
				float w2 = 1 / ptmp.distance(vertices[vIndices[index][2]]);
				float sum = w0 + w1 + w2;
				vertexWeights[index][0] = w0 / sum;
				vertexWeights[index][1] = w1 / sum;
				vertexWeights[index][2] = w2 / sum;
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
				if(vIndices[index][0] == -1) {
					ip.setf(x, y, 0);
					continue;
				}

				float v0 = vertexWeights[index][0] * maxima[vIndices[index][0]];
				float v1 = vertexWeights[index][1] * maxima[vIndices[index][1]];
				float v2 = vertexWeights[index][2] * maxima[vIndices[index][2]];

				ip.setf(x, y, v0 + v1 + v2);
			}
		}
		return ip;
	}

	private void initTable() {
		for(int a = 0; a < 6; a++)
			for(int b = 0; b < 5; b++)
				for(int c = 0; c < 3; c++)
					table[a][b][c] = -1;

		table[0][0][1] = 12;
		table[0][0][2] = 13;

		table[1][0][0] = 8;
		table[1][0][1] = 0;
		table[1][1][1] = 1;
		table[1][1][2] = 19;

		table[2][1][0] = 10;
		table[2][1][1] = 2;
		table[2][2][1] = 3;
		table[2][2][2] = 14;

		table[3][2][0] = 11;
		table[3][2][1] = 5;
		table[3][3][1] = 4;
		table[3][3][2] = 16;

		table[4][3][0] = 9;
		table[4][3][1] = 6;
		table[4][4][1] = 15;
		table[4][4][2] = 17;

		table[5][4][0] = 7;
		table[5][4][1] = 18;
	}
}
