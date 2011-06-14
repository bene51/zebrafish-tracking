package huisken.projection;

import ij.IJ;
import ij.ImagePlus;

import ij.plugin.filter.PlugInFilter;

import ij.process.ImageProcessor;

import vib.FastMatrix;

import mpicbg.models.AffineModel3D;

import mpicbg.imglib.image.Image;
import mpicbg.imglib.image.ImagePlusAdapter;

import mpicbg.imglib.outofbounds.OutOfBoundsStrategyFactory;
import mpicbg.imglib.outofbounds.OutOfBoundsStrategyValueFactory;
import mpicbg.imglib.outofbounds.OutOfBoundsStrategyMirrorFactory;

import mpicbg.imglib.image.display.imagej.ImageJFunctions;

import mpicbg.imglib.type.numeric.real.FloatType;

import mpicbg.imglib.algorithm.math.NormalizeImageMinMax;

import mpicbg.imglib.algorithm.transformation.ImageTransform;

import mpicbg.imglib.algorithm.gauss.DownSample;

import mpicbg.imglib.cursor.LocalizableByDimCursor;
import mpicbg.imglib.cursor.LocalizableCursor;
import mpicbg.imglib.cursor.Cursor;

import mpicbg.imglib.cursor.special.RegionOfInterestCursor;

import mpicbg.imglib.interpolation.Interpolator;
import mpicbg.imglib.interpolation.InterpolatorFactory;

import mpicbg.imglib.interpolation.linear.LinearInterpolatorFactory;
import mpicbg.imglib.interpolation.nearestneighbor.NearestNeighborInterpolatorFactory;

public class FuseImages implements PlugInFilter {

	private Image<FloatType> image;

	private static final int HISTOGRAM_BINS = 500;

	public int setup(String arg, ImagePlus imp) {
		this.image = ImagePlusAdapter.wrap(imp);
		return DOES_8G | DOES_16 | DOES_32;
	}

	public void run(ImageProcessor ip) {

		int r = (int)IJ.getNumber("Window radius", 5);
		if(r == IJ.CANCELED)
			return;

		int startLevel = 1; // 1 is the original
		int numLevels = 4;

		// normalize
		normalize(image);

		// filter in multi resolution
		Image<FloatType> target = filterMultiResolution(image, 1, startLevel, numLevels, r);

		// output
		ImageJFunctions.copyToImagePlus( target ).show();
	}

	public static void normalize(Image<FloatType> img) {
		NormalizeImageMinMax<FloatType> mm = new NormalizeImageMinMax<FloatType>(img);
		mm.process();
	}

	// source: source image for that level
	// target: target image for that level
	public static Image<FloatType> filterMultiResolution(Image<FloatType> source, int level, int startLevel, int numLevels, int r) {
		System.out.println("level = " + level);

		// not yet started, just upsample result from deeper levels
		if(level < startLevel)
			return upsample(filterMultiResolution(downsample(source), level + 1, startLevel, numLevels, r));


		Image<FloatType> t = filter(source, r);
		ImageJFunctions.copyToImagePlus(t).show();

		// deepest level: just return the filtered image of this level
		if(level == startLevel + numLevels - 1)
			return t;

		// in between: add upsampled result from deeper levels and the filtered image of this level
		add(t, upsample(filterMultiResolution(downsample(source), level + 1, startLevel, numLevels, r)));
		return t;
	}

