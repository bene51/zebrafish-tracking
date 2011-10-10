package huisken.projection;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

import javax.vecmath.Matrix4f;
import javax.vecmath.Point3f;

import math3d.JacobiDouble;
import meshtools.PointMatch;
import meshtools.PointOctree;


public class ICPRegistration {

	public static final float THRESHOLD = 1600f;

	private final SphericalMaxProjection tgt;
	private final SphericalMaxProjection src;

	// assume the maxima are loaded
	public ICPRegistration(SphericalMaxProjection tgt, SphericalMaxProjection src) {
		this.tgt = tgt;
		this.src = src;
	}

	private static Point3f[] filter(SphericalMaxProjection mesh) {
		float[] maxima = mesh.getMaxima();
		boolean[] isMax = mesh.isMaximum();
		Point3f[] vertices = mesh.getSphere().getVertices();
		ArrayList<Point3f> pts = new ArrayList<Point3f>();
		for(int i = 0; i < maxima.length; i++)
			if(isMax[i] && maxima[i] > THRESHOLD)
				pts.add(new Point3f(vertices[i]));
		Point3f[] ret = new Point3f[pts.size()];
		pts.toArray(ret);
		return ret;
	}

	public float register(Matrix4f mat, Point3f cor) {
		Point3f[] sSphere = filter(src);
		Point3f[] tSphere = filter(tgt);
		float mse = icp(sSphere, tSphere, mat, cor, 500, 0.8f);
		System.out.println(mat);
		return mse;
	}

	private static float icp(Point3f[] m,
				Point3f[] t,
				Matrix4f result,
				Point3f cor,
				int maxIter,
				float ratioToUse) {
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
			bestRigid(correspondences, fm, cor);
			result.mul(fm, result);
			apply(m, fm);
			float mse = calculateMSE(correspondences);
			if(mse == mseOld)
				break;
			mseOld = mse;
		}
		// Only keep X% of the best-matching correspondences
		if(ratioToUse != 1) {
			Collections.sort(correspondences);
			int toRemove = correspondences.size() - Math.round(ratioToUse * correspondences.size());
			for(int i = 0; i < toRemove; i++)
				correspondences.remove(0);
			// Calculate a best rigid transform
			Matrix4f fm = new Matrix4f();
			bestRigid(correspondences, fm, cor);
			result.mul(fm, result);
			apply(m, fm);
		}
		System.out.println("ICP: stopping after " + it + " iterations");
		return mseOld;
	}

	private static final void apply(Point3f[] list, Matrix4f m) {
		for(Point3f p : list)
			m.transform(p);
	}

	private static final Point3f nearestNeighbor(
			Point3f p, PointOctree t) {
		return t.getNearestNeighbor(p);
	}

	private static final float calculateMSE(ArrayList<PointMatch> pm) {
		double sum = 0.0;
		for(PointMatch p : pm)
			sum += p.p1.distanceSquared(p.p2);
		return (float)(sum / pm.size());
	}

	private static void bestRigid(ArrayList<PointMatch> pm, Matrix4f result, Point3f cor) {
		double c1x, c1y, c1z, c2x, c2y, c2z;
		c1x = c1y = c1z = c2x = c2y = c2z = 0;

		for (PointMatch m : pm) {
			c1x += m.p1.x;
			c1y += m.p1.y;
			c1z += m.p1.z;
			c2x += m.p2.x;
			c2y += m.p2.y;
			c2z += m.p2.z;
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
			double x1 = m.p1.x - c1x;
			double y1 = m.p1.y - c1y;
			double z1 = m.p1.z - c1z;
			double x2 = m.p2.x - c2x;
			double y2 = m.p2.y - c2y;
			double z2 = m.p2.z - c2z;
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

		Point3f tr = new Point3f(cor);
		// translational part
		result.transform(tr);
		result.m03 = cor.x - tr.x;
		result.m13 = cor.y - tr.y;
		result.m23 = cor.z - tr.z;
	}
}
