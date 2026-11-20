import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

/**
 * Makes the desktop app icons from the store icon (fastlane/metadata/android/en-US/images/icon.png):
 * a rounded square with a margin, as macOS draws app icons, used on every OS so they match.
 * Run from the repo root: java desktop/icons/MakeIcons.java (macOS only: .icns needs iconutil).
 */
public class MakeIcons {
    public static void main(String[] args) throws Exception {
        BufferedImage source = ImageIO.read(new File("fastlane/metadata/android/en-US/images/icon.png"));
        Path out = Path.of("desktop/icons");

        // Linux packages and the window icon on every OS read this one from the classpath.
        ImageIO.write(icon(source, 512), "png", new File("desktop/src/main/resources/icon.png"));

        List<byte[]> icoImages = new ArrayList<>();
        int[] icoSizes = {16, 24, 32, 48, 64, 128, 256};
        for (int size : icoSizes) icoImages.add(png(icon(source, size)));
        Files.write(out.resolve("icon.ico"), ico(icoSizes, icoImages));

        Path iconset = Files.createTempDirectory("reliquary").resolve("icon.iconset");
        Files.createDirectories(iconset);
        for (int size : new int[] {16, 32, 128, 256, 512}) {
            ImageIO.write(icon(source, size), "png", iconset.resolve("icon_" + size + "x" + size + ".png").toFile());
            ImageIO.write(icon(source, size * 2), "png", iconset.resolve("icon_" + size + "x" + size + "@2x.png").toFile());
        }
        Process iconutil = new ProcessBuilder("iconutil", "-c", "icns", iconset.toString(), "-o", out.resolve("icon.icns").toString())
            .inheritIO().start();
        if (iconutil.waitFor() != 0) throw new IOException("iconutil failed");
    }

    /** Apple's grid: an 824 px rounded square (185 px corners) centred on a 1024 px canvas. */
    static BufferedImage icon(BufferedImage source, int size) {
        double scale = size / 1024.0;
        double inset = 100 * scale;
        double side = 824 * scale;
        double radius = 185 * scale;
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setClip(new RoundRectangle2D.Double(inset, inset, side, side, radius * 2, radius * 2));
        g.drawImage(source, (int) Math.round(inset), (int) Math.round(inset), (int) Math.round(side), (int) Math.round(side), null);
        g.dispose();
        return image;
    }

    static byte[] png(BufferedImage image) throws IOException {
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bytes);
        return bytes.toByteArray();
    }

    /** An .ico holding PNG images, which Windows Vista and later read. */
    static byte[] ico(int[] sizes, List<byte[]> images) {
        int headerSize = 6 + 16 * sizes.length;
        int total = headerSize + images.stream().mapToInt(i -> i.length).sum();
        ByteBuffer buffer = ByteBuffer.allocate(total).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putShort((short) 0).putShort((short) 1).putShort((short) sizes.length);
        int offset = headerSize;
        for (int i = 0; i < sizes.length; i++) {
            int size = sizes[i] >= 256 ? 0 : sizes[i]; // 0 means 256
            buffer.put((byte) size).put((byte) size).put((byte) 0).put((byte) 0);
            buffer.putShort((short) 1).putShort((short) 32);
            buffer.putInt(images.get(i).length).putInt(offset);
            offset += images.get(i).length;
        }
        images.forEach(buffer::put);
        return buffer.array();
    }
}