	public static Image<FloatType> filter(Image<FloatType> source, int r) {
		int d = source.getNumDimensions();
		int[] offs   = new int[d];
		int[] size   = new int[d];

		// initialize the size of planar ROI
		for(int i = 0; i < d; i++)
			size[i] = i < 2 ? 2 * r + 1 : 1;

		float background = getBackgroundValue(source);

		Image<FloatType> target = source.createNewImage(source.getName() + " - filtered");

		LocalizableCursor<FloatType> cursor = source.createLocalizableCursor();
		LocalizableByDimCursor<FloatType> tc = target.createLocalizableByDimCursor();

		OutOfBoundsStrategyFactory<FloatType> oobsf = new OutOfBoundsStrategyValueFactory<FloatType>(new FloatType(background));
		LocalizableByDimCursor<FloatType> roiParentCursor = source.createLocalizableByDimCursor(oobsf);

		while(cursor.hasNext()) {
			cursor.fwd();
			cursor.getPosition(offs);
			tc.setPosition(offs);

			// adjust offset of planar ROI
			for(int i = 0; i < 2; i++)
				offs[i] -= r;
			RegionOfInterestCursor<FloatType> rc = new RegionOfInterestCursor<FloatType>(roiParentCursor, offs, size);
			// filter
			float v = filterLocal(rc, HISTOGRAM_BINS);
			// output

			tc.getType().set(v);

			// close the ROI cursor
			rc.close();

		}
		return target;
	}

	public static Image<FloatType> calculateGradient(Image<FloatType> source) {
		int d = source.getNumDimensions();
		int[] pos   = new int[d];
		int[] nei   = new int[d];

		Image<FloatType> target = source.createNewImage(source.getName() + " - gradient");

		OutOfBoundsStrategyFactory<FloatType> oobsf = new OutOfBoundsStrategyMirrorFactory<FloatType>();

		LocalizableCursor<FloatType> sCursor = source.createLocalizableCursor();
		LocalizableByDimCursor<FloatType> nCursor = source.createLocalizableByDimCursor(oobsf);
		LocalizableByDimCursor<FloatType> tCursor = target.createLocalizableByDimCursor();

		// TODO take calibration into account
		while(sCursor.hasNext()) {
			sCursor.fwd();
			sCursor.getPosition(pos);
			tCursor.setPosition(pos);

			float sq = 0;
			for(int i = 0; i < d; i++) {
				nCursor.setPosition(pos);
				float v = nCursor.getType().get();
				nCursor.fwd(i);
				float v1 = nCursor.getType().get();
				nCursor.bck(i);
				nCursor.bck(i);
				float v2 = nCursor.getType().get();

				if(v == 0f || v1 == 0f || v2 == 0f) {
					sq = 0;
					break;
				}

				float diff = (v1 - v2) / 2f;
				sq += diff * diff;
			}

			sq = (float)Math.sqrt(sq);
			tCursor.getType().set(sq);
		}
		sCursor.close();
		tCursor.close();
		nCursor.close();
		return target;
	}

	public static Image<FloatType> createFusionMask(
			Image<FloatType> w1,
			Image<FloatType> img1,
			Image<FloatType> w2,
			Image<FloatType> img2) {
		int[] dim = img1.getDimensions();
		final Image<FloatType> target = img1.getImageFactory().createImage(dim);
		return null;
	}

	public static float getBackgroundValue(Image<FloatType> img) {
		Cursor<FloatType> c = img.createCursor();
		int[] h = getHistogram(c, 500);
		int index = HistogramFeatures.getFirstMode(HistogramFeatures.smooth(h, 5));
		return index * 1f / 500;
	}

	public static Image<FloatType> downsample(Image<FloatType> source) {
		DownSample<FloatType> ds = new DownSample<FloatType>(source, 0.5f);
		ds.process();
		return ds.getResult();
	}

	public static Image<FloatType> upsample(Image<FloatType> source) {
		final int d = source.getNumDimensions();

		final int[] dim = new int[d];
		for (int i=0; i<dim.length; i++)
			dim[i] = (int)((source.getDimension(i) * 2.0) + 0.5);

		final Image<FloatType> target = source.getImageFactory().createImage(dim);


		final InterpolatorFactory<FloatType> ifac = new LinearInterpolatorFactory<FloatType>(new OutOfBoundsStrategyMirrorFactory<FloatType>());
		final Interpolator<FloatType> inter = ifac.createInterpolator(source);
		final LocalizableCursor<FloatType> c2 = target.createLocalizableCursor();


		final int[]   pt = new int[d];
		final float[] ps = new float[d];

		final float[] s = new float[dim.length];
 		for (int i=0; i<s.length; i++)
 			s[i] = (float)source.getDimension(i) / dim[i];

		while (c2.hasNext()) {
			c2.fwd();
			c2.getPosition(pt);
			for (int i = 0; i < d; i++)
				ps[i] = pt[i] * s[i];
			inter.moveTo(ps);
			float sum = c2.getType().get() + inter.getType().get();
			c2.getType().set(sum);
		}
		inter.close();
		c2.close();
		return target;
	}

