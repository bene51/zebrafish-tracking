package huisken.projection.viz;

import static java.lang.Math.PI;
import fiji.util.gui.GenericDialogPlus;
import huisken.projection.processing.Floodfill;
import huisken.projection.processing.IndexedTriangleMesh;
import huisken.projection.viz.CustomContent.CustomIndexedTriangleMesh;
import ij.IJ;
import ij.plugin.PlugIn;
import ij3d.Content;
import ij3d.ContentInstant;
import ij3d.Image3DUniverse;
import ij3d.TimelapseListener;
import ij3d.UniverseListener;
import ij3d.behaviors.InteractiveBehavior;

import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.io.File;
import java.util.ArrayList;

import javax.media.j3d.IndexedTriangleArray;
import javax.media.j3d.PickInfo;
import javax.media.j3d.View;
import javax.vecmath.Color3f;
import javax.vecmath.Point2f;
import javax.vecmath.Point3d;
import javax.vecmath.Point3f;

import com.sun.j3d.utils.pickfast.PickCanvas;

import customnode.CustomMeshNode;
import customnode.CustomQuadMesh;
import customnode.MeshMaker;

public class SegmentationViewer implements PlugIn {

	private int currentView = 0;
	private SegmentableContent segContent;
	private Floodfill floodFill;
	private CustomIndexedTriangleMesh mesh;
	private boolean[] outlines;

	private static final float OVERLAP = 10 * (float)PI / 180;

	private static final int nLat = 2;
	private static final int nLon = 4;

	private static final Point2f[] min, max;
	static {
		float dLat = (float)PI / nLat;
		float dLon = 2 * (float)PI / nLon;
		int n = nLat * nLon;
		min = new Point2f[n];
		max = new Point2f[n];
		for(int la = 0, i = 0; la < nLat; la++) {
			for(int lo = 0; lo < nLon; lo++, i++) {
				min[i] = SegmentableContent.toRange(new Point2f(-(float)PI + lo * dLon - OVERLAP, -(float)PI/2 + la * dLat - OVERLAP));
				max[i] = SegmentableContent.toRange(new Point2f(min[i].x + dLon + 2 * OVERLAP, min[i].y + dLat + 2 * OVERLAP));
			}
		}
	}

	public static void main(String[] args) {
		new ij.ImageJ();
		new SegmentationViewer().run("");
	}

	@Override
	public void run(String arg) {
		GenericDialogPlus gd = new GenericDialogPlus("Sphere Projection Viewer");
		gd.addDirectoryField("Data directory", "");
		gd.addStringField("File name contains", "");
		gd.showDialog();
		if(gd.wasCanceled())
			return;
		String dir = gd.getNextString();
		String pattern = gd.getNextString();

		String objfile = dir + File.separator + "Sphere.obj";
		if(dir == null || objfile == null)
			return;
		try {
			show(objfile, dir, pattern);
		} catch(Exception e) {
			IJ.error(e.getMessage());
			e.printStackTrace();
		}
	}

	public void nextView() {
		currentView = (currentView + 1) % min.length;

		segContent.transferSegmentation();

		segContent.restrictTo(min[currentView], max[currentView]);
		IndexedTriangleMesh segmentationMesh = segContent.getMesh();

		mesh = new CustomIndexedTriangleMesh(segmentationMesh.getVertices(), segContent.getColors(), segmentationMesh.getFaces());

		CustomMeshNode node = new CustomMeshNode(mesh);

		ContentInstant content = segContent.getInstant(0);
		content.display(node);

		outlines = new boolean[segmentationMesh.nVertices];
		floodFill = new Floodfill(segContent.getMesh(), segContent.getIntensities(), segContent.getSegmentation());
		floodFill.calculateOutlines(outlines);
		setSegmentation(new ArrayList<Integer>(), -1);
	}

