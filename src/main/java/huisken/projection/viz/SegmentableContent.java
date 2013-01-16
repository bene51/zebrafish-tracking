package huisken.projection.viz;

import huisken.projection.processing.IndexedTriangleMesh;
import huisken.projection.processing.SphericalMaxProjection;

import java.util.ArrayList;
import java.util.List;

import javax.vecmath.Point2f;
import javax.vecmath.Point3f;

public class SegmentableContent {

	private CustomContent content;

	private int[] segmentationMeshToFullMesh;
	private IndexedTriangleMesh segmentationMesh;

	// in rad
	public void restrictTo(Point2f min, Point2f max) {
		SphericalMaxProjection smp = content.getSMP();
		IndexedTriangleMesh full = smp.getSphere();
		int[] fullFaces = full.getFaces();
		Point3f[] fullVertices = full.getVertices();

		List<Point3f> segVertices = new ArrayList<Point3f>();
		List<Integer> segFaces = new ArrayList<Integer>();
		List<Integer> segToFull = new ArrayList<Integer>();

		Point2f pol1 = new Point2f();
		Point2f pol2 = new Point2f();
		Point2f pol3 = new Point2f();

		for(int f = 0; f < full.nFaces; f += 3) {

			int v1I = fullFaces[f];
			int v2I = fullFaces[f + 1];
			int v3I = fullFaces[f + 2];

			Point3f v1 = fullVertices[v1I];
			Point3f v2 = fullVertices[v2I];
			Point3f v3 = fullVertices[v3I];

			smp.getPolar(v1, pol1);
			smp.getPolar(v2, pol2);
			smp.getPolar(v3, pol3);

			if(inRange(pol1, min, max) || inRange(pol2, min, max) || inRange(pol3, min, max)) {
				int idx = segVertices.size();
				segVertices.add(new Point3f(v1));
				segVertices.add(new Point3f(v2));
				segVertices.add(new Point3f(v3));
				segFaces.add(idx);
				segFaces.add(idx + 1);
				segFaces.add(idx + 2);

				segToFull.add(v1I);
				segToFull.add(v2I);
				segToFull.add(v3I);
			}
		}

		Point3f[] segVerticesA = new Point3f[segVertices.size()];
		segVertices.toArray(segVerticesA);

		int[] segFacesA = new int[segFaces.size()];
		for(int i = 0; i < segFacesA.length; i++)
			segFacesA[i] = segFaces.get(i);

		segmentationMesh = new IndexedTriangleMesh(segVerticesA, segFacesA);

		segmentationMeshToFullMesh = new int[segToFull.size()];
		for(int i = 0; i < segmentationMeshToFullMesh.length; i++)
			segmentationMeshToFullMesh[i] = segToFull.get(i);
	}

	static boolean inRange(Point2f p, Point2f min, Point2f max) {
		return p.x >= min.x && p.x <= max.x && p.y >= min.y && p.y <= max.y;
	}
}
