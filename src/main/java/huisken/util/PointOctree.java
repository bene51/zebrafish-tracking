package huisken.util;

import java.util.Set;
import java.util.HashSet;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import javax.vecmath.Point3f;

/**
 * A class representing an octree for spatial indexing
 * in 3D.
 * The same point can be added multiple times.
 *
 * @author Benjamin Schmid
 */
public class PointOctree implements Cloneable {

	/**
	 * The maximum number of points in one node; if
	 * this number is reached and another point is to
	 * be added to a node, the node is split into
	 * 8 child nodes.
	 */
	private static final int MAX_POINTS_PER_NODE = 4;

	/**
	 * The spatial extent of the octree; points to be
	 * added must be in the range [0, 0, 0 ; w, h, d [
	 */
	public final float minx, miny, minz, maxx, maxy, maxz;

	/**
	 * The root node of the octree.
	 */
	private final Node root;

	/**
	 * Construct a new tree with the given spatial calculated
	 * from the given point list;
	 * All further points to be added must be within that range;
	 * all the points are added to the tree.
	 */
	public PointOctree(List<Point3f> pts) {
		float minx, maxx, miny, maxy, minz, maxz;
		minx = miny = minz = Float.MAX_VALUE;
		maxx = maxy = maxz = Float.MIN_VALUE;

		for(Point3f p : pts) {
			if(p.x < minx) minx = p.x;
			if(p.y < miny) miny = p.y;
			if(p.z < minz) minz = p.z;

			if(p.x > maxx) maxx = p.x;
			if(p.y > maxy) maxy = p.y;
			if(p.z > maxz) maxz = p.z;
		}
		maxx += 0.1f;
		maxy += 0.1f;
		maxz += 0.1f;

		this.minx = minx; this.miny = miny; this.minz = minz;
		this.maxx = maxx; this.maxy = maxy; this.maxz = maxz;

		root = new Node(minx, miny, minz, maxx - minx, maxy - miny, maxz - minz);
		for(Point3f p : pts)
			add(p);
	}

	/**
	 * Construct a new tree with the given spatial extent;
	 * Points to be added must be in the range
	 * [minx, miny, minz ; maxx, maxy, maxz[
	 */
	public PointOctree(float minx, float miny, float minz,
			float maxx, float maxy, float maxz) {
		this.minx = minx;
		this.miny = miny;
		this.minz = minz;
		this.maxx = maxx;
		this.maxy = maxy;
		this.maxz = maxz;
		root = new Node(minx, miny, minz, maxx-minx, maxy-miny, maxz-minz);
	}

	/**
	 * Construct a new tree with the given spatial extent;
	 * Points to be added must be in the range [0, 0, 0 ; w, h, d [
	 */
	public PointOctree(float w, float h, float d) {
		minx = miny = minz = 0;
		maxx = w;
		maxy = h;
		maxz = d;
		root = new Node(minx, miny, minz, maxx, maxy, maxz);
	}

	/**
	 * Returns the number of points contained in this octree.
	 */
	public int size() {
		return root.nPoints;
	}

	/**
	 * Add a new point to the octree.
	 */
	public void add(Point3f p) {
		root.addPoint(p);
	}

	/**
	 * Removes the point n for which p.equals(n)
	 * yields true and returns the contained point.
	 */
	public Point3f remove(Point3f p) {
		return root.removePoint(p);
	}

	/**
	 * Get the point n in the tree for which
	 * n.equals(p) == true.
	 */
	public Point3f get(Point3f p) {
		return root.get(p);
	}

	/**
	 * Returns true if the tree contains an object n
	 * such that n.equals(p) == true.
	 */
	public boolean contains(Point3f p) {
		return get(p) != null;
	}

	/**
	 * Remove all points and reset the tree structure.
	 */
	public void clear() {
		this.root.children = null;
		this.root.nPoints = 0;
		this.root.points = null;
	}

	/**
	 * Returns all the contained points as a list.
	 */
	public ArrayList<Point3f> asList() {
		ArrayList<Point3f> ret = new ArrayList<Point3f>(size());
		root.addAll(ret);
		return ret;
	}

