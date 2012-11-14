package huisken.projection.processing;

import java.util.ArrayList;
import java.util.List;

import javax.vecmath.Point3f;
import javax.vecmath.Vector3f;

public class TriangleSubdivision {

	private final Point3f p1;
	private final Vector3f v21;
	private final Vector3f v31;
	private final int subd;

	public TriangleSubdivision(Point3f p1, Point3f p2, Point3f p3, int subd) {
		this.p1 = p1;
		this.subd = subd;
		v21 = new Vector3f();
		v21.sub(p2, p1);
		v21.scale(1f / subd);
		v31 = new Vector3f();
		v31.sub(p3, p1);
		v31.scale(1f / subd);
	}

	public List<Point3f> createTriangles() {
		List<Point3f> triangles = new ArrayList<Point3f>();
		int i1 = 0, i2 = 0;
		for(i2 = 0; i2 < subd - 1; i2++) {
			for(i1 = 0; i1 < subd - i2 - 1; i1++) {
				Point3f t1 = new Point3f(p1);
				Point3f t2 = new Point3f();
				Point3f t3 = new Point3f();
				Point3f t4 = new Point3f();
				t1.scaleAdd(i1, v21, t1);
				t1.scaleAdd(i2, v31, t1);
				t2.scaleAdd(1, v21, t1);
				t3.scaleAdd(1, v31, t1);
				t4.scaleAdd(1, v21, t3);
				triangles.add(t1);
				triangles.add(t2);
				triangles.add(t3);
				triangles.add(new Point3f(t2));
				triangles.add(new Point3f(t4));
				triangles.add(new Point3f(t3));
			}
			Point3f t1 = new Point3f(p1);
			Point3f t2 = new Point3f();
			Point3f t3 = new Point3f();
			t1.scaleAdd(i1, v21, t1);
			t1.scaleAdd(i2, v31, t1);
			t2.scaleAdd(1, v21, t1);
			t3.scaleAdd(1, v31, t1);
			triangles.add(t1);
			triangles.add(t2);
			triangles.add(t3);
		}
		i1--;
		Point3f t1 = new Point3f(p1);
		Point3f t2 = new Point3f();
		Point3f t3 = new Point3f();
		t1.scaleAdd(i1, v21, t1);
		t1.scaleAdd(i2, v31, t1);
		t2.scaleAdd(1, v21, t1);
		t3.scaleAdd(1, v31, t1);
		triangles.add(t1);
		triangles.add(t2);
		triangles.add(t3);

		return triangles;
	}
}
