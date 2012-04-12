package huisken.projection;

import ij.process.FloatProcessor;
import ij.process.ImageProcessor;

import java.awt.geom.GeneralPath;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

import javax.vecmath.Point3f;

import com.jhlabs.map.Ellipsoid;
import com.jhlabs.map.proj.Projection;


public class GeneralProjProjection {

	private SphericalMaxProjection smp;
	private int w;
	private int h;

	private int minx, miny, maxx, maxy;

	private int[][] vIndices;
	private float[][] vertexWeights;

	private final Projection projection;

	public GeneralProjProjection(Projection proj) {
		this.projection = proj;
	}

	public static GeneralPath readDatFile(String file) throws IOException {
		BufferedReader buf = new BufferedReader(new FileReader(file));
		GeneralPath path = new GeneralPath();
		String line;
		boolean closed = true;
		int lineno = 0;
		while((line = buf.readLine()) != null) {
			lineno++;
			line = line.trim();
			if(line.equals("")) continue;
			if(line.startsWith(">")) {
				closed = true;
				continue;
			}
			String[] toks = line.split("\\s");
			try {
				float x = Float.parseFloat(toks[0]);
				float y = Float.parseFloat(toks[1]);
				if(closed)
					path.moveTo(x, y);
				else
					path.lineTo(x, y);
			} catch(Exception e) {
				throw new RuntimeException("Error parsing line " + lineno + ": " + line, e);
			}
			closed = false;
		}
		buf.close();
		return path;
	}

	public int getWidth() {
		return w;
	}

	public int getHeight() {
		return h;
	}

	public GeneralPath createLines() {
		GeneralPath lines = new GeneralPath();
		double minLon = projection.getMinLongitudeDegrees();
		double maxLon = projection.getMaxLongitudeDegrees();
		double minLat = projection.getMinLatitudeDegrees();
		double maxLat = projection.getMaxLatitudeDegrees();
		int nLos = 10;
		int nLas = 100;
		for(int lo = 0; lo <= nLos; lo++) {
			boolean first = true;
			for(int la = 0; la < nLas; la++) {
				float x = (lo - nLos/2) * (360.0f / nLos);
				float y = (la - nLas/2) * (180.0f / nLas);
				if(x < minLon || x > maxLon || y < minLat || y > maxLat)
					continue;
				if(first)
					lines.moveTo(x, y);
				else
					lines.lineTo(x, y);
				first = false;
			}
		}

		nLos = 100;
		nLas = 10;
		for(int la = 0; la <= nLas; la++) {
			boolean first = true;
			for(int lo = 0; lo < nLos; lo++) {
				float x = (lo - nLos/2) * (360.0f / nLos);
				float y = (la - nLas/2) * (180.0f / nLas);
				if(x < minLon || x > maxLon || y < minLat || y > maxLat)
					continue;
				if(first)
					lines.moveTo(x, y);
				else
					lines.lineTo(x, y);
				first = false;
			}
		}
		return lines;
	}

	public void prepareForProjection(SphericalMaxProjection smp) {
		this.smp = smp;

		float globeRadius = 300f;
		projection.setEllipsoid(new Ellipsoid("", globeRadius, globeRadius, 0.0D, ""));
		projection.initialize();

		if(!projection.hasInverse())
			throw new RuntimeException("Inverse mapping missing");

		minx = Integer.MAX_VALUE;
		maxx = -minx;
		miny = minx;
		maxy = -minx;

		double dlon = projection.getMaxLongitude() - projection.getMinLongitude();
		double dlat = projection.getMaxLatitude() - projection.getMinLatitude();

		Point2D.Double tmpin = new Point2D.Double();
		Point2D.Double tmpout = new Point2D.Double();
		for(int lo = 0; lo <= 100; lo++) {
			for(int la = 0; la <= 100; la++) {
				tmpin.x = (lo - 50) * dlon / 100 - 0.001;
				tmpin.y = (la - 50) * dlat / 100 - 0.001;
				projection.transformRadians(tmpin, tmpout);
				minx = Math.min((int)tmpout.x, minx);
				miny = Math.min((int)tmpout.y, miny);
				maxx = Math.max((int)tmpout.x, maxx);
				maxy = Math.max((int)tmpout.y, maxy);
			}
		}
		System.out.println(maxx + " " + maxy);
		System.out.println(minx + " " + miny);

		this.w = maxx - minx + 1;
		this.h = maxy - miny + 1;
		System.out.println(w);
		System.out.println(h);

		vIndices = new int[w * h][3];
		vertexWeights = new float[w * h][3];
		Point3f p = new Point3f();
		Point3f[] vertices = smp.getSphere().getVertices();
		int index = 0;
		for(int y = maxy; y >= miny; y--) {
			for(int x = minx; x <= maxx; x++) {
				Point2D.Double in = new Point2D.Double(x, y);
				Point2D.Double out = new Point2D.Double();
				out = projection.inverseTransformRadians(in, out);

				double lon = out.x;
				double lat = out.y;

				if(lat >= projection.getMaxLatitude() ||
					lat <= projection.getMinLatitude() ||
					lon >= projection.getMaxLongitude() ||
					lon <= projection.getMinLongitude()) {
					index++;
					continue;
				}

				double sLon = (float)Math.sin(lon);
				double cLon = (float)Math.cos(lon);
				double sLat = (float)Math.sin(lat);
				double cLat = (float)Math.cos(lat);

				smp.getPoint(sLon, cLon, sLat, cLat, p);

				smp.getThreeNearestVertexIndices(p, vIndices[index]);

				// interpolate according to distance
				float d0 = 1 / p.distance(vertices[vIndices[index][0]]);
				float d1 = 1 / p.distance(vertices[vIndices[index][1]]);
				float d2 = 1 / p.distance(vertices[vIndices[index][2]]);
				float sum = d0 + d1 + d2;
				vertexWeights[index][0] = d0 / sum;
				vertexWeights[index][1] = d1 / sum;
				vertexWeights[index][2] = d2 / sum;

				index++;
			}
		}
	}

