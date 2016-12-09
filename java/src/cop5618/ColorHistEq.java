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
	static int binNum = 16;

	static Timer colorHistEq_serial(BufferedImage image, BufferedImage newImage) {
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
									.mapToObj(pixel -> Color.RGBtoHSB(colorModel.getRed(pixel),
															colorModel.getGreen(pixel),
															colorModel.getBlue(pixel),
															new float[3]))
									.toArray();
		times.now();

		Map<Integer, Long> brightnessMap =
				Arrays.stream(HSBArray)
						.mapToInt(pixel -> Math.min((int)(((float[])pixel)[2]*bins), bins-1))
						.boxed()
						.collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

		int[] histogram = IntStream.range(0, bins)
									.map(index -> brightnessMap.containsKey(index) ? brightnessMap.get(index).intValue() : 0)
									.toArray();
		times.now();
		double[] probArray = Arrays.stream(histogram)
									// No such thing as a FloatStream; must use DoubleStream
									.mapToDouble(bin -> (double)bin / (double)pixelCount)
									.toArray();
		times.now();
		Arrays.parallelPrefix(probArray, (x,y)->x+y);
		times.now();
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
