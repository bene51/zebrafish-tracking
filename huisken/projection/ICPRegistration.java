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

import math3d.JacobiDouble;

import meshtools.PointMatch;
import meshtools.PointOctree;


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
		float mse = icp(sSphere, tSphere, mat, 500);
		src.applyTransform(mat);
		System.out.println(mat);
		return mse;
	}

	public static float icp(Point3f[] m,
				Point3f[] t,
				Matrix4f result,
				int maxIter) {
		int ms = m.length;

		// use 'result' as initial transformation
		apply(m, result);
		float mseOld = Float.MAX_VALUE;

		PointOctree ttree = new PointOctree(Arrays.asList(t));

		final ArrayList<PointMatch> correspondences =
				new ArrayList<PointMatch>(ms);

		int it = 0;
		for(it = 0; it < maxIter; it++) {
			// create hypothetical point matches by searching
			// for each point in m for a nearest neighbor point
			// in t
			correspondences.clear();
			for(Point3f mp : m)
				correspondences.add(new PointMatch(
					mp, nearestNeighbor(mp, ttree)));

			// Calculate a best rigid transform
			Matrix4f fm = new Matrix4f();
			bestRigid(correspondences, fm);
			result.mul(fm, result);
			apply(m, fm);
			float mse = calculateMSE(correspondences);
			if(mse == mseOld)
				break;
			mseOld = mse;
		}
		System.out.println("ICP: stopping after " + it + " iterations");
		return mseOld;
	}

	public static final void apply(Point3f[] list, Matrix4f m) {
		for(Point3f p : list)
			m.transform(p);
	}

	public static final Point3f nearestNeighbor(
			Point3f p, PointOctree t) {
		return t.getNearestNeighbor(p);
	}

	public static final float calculateMSE(ArrayList<PointMatch> pm) {
		double sum = 0.0;
		for(PointMatch p : pm)
			sum += p.p1.distanceSquared(p.p2);
		return (float)(sum / pm.size());
	}

	public static void bestRigid(ArrayList<PointMatch> pm, Matrix4f result) {
		double c1x, c1y, c1z, c2x, c2y, c2z;
		c1x = c1y = c1z = c2x = c2y = c2z = 0;

		for (PointMatch m : pm) {
			c1x += (double)m.p1.x;
			c1y += (double)m.p1.y;
			c1z += (double)m.p1.z;
			c2x += (double)m.p2.x;
			c2y += (double)m.p2.y;
			c2z += (double)m.p2.z;
		}
		c1x /= pm.size();
		c1y /= pm.size();
		c1z /= pm.size();
		c2x /= pm.size();
		c2y /= pm.size();
		c2z /= pm.size();

		// calculate N
		double Sxx, Sxy, Sxz, Syx, Syy, Syz, Szx, Szy, Szz;
		Sxx = Sxy = Sxz = Syx = Syy = Syz = Szx = Szy = Szz = 0;
		for (PointMatch m : pm) {
			double x1 = (double)m.p1.x - c1x;
			double y1 = (double)m.p1.y - c1y;
			double z1 = (double)m.p1.z - c1z;
			double x2 = (double)m.p2.x - c2x;
			double y2 = (double)m.p2.y - c2y;
			double z2 = (double)m.p2.z - c2z;
			Sxx += x1 * x2;
			Sxy += x1 * y2;
			Sxz += x1 * z2;
			Syx += y1 * x2;
			Syy += y1 * y2;
			Syz += y1 * z2;
			Szx += z1 * x2;
			Szy += z1 * y2;
			Szz += z1 * z2;
		}
		double[][] N = new double[4][4];
		N[0][0] = Sxx + Syy + Szz;
		N[0][1] = Syz - Szy;
		N[0][2] = Szx - Sxz;
		N[0][3] = Sxy - Syx;
		N[1][0] = Syz - Szy;
		N[1][1] = Sxx - Syy - Szz;
		N[1][2] = Sxy + Syx;
		N[1][3] = Szx + Sxz;
		N[2][0] = Szx - Sxz;
		N[2][1] = Sxy + Syx;
		N[2][2] = -Sxx + Syy - Szz;
		N[2][3] = Syz + Szy;
		N[3][0] = Sxy - Syx;
		N[3][1] = Szx + Sxz;
		N[3][2] = Syz + Szy;
		N[3][3] = -Sxx - Syy + Szz;

		// calculate eigenvector with maximal eigenvalue
		JacobiDouble jacobi = new JacobiDouble(N);
		double[][] eigenvectors = jacobi.getEigenVectors();
		double[] eigenvalues = jacobi.getEigenValues();
		int index = 0;
		for (int i = 1; i < 4; i++)
			if (eigenvalues[i] > eigenvalues[index])
				index = i;

		double[] q = eigenvectors[index];
		double q0 = q[0], qx = q[1], qy = q[2], qz = q[3];


		// turn into matrix
		// rotational part
		result.m00 = (float)(q0 * q0 + qx * qx - qy * qy - qz * qz);
		result.m01 = (float)(2 * (qx * qy - q0 * qz));
		result.m02 = (float)(2 * (qx * qz + q0 * qy));
		result.m03 = 0;
		result.m10 = (float)(2 * (qy * qx + q0 * qz));
		result.m11 = (float)(q0 * q0 - qx * qx + qy * qy - qz * qz);
		result.m12 = (float)(2 * (qy * qz - q0 * qx));
		result.m13 = 0;
		result.m20 = (float)(2 * (qz * qx - q0 * qy));
		result.m21 = (float)(2 * (qz * qy + q0 * qx));
		result.m22 = (float)(q0 * q0 - qx * qx - qy * qy + qz * qz);
		result.m23 = 0;
		result.m30 = 0;
		result.m31 = 0;
		result.m32 = 0;
		result.m33 = 1;

		Point3f tr = new Point3f((float)c1x, (float)c1y, (float)c1z);
		// translational part
		result.transform(tr);
		result.m03 = (float)c1x - tr.x;
		result.m13 = (float)c1y - tr.y;
		result.m23 = (float)c1z - tr.z;
	}
}

