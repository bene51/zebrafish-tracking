package huisken.texture;
//=================================================================================================
// modified from GLCM_Texture_Too by Toby C. Cornish http://tobycornish.com/downloads/imagej/, which was in turn
// modified from GLCM_Texture (Gray Level Correlation Matrix Texture Analyzer) v0.4, Author: Julio E. Cabrera, 06/10/05
// 
//=================================================================================================
//
// References: 
//   R.M. Haralick, Texture feature for image classification, IEEE Trans. SMC 3 (1973) (1), pp. 610�621.
//   Conners, R.W., Trivedi, M.M., and Harlow, C.A., Segmentation of a High-Resolution Urban Scene
//     Using Texture Operators, CVGIP(25), No. 3, March, 1984, pp. 273-310.
//   Walker, RF, Jackway, P and Longstaff, ID (1995) Improving Co-occurrence Matrix Feature Discrimination.'
//     In  DICTA '95, 3rd Conference on Digital Image Computing: Techniques and Application, 6 - 8 December,
//     1995, pages 643-648.
//   Parker, JR, Algorithms for Image Processing and Computer Vision, John Wiley & Sons, 1997.
//   Image processing lab, Department of Informatics, University of Oslo. Xite v1.35: glcmParameter.c, v1.30
//     2004/05/05 07:34:19 (2004)
        
import ij.*;
import ij.IJ.*;
import ij.gui.*;
import ij.plugin.filter.PlugInFilter;
import ij.process.*;
import java.awt.*;
import ij.plugin.PlugIn;
import ij.text.*;
import ij.measure.ResultsTable;

//==========================================================
public class GLCM_TextureFilter implements PlugInFilter {
	public static final int d = 1;
	public static final boolean symmetry = true;

	public static enum Method {
		Homogeneity,
		Contrast,
		Energy,
		Entropy,
		Variance,
		Shade,
		Prominence,
		Inertia,
		Correlation,
		Binaryness
	}

	private double[][] glcm = new double[256][256];
	private double meanx, meany;
	private double stdevx, stdevy;

	public int setup(String arg, ImagePlus imp) {
		return DOES_8G;
	}

	/**
	 * Average over all angles
	 */
	public static double[] filter(ImagePlus imp, Method method) {
		int d = imp.getStackSize();
		double[] v = new double[d];
		GLCM_TextureFilter glcm = new GLCM_TextureFilter();

		for(int z = 0; z < d; z++) {
			ImageProcessor ip = imp.getStack().getProcessor(z + 1);
			v[z] = filter(ip, method);
		}
		return v;
	}

	public static double filter(ImageProcessor ip, Method method) {
		GLCM_TextureFilter glcm = new GLCM_TextureFilter();
		glcm.calculateGLCM(ip, 0);
		double v = glcm.calculate(method);
	
		glcm.calculateGLCM(ip, 45);
		v += glcm.calculate(method);
		
		glcm.calculateGLCM(ip, 90);
		v += glcm.calculate(method);
		
		glcm.calculateGLCM(ip, 135);
		v += glcm.calculate(method);
	
		return v / 4;
	}

	public static FloatProcessor getGLCMAsImage(ImageProcessor ip, int angle) {
		GLCM_TextureFilter glcm = new GLCM_TextureFilter();
		glcm.calculateGLCM(ip, angle);

		float[] v = new float[256 * 256];
		for(int i = 0; i < v.length; i++)
			v[i] = (float)glcm.glcm[i / 256][i % 256];

		return new FloatProcessor(256, 256, v, null);
	}

	public void run(ImageProcessor ip) {
		GenericDialog gd = new GenericDialog("GLCM Texture Filter");

		int phi = 0;
		Method method = Method.Homogeneity;
		boolean symmetry = true;

		String[] angles = {"0", "45", "90", "135"};
		String[] methods = new String[Method.values().length];
		for(int i = 0; i < Method.values().length; i++)
			methods[i] = Method.values()[i].toString();
		
		gd.addChoice("Direction", angles, Integer.toString(phi));
		gd.addChoice("Method", methods, method.toString());

		gd.showDialog();
		if (gd.wasCanceled())
			return;

		phi = Integer.parseInt(gd.getNextChoice());
		method = Method.values()[gd.getNextChoiceIndex()];

		int w = ip.getWidth();
		int h = ip.getHeight();

		calculateGLCM(ip, phi);
		FloatProcessor result = new FloatProcessor(w, h);
		double d = calculate(method);
		IJ.showMessage(method + " is " + d);
	}

