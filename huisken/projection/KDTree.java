package huisken.projection;

import java.util.Arrays;
import java.util.Comparator;

import javax.vecmath.Point3f;

public class KDTree {

	private Node root;
	private static final AxisComparator[] comp = new AxisComparator[3];

	public KDTree(Point3f[] points) {
		comp[0] = new AxisComparator(0);
		comp[1] = new AxisComparator(1);
		comp[2] = new AxisComparator(2);
		root = createTree(points, 0, points.length, 0);
	}

	public static Node createTree(Point3f[] points, int offs, int len, int depth) {
		if(len == 0)
			return null;

		// Select axis based on depth so that axis cycles through all valid values
		int axis = depth % 3;
		
		// Sort point list and choose median as pivot element
		Arrays.sort(points, offs, len, comp[0]);
		int median = offs + len / 2;
		
		// Create node and construct subtrees
		Node node = new Node();
		Point3f med = points[offs + len/2];
		
		node.location = axis == 0 ? med.x : (axis == 1 ? med.y : med.z);
		node.left  = createTree(points, offs, len / 2, depth + 1);
		node.right = createTree(points, offs + len / 2, len - len / 2, depth + 1);
		return node;
	}

	static final class AxisComparator implements Comparator<Point3f> {

		private int axis;
		
		AxisComparator(int axis) {
			this.axis = axis;
		}

		public int compare(Point3f p1, Point3f p2) {
			switch(axis) {
				case 0: return p1.x < p2.x ? -1 : (p1.x > p2.x ? +1 : 0);
				case 1: return p1.y < p2.y ? -1 : (p1.y > p2.y ? +1 : 0);
				case 2: return p1.z < p2.z ? -1 : (p1.z > p2.z ? +1 : 0);
			}
			return 0;
		}
	}

	static final class Node {
		Node left, right;
		float location;
	}
}
