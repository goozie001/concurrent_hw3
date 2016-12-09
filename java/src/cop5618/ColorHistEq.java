package cop5618;


import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.function.DoubleBinaryOperator;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class ColorHistEq {

    //Use these labels to instantiate you timers.  You will need 8 invocations of now()
	static String[] labels = { "getRGB", "convert to HSB", "create brightness map", "probability array",
			"parallel prefix", "equalize pixels", "setRGB" };
	// In the assignment, this was said to be given. However, I don't see any additional input arguments in the
	// HW3TestColorHisEq to provide a binNum, so the amount of bins can be specified here
	static int binNum = 256;

	static Timer colorHistEq_serial(BufferedImage image, BufferedImage newImage) {
		Timer times = new Timer(labels);
		ColorModel colorModel = ColorModel.getRGBdefault();
		int w = image.getWidth();
		int h = image.getHeight();
		// total amount of pixels is used in later calculations for array sizew
		int pixelCount = w * h;
		// I don't want more bins than there are pixelCounts
		int bins = Math.min(binNum, pixelCount);
		times.now();
		int[] srcPixelArray = image.getRGB(0, 0, w, h, new int[pixelCount], 0, w);
		times.now();
		// Convert to HSB from RGB
		Object[] HSBArray =
				Arrays.stream(srcPixelArray)
									// The input is ints, but we need to convert every entry to a float array. Therefore,
									// it has to be mapped to an object
									.mapToObj(pixel -> Color.RGBtoHSB(colorModel.getRed(pixel),
															colorModel.getGreen(pixel),
															colorModel.getBlue(pixel),
															new float[3]))
									.toArray();
		times.now();
		// The brightness map has to be contained in a Map at first; it is the only available return type for collect.
		// I will convert the map to an index-based map in the next part of this step
		Map<Integer, Long> brightnessMap =
				Arrays.stream(HSBArray)
						// Convert to integer. This is the bin number assignment: brightness * bin_count
						.mapToInt(pixel -> Math.min((int)(((float[])pixel)[2]*bins), bins-1))
						// Must box IntStream into Stream of Integer objects to be able to call collect with a Collector
						// input argument
						.boxed()
						// Collect into Map by grouping the Integer values by their identity (the value itself), and reducing
						// based on the aggregate count of items in every bin, whose numbers are specified as the Integer
						// values
						.collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

		// Now convert the map to an integer array. No key in map means 0 count; otherwise the amount of elements in the bin
		// equal the value matching the bin number key
		int[] histogram = IntStream.range(0, bins)
									.map(index -> brightnessMap.containsKey(index) ? brightnessMap.get(index).intValue() : 0)
									.toArray();
		times.now();
		// Calculate the probability array by dividing the count of pixels in each bin by the total number of pixels
		// contained through all bins
		double[] probArray = Arrays.stream(histogram)
									// No such thing as a FloatStream; must use DoubleStream
									.mapToDouble(bin -> (double)bin / (double)pixelCount)
									.toArray();
		times.now();
		// Calculate the parallelPrefix to get cumulative probability distribution
		Arrays.parallelPrefix(probArray, (x,y)->x+y);
		times.now();
		// Now, for each pixel in the HSBArray, return a new array of RGB values equal to the converted HSB array while
		// replacing each pixel's brightness by the brightness value calculated in the pixel's corresponding bin in the
		// cumulative probArray
		int[] equalizedPixelArray = Arrays.stream(HSBArray)
											.mapToInt(pixel -> Color.HSBtoRGB(((float[])pixel)[0],
																				((float[])pixel)[1],
																				(float)probArray[Math.min((int) (((float[])pixel)[2]*bins), bins-1)]))
											.toArray();
		times.now();
		newImage.setRGB(0, 0, w, h, equalizedPixelArray, 0, w);
		times.now();

		return times;
	}


	/**
	 *
	 * @param image
	 * @param newImage
	 * @return times
	 *
	 * This parallel variation of the colorHistEq calculation simply makes every stream calculation run in parallel with
	 * the parallel() specification, because the FJBufferedImage automatically converts both getRGB and setRGB to parallel.
	 * Everything else remains the same.
	 */
	static Timer colorHistEq_parallel(FJBufferedImage image, FJBufferedImage newImage) {
		Timer times = new Timer(labels);
		ColorModel colorModel = ColorModel.getRGBdefault();
		int w = image.getWidth();
		int h = image.getHeight();
		int pixelCount = w * h;
		int bins = Math.min(binNum, pixelCount);
		times.now();
		int[] srcPixelArray = image.getRGB(0, 0, w, h, new int[pixelCount], 0, w);
		times.now();
		Object[] HSBArray =
				Arrays.stream(srcPixelArray)
						.parallel()
						.mapToObj(pixel -> Color.RGBtoHSB(colorModel.getRed(pixel),
								colorModel.getGreen(pixel),
								colorModel.getBlue(pixel),
								new float[3]))
						.toArray();
		times.now();

		Map<Integer, Long> brightnessMap =
				Arrays.stream(HSBArray)
						.parallel()
						.mapToInt(pixel -> Math.min((int)(((float[])pixel)[2]*bins), bins-1))
						.boxed()
						.collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

		int[] histogram = IntStream.range(0, bins)
							.parallel()
							.map(index -> brightnessMap.containsKey(index) ? brightnessMap.get(index).intValue() : 0)
							.toArray();
		times.now();
		double[] probArray = Arrays.stream(histogram)
									.parallel()
									.mapToDouble(bin -> (double)bin / (double)pixelCount)
									.toArray();
		times.now();
		Arrays.parallelPrefix(probArray, (x,y)->x+y);
		times.now();
		int[] equalizedPixelArray = Arrays.stream(HSBArray)
											.parallel()
											.mapToInt(pixel -> Color.HSBtoRGB(((float[])pixel)[0],
												((float[])pixel)[1],
												(float)probArray[Math.min((int) (((float[])pixel)[2]*bins), bins-1)]))
											.toArray();
		times.now();
		newImage.setRGB(0, 0, w, h, equalizedPixelArray, 0, w);
		times.now();

		return times;
	}

}
