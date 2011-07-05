package huisken.projection.viz;

import customnode.CustomTriangleMesh;
import customnode.CustomMeshNode;

import javax.vecmath.Point3f;
import javax.vecmath.Color3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.FileInputStream;
import java.io.IOException;

import javax.media.j3d.GeometryArray;
import javax.media.j3d.TriangleArray;
import javax.media.j3d.IndexedTriangleArray;

import ij3d.Image3DUniverse;
import ij3d.ContentInstant;
import ij3d.Content;

import com.sun.j3d.utils.geometry.GeometryInfo;
import com.sun.j3d.utils.geometry.NormalGenerator;

import java.io.File;


public class CustomContent extends Content {

	private CustomIndexedTriangleMesh mesh;
	public final int nVertices;
	public final String[] files;

	private float[] rawData;

	private float displayedMaximum = 0;
	private float displayedMinimum = 0;

	public CustomContent(
			Point3f[] vertices,
			int[] faces,
			String vertexDir) {

		super("bla", 0);

		List<String> tmp = new ArrayList<String>();
		tmp.addAll(Arrays.asList(new File(vertexDir).list()));
		for(int i = tmp.size() - 1; i >= 0; i--)
			if(!tmp.get(i).endsWith(".vertices"))
				tmp.remove(i);
		this.files = new String[tmp.size()];
		tmp.toArray(files);
		for(int i = 0; i < files.length; i++)
			files[i] = vertexDir + File.separator + files[i];
		Arrays.sort(files);

		nVertices = vertices.length;
		this.rawData = new float[nVertices];
		Color3f[] colors = new Color3f[nVertices];
		for(int i = 0; i < colors.length; i++)
			colors[i] = new Color3f(0, 1, 0);

		try {
			readColors(files[0], colors);
		} catch(Exception e) {
			throw new RuntimeException("Cannot load " + files[0], e);
		}
		mesh = new CustomIndexedTriangleMesh(vertices, colors, faces);
		CustomMeshNode node = new CustomMeshNode(mesh);
	
		ContentInstant content = getInstant(0);
		content.display(node);

		displayedMinimum = getCurrentMinimum();
		displayedMaximum = getCurrentMaximum();

	}

	public float getCurrentMinimum() {
		float min = rawData[0];
		for(int i = 1; i < rawData.length; i++)
			if(rawData[i] < min)
				min = rawData[i];
		return min;
	}

	public float getCurrentMaximum() {
		float max = rawData[0];
		for(int i = 1; i < rawData.length; i++)
			if(rawData[i] > max)
				max = rawData[i];
		return max;
	}

	public void setDisplayedMinimum(float min) {
		displayedMinimum = min;
		updateDisplayRange();
	}

	public void setDisplayedMaximum(float max) {
		displayedMaximum = max;
		updateDisplayRange();	
	}

	private void updateDisplayRange() {
		for(int i = 0; i < mesh.colors.length; i++) {
			float v = rawData[i];
			v = (v - displayedMinimum) / (displayedMaximum - displayedMinimum);
			mesh.colors[i].set(v, v, v);
		}
		setColors(mesh.colors);
	}

	void readColors(String file, Color3f[] colors) throws IOException {
		DataInputStream in = new DataInputStream(
			new BufferedInputStream(
				new FileInputStream(file)));
		for(int i = 0; i < colors.length; i++) {
			try {
				float v = in.readFloat();
				rawData[i] = v;
				v = (v - displayedMinimum) / (displayedMaximum - displayedMinimum);
				colors[i].set(v, v, v);
			} catch(EOFException e) {
				break;
			}
		}
		in.close();
	}

	public void setColors(Color3f[] colors) {
		((IndexedTriangleArray)mesh.getGeometry()).setColors(0, colors);
	}

	// timeline stuff
	@Override public void addInstant(ContentInstant ci) {}
	@Override public void removeInstant(int t) {}
	@Override public void setShowAllTimepoints(boolean b) {}

	@Override public int getNumberOfInstants() {
		return files.length;
	}

	@Override public ContentInstant getInstant(int t) {
		try {
			readColors(files[t], mesh.colors);
			setColors(mesh.colors);
		} catch(Exception e) {
			throw new RuntimeException("Cannot load " + files[t], e);
		}
		return super.getInstant(0);
	}

	@Override public ContentInstant getCurrent() {
		return super.getInstant(0);
	}

	@Override public void showTimepoint(int t) {
		try {
			readColors(files[t], mesh.colors);
			setColors(mesh.colors);
		} catch(Exception e) {
			throw new RuntimeException("Cannot load " + files[t], e);
		}
	}

	@Override public void showTimepoint(int t, boolean force) {
		if(force)
			super.showTimepoint(t, true);
		showTimepoint(t);
	}

	@Override public boolean isVisibleAt(int t) {
		return t > 0 && t < getNumberOfInstants();
	}

	@Override public int getStartTime() {
		return 0;
	}

	@Override public int getEndTime() {
		return files.length - 1;
	}


	private static final class CustomIndexedTriangleMesh extends CustomTriangleMesh {

		private Point3f[] vertices;
		private Color3f[] colors;
		private int[] faces;

		CustomIndexedTriangleMesh(Point3f[] vertices, Color3f[] colors, int[] faces) {
			super(null);
			this.vertices = vertices;
			this.colors = colors;
			this.faces = faces;
			update();
		}

		@Override
		protected GeometryArray createGeometry() {
			if(vertices != null)
				return createGeometry(vertices, colors, faces);
			return null;
		}

		@Override
		public void calculateMinMaxCenterPoint(Point3f min,
					Point3f max, Point3f center) {

			min.x = min.y = min.z = Float.MAX_VALUE;
			max.x = max.y = max.z = Float.MIN_VALUE;
			for(int i = 0; i < vertices.length; i++) {
				Point3f p = vertices[i];
				if(p.x < min.x) min.x = p.x;
				if(p.y < min.y) min.y = p.y;
				if(p.z < min.z) min.z = p.z;
				if(p.x > max.x) max.x = p.x;
				if(p.y > max.y) max.y = p.y;
				if(p.z > max.z) max.z = p.z;
			}
			center.x = (max.x + min.x) / 2;
			center.y = (max.y + min.y) / 2;
			center.z = (max.z + min.z) / 2;
		}

		protected GeometryArray createGeometry(Point3f[] vertices, Color3f[] colors, int[] faces) {

			IndexedTriangleArray ta = new IndexedTriangleArray(
				vertices.length,
					TriangleArray.COORDINATES |
					TriangleArray.COLOR_3, // |
					// TriangleArray.NORMALS,
				faces.length);

			ta.setCoordinates(0, vertices);
			ta.setColors(0, colors);

			ta.setCoordinateIndices(0, faces);
			ta.setColorIndices(0, faces);

			ta.setCapability(GeometryArray.ALLOW_COLOR_WRITE);
			ta.setCapability(GeometryArray.ALLOW_INTERSECT);

			return ta;
		}
	}

	public static void main(String[] args) {
		Point3f[] vertices = new Point3f[] {
			new Point3f(0, 0, 0),
			new Point3f(1, 0, 0),
			new Point3f(.5f, 1, 0),
			new Point3f(1.5f, 1, 0) };
		Color3f[] colors = new Color3f[] {
			new Color3f(1, 0, 0),
			new Color3f(0, 0, 1),
			new Color3f(0, 1, 0),
			new Color3f(1, 1, 0) };
		int[] faces = new int[] {0, 1, 2, 2, 1, 3};

		Image3DUniverse u = new Image3DUniverse();
		u.show();

//		u.addContent(new CustomContent(vertices, colors, faces));
	}
}

