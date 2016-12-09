package cop5618;

import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.IndexColorModel;
import java.awt.image.WritableRaster;
import java.util.Hashtable;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;

public class FJBufferedImage extends BufferedImage {

	private static ForkJoinPool fjp = new ForkJoinPool();

	/**
	 * The SetRGBTask and GetRGBTask are inner classes of FJBufferedImage so that they are both able to call the serial
	 * super class implementation of SetRGB and GetRGB as the base case for forking. An alternative way would have been
	 * to provide an alternate public facing method in FJBufferedImage that provides access to the super methods, and
	 * then the SetRGBTask and GetRGBTask would not have to be inner classes.
	 *
	 * I thought this was the best implementation because they are specific utility classes extending RecursiveAction
	 * exclusively for FJBufferedImage, so they should be contained in the class itself;
	 */
	private class SetRGBTask extends RecursiveAction {

		int x;
		int y;
		int w;
		int h;
		int[] rgbArray;
		int offset;
		int scansize;
		int tasks;

		public SetRGBTask(int x, int y, int w, int h, int[] rgbArray, int offset, int scansize, int tasks) {
			this.x = x;
			this.y = y;
			this.w = w;
			this.h = h;
			this.rgbArray = rgbArray;
			this.offset = offset;
			this.scansize = scansize;
			this.tasks = tasks;
		}

		@Override
		protected void compute() {
			// Split tasks until this holds one task OR the height of the calculation is one
			if (tasks < 2 || h < 2) {
				FJBufferedImage.super.setRGB(x, y, w, h, rgbArray, offset, scansize);
			}
			else {
				int firstHalf = h/2;
				invokeAll(new SetRGBTask(x, y, w, firstHalf, rgbArray, offset, scansize, tasks/2),
							new SetRGBTask(x, y + firstHalf, w, h - firstHalf, rgbArray, offset + (firstHalf * scansize), scansize, tasks - tasks/2));
			}
		}
	}

	private class GetRGBTask extends RecursiveAction {

		int x;
		int y;
		int w;
		int h;
		int[] rgbArray;
		int offset;
		int scansize;
		int tasks;

		public GetRGBTask(int x, int y, int w, int h, int[] rgbArray, int offset, int scansize, int tasks) {
			this.x = x;
			this.y = y;
			this.w = w;
			this.h = h;
			this.rgbArray = rgbArray;
			this.offset = offset;
			this.scansize = scansize;
			this.tasks = tasks;
		}

		@Override
		protected void compute() {
			// Split tasks until this holds one task OR the height of the calculation is one
			if (tasks < 2 || h < 2) {
				FJBufferedImage.super.getRGB(x, y, w, h, rgbArray, offset, scansize);
			}
			else {
				int firstHalf = h/2;
				invokeAll(new GetRGBTask(x, y, w, firstHalf, rgbArray, offset, scansize, tasks/2),
						new GetRGBTask(x, y + firstHalf, w, h - firstHalf, rgbArray, offset + (firstHalf * scansize), scansize, tasks - tasks/2));
			}
		}
	}
	
	public FJBufferedImage(int width, int height, int imageType) {
		super(width, height, imageType);
	}

	public FJBufferedImage(int width, int height, int imageType, IndexColorModel cm) {
		super(width, height, imageType, cm);
	}

	public FJBufferedImage(ColorModel cm, WritableRaster raster, boolean isRasterPremultiplied,
			Hashtable<?, ?> properties) {
		super(cm, raster, isRasterPremultiplied, properties);
	}
	

	/**
	 * Creates a new FJBufferedImage with the same fields as source.
	 * @param source
	 * @return
	 */
	public static FJBufferedImage BufferedImageToFJBufferedImage(BufferedImage source){
	   	Hashtable<String,Object> properties=null;
	   	String[] propertyNames = source.getPropertyNames();
	   	if (propertyNames != null) {
			properties = new Hashtable<String,Object>();
		   	for (String name: propertyNames){properties.put(name, source.getProperty(name));}
		}
	   return new FJBufferedImage(source.getColorModel(), source.getRaster(), source.isAlphaPremultiplied(), properties);
	}
	
	@Override
	public void setRGB(int xStart, int yStart, int w, int h, int[] rgbArray, int offset, int scansize){
		// Invoke up to 16 * the amount of parallelism available. If it wasn't a greater factor than the amount of parallelism
		// available, there is no advantage of using the Fork/Join framework over other parallel paradigms since there would be
		// no work stealing if there are no tasks left over in case one thread finishes faster. If the factor is too great,
		// there is too much splitting and overhead and each individual base task does not have enough computing.
		// I got the largest speedup with this value of 16, at about 2.2x for all parallelism with 4 cores.
		fjp.invoke(new SetRGBTask(xStart, yStart, w, h, rgbArray, offset, scansize, fjp.getParallelism() * 16));
	}

	@Override
	public int[] getRGB(int xStart, int yStart, int w, int h, int[] rgbArray, int offset, int scansize){
		fjp.invoke(new GetRGBTask(xStart, yStart, w, h, rgbArray, offset, scansize, fjp.getParallelism() * 16));
		return rgbArray;
	}
}
