package cop5618;

import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.IndexColorModel;
import java.awt.image.WritableRaster;
import java.io.File;
import java.io.IOException;
import java.util.Hashtable;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.RecursiveTask;
import java.util.function.Function;

import javax.imageio.ImageIO;

import org.junit.BeforeClass;

public class FJBufferedImage extends BufferedImage {

	private static ForkJoinPool fjp = new ForkJoinPool();

	private class SetRGBTask extends RecursiveAction {

		int x;
		int y;
		int w;
		int h;
		int[] rgbArray;
		int offset;
		int scansize;
		int min_h;

		public SetRGBTask(int x, int y, int w, int h, int[] rgbArray, int offset, int scansize, int min_h) {
			this.x = x;
			this.y = y;
			this.w = w;
			this.h = h;
			this.rgbArray = rgbArray;
			this.offset = offset;
			this.scansize = scansize;
			this.min_h = min_h;
		}

		@Override
		protected void compute() {
			if (h <= min_h) {
//				Height of 2 and full width of integers are baseline for cache performance (hopefully)
				FJBufferedImage.super.setRGB(x, y, w, h, rgbArray, offset, scansize);
			}
			else {
				int firstHalf = h/2;
				invokeAll(new SetRGBTask(x, y, w, firstHalf, rgbArray, offset, scansize, min_h),
							new SetRGBTask(x, y + firstHalf, w, h - firstHalf, rgbArray, offset + (firstHalf * scansize), scansize, min_h));
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

		public GetRGBTask(int x, int y, int w, int h, int[] rgbArray, int offset, int scansize) {
			this.x = x;
			this.y = y;
			this.w = w;
			this.h = h;
			this.rgbArray = rgbArray;
			this.offset = offset;
			this.scansize = scansize;
		}

		@Override
		protected void compute() {
			if (h <= 2) {
//				Height of 2 and full width of integers are baseline for cache performance (hopefully)
				FJBufferedImage.super.getRGB(x, y, w, h, rgbArray, offset, scansize);
			}
			else {
				int firstHalf = h/2;
				invokeAll(new GetRGBTask(x, y, w, firstHalf, rgbArray, offset, scansize),
						new GetRGBTask(x, y + firstHalf, w, h - firstHalf, rgbArray, offset + (firstHalf * scansize), scansize));
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
		new SetRGBTask(xStart, yStart, w, h, rgbArray, offset, scansize).compute();
	}
	

	@Override
	public int[] getRGB(int xStart, int yStart, int w, int h, int[] rgbArray, int offset, int scansize){
		new GetRGBTask(xStart, yStart, w, h, rgbArray, offset, scansize).compute();
		return rgbArray;
	}
	

	}