	public static void add(Image<FloatType> i1, Image<FloatType> i2) {
		final int d = i1.getNumDimensions();

		final LocalizableCursor<FloatType> c1 = i1.createLocalizableCursor();
		final LocalizableByDimCursor<FloatType> c2 = i2.createLocalizableByDimCursor();

		while (c1.hasNext()) {
			c1.fwd();
			c2.setPosition(c1);
			c1.getType().add(c2.getType());
		}
		c1.close();
		c2.close();
	}

	public static FastMatrix fromCalibration(Image<FloatType> img) {
		double a00 = img.getCalibration(0);
		double a11 = img.getCalibration(1);
		double a22 = img.getCalibration(2);
		return new FastMatrix(new double[][] {
			{ a00, 0.0, 0.0, 0.0 },
			{ 0.0, a11, 0.0, 0.0 },
			{ 0.0, 0.0, a22, 0.0 }
		});
	}

	// assumes a 3x4 matrix row by row
	public static Image<FloatType> transformImage(Image<FloatType> tgt, Image<FloatType> model, FastMatrix matrix) {

		if(tgt.getNumDimensions() != 3)
			throw new IllegalArgumentException();

		FastMatrix m = fromCalibration(model).inverse().times(matrix.inverse()).times(fromCalibration(tgt));

		// InterpolatorFactory intf = new LinearInterpolatorFactory(
		InterpolatorFactory intf = new NearestNeighborInterpolatorFactory(
			new OutOfBoundsStrategyMirrorFactory<FloatType>());
		Interpolator<FloatType> interpol = model.createInterpolator(intf);

		final Image<FloatType> result = tgt.getImageFactory().createImage(tgt.getDimensions());

		final LocalizableByDimCursor<FloatType> cTempl = tgt.createLocalizableByDimCursor();
		final LocalizableByDimCursor<FloatType> cResult = result.createLocalizableByDimCursor();
		int[] pos = new int[3];
		float[] transformed = new float[3];

		while(cTempl.hasNext()) {
			cTempl.fwd();
			cTempl.getPosition(pos);
			cResult.setPosition(pos);
			m.apply(pos[0], pos[1], pos[2]);
			transformed[0] = (float)m.x;
			transformed[1] = (float)m.y;
			transformed[2] = (float)m.z;
			interpol.setPosition(transformed);
			cResult.getType().set(interpol.getType().get());
		}
		cTempl.close();
		cResult.close();
		interpol.close();
		return result;
	}

	public static float filterLocal(Cursor<FloatType> cursor, int bins) {
		int[] histo = getHistogram(cursor, bins);
		int sum = HistogramFeatures.getSum(histo);
		// return HistogramFeatures.getEntropy   (histo, sum);
		// return HistogramFeatures.getMin       (histo, sum);
		// return HistogramFeatures.getMax       (histo, sum);
		// return HistogramFeatures.getQuantile  (histo, sum, 0.5f);
		// return HistogramFeatures.getContrast  (histo, sum);
		// return HistogramFeatures.getMean      (histo, sum);
		return HistogramFeatures.getVariance  (histo, sum);
		// return HistogramFeatures.getBimodality(histo, sum);
	}

	// assumes the image is normalized
	public static int[] getHistogram(Cursor<FloatType> cursor, int bins) {
		double binwidth = 1.0 / bins;
		int[] histo = new int[bins + 1];
		int sum = 0;
		while(cursor.hasNext()) {
			cursor.fwd();
			float v = cursor.getType().get();
			if(v == 0f)
				continue;
			int index = (int)Math.floor(v / binwidth);
			histo[index]++;
			sum++;
		}
		return histo;
	}
}
