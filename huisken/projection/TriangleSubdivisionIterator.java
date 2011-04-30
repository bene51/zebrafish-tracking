package huisken.projection;

import javax.vecmath.Point3f;
import javax.vecmath.Vector3f;

/**
 * Iterates over the vertices of a subdivided
 * triangle
 */
public class TriangleSubdivisionIterator {

	private final Point3f p1;
	private final Point3f p2;
	private final Point3f p3;

	private final Vector3f v21;
	private final Vector3f v31;
	private final int subd;

	private int i1 = -1;
	private int i2 = 0;

	public TriangleSubdivisionIterator(Point3f p1, Point3f p2, Point3f p3, int subd) {
		this.p1 = p1;
		this.p2 = p2;
		this.p3 = p3;
		this.subd = subd;
		v21 = new Vector3f();
		v21.sub(p2, p1);
		v21.scale(1f / subd);
		v31 = new Vector3f();
		v31.sub(p3, p1);
		v31.scale(1f / subd);
	}

	public void reset() {
		i1 = -1;
		i2 = 0;
	}

	public boolean hasNext() {
		return i1 < (subd - i2) || i2 < subd; 
	}

	public Point3f next() {
		i1++;
		if(i1 > subd - i2) {
			i1 = 0;
			i2++;
		}
		if(i2 > subd)
			return null;

		Point3f nextVertex = new Point3f(p1);
		nextVertex.scaleAdd(i1, v21, nextVertex);
		nextVertex.scaleAdd(i2, v31, nextVertex);

		return nextVertex;
	}
}