	/**
	 * Adds all neighbors to <code>ret</code> the distance
	 * of which to the given point <code>p</code> is smaller
	 * or equal to <code>d</code>; if p is contained in the
	 * octree, it is added to the list, too.
	 */
	public void neighborsWithin(Point3f p, float d, List<Point3f> ret) {
		Point3f l = new Point3f(p.x - d, p.y - d, p.z - d);
		Point3f u = new Point3f(p.x + d, p.y + d, p.z + d);
		float dSq = d * d;
		root.pointsInRange(l, u, p, dSq, ret);
	}

	/**
	 * Returns the nearest neighbor of p; if there is
	 * a point at the exact position of p, it is returned, too.
	 */
	public Point3f getNearestNeighbor(Point3f p) {
		Set<Point3f> exclude = new HashSet<Point3f>();
		Point3f nn = root.approximateNearestNeighbor(p, exclude);
		if(nn == null) {
			assert size() == 0;
			return null;
		}
		return root.nearestNeighborInRange(p, p.distance(nn), nn, exclude);
	}

	/**
	 * Returns the nearest neighbor of p; if there is
	 * a point at the exact position of p, it is returned, too.
	 */
	public void getNNearestNeighbors(Point3f p, Point3f[] n) {
		Set<Point3f> exclude = new HashSet<Point3f>();
		for(int i = 0; i < n.length; i++) {
			n[i] = root.approximateNearestNeighbor(p, exclude);
			exclude.add(n[i]);
		}
	}

	public Object clone() {
		return root.cloneNode();
	}

	/**
	 * Represents the nodes of the octree.
	 */
	private static class Node {

		/**
		 * The children of this node. This array is uninitialized
		 * as long as it is not needed (as long as the number
		 * of contained points does not exceed MAX_POINTS_PER_NODE).
		 * Once the children are created, the <code>points</code>
		 * array is set to null. So at any time, at most one of both
		 * is not null.
		 */
		Node[] children;

		/**
		 * The number of points, which is the number of own points,
		 * as long as this node is a leaf node, or the number of
		 * points in the subtree, when this node has become an
		 * non-leaf node.
		 */
		int nPoints = 0;

		/**
		 * Contains the points, as long as this node is a leaf node.
		 * This array is null, as long as this node contains no points,
		 * and becomes null again once this node has become a non-leaf
		 * node.
		 */
		Point3f[] points;

		/**
		 * The position and dimensions of this node.
		 */
		final float x, y, z, w, h, d, cx, cy, cz;

		/**
		 * Create a new Node with the given position and dimensions.
		 */
		Node(float x, float y, float z, float w, float h, float d) {
			this.x = x; this.y = y; this.z = z;
			this.w = w; this.h = h; this.d = d;
			cx = x + w / 2; cy = y + h / 2; cz = z + d / 2;
		}

		/**
		 * Add all points to the given list.
		 */
		void addAll(ArrayList<Point3f> list) {
			if(nPoints == 0)
				return;
			if(points != null) {
				for(int i = 0; i < nPoints; i++)
					list.add(points[i]);
				return;
			}
			if(children != null)
				for(int i = 0; i < 8; i++)
					children[i].addAll(list);
		}

		/**
		 * Search for the point prev in the octree for
		 * which prev.equals(p) == true.
		 */
		Point3f get(Point3f p) {
			if(nPoints == 0)
				return null;
			if(points != null) {
				for(int i = 0; i < nPoints; i++)
					if(points[i].equals(p))
						return points[i];
				return null;
			}
			if(children == null)
				return null;

			return children[indexInChilds(p)].get(p);
		}

		/**
		 * Add the given point.
		 * This will actually fail when there exist more
		 * than MAX_POINTS_PER_NODE identical points.
		 */
		void addPoint(Point3f p) {
			if(children != null) {
				nPoints++;
				addToChildren(p);
				return;
			}
			if(nPoints == 0)
				points = new Point3f[MAX_POINTS_PER_NODE];

			if(nPoints < MAX_POINTS_PER_NODE) {
				points[nPoints++] = p;
				return;
			}
			// nPoints == MAX_POINTS_PER_NODE:
			// create 8 children and distribute the
			// points to them:
			createChildren();
			for(int i = 0; i < nPoints; i++)
				addToChildren(points[i]);

			nPoints++;
			addToChildren(p);

			points = null;
		}