	public void prevView() {
		currentView = (currentView - 1 + min.length) % min.length;

		segContent.transferSegmentation();

		segContent.restrictTo(min[currentView], max[currentView]);
		IndexedTriangleMesh segmentationMesh = segContent.getMesh();

		mesh = new CustomIndexedTriangleMesh(segmentationMesh.getVertices(), segContent.getColors(), segmentationMesh.getFaces());

		CustomMeshNode node = new CustomMeshNode(mesh);

		ContentInstant content = segContent.getInstant(0);
		content.display(node);

		outlines = new boolean[segmentationMesh.nVertices];
		floodFill = new Floodfill(segContent.getMesh(), segContent.getIntensities(), segContent.getSegmentation());
		floodFill.calculateOutlines(outlines);
		setSegmentation(new ArrayList<Integer>(), -1);
	}

	public Image3DUniverse show(String objfile, String vertexDir, String filenameContains) {

		// load mesh
		try {
			segContent = new SegmentableContent(objfile, vertexDir, filenameContains);
			segContent.restrictTo(min[currentView], max[currentView]);

			IndexedTriangleMesh segmentationMesh = segContent.getMesh();

			mesh = new CustomIndexedTriangleMesh(segmentationMesh.getVertices(), segContent.getColors(), segmentationMesh.getFaces());

			CustomMeshNode node = new CustomMeshNode(mesh);

			ContentInstant content = segContent.getInstant(0);
			content.display(node);
		} catch(Exception e) {
			throw new RuntimeException("Cannot load " + objfile, e);
		}

		Image3DUniverse univ = new Image3DUniverse();
		// univ.addInteractiveBehavior(new SphereProjectionViewer.CustomBehavior(univ, cc));
		univ.addInteractiveBehavior(new CustomBehavior(univ, segContent));
		univ.show();

		univ.addContent(segContent);
		segContent.setLocked(true);

		Point3f center = segContent.getCustomContent().getSMP().getCenter();
		float radius = segContent.getCustomContent().getSMP().getRadius();
		CustomQuadMesh ctm = new CustomQuadMesh(MeshMaker.createQuadSphere(center.x, center.y, center.z, radius - 0.1f * radius, 12, 12));
		Content tmp = univ.addCustomMesh(ctm, "sphere");
		tmp.setLocked(true);
		tmp.setShaded(false);
		tmp.setShowAllTimepoints(true);

		univ.addTimelapseListener(new TimelapseListener() {
			@Override
			public void timepointChanged(int timepoint) {
				IndexedTriangleMesh segmentationMesh = segContent.getMesh();

				mesh = new CustomIndexedTriangleMesh(segmentationMesh.getVertices(), segContent.getColors(), segmentationMesh.getFaces());

				CustomMeshNode node = new CustomMeshNode(mesh);

				ContentInstant content = segContent.getInstant(0);
				content.display(node);

				outlines = new boolean[segmentationMesh.nVertices];
				floodFill = new Floodfill(segContent.getMesh(), segContent.getIntensities(), segContent.getSegmentation());
				floodFill.calculateOutlines(outlines);
				setSegmentation(new ArrayList<Integer>(), -1);
			}
		});
		univ.addUniverseListener(new UniverseListener() {
			@Override public void transformationStarted(View view) {}
			@Override public void transformationUpdated(View view) {}
			@Override public void transformationFinished(View view) {}
			@Override public void contentAdded(Content c) {}
			@Override public void contentRemoved(Content c) {}
			@Override public void contentChanged(Content c) {}
			@Override public void contentSelected(Content c) {}
			@Override public void canvasResized() {}

			@Override
			public void universeClosed() {
				segContent.saveCurrentTimepoint();
			}
		});

		floodFill = new Floodfill(segContent.getMesh(), segContent.getIntensities(), segContent.getSegmentation());
		outlines = new boolean[segContent.getMesh().nVertices];
		floodFill.calculateOutlines(outlines);
		setSegmentation(new ArrayList<Integer>(), -1);

		return univ;
	}