	// requires the glcm to be calculated before
	public double calculate(Method method) {
		switch(method) {
			case Homogeneity: return calculateHomogeneity();
			case Contrast:    return calculateContrast();
			case Energy:      return calculateEnergy();
			case Entropy:     return calculateEntropy();
			case Variance:    return calculateVariance();
			case Shade:       return calculateShade();
			case Prominence:  return calculateProminence();
			case Inertia:     return calculateInertia();
			case Correlation: return calculateCorrelation();
			case Binaryness:  return calculateBinaryness();
			default: throw new IllegalArgumentException("Invalid method");
		}
	}

	// angle must be 0, 45, 90 or 135
	public void calculateGLCM(ImageProcessor ip, int phi) {

		int w = ip.getWidth();
		int h = ip.getHeight();

		byte[] pixels = (byte[])ip.getPixels();
		
		int offsetX = 1;
		int offsetY = 0;

		int pixelCount = 0;

		// set our offsets based on the selected angle
		if (phi == 0) {
			offsetX = d;
			offsetY = 0;
		} else if (phi == 45) {
			offsetX = d;
			offsetY = -d;
		} else if (phi == 90) {
			offsetX = 0;
			offsetY = -d;
		} else if (phi == 135) {
			offsetX = -d;
			offsetY = -d;
		} else {
			throw new IllegalArgumentException("Invalid angle");
		} 

		for(int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				
				// check to see if the offset pixel is in the roi
				int dx = x + offsetX;
				int dy = y + offsetY;
				if(dx >= 0 && dx < w && dy >= 0 && dy < h) {
					// check to see if the offset pixel is in the mask (if it exists) 
					int value  = 0xff & pixels[ y * w + x];
					int dValue = 0xff & pixels[dy * w + dx];
					glcm [value][dValue]++;		  			
					pixelCount++;
				}
				// if symmetry is selected, invert the offsets and go through the process again
				if (symmetry) {
					dx = x - offsetX;
					dy = y - offsetY;
					if(dx >= 0 && dx < w && dy >= 0 && dy < h) {
						int value  = 0xff & pixels[y  * w + x];
						int dValue = 0xff & pixels[dy * w + dx];
						glcm [dValue][value]++;		  			
						pixelCount++;
					}	
				}
			}
		}

		// convert the GLCM from absolute counts to probabilities
		for (int i=0; i<256; i++)
			for (int j=0; j<256; j++)
				glcm[i][j] = glcm[i][j] / pixelCount;

		// calculate meanx, meany, stdevx and stdevy for the glcm
		double [] px = new double [256];
		double [] py = new double [256];
		meanx=0.0;
		meany=0.0;
		stdevx=0.0;
		stdevy=0.0;

		// Px(i) and Py(j) are the marginal-probability matrix; sum rows (px) or columns (py) 
		// First, initialize the arrays to 0
		for (int i=0;  i<256; i++){
			px[i] = 0.0;
			py[i] = 0.0;
		}

		// sum the glcm rows to Px(i)
		for (int i=0;  i<256; i++)
			for (int j=0; j<256; j++)
				px[i] += glcm [i][j];

		// sum the glcm rows to Py(j)
		for (int j=0;  j<256; j++)
			for (int i=0; i<256; i++)
				py[j] += glcm [i][j];

		// calculate meanx and meany
		for (int i=0;  i<256; i++) {
			meanx += i * px[i];
			meany += i * py[i];
		}

