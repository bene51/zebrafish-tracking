package huisken.projection;

import customnode.CustomMesh;
import customnode.CustomTriangleMesh;
import customnode.WavefrontExporter;

import ij.ImagePlus;
import ij.ImageStack;

import ij.measure.Calibration;

import ij.process.ImageProcessor;
import ij.process.FloatProcessor;

import java.io.DataOutputStream;
import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;

import javax.vecmath.Point3i;
import javax.vecmath.Point3f;
import javax.vecmath.Vector3f;

import meshtools.IndexedTriangleMesh;

import fiji.util.node.Leaf;
import fiji.util.KDTree;
import fiji.util.NearestNeighborSearch;
import fiji.util.NNearestNeighborSearch;

import java.util.Map;

import vib.FastMatrix;

public class SphericalMaxProjection {

	final Point3f center;
	final float radius;

	private final int w, h, d;
	private final double pw, ph, pd;

	private final Icosahedron icosa;
	private final IndexedTriangleMesh sphere;
	private final HashMap<Point3f, Integer> vertexToIndex;
	private final NNearestNeighborSearch<Node3D> nnSearch;
	private final Point4[] lut;

	private float[] maxima;
	private float[] weights;

	public SphericalMaxProjection(ImagePlus mask, Point3f center, float radius, FusionWeight weighter) {
		this.center = center;
		this.radius = radius;

		Calibration c = mask.getCalibration();
		pw = c.pixelWidth;
		ph = c.pixelHeight;
		pd = c.pixelDepth;

		w = mask.getWidth();
		h = mask.getHeight();
		d = mask.getStackSize();

		// calculate the sphere coordinates
		float tao = 1.61803399f;
		icosa = new Icosahedron(tao, radius);

		// use some cleverness for determining a meaningful subdivision.
		// maybe ensure that the final edge length is comparable to
		// the minimum pixel dimension
		double dpixel = Math.min(pw, Math.min(ph, pd));
		// the edge length of a icosahedron is close to the radius of the circumscribed sphere
		int subd = (int)Math.round(radius / dpixel);
System.out.println("subd = " + subd);
subd /= 4;
		sphere = icosa.createBuckyball(radius, subd);
		for(Point3f p : sphere.getVertices())
			p.add(center);
		ArrayList<Node3D> nodes = new ArrayList<Node3D>(sphere.nVertices);
		for(Point3f p : sphere.getVertices())
			nodes.add(new Node3D(p));
		KDTree<Node3D> tree = new KDTree<Node3D>(nodes);
		nnSearch = new NNearestNeighborSearch<Node3D>(tree);

		vertexToIndex = new HashMap<Point3f, Integer>();
		for(int i = 0; i < sphere.nVertices; i++)
			vertexToIndex.put(sphere.getVertices()[i], i);

		this.lut = calculateLUT(weighter);
	}

	public void saveSphere(String objpath) throws IOException {
		Map<String, CustomMesh> mesh = new HashMap<String, CustomMesh>();
		mesh.put("Sphere", new CustomTriangleMesh(sphere.createMesh()));
		WavefrontExporter.save(mesh, objpath);
	}

	public void saveMaxima(String path) throws IOException {
		DataOutputStream out = new DataOutputStream(
			new BufferedOutputStream(
				new FileOutputStream(path)));
		for(float f : maxima)
			out.writeFloat(f);
		out.close();
	}

	public SphericalMaxProjection(ImagePlus mask, IndexedTriangleMesh sphere, Point3f center, float radius, FusionWeight weighter) {
		this.center = center;
		this.radius = radius;

		Calibration c = mask.getCalibration();
		pw = c.pixelWidth;
		ph = c.pixelHeight;
		pd = c.pixelDepth;

		w = mask.getWidth();
		h = mask.getHeight();
		d = mask.getStackSize();

		this.icosa = null;
		this.sphere = sphere; // TODO make sure we are not changing the original

		ArrayList<Node3D> nodes = new ArrayList<Node3D>(sphere.nVertices);
		for(Point3f p : sphere.getVertices())
			nodes.add(new Node3D(p));
		KDTree<Node3D> tree = new KDTree<Node3D>(nodes);
		nnSearch = new NNearestNeighborSearch<Node3D>(tree);

		vertexToIndex = new HashMap<Point3f, Integer>();
		for(int i = 0; i < sphere.nVertices; i++)
			vertexToIndex.put(sphere.getVertices()[i], i);

		this.lut = calculateLUT(weighter);
	}

	public IndexedTriangleMesh getSphere() {
		return sphere;
	}

	public float[] getMaxima() {
		return maxima;
	}

	public void addMaxima(float[] maxima) {
		for(int i = 0; i < this.maxima.length; i++)
			this.maxima[i] += maxima[i];
	}

	public void scaleMaxima(FusionWeight weighter) {
		for(int vIndex = 0; vIndex < sphere.nVertices; vIndex++) {
			Point3f vertex = sphere.getVertices()[vIndex];
			maxima[vIndex] *= weighter.getWeight(vertex.x, vertex.y, vertex.z);
		}
	}

