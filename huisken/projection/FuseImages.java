package huisken.projection;

import ij.IJ;
import ij.ImagePlus;

import ij.plugin.filter.PlugInFilter;

import ij.process.ImageProcessor;

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


public class FuseImages implements PlugInFilter {

	private Image<FloatType> image;

	private static final int HISTOGRAM_BINS = 500;


	public int setup(String arg, ImagePlus imp) {
		this.image = ImagePlusAdapter.wrap(imp);
		return DOES_8G | DOES_16 | DOES_32;
	}

	private int startLevel, numLevels;

	public void run(ImageProcessor ip) {

		int r = (int)IJ.getNumber("Window radius", 5);
		if(r == IJ.CANCELED)
			return;

		startLevel = 1; // 1 is the original
		numLevels = 4;

		// normalize
		NormalizeImageMinMax<FloatType> mm = new NormalizeImageMinMax<FloatType>(image);
		mm.process();

		// filter in multi resolution
		Image<FloatType> target = filterMultiResolution(image, 1, r);

		// output
		ImageJFunctions.copyToImagePlus( target ).show();
	}

	// source: source image for that level
	// target: target image for that level
	public Image<FloatType> filterMultiResolution(Image<FloatType> source, int level, int r) {
		System.out.println("level = " + level);

		// not yet started, just upsample result from deeper levels
		if(level < startLevel)
			return upsample(filterMultiResolution(downsample(source), level + 1, r));

		
		Image<FloatType> t = filter(source, r);
		ImageJFunctions.copyToImagePlus(t).show();

		// deepest level: just return the filtered image of this level
		if(level == startLevel + numLevels - 1)
			return t;

		// in between: add upsampled result from deeper levels and the filtered image of this level
		add(t, upsample(filterMultiResolution(downsample(source), level + 1, r)));
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

		int sum = 0;
		while(cursor.hasNext()) {
			sum++;
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

	public static float getBackgroundValue(Image<FloatType> img) {
		Cursor<FloatType> c = img.createCursor();
		int[] h = getHistogram(c, 500);
		int index = HistogramFeatures.getFirstMode(HistogramFeatures.smooth(h, 5));
		return index * 1f / 500;
	}

	public Image<FloatType> downsample(Image<FloatType> source) {
		DownSample<FloatType> ds = new DownSample<FloatType>(source, 0.5f);
		ds.process();
		return ds.getResult();
	}

	public Image<FloatType> upsample(Image<FloatType> source) {
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

	public void add(Image<FloatType> i1, Image<FloatType> i2) {
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

	// assumes a 3x4 matrix row by row
	public static Image<FloatType> transformImage(Image<FloatType> image, float[] matrix, float bg) {
		AffineModel3D model = new AffineModel3D();
		model.set(
			matrix[0], matrix[1], matrix[2], matrix[3],
			matrix[4], matrix[5], matrix[6], matrix[7],
			matrix[8], matrix[9], matrix[10], matrix[11]);

		InterpolatorFactory intf = new LinearInterpolatorFactory(
			new OutOfBoundsStrategyValueFactory(new FloatType(bg)));


		ImageTransform transform = new ImageTransform(image, model, intf );
		transform.process();
		return transform.getResult();
	}

	public static float filterLocal(Cursor<FloatType> cursor, int bins) {
		int[] histo = getHistogram(cursor, bins);
		int sum = HistogramFeatures.getSum(histo);
		return HistogramFeatures.getMean(histo, sum);
	}

	// assumes the image is normalized
	public static int[] getHistogram(Cursor<FloatType> cursor, int bins) {
		double binwidth = 1.0 / bins;
		int[] histo = new int[bins + 1];
		int sum = 0;
		while(cursor.hasNext()) {
			cursor.fwd();
			float v = cursor.getType().get();
			int index = (int)Math.floor(v / binwidth);
			histo[index]++;
			sum++;
		}
		return histo;
	}
}