	public GeneralPath transform(GeneralPath in, boolean postscript) {
		GeneralPath out = new GeneralPath();
		PathIterator it = in.getPathIterator(null);
		float[] seg = new float[6];

		Point2D.Double din = new Point2D.Double();
		Point2D.Double dout = new Point2D.Double();

		while(!it.isDone()) {
			int l = it.currentSegment(seg);
			din.x = seg[0];
			din.y = seg[1];
			// clip to min/max long/lat
			if(din.x < projection.getMinLongitudeDegrees()) din.x = projection.getMinLongitudeDegrees();
			if(din.y < projection.getMinLatitudeDegrees()) din.y = projection.getMinLatitudeDegrees();
			if(din.x > projection.getMaxLongitudeDegrees()) din.x = projection.getMaxLongitudeDegrees();
			if(din.y > projection.getMaxLatitudeDegrees()) din.y = projection.getMaxLatitudeDegrees();
			projection.transform(din, dout);
			float x = (float)dout.x - minx;
			float y = postscript ? maxy - (float)dout.y : (float)dout.y - miny;

			if(l == PathIterator.SEG_MOVETO)
				out.moveTo(x, y);
			else
				out.lineTo(x, y);
			it.next();
		}
		return out;
	}

	public static void drawInto(ImageProcessor ip, int value, int linewidth, GeneralPath path) {
		ip.setValue(value);
		ip.setLineWidth(linewidth);
		PathIterator it = path.getPathIterator(null);
		float[] seg = new float[6];
		Point2D.Double din = new Point2D.Double();
		while(!it.isDone()) {
			int l = it.currentSegment(seg);
			din.x = seg[0];
			din.y = seg[1];
			if(l == PathIterator.SEG_MOVETO)
				ip.moveTo((int)Math.round(din.x), (int)Math.round(din.y));
			else
				ip.lineTo((int)Math.round(din.x), (int)Math.round(din.y));
			it.next();
		}
	}

	public static void savePath(GeneralPath path, String file, int w, int h, boolean fill) throws IOException {
		PrintWriter out = new PrintWriter(new FileWriter(file));
		out.println("%%!PS-Adobe-1.0");
		out.println("%%%%BoundingBox: 0 0 " + w + " " + h);
		out.println("%%%%EndComments");

		// Draw background:
		out.println("0 0 moveto");
		out.println(w + " 0 lineto");
		out.println(w + " " + h + " lineto");
		out.println("0 " + h + " lineto");
		out.println("closepath");
		out.println("0 setcolor");
		out.println("fill");

		out.println("0.3 setlinewidth");

		PathIterator it = path.getPathIterator(null);
		float[] seg = new float[6];
		while(!it.isDone()) {
			int l = it.currentSegment(seg);
			if(l == PathIterator.SEG_MOVETO)
				out.println(seg[0] + " " + seg[1] + " moveto");
			else
				out.println(seg[0] + " " + seg[1] + " lineto");
			it.next();
		}
		out.println("1 setcolor");
		if(fill)
			out.println("fill");
		else
			out.println("stroke");
		out.close();
	}

	public ImageProcessor project() {
		return project(smp.getMaxima());
	}

	public ImageProcessor project(float[] maxima) {
		FloatProcessor ip = new FloatProcessor(w, h);
		for(int y = 0; y < h; y++) {
			for(int x = 0; x < w; x++) {
				int index = y * w + x;

				float v0 = vertexWeights[index][0] * maxima[vIndices[index][0]];
				float v1 = vertexWeights[index][1] * maxima[vIndices[index][1]];
				float v2 = vertexWeights[index][2] * maxima[vIndices[index][2]];

				ip.setf(x, y, v0 + v1 + v2);
			}
		}
		return ip;
	}
}
