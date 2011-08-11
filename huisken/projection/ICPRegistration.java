package huisken.projection;

import ij.ImagePlus;

import meshtools.ICP;
import meshtools.IndexedTriangleMesh;

import vib.FastMatrix;

import javax.vecmath.Matrix4f;
import javax.vecmath.Point3f;

import java.io.File;

import java.util.ArrayList;
import java.util.Arrays;

public class ICPRegistration {

	public static final float THRESHOLD = 1600f;

	public static void main(String[] args) throws Exception {
		String dir = "/home/bene/PostDoc/data/SphereProjection/";

		SphericalMaxProjection src = new SphericalMaxProjection(dir + "Sphere.obj");
		SphericalMaxProjection tgt = new SphericalMaxProjection(dir + "Sphere.obj");

		tgt.loadMaxima(dir + "tp0000.tif.vertices");
		src.loadMaxima(dir + "tp0010.tif.vertices");
		// src.applyTransform(FastMatrix.rotateEulerAt(15 * Math.PI / 180, 1 * Math.PI / 180, 0, tgt.center.x, tgt.center.y, tgt.center.z));

		int WIDTH = 360;
		PolarTransform pt = new PolarTransform();
		pt.prepareForProjection(src, WIDTH);
		new ImagePlus("src", pt.project()).show();
		pt.prepareForProjection(tgt, WIDTH);
		new ImagePlus("tgt", pt.project()).show();

		float mse = new ICPRegistration(tgt, src).register(new Matrix4f());
		System.out.println("mse = " + mse);

		pt.prepareForProjection(src, WIDTH);
		new ImagePlus("transformed", pt.project()).show();
	}

	private final SphericalMaxProjection tgt;
	private final SphericalMaxProjection src;

	// assume the maxima are loaded
	public ICPRegistration(SphericalMaxProjection tgt, SphericalMaxProjection src) {
		this.tgt = tgt;
		this.src = src;
	}

	public static Point3f[] filter(SphericalMaxProjection mesh) {
		ArrayList<Point3f> pts = new ArrayList<Point3f>();
		for(int i = 0; i < mesh.getSphere().nVertices; i++) {
			if(mesh.getMaxima()[i] > THRESHOLD)
				pts.add(new Point3f(mesh.getSphere().getVertices()[i]));
		}
		Point3f[] ret = new Point3f[pts.size()];
		pts.toArray(ret);
		return ret;
	}

	public float register(Matrix4f mat) {
		Point3f[] sSphere = filter(src);
		Point3f[] tSphere = filter(tgt);
		float mse = ICP.icp(sSphere, tSphere, mat, false);
		src.applyTransform(mat);
		System.out.println(mat);
		return mse;
	}
}