		// calculate stdevx and stdevy
		for (int i=0;  i<256; i++) {
			double dx = i - meanx;
			double dy = i - meany;
			stdevx += dx * dx * px[i];
			stdevy += dy * dy * py[i];
		}
	}

	public double calculateHomogeneity() {
		//===============================================================================================
		// calculate the inverse difference moment (idm) (Walker, et al. 1995)
		// this is calculated using the same formula as Conners, et al., 1984 "Local Homogeneity"

		double IDM = 0.0;
		for (int i=0;  i<256; i++) {
			for (int j=0; j<256; j++) {
				double d = i - j;
				IDM += glcm[i][j] / (1 + d * d);
			}
		}
		return IDM;
	}

	public double calculateContrast() {
		//=====================================================================================================
		// calculate the contrast (Haralick, et al. 1973)
		// similar to the inertia, except abs(i-j) is used

		double contrast=0.0;

		for (int i=0;  i<256; i++)  {
			for (int j=0; j<256; j++) {
				double d = i - j;
				contrast += d * d *(glcm[i][j]);
			}
		}
		return contrast;
	}

	public double calculateEnergy() {
		//===============================================================================================
		// calculate the energy

		double energy = 0.0;
		for (int i=0;  i<256; i++)  {
			for (int j=0; j<256; j++) {
				energy += Math.pow(glcm[i][j],2);
			}
		}
		return Math.sqrt(energy);
	}

	public double calculateEntropy() {
		//===============================================================================================
		// calculate the entropy (Haralick et al., 1973; Walker, et al., 1995)

		double entropy = 0.0;
		for (int i=0;  i<256; i++)
			for (int j=0; j<256; j++)
				if (glcm[i][j] != 0)
					entropy = entropy - glcm[i][j] * Math.log(glcm[i][j]);
		return entropy;
	}

	public double calculateVariance() {
		//===============================================================================================
		// calculate the variance ("variance" in Walker 1995; "Sum of Squares: Variance" in Haralick 1973)

		double variance = 0.0;
		double mean = 0.0;

		mean = (meanx + meany)/2;
		for (int i=0;  i<256; i++)  {
			for (int j=0; j<256; j++) {
				double d = i - mean;
				variance += d * d * glcm[i][j];
			}
		}
		return variance;
	}

	public double calculateShade() {
		//===============================================================================================
		// calculate the shade (Walker, et al., 1995; Connors, et al. 1984)
		double shade = 0.0;

		// calculate the shade parameter
		for (int i=0;  i<256; i++) {
			for (int j=0; j<256; j++) {
				double l = i + j - meanx - meany;
				shade += l * l * l * glcm[i][j];
			}
		}
		return shade;
	}

	public double calculateProminence() {
		//==============================================================================================
		// calculate the prominence (Walker, et al., 1995; Connors, et al. 1984)
		double prominence=0.0;

		for (int i=0;  i<256; i++) {
			for (int j=0; j<256; j++) {
				double l = i + j - meanx - meany;
				prominence += l * l * l * l * glcm[i][j];
			}
		}
		return prominence;
	} 

	public double calculateInertia() {
		//===============================================================================================
		// calculate the inertia (Walker, et al., 1995; Connors, et al. 1984)

		double inertia = 0.0;
		for (int i=0;  i<256; i++)  {
			for (int j=0; j<256; j++) {
				if (glcm[i][j] != 0) {
					double d = i - j;
					inertia += d * d * glcm[i][j];
				}
			}
		}
		return inertia;
	}

	public double calculateCorrelation() {
		//=====================================================================================================
		// calculate the correlation
		// methods based on Haralick 1973 (and MatLab), Walker 1995 are included below
		// Haralick/Matlab result reported for correlation currently; will give Walker as an option in the future

		double correlation=0.0;

		// calculate the correlation parameter
		for (int i=0;  i<256; i++) {
			for (int j=0; j<256; j++) {
				//Walker, et al. 1995 (matches Xite)
				//correlation += ((((i-meanx)*(j-meany))/Math.sqrt(stdevx*stdevy))*glcm[i][j]);
				//Haralick, et al. 1973 (continued below outside loop; matches original GLCM_Texture)
				//correlation += (i*j)*glcm[i][j];
				//matlab's rephrasing of Haralick 1973; produces the same result as Haralick 1973
				correlation += ((((i-meanx)*(j-meany))/( stdevx*stdevy))*glcm[i][j]);
			}
		}
		//Haralick, et al. 1973, original method continued.
		//correlation = (correlation -(meanx*meany))/(stdevx*stdevy);
		return correlation;
	}

	public double calculateBinaryness() {
		double binaryness = 0.0;
		for (int i=0;  i<256; i++) {
			for (int j=0; j<256; j++) {
				binaryness += glcm[i][j] * (float)Math.exp(-0.005 * (256 - i) * (256 - j));			
			}
		}

		return binaryness;
	}
}