		/**
		 * Remove the point prev for which prev.equals(p) == true and
		 * returns the removed point prev.
		 */
		Point3f removePoint(Point3f p) {
			if(nPoints == 0)
				return null;

			if(points != null) {
				int idx = -1;
				for(int i = 0; i < nPoints; i++) {
					if(points[i].equals(p)) {
						idx = i;
						break;
					}
				}

				if(idx == -1)
					return null;

				Point3f ret = points[idx];
				for(int i = idx; i < nPoints - 1; i++)
					points[i] = points[i + 1];
				points[nPoints - 1] = null;

				nPoints--;
				if(nPoints == 0)
					points = null;
				return ret;
			}
			if(children == null)
				return null;

			Point3f ret = children[indexInChilds(p)].removePoint(p);
			if(ret == null)
				return null;

			nPoints--;
			if(nPoints == 0)
				this.children = null;

			return ret;
		}

		public Node cloneNode() {
			Node n = new Node(x, y, z, w, h, d);
			n.nPoints = nPoints;

			if(points != null) {
				n.points = new Point3f[points.length];
				for(int i = 0; i < nPoints; i++)
					n.points[i] = (Point3f)points[i].clone();
			}

			if(children == null)
				return n;

			n.children = new Node[8];
			for(int i = 0; i < children.length; i++)
				n.children[i] = children[i].cloneNode();

			return n;
		}

		/**
		 * Adds all points of this and node and the nodes below this
		 * node which are not farther from p than d.
		 * @param l       lower bound
		 * @param u       upper bound
		 * @param p       a point from which target points are not
		 *                further away than maxD
		 * @param maxDSqd the maximum squared distance of target points to p
		 * @param ret     the result list
		 */
		void pointsInRange(Point3f l, Point3f u, Point3f p,
					float maxDSq, List<Point3f> ret) {

			if(nPoints == 0)
				return;

			if(points != null) {
				for(int i = 0; i < nPoints; i++) {
					Point3f n = points[i];
					if(n.distanceSquared(p) <= maxDSq)
						ret.add(n);
				}
				return;
			}

			if(children == null)
				return;

			int lx, ly, lz, ux, uy, uz;
			lx = ly = lz = 0;
			ux = uy = uz = 1;

			if(u.x < cx)  ux = 0;
			else if(l.x >= cx) lx = 1;

			if(u.y < cy)  uy = 0;
			else if(l.y >= cy) ly = 1;

			if(u.z < cz)  uz = 0;
			else if(l.z >= cz) lz = 1;

			for(int z = lz; z <= uz; z++) {
				int iz = z * 4;
				for(int y = ly; y <= uy; y++) {
					int iy = y * 2;
					for(int x = lx; x <= ux; x++) {
						int idx = iz + iy + x;
						children[idx].pointsInRange(
							l, u, p, maxDSq, ret);
					}
				}
			}
		}

		/**
		 * Returns the approximate neighbor to the given point;
		 * the strategy is the following:
		 * 1) Search first in the exact node into which the given
		 *    point belongs; if there are points other than p,
		 *    the returned result is the nearest of these
		 * 2) If there's no such point in the exact node, look
		 *    into all of its siblings, and like this, go
		 *    recursively up in the tree hierarchy until a point
		 *    is found in the same node (of a potentially higher
		 *    level).
		 */
		Point3f approximateNearestNeighbor(Point3f p, Set<Point3f> exclude) {
			// if there aren't any points in the subtree,
			// we certainly have no nearest neighbor
			if(nPoints == 0)
				return null;

			// if we are a leaf node, the nearest point
			// not at p's position is the approximate nearest
			// neighbor (if such a point exists).
			if(points != null) {
				Point3f min = null;
				float mind = Float.MAX_VALUE;
				for(int i = 0; i < nPoints; i++) {
					if(!exclude.contains(points[i])) {
						float d = p.distanceSquared(points[i]);
						if(d < mind ) {
							mind = d;
							min = points[i];
						}
					}
				}
				return min;
			}

			// if we aren't a leaf node, and we have no children
			// (OK, this should actually not happen ;) then there's
			// no nearest neighbor.
			if(children == null)
				return null;

			// here comes the core: we are no leaf node: first
			// search for a neighbor in the correct child
			int idx = indexInChilds(p);
			Point3f nearest = children[idx].approximateNearestNeighbor(p, exclude);
			if(nearest != null)
				return nearest;

			// if we haven't found something in the correct child,
			// let's try the other children, too.
			for(int i = 0; i < children.length; i++) {
				if(i == idx)
					continue;
				nearest = children[i].
					approximateNearestNeighbor(p, exclude);
				if(nearest != null)
					return nearest;
			}

			// if we are here, there's nothing else we can do
			return null;
		}