	private Point4[] calculateLUT(FusionWeight weighter) {
		Vector3f dx = new Vector3f();
		Point3f pos = new Point3f();
		Point3i imagePos = new Point3i();
		ArrayList<Point4> correspondences = new ArrayList<Point4>();
		weights = new float[sphere.nVertices];

		for(int vIndex = 0; vIndex < sphere.nVertices; vIndex++) {
			Point3f vertex = sphere.getVertices()[vIndex];
			weights[vIndex] = weighter.getWeight(vertex.x, vertex.y, vertex.z);
			if(weights[vIndex] == 0)
				continue;

			dx.sub(vertex, center);
			dx.normalize();
			float scale = scaleForOnePixel(dx);
			int k = (int)Math.round(0.2f * radius / scale);

			for(int i = -k; i <= k; i++) {
				pos.scaleAdd(i * scale, dx, vertex);
				toImagePos(pos, imagePos);

				correspondences.add(new Point4(imagePos, vIndex));
			}
		}

		// sort according to ascending z coordinate
		Collections.sort(correspondences, new Comparator<Point4>() {
			public int compare(Point4 p1, Point4 p2) {
				if(p1.z < p2.z) return -1;
				if(p1.z > p2.z) return +1;
				return 0;
			}
		});

		return correspondences.toArray(new Point4[] {});
	}

	public void project(ImagePlus image) {
		ImageStack stack = image.getStack();
		int wh = w * h;
		maxima = new float[sphere.nVertices];
		int lutIndex = 0;
		for(int z = 0; z < d; z++) {
			ImageProcessor ip = stack.getProcessor(z + 1);
			Point4 p;
			while(lutIndex < lut.length && (p = (Point4)lut[lutIndex++]).z == z) {
				float v = ip.getf(p.x, p.y);
				if(v > maxima[p.vIndex])
					maxima[p.vIndex] = v;
			}
			lutIndex--;
		}
		for(int i = 0; i < maxima.length; i++)
			maxima[i] *= weights[i];
	}

	Point3f tmp = new Point3f();
	Point3f[] nn = new Point3f[3];
	public float get(float longitude, float latitude) {
		return get(Math.sin(longitude), Math.cos(longitude), Math.sin(latitude), Math.cos(latitude));
	}

	public float get(double sinLong, double cosLong, double sinLat, double cosLat) {
		// get point on sphere
		tmp.x = (float)(center.x + radius * cosLat * cosLong);
		tmp.y = (float)(center.y + radius * cosLat * sinLong);
		tmp.z = (float)(center.z + radius * sinLat);
		return get(tmp);
	}

	public float get(Point3f p) {
		// get three nearest neighbors
		Node3D[] nn = nnSearch.findNNearestNeighbors(new Node3D(p), 3);
		// interpolate according to distance
		float d0 = 1 / p.distance(nn[0].p);
		float d1 = 1 / p.distance(nn[1].p);
		float d2 = 1 / p.distance(nn[2].p);
		float sum = d0 + d1 + d2;
		d0 /= sum;
		d1 /= sum;
		d2 /= sum;
		float v0 = d0 * maxima[vertexToIndex.get(nn[0].p)];
		float v1 = d1 * maxima[vertexToIndex.get(nn[1].p)];
		float v2 = d2 * maxima[vertexToIndex.get(nn[2].p)];
		return v0 + v1 + v2;
	}

	public void getThreeNearestVertexIndices(Point3f p, int[] ret) {
		Node3D[] nn = nnSearch.findNNearestNeighbors(new Node3D(p), 3);
		ret[0] = vertexToIndex.get(nn[0].p);
		ret[1] = vertexToIndex.get(nn[1].p);
		ret[2] = vertexToIndex.get(nn[2].p);
	}

	final boolean inImage(Point3i p) {
		return p.x >= 0 && p.y >= 0 && p.z >= 0 &&
			p.x < w && p.y < h && p.z < d;
	}

	final void toImagePos(Point3f input, Point3i output) {
		output.x = (int)Math.round(input.x / pw);
		output.y = (int)Math.round(input.y / ph);
		output.z = (int)Math.round(input.z / pd);
	}

	final float scaleForOnePixel(Vector3f dir) {
		return 1f / (float)Math.max(Math.abs(dir.x / pw), Math.max(
			Math.abs(dir.y / ph), Math.abs(dir.z / pd)));
	}

	/**
	 * Holds the image coordinates together with the vertex index.
	 */
	private final class Point4 {
		int vIndex;
		int x, y, z;

		public Point4(Point3i p, int vIndex) {
			this.x = p.x;
			this.y = p.y;
			this.z = p.z;
			this.vIndex = vIndex;
		}
	}


	private static class Node3D implements Leaf<Node3D> {

		final Point3f p;

		public Node3D(final Point3f p) {
			this.p = p;
		}

		public Node3D(final Node3D node) {
			this.p = (Point3f)node.p.clone();
		}

		@Override
		public boolean isLeaf() {
			return true;
		}

		public boolean equals(final Node3D o) {
	                 return p.equals(o.p);
		}

		@Override
		public float distanceTo(final Node3D o) {
			return p.distance(o.p);
		}

		@Override
		public float get(final int k) {
			switch(k) {
				case 0: return p.x;
				case 1: return p.y;
				case 2: return p.z;
			}
			return 0f;
		}

		@Override
		public String toString() {
			return p.toString();
		}

		@Override
		public Node3D[] createArray(final int n) {
			return new Node3D[n];
		}

		@Override
		public int getNumDimensions() {
			return 3;
		}
	}
}
