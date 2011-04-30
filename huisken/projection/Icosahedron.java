package huisken.projection;

import customnode.CustomPointMesh;
import customnode.CustomTriangleMesh;

import ij3d.Image3DUniverse;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import meshtools.IndexedTriangleMesh;

import javax.vecmath.Point3f;
import javax.vecmath.Vector3f;
import javax.vecmath.Color3f;

import meshtools.PointOctree;

public class Icosahedron extends IndexedTriangleMesh {

	public static Point3f[] createVertices(float tao, float r) {
		Point3f[] v = new Point3f[] {
			new Point3f(1, tao, 0), new Point3f(-1, tao, 0), new Point3f(1, -tao, 0), new Point3f(-1, -tao, 0),
			new Point3f(0, 1, tao), new Point3f(0, -1, tao), new Point3f(0, 1, -tao), new Point3f(0, -1, -tao),
			new Point3f(tao, 0, 1), new Point3f(-tao, 0, 1), new Point3f(tao, 0, -1), new Point3f(-tao, 0, -1)};
		float scale = (float)Math.sqrt((r * r) / (1f + tao * tao));
		for(Point3f p : v)
			p.scale(scale);
		return v;
	}

	public static int[] createFaces() {
		return new int[] {
			0, 1, 4, 1, 9, 4, 4, 9, 5, 5, 9, 3,
			2, 3, 7, 3, 2, 5, 7, 10, 2, 0, 8, 10,
			0, 4, 8, 8, 2, 10, 8, 4, 5, 8, 5, 2,
			1, 0, 6, 11, 1, 6, 3, 9, 11, 6, 10, 7,
			3, 11, 7, 11, 6, 7, 6, 0, 10, 9, 1, 11};
	}

	public Icosahedron(float tao, float r) {
		super(createVertices(tao, r), createFaces());
	}

	public IndexedTriangleMesh createBuckyball(float r, int subd) {
		// Subdivide faces
		ArrayList<Point3f> triangles = new ArrayList<Point3f>();
		for(int i = 0; i < faces.length; i += 3) {
			int f1 = faces[i];
			int f2 = faces[i + 1];
			int f3 = faces[i + 2];
			TriangleSubdivision ts = new TriangleSubdivision(
				vertices[f1],
				vertices[f2],
				vertices[f3],
				subd);
			triangles.addAll(ts.createTriangles());
		}

		PointOctree tree = new PointOctree(-r, -r, -r, r, r, r);
		float mindist = (r / subd) / 2;
		for(int i = 0; i < triangles.size(); i++) {
			Point3f p = triangles.get(i);
			Point3f n = tree.getNearestNeighbor(p);
			if(n != null && p.distance(n) < mindist)
				p.set(n);
			else
				tree.add(p);
		}
		IndexedTriangleMesh sphere = new IndexedTriangleMesh(triangles);
		// project onto sphere surface
		Point3f m = new Point3f();
		for(Point3f v : sphere.getVertices()) {
			Vector3f d = new Vector3f();
			d.sub(v, m);
			d.normalize();
			v.scaleAdd(r, d, m);
		}
		return sphere;
	}

	public static void main(String[] args) {
		float tao = 1.61803399f;
		Image3DUniverse univ = new Image3DUniverse();
		univ.show();

		Icosahedron icosa = new Icosahedron(tao, 1f);
		univ.addCustomMesh(new CustomTriangleMesh(icosa.createMesh()), "icosahedron");

		float r = 1f;
		int subd = 5;	

		IndexedTriangleMesh buckyball = icosa.createBuckyball(r, subd);
		List<Point3f> spherepoints = Arrays.asList(buckyball.getVertices());
		List<Point3f> triangles = buckyball.createMesh();

		CustomPointMesh cpm = new CustomPointMesh(spherepoints);
		cpm.setColor(new Color3f(1, 0, 0));
		cpm.setPointSize(3f);
		univ.addCustomMesh(cpm, "Sphere points");

		CustomTriangleMesh ctm = new CustomTriangleMesh(triangles);
		ctm.setColor(new Color3f(0.4f, 0.4f, 1));
		univ.addCustomMesh(ctm, "triangles");
	}
}
