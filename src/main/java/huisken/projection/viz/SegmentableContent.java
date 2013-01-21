package huisken.projection.viz;

import huisken.projection.processing.IndexedTriangleMesh;
import huisken.projection.processing.SphericalMaxProjection;
import ij.IJ;
import ij3d.Content;
import ij3d.ContentInstant;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.vecmath.Color3f;
import javax.vecmath.Point2f;
import javax.vecmath.Point3f;

public class SegmentableContent extends Content {

	private CustomContent content;
	private int[] fullSegmentation;

	private int[] segmentationMeshToFullMesh;
	private IndexedTriangleMesh segmentationMesh;
	private int[] segmentation;
	private Color3f[] colors;
	private int[] intensities;

	public SegmentableContent(String objfile, String vertexDir, String filenameContains) throws IOException {
		super("bla", 0);
		content = new CustomContent(objfile, vertexDir, filenameContains);
		File f = content.getCurrentFile();
		File dir = new File(f.getParentFile(), "segmentation");
		f = content.getCurrentFile();
		f = new File(dir, f.getName());
		if(f.exists()) {
			String path = f.getAbsolutePath();
			try {
				fullSegmentation = SphericalMaxProjection.loadIntData(path, content.getSMP().getSphere().nVertices);
			} catch(Exception e) {
				IJ.error("cannot load " + path);
				e.printStackTrace();
			}
		} else {
			fullSegmentation = new int[content.getSMP().getSphere().nVertices];
		}

	}

	public IndexedTriangleMesh getMesh() {
		return segmentationMesh;
	}

	public Color3f[] getColors() {
		return colors;
	}

	public int[] getIntensities() {
		return intensities;
	}

	public CustomContent getCustomContent() {
		return content;
	}

	public void transferSegmentation() {
		for(int i = 0; i < segmentation.length; i++)
			fullSegmentation[segmentationMeshToFullMesh[i]] = segmentation[i];
	}

	public int[] getSegmentation() {
		return segmentation;
	}

	public int[] getFullSegmentation() {
		return fullSegmentation;
	}

	// in rad
	public void restrictTo(Point2f min, Point2f max) {
		SphericalMaxProjection smp = content.getSMP();
		IndexedTriangleMesh full = smp.getSphere();
		int[] fullFaces = full.getFaces();
		Point3f[] fullVertices = full.getVertices();

		List<Point3f> segVertices = new ArrayList<Point3f>();
		List<Integer> segFaces = new ArrayList<Integer>();

		List<Integer> segToFull = new ArrayList<Integer>();
		int[] fullToSeg = new int[full.nVertices];
		Arrays.fill(fullToSeg, -1);

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
				int v1In = fullToSeg[v1I];
				if(v1In == -1) {
					v1In = segVertices.size();
					fullToSeg[v1I] = v1In;
					segVertices.add(new Point3f(v1));
					segToFull.add(v1I);
				}
				int v2In = fullToSeg[v2I];
				if(v2In == -1) {
					v2In = segVertices.size();
					fullToSeg[v2I] = v2In;
					segVertices.add(new Point3f(v2));
					segToFull.add(v2I);
				}
				int v3In = fullToSeg[v3I];
				if(v3In == -1) {
					v3In = segVertices.size();
					fullToSeg[v3I] = v3In;
					segVertices.add(new Point3f(v3));
					segToFull.add(v3I);
				}

				segFaces.add(v1In);
				segFaces.add(v2In);
				segFaces.add(v3In);
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


		colors = new Color3f[segmentationMesh.nVertices];
		intensities = new int[segmentationMesh.nVertices];
		int[] maxima = content.getMaxima();
		segmentation = new int[segmentationMesh.nVertices];
		for(int i = 0; i < segmentationMesh.nVertices; i++) {
			int idx = segmentationMeshToFullMesh[i];
			colors[i] = content.getColor(idx);
			intensities[i] = maxima[segmentationMeshToFullMesh[i]];
			segmentation[i] = fullSegmentation[idx];
		}
	}

	public static final boolean inRange(Point2f p, Point2f min, Point2f max) {
		if(min.x < max.x) {
			if(p.x < min.x || p.x > max.x)
				return false;
		} else {
			// min.x > max.x
			// check that v > min.x || v < max.x
			if(p.x < min.x && p.x > max.x)
				return false;
		}

		if(min.y < max.y) {
			if(p.y < min.y || p.y > max.y)
				return false;
		} else {
			if(p.y < min.y && p.y > max.y)
				return false;
		}

		return true;
	}

	public static final boolean inRangeOld(Point2f p, Point2f min, Point2f max) {
		return p.x >= min.x && p.x <= max.x && p.y >= min.y && p.y <= max.y;
	}

	public static final Point2f toRange(Point2f p) {
		if(p.x < -Math.PI)
			p.x += (float)Math.PI * 2;
		if(p.x > Math.PI)
			p.x -= (float)Math.PI * 2;
		if(p.y < -Math.PI)
			p.y += (float)Math.PI * 2;
		if(p.y > Math.PI)
			p.y -= (float)Math.PI * 2;
		return p;
	}

	// timeline stuff
	@Override public void addInstant(ContentInstant ci) {}
	@Override public void removeInstant(int t) {}
	@Override public void setShowAllTimepoints(boolean b) {}

	@Override public int getNumberOfInstants() {
		return content.getNumberOfInstants();
	}

	@Override public ContentInstant getInstant(int t) {
		return super.getInstant(0);
	}

	@Override public ContentInstant getCurrent() {
		return super.getCurrent();
	}

	@Override public void showTimepoint(int t) {
		showTimepoint(t, false);
	}

	public void saveCurrentTimepoint() {
		transferSegmentation();
		File f = content.getCurrentFile();
		File dir = new File(f.getParentFile(), "segmentation");
		if(!dir.exists())
			dir.mkdir();
		String path = new File(dir, f.getName()).getAbsolutePath();
		try {
			SphericalMaxProjection.saveIntData(fullSegmentation, path);
		} catch(Exception e) {
			IJ.error("cannot save " + path);
			e.printStackTrace();
		}
	}

	@Override public void showTimepoint(int t, boolean force) {
		if(force)
			super.showTimepoint(t, true);
		saveCurrentTimepoint();

		content.showTimepoint(t);

		File f = content.getCurrentFile();
		File dir = new File(f.getParentFile(), "segmentation");
		f = content.getCurrentFile();
		f = new File(dir, f.getName());
		if(f.exists()) {
			String path = f.getAbsolutePath();
			try {
				fullSegmentation = SphericalMaxProjection.loadIntData(path, fullSegmentation.length);
			} catch(Exception e) {
				IJ.error("cannot load " + path);
				e.printStackTrace();
			}
		} else {
			fullSegmentation = new int[fullSegmentation.length];
		}

		colors = new Color3f[segmentationMesh.nVertices];
		intensities = new int[segmentationMesh.nVertices];
		int[] maxima = content.getMaxima();
		segmentation = new int[segmentationMesh.nVertices];
		for(int i = 0; i < segmentationMesh.nVertices; i++) {
			int idx = segmentationMeshToFullMesh[i];
			colors[i] = content.getColor(idx);
			intensities[i] = maxima[segmentationMeshToFullMesh[i]];
			segmentation[i] = fullSegmentation[idx];
		}
	}

	@Override public boolean isVisibleAt(int t) {
		return content.isVisibleAt(t);
	}

	@Override public int getStartTime() {
		return content.getStartTime();
	}

	@Override public int getEndTime() {
		return content.getEndTime();
	}
}
