import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.io.File;
import java.util.*;
import javax.imageio.ImageIO;

/** Export remastered portraits to 256px indexed PNGs without grain-producing dithering. */
public final class PortraitExport {
    private record Sample(int argb, int count) {
        int channel(int n) { return (argb >>> (n * 8)) & 255; }
    }
    private static final class Box {
        final List<Sample> samples;
        final int population, channel, range;
        Box(List<Sample> samples) {
            this.samples = samples;
            int sum = 0, widest = -1, axis = 0;
            for (Sample sample : samples) sum += sample.count;
            for (int c = 0; c < 4; c++) {
                int low = 255, high = 0;
                for (Sample sample : samples) {
                    low = Math.min(low, sample.channel(c)); high = Math.max(high, sample.channel(c));
                }
                if (high - low > widest) { widest = high - low; axis = c; }
            }
            population = sum; channel = axis; range = widest;
        }
        long priority() { return samples.size() > 1 ? (long) range * population : -1; }
        int average() {
            long[] sums = new long[4];
            for (Sample sample : samples) for (int c = 0; c < 4; c++)
                sums[c] += (long) sample.channel(c) * sample.count;
            int argb = 0;
            for (int c = 0; c < 4; c++) argb |= (int) ((sums[c] + population / 2) / population) << (c * 8);
            return argb;
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("Usage: java -Djava.awt.headless=true tools/PortraitExport.java SOURCE.png OUTPUT.png");
        BufferedImage source = ImageIO.read(new File(args[0]));
        if (source == null) throw new IllegalArgumentException("Unsupported image: " + args[0]);
        BufferedImage scaled = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
        var graphics = scaled.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        double scale = Math.min(256d / source.getWidth(), 256d / source.getHeight());
        int w = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int h = Math.max(1, (int) Math.round(source.getHeight() * scale));
        graphics.drawImage(source, (256-w)/2, (256-h)/2, w, h, null);
        graphics.dispose();

        // Keep alpha in the quantization space, reserving one truly transparent palette entry.
        Map<Integer, Integer> histogram = new TreeMap<>();
        for (int y = 0; y < 256; y++) for (int x = 0; x < 256; x++) {
            int pixel = scaled.getRGB(x, y);
            if ((pixel >>> 24) != 0) histogram.merge(pixel, 1, Integer::sum);
        }
        if (histogram.isEmpty()) throw new IllegalArgumentException("Source contains no visible artwork");
        List<Sample> samples = new ArrayList<>();
        histogram.forEach((pixel, count) -> samples.add(new Sample(pixel, count)));
        List<Box> boxes = new ArrayList<>();
        boxes.add(new Box(samples));
        while (boxes.size() < 255) {
            Box box = boxes.stream().max(Comparator.comparingLong(Box::priority)).orElseThrow();
            if (box.priority() < 0) break;
            box.samples.sort(Comparator.comparingInt(s -> s.channel(box.channel)));
            int count = 0, split = 1;
            for (int i = 0; i < box.samples.size() - 1; i++) {
                count += box.samples.get(i).count;
                split = i + 1;
                if (count >= box.population / 2) break;
            }
            boxes.remove(box);
            boxes.add(new Box(new ArrayList<>(box.samples.subList(0, split))));
            boxes.add(new Box(new ArrayList<>(box.samples.subList(split, box.samples.size()))));
        }
        int[] palette = new int[boxes.size() + 1];
        for (int i = 0; i < boxes.size(); i++) palette[i + 1] = boxes.get(i).average();
        IndexColorModel model = new IndexColorModel(8, palette.length, palette, 0, true, 0, java.awt.image.DataBuffer.TYPE_BYTE);
        BufferedImage output = new BufferedImage(256, 256, BufferedImage.TYPE_BYTE_INDEXED, model);
        Map<Integer, Integer> nearest = new HashMap<>();
        for (int y = 0; y < 256; y++) for (int x = 0; x < 256; x++) {
            int pixel = scaled.getRGB(x, y);
            int index = (pixel >>> 24) == 0 ? 0 : nearest.computeIfAbsent(pixel, value -> {
                int best = 1; long distance = Long.MAX_VALUE;
                for (int i = 1; i < palette.length; i++) {
                    long error = 0;
                    for (int c = 0; c < 4; c++) {
                        int delta = ((value >>> (c*8)) & 255) - ((palette[i] >>> (c*8)) & 255);
                        error += (long) delta * delta;
                    }
                    if (error < distance) { distance = error; best = i; }
                }
                return best;
            });
            output.getRaster().setSample(x, y, 0, index);
        }
        ImageIO.write(output, "png", new File(args[1]));
        System.out.println(args[1] + ": 256x256, " + palette.length + " palette entries including transparency, no dithering");
    }
}