	private void setSegmentation(ArrayList<Integer> outline, int selectedLabel) {
		if(outline == null)
			outline = new ArrayList<Integer>();
		Color3f[] colors = new Color3f[segContent.getColors().length];
		for(int i = 0; i < colors.length; i++)
			colors[i] = outlines[i] ? new Color3f(0, 1, 0) : new Color3f(segContent.getColors()[i]);
		for(int i : outline)
			colors[i] = new Color3f(0, 0, 1);
		if(selectedLabel > 0) {
			ArrayList<Integer> outl = floodFill.calculateOutlines(selectedLabel);
			for(int i : outl)
				colors[i] = new Color3f(1, 1, 0);
		}
		((IndexedTriangleArray)mesh.getGeometry()).setColors(0, colors);
	}


	protected class CustomBehavior extends InteractiveBehavior {

		private final SegmentableContent cc;

		public CustomBehavior(Image3DUniverse univ, SegmentableContent cc) {
			super(univ);
			this.cc = cc;
		}

		@Override
		public void doProcess(KeyEvent e) {
			if(e.getID() != KeyEvent.KEY_PRESSED)
				return;

			if(e.getKeyCode() == KeyEvent.VK_RIGHT) {
				nextView();
				e.consume();
			}
			else if(e.getKeyCode() == KeyEvent.VK_LEFT) {
				prevView();
				e.consume();
			}
			else if(e.getKeyCode() == KeyEvent.VK_X) {
				floodFill.exec();
				floodFill.calculateOutlines(outlines);
				setSegmentation(new ArrayList<Integer>(), -1);
				e.consume();
			}
		}

		@Override
		public void doProcess(MouseEvent e) {
			int id = e.getID();
			if(id == MouseEvent.MOUSE_PRESSED && e.isAltDown()) {
				int idx = pickClosestVertexIndex(e);
				if(idx == -1)
					return;
				floodFill.setSelectedVertex(idx);
				setSegmentation(floodFill.getCurrentSegmentation(), floodFill.getSelectedLabel());
				e.consume();
			}
			if(id == MouseEvent.MOUSE_WHEEL && e.isAltDown()) {
				MouseWheelEvent we = (MouseWheelEvent)e;
				int units = 0;
				if(we.getScrollType() == MouseWheelEvent.WHEEL_UNIT_SCROLL) {
					units = we.getUnitsToScroll();
					floodFill.setLowerThreshold(units);
					ArrayList<Integer> seg = floodFill.getOutline();
					setSegmentation(seg, -1);
				}
				e.consume();
			}
		}

		int pickClosestVertexIndex(MouseEvent e) {
			PickCanvas pickCanvas = new PickCanvas(univ.getCanvas(), cc);
			pickCanvas.setTolerance(3f);
			pickCanvas.setMode(PickInfo.PICK_GEOMETRY);
			pickCanvas.setFlags(PickInfo.CLOSEST_GEOM_INFO | PickInfo.CLOSEST_INTERSECTION_POINT);
			pickCanvas.setShapeLocation(e);
			try {
				PickInfo result = pickCanvas.pickClosest();
				if(result == null)
					return -1;

				Point3d vertex = result.getClosestIntersectionPoint();
				PickInfo.IntersectionInfo info =
						result.getIntersectionInfos()[0];
				int[] indices = info.getVertexIndices();

				Point3f[] vertices = segContent.getMesh().getVertices();
				int[] faces = segContent.getMesh().getFaces();

				int closest = faces[indices[0]];
				Point3f v = vertices[closest];
				double dist = vertex.distance(new Point3d(v));
				for(int i = 1; i < indices.length; i++) {
					int idx = faces[indices[i]];
					v = vertices[idx];
					double d = vertex.distance(new Point3d(v));
					if(d < dist) {
						dist = d;
						closest = idx;
					}
				}
				return closest;
			} catch(Exception ex) {
				ex.printStackTrace();
				return -1;
			}
		}
	}
}
