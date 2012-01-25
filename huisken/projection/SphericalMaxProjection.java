package huisken.projection;

import ij.process.ImageProcessor;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.vecmath.Matrix4f;
import javax.vecmath.Point3f;
import javax.vecmath.Point3i;
import javax.vecmath.Vector3f;

import meshtools.IndexedTriangleMesh;
import vib.FastMatrix;
import fiji.util.KDTree;
import fiji.util.NNearestNeighborSearch;
import fiji.util.node.Leaf;

public class SphericalMaxProjection {

	// These fields are set in prepareForProjection();
	private int[][] lutx;
	private int[][] luty;
	private int[][] luti;
	private float[] maxima;

	// These fields must be set in the constructor and
	// contain info about the sphere geometry
	final Point3f center;
	final float radius;
	private final IndexedTriangleMesh sphere;
	private final HashMap<Point3f, Integer> vertexToIndex;
	private final NNearestNeighborSearch<Node3D> nnSearch;

	public SphericalMaxProjection(IndexedTriangleMesh sphere, Point3f center, float radius) {
		this(sphere, center, radius, null);
	}

	public SphericalMaxProjection(IndexedTriangleMesh sph, Point3f c, float radius, FastMatrix transform) {
		this.center = new Point3f(c);
		this.radius = radius;

		this.sphere = (IndexedTriangleMesh)sph.clone();

		if(transform != null && !transform.isIdentity()) {
			for(Point3f v : sphere.getVertices()) {
				transform.apply(v.x, v.y, v.z);
				v.set((float)transform.x, (float)transform.y, (float)transform.z);
			}
			transform.apply(center.x, center.y, center.z);
			center.set((float)transform.x, (float)transform.y, (float)transform.z);
		}

		ArrayList<Node3D> nodes = new ArrayList<Node3D>(sphere.nVertices);
		for(Point3f p : sphere.getVertices())
			nodes.add(new Node3D(p));
		KDTree<Node3D> tree = new KDTree<Node3D>(nodes);
		nnSearch = new NNearestNeighborSearch<Node3D>(tree);

		vertexToIndex = new HashMap<Point3f, Integer>();
		for(int i = 0; i < sphere.nVertices; i++)
			vertexToIndex.put(sphere.getVertices()[i], i);

	}

	public SphericalMaxProjection(String objfile) throws IOException {
		this.sphere = loadSphere(objfile);

		ArrayList<Node3D> nodes = new ArrayList<Node3D>(sphere.nVertices);
		double mx = 0, my = 0, mz = 0;
		for(Point3f p : sphere.getVertices()) {
			nodes.add(new Node3D(p));
			mx += p.x;
			my += p.y;
			mz += p.z;
		}
		this.center = new Point3f(
			(float)(mx / sphere.nVertices),
			(float)(my / sphere.nVertices),
			(float)(mz / sphere.nVertices));
		this.radius = sphere.getVertices()[0].distance(center);


		KDTree<Node3D> tree = new KDTree<Node3D>(nodes);
		nnSearch = new NNearestNeighborSearch<Node3D>(tree);

		vertexToIndex = new HashMap<Point3f, Integer>();
		for(int i = 0; i < sphere.nVertices; i++)
			vertexToIndex.put(sphere.getVertices()[i], i);
	}

	public Point3f getCenter() {
		return center;
	}

	public float getRadius() {
		return radius;
	}

