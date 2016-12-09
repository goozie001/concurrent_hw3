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
			// Threads
			if (tasks < 2 || h < 2) {
//				Height of 2 and full width of integers are baseline for cache performance (hopefully)
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
		fjp.invoke(new SetRGBTask(xStart, yStart, w, h, rgbArray, offset, scansize, fjp.getParallelism() * 16));
	}

	@Override
	public int[] getRGB(int xStart, int yStart, int w, int h, int[] rgbArray, int offset, int scansize){
		fjp.invoke(new GetRGBTask(xStart, yStart, w, h, rgbArray, offset, scansize, fjp.getParallelism() * 16));
		return rgbArray;
	}
}