		/**
		 * Basically does a naive nearest neighbor approach
		 * amongst all neighbors which are in the range
		 * [p-d; p+d[.
		 */
		Point3f nearestNeighborInRange(Point3f p, float d, Point3f best, Set<Point3f> exclude) {
			// this is similar to range search
			if(nPoints == 0)
				return best;

			if(points != null) {
				float bestDSq = p.distanceSquared(best);
				for(int i = 0; i < nPoints; i++) {
					Point3f n = points[i];
					if(!exclude.contains(n)) {
						float dSq = p.distanceSquared(n);
						if(dSq <= bestDSq) {
							bestDSq = dSq;
							best = n;
						}
					}
				}
				return best;
			}

			if(children == null)
				return best;

			int lx, ly, lz, ux, uy, uz;
			lx = ly = lz = 0;
			ux = uy = uz = 1;

			if(p.x + d < cx)  ux = 0;
			else if(p.x - d >= cx) lx = 1;

			if(p.y + d < cy)  uy = 0;
			else if(p.y - d >= cy) ly = 1;

			if(p.z + d < cz)  uz = 0;
			else if(p.z - d >= cz) lz = 1;

			for(int z = lz; z <= uz; z++) {
				int iz = z * 4;
				for(int y = ly; y <= uy; y++) {
					int iy = y * 2;
					for(int x = lx; x <= ux; x++) {
						int idx = iz + iy + x;
						best = children[idx].nearestNeighborInRange(p, d, best, exclude);
					}
				}
			}
			return best;
		}
		/**
		 * Get the parent node of a point.
		 */
		private Node getParentOfPoint(Point3f p) {
			if(nPoints == 0)
				return null;

			if(points != null) {
				for(int i = 0; i < nPoints; i++) {
					if(points[i].equals(p))
						return this;
				}
				return null;
			}
			if(children == null)
				return null;

			return children[indexInChilds(p)].getParentOfPoint(p);
		}

		/**
		 * Get the index of the given point in the children array,
		 * based on its 3D coordinates.
		 */
		private int indexInChilds(Point3f p) {
			int idx = 0;
			if(p.z >= cz) idx += 4;
			if(p.y >= cy) idx += 2;
			if(p.x >= cx) idx += 1;
			return idx;
		}

		/**
		 * Adds the given point to the appropriate child.
		 */
		private void addToChildren(Point3f p) {
			if(p == null)
				throw new NullPointerException();
			children[indexInChilds(p)].addPoint(p);
		}

		/**
		 * Initializes the 'children' array and each Node
		 * in it with the appropriate spatial extents.
		 */
		private void createChildren() {
			children = new Node[8];
			float w2 = w / 2, h2 = h / 2, d2 = d / 2;
			children[0] = new Node( x,  y,  z, w2, h2, d2);
			children[1] = new Node(cx,  y,  z, w2, h2, d2);
			children[2] = new Node( x, cy,  z, w2, h2, d2);
			children[3] = new Node(cx, cy,  z, w2, h2, d2);
			children[4] = new Node( x,  y, cz, w2, h2, d2);
			children[5] = new Node(cx,  y, cz, w2, h2, d2);
			children[6] = new Node( x, cy, cz, w2, h2, d2);
			children[7] = new Node(cx, cy, cz, w2, h2, d2);
		}

		/**
		 * Returns true if the two arguments have the same position.
		 */
		private static final boolean posEquals(Point3f a, Point3f b) {
			return a.x == b.x && a.y == b.y && a.z == b.z;
		}
	}
}