	public static void saveSphere(IndexedTriangleMesh sphere, String objpath) throws IOException {
		PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(objpath)));
		out.println("# OBJ File");
		out.println("g Sphere");
		for(Point3f v : sphere.getVertices())
			out.println("v " + v.x + " " + v.y + " " + v.z);
		out.println("s 1");
		int[] faces = sphere.getFaces();
		for(int i = 0; i < faces.length; i += 3)
			out.println("f " + faces[i] + " " + faces[i+1] + " " + faces[i+2]);
		out.close();
	}

	public void saveSphere(String objpath) throws IOException {
		saveSphere(sphere, objpath);
	}

	public static IndexedTriangleMesh loadSphere(String objpath) throws IOException {
		BufferedReader in = new BufferedReader(new FileReader(objpath));
		ArrayList<Point3f> points = new ArrayList<Point3f>();
		ArrayList<Integer> faces = new ArrayList<Integer>();
		String line = in.readLine();
		while(line != null && !line.startsWith("v "))
			line = in.readLine();

		while(line != null && line.startsWith("v ")) {
			String[] toks = line.split("\\s");
			points.add(new Point3f(
				Float.parseFloat(toks[1]),
				Float.parseFloat(toks[2]),
				Float.parseFloat(toks[3])));
			line = in.readLine();
		}

		while(line != null && !line.startsWith("f "))
			line = in.readLine();

		while(line != null && line.startsWith("f ")) {
			String[] toks = line.split("\\s");
			faces.add(Integer.parseInt(toks[1]));
			faces.add(Integer.parseInt(toks[2]));
			faces.add(Integer.parseInt(toks[3]));
			line = in.readLine();
		}

		Point3f[] vertices = new Point3f[points.size()];
		points.toArray(vertices);

		int[] f = new int[faces.size()];
		for(int i = 0; i < f.length; i++)
			f[i] = faces.get(i);
		return new IndexedTriangleMesh(vertices, f);
	}

	public void saveMaxima(String path) throws IOException {
		saveFloatData(maxima, path);
	}

	private static void saveFloatData(float[] data, String path) throws IOException {
		DataOutputStream out = new DataOutputStream(
			new BufferedOutputStream(
				new FileOutputStream(path)));
		for(float f : data)
			out.writeFloat(f);
		out.close();
	}

	public void loadMaxima(String file) throws IOException {
		maxima = loadFloatData(file);
	}

	private float[] loadFloatData(String file) throws IOException {
		float[] data = new float[sphere.nVertices];
		DataInputStream in = new DataInputStream(
			new BufferedInputStream(
				new FileInputStream(file)));
		for(int i = 0; i < data.length; i++) {
			try {
				data[i] = in.readFloat();
			} catch(EOFException e) {
				break;
			}
		}
		in.close();
		return data;
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

	public void smooth() {
		int[] nNeighbors = new int[maxima.length];
		float[] newMaxima = new float[maxima.length];
		System.arraycopy(maxima, 0, newMaxima, 0, maxima.length);

		int[] faces = sphere.getFaces();
		for(int i = 0; i < sphere.nFaces; i += 3) {
			int f1 = faces[i];
			int f2 = faces[i + 1];
			int f3 = faces[i + 2];
			nNeighbors[f1] += 2;
			newMaxima[f1] += maxima[f2];
			newMaxima[f1] += maxima[f3];
			nNeighbors[f2] += 2;
			newMaxima[f2] += maxima[f1];
			newMaxima[f2] += maxima[f3];
			nNeighbors[f3] += 2;
			newMaxima[f3] += maxima[f1];
			newMaxima[f3] += maxima[f2];
		}
		for(int i = 0; i < newMaxima.length; i++)
			newMaxima[i] /= (nNeighbors[i] + 1);
		maxima = newMaxima;
	}

	public boolean[] isMaximum() {
		boolean[] maxs = new boolean[maxima.length];
		for(int i = 0; i < maxs.length; i++)
			maxs[i] = true;
		int[] faces = sphere.getFaces();
		for(int i = 0; i < sphere.nFaces; i += 3) {
			int f1 = faces[i];
			int f2 = faces[i + 1];
			int f3 = faces[i + 2];
			float m1 = maxima[f1];
			float m2 = maxima[f2];
			float m3 = maxima[f3];

			if(m1 <= m2 || m1 <= m3)
				maxs[f1] = false;
			if(m2 <= m1 || m2 <= m3)
				maxs[f2] = false;
			if(m3 <= m1 || m3 <= m1)
				maxs[f3] = false;
		}
		return maxs;
	}

	public void applyTransform(Matrix4f matrix) {
		Matrix4f inverse = new Matrix4f(matrix);
		inverse.invert();
		applyInverseTransform(inverse);
	}

	public void applyTransform(FastMatrix matrix) {
		applyInverseTransform(matrix.inverse());
	}

	public void applyInverseTransform(FastMatrix inverse) {
		float[] newmaxima = new float[sphere.nVertices];
		Point3f p = new Point3f();
		Point3f[] vertices = sphere.getVertices();
		for(int i = 0; i < vertices.length; i++) {
			p.set(vertices[i]);
			inverse.apply(p.x, p.y, p.z);
			p.set((float)inverse.x, (float)inverse.y, (float)inverse.z);
			newmaxima[i] = getValueOfNearestNeighbor(p); // get((float)lon, (float)lat);
		}
		maxima = newmaxima;
	}

	public void applyInverseTransform(Matrix4f inverse) {
		float[] newmaxima = new float[sphere.nVertices];
		Point3f p = new Point3f();
		Point3f[] vertices = sphere.getVertices();
		for(int i = 0; i < vertices.length; i++) {
			p.set(vertices[i]);
			inverse.transform(p);
			newmaxima[i] = getValueOfNearestNeighbor(p); // ((float)lon, (float)lat);
		}
		maxima = newmaxima;
	}

	public void scaleMaxima(FusionWeight weighter) {
		for(int vIndex = 0; vIndex < sphere.nVertices; vIndex++) {
			Point3f vertex = sphere.getVertices()[vIndex];
			maxima[vIndex] *= weighter.getWeight(vertex.x, vertex.y, vertex.z);
		}
	}

	public void prepareForProjection(final int w, final int h, final int d, final double pw, final double ph, final double pd, final FusionWeight weighter) {

		final int nProcessors = Runtime.getRuntime().availableProcessors();
		ExecutorService exec = Executors.newFixedThreadPool(nProcessors);

		@SuppressWarnings("unchecked")
		final ArrayList<Point3i>[] all_correspondences = new ArrayList[d];
		for(int i = 0; i < d; i++)
			all_correspondences[i] = new ArrayList<Point3i>();

		for(int proc = 0; proc < nProcessors; proc++) {
			final int currentProc = proc;
			exec.execute(new Runnable() {
				@Override
				public void run() {
					Vector3f dx = new Vector3f();
					Point3f pos = new Point3f();

					@SuppressWarnings("unchecked")
					ArrayList<Point3i>[] correspondences = new ArrayList[d];
					for(int i = 0; i < d; i++)
						correspondences[i] = new ArrayList<Point3i>();

					int nVerticesPerThread = (int)Math.ceil(sphere.nVertices / (double)nProcessors);
					int startV = currentProc * nVerticesPerThread;
					int lenV = Math.min((currentProc + 1) * nVerticesPerThread, sphere.nVertices);
					for(int vIndex = startV; vIndex < lenV; vIndex++) {
						Point3f vertex = sphere.getVertices()[vIndex];
						float weight = weighter.getWeight(vertex.x, vertex.y, vertex.z);
						if(weight == 0)
							continue;

						dx.sub(vertex, center);
						dx.normalize();

						// calculate the distance needed to move to the neighbor pixel
						float scale = 1f / (float)Math.max(Math.abs(dx.x / pw), Math.max(
								Math.abs(dx.y / ph), Math.abs(dx.z / pd)));

						int k = Math.round(0.2f * radius / scale);

						for(int i = -k; i <= k; i++) {
							pos.scaleAdd(i * scale, dx, vertex);

							// calculate the position in pixel dims
							int x = (int)Math.round(pos.x / pw);
							int y = (int)Math.round(pos.y / ph);
							int z = (int)Math.round(pos.z / pd);

							// only add it if the pixel is inside the image
							if(x >= 0 && x < w && y >= 0 && y < h && z >= 0 && z < d)
								correspondences[z].add(new Point3i(x, y, vIndex));
						}
					}
					synchronized(SphericalMaxProjection.this) {
						System.out.println("processor " + currentProc + ": before" + SphericalMaxProjection.this.hashCode());
						for(int i = 0; i < d; i++) {
							try {
								all_correspondences[i].addAll(correspondences[i]);
							} catch(Throwable t) {
								System.out.println("i = " + i);
								System.out.println(all_correspondences[i].size());
								System.out.println(correspondences[i].size());
							}
						}
						System.out.println("processor " + currentProc + ": after " + SphericalMaxProjection.this.hashCode());
					}
				}
			});
		}

		try {
			exec.shutdown();
			exec.awaitTermination(30, TimeUnit.MINUTES);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		lutx = new int[d][];
		luty = new int[d][];
		luti = new int[d][];
		for(int i = 0; i < d; i++) {
			int l = all_correspondences[i].size();
			lutx[i] = new int[l];
			luty[i] = new int[l];
			luti[i] = new int[l];

			for(int j = 0; j < l; j++) {
				Point3i p = all_correspondences[i].get(j);
				lutx[i][j] = p.x;
				luty[i][j] = p.y;
				luti[i][j] = p.z;
			}
		}
	}

	public void resetMaxima() {
		maxima = new float[sphere.nVertices];
	}

	/*
	 * z starts with 0;
	 */
	public void projectPlane(int z, ImageProcessor ip) {
		for(int i = 0; i < luti[z].length; i++) {
			float v = ip.getf(lutx[z][i], luty[z][i]);
			if(v > maxima[luti[z][i]])
				maxima[luti[z][i]] = v;
		}
	}

	private final Point3f tmp = new Point3f();
	// in radians
	public float get(float longitude, float latitude) {
		return get(Math.sin(longitude), Math.cos(longitude), Math.sin(latitude), Math.cos(latitude));
	}

	public void getPoint(float longitude, float latitude, Point3f ret) {
		getPoint(Math.sin(longitude), Math.cos(longitude), Math.sin(latitude), Math.cos(latitude), ret);
	}

	public void getPoint(double sinLong, double cosLong, double sinLat, double cosLat, Point3f ret) {
		ret.z = (float)(center.z + radius * cosLat * cosLong);
		ret.x = (float)(center.x - radius * cosLat * sinLong);
		ret.y = (float)(center.y - radius * sinLat);
	}

	public float get(double sinLong, double cosLong, double sinLat, double cosLat) {
		// get point on sphere
		getPoint(sinLong, cosLong, sinLat, cosLat, tmp);
		return getValueOfNearestNeighbor(tmp);
	}

	public float getValueOfNearestNeighbor(Point3f p) {
		// get three nearest neighbors
		Node3D[] nn = nnSearch.findNNearestNeighbors(new Node3D(p), 3);
		int i0 = vertexToIndex.get(nn[0].p);
		int i1 = vertexToIndex.get(nn[1].p);
		int i2 = vertexToIndex.get(nn[2].p);

		// interpolate according to distance
		float d0 = p.distance(nn[0].p);
		float d1 = p.distance(nn[1].p);
		float d2 = p.distance(nn[2].p);

		if(d0 == 0) return maxima[i0];
		if(d1 == 0) return maxima[i1];
		if(d2 == 0) return maxima[i2];

		float sum = 1 / d0 + 1 / d1 + 1 / d2;

		d0 = 1 / d0 / sum;
		d1 = 1 / d1 / sum;
		d2 = 1 / d2 / sum;
		float v0 = d0 * maxima[i0];
		float v1 = d1 * maxima[i1];
		float v2 = d2 * maxima[i2];
		float ret = v0 + v1 + v2;
		return ret;
	}

	public void getThreeNearestVertexIndices(Point3f p, int[] ret) {
		Node3D[] nn = nnSearch.findNNearestNeighbors(new Node3D(p), 3);
		ret[0] = vertexToIndex.get(nn[0].p);
		ret[1] = vertexToIndex.get(nn[1].p);
		ret[2] = vertexToIndex.get(nn[2].p);
	}

	@Override
	public SphericalMaxProjection clone() {
		SphericalMaxProjection cp = new SphericalMaxProjection(this.sphere, this.center, this.radius);
		if(this.maxima != null) {
			cp.maxima = new float[this.maxima.length];
			System.arraycopy(this.maxima, 0, cp.maxima, 0, this.maxima.length);
		}
		if(this.luti != null) {
			int l = this.luti.length;
			cp.luti = new int[l][];
			cp.lutx = new int[l][];
			cp.luty = new int[l][];
			for(int z = 0; z < l; z++) {
				int lz = this.luti[z].length;
				cp.luti[z] = new int[lz];
				cp.lutx[z] = new int[lz];
				cp.luty[z] = new int[lz];
				System.arraycopy(this.luti[z], 0, cp.luti[z], 0, lz);
				System.arraycopy(this.lutx[z], 0, cp.lutx[z], 0, lz);
				System.arraycopy(this.luty[z], 0, cp.luty[z], 0, lz);
			}
		}
		return cp;
	}

	private static class Node3D implements Leaf<Node3D> {

		final Point3f p;

		public Node3D(final Point3f p) {
			this.p = p;
		}

		@SuppressWarnings("unused")
		public Node3D(final Node3D node) {
			this.p = (Point3f)node.p.clone();
		}

		@Override
		public boolean isLeaf() {
			return true;
		}

		@SuppressWarnings("unused")
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
