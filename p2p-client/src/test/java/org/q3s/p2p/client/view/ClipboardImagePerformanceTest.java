package org.q3s.p2p.client.view;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.*;
import javax.swing.text.AttributeSet;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.rtf.RTFEditorKit;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.util.Base64;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

public class ClipboardImagePerformanceTest {

    @TempDir
    Path tempDir;
    private Path testImagePath;

    @BeforeEach
    void setup() throws Exception {
        BufferedImage img = new BufferedImage(3440, 1440, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        java.util.Random random = new java.util.Random(42);
        for (int y = 0; y < 1440; y += 4) {
            for (int x = 0; x < 3440; x += 4) {
                int r = random.nextInt(256);
                int gg = random.nextInt(256);
                int b = random.nextInt(256);
                g.setColor(new java.awt.Color(r, gg, b));
                g.fillRect(x, y, 4, 4);
            }
        }
        g.dispose();
        testImagePath = tempDir.resolve("test_image.png");
        javax.imageio.ImageIO.write(img, "png", testImagePath.toFile());
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void testImageLoadTime() throws Exception {
        long loadTime = 0;
        BufferedImage image = null;

        long start = System.nanoTime();
        image = javax.imageio.ImageIO.read(testImagePath.toFile());
        loadTime = System.nanoTime() - start;

        assertNotNull(image, "Test image should load");
        System.out.println("[PERF] Image loaded: " + image.getWidth() + "x" + image.getHeight());
        System.out.println("[PERF] ImageIO read time: " + (loadTime / 1_000_000) + "ms");
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void testImageSerializationToBase64Time() throws Exception {
        BufferedImage image = javax.imageio.ImageIO.read(testImagePath.toFile());
        assertNotNull(image);

        final long[] serializationTime = {0};
        final String[] encoded = {null};
        CountDownLatch latch = new CountDownLatch(1);

        new Thread(() -> {
            long start = System.nanoTime();
            try {
                BufferedImage bi = new BufferedImage(image.getWidth(null), image.getHeight(null), BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = bi.createGraphics();
                g.drawImage(image, 0, 0, null);
                g.dispose();

                ByteArrayOutputStream out = new ByteArrayOutputStream();
                javax.imageio.ImageIO.write(bi, "png", out);

                encoded[0] = Base64.getEncoder().encodeToString(out.toByteArray());
            } catch (Exception e) {
                fail("Serialization failed: " + e.getMessage());
            }
            serializationTime[0] = System.nanoTime() - start;
            latch.countDown();
        }).start();

        assertTrue(latch.await(60, TimeUnit.SECONDS), "Serialization should complete within 60s");
        System.out.println("[PERF] PNG serialization + Base64 encode time: " + (serializationTime[0] / 1_000_000) + "ms");
        System.out.println("[PERF] Encoded size: " + (encoded[0] != null ? encoded[0].length() : 0) + " chars");
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void testNotesDocumentInsertTime() throws Exception {
        BufferedImage image = javax.imageio.ImageIO.read(testImagePath.toFile());
        assertNotNull(image);

        final long[] insertTime = {0};
        final long[] serializeTime = {0};
        CountDownLatch latch = new CountDownLatch(1);

        SwingUtilities.invokeLater(() -> {
            try {
                Image scaled = image.getScaledInstance(400, 300, Image.SCALE_SMOOTH);
                SimpleAttributeSet attrs = new SimpleAttributeSet();
                StyleConstants.setIcon(attrs, new ImageIcon(scaled));

                DefaultStyledDocument doc = new DefaultStyledDocument();

                long startInsert = System.nanoTime();
                doc.insertString(0, "Test content ", null);
                doc.insertString(doc.getLength(), " ", attrs);
                doc.insertString(doc.getLength(), " More text after image", null);
                long insertDone = System.nanoTime();

                insertTime[0] = insertDone - startInsert;

                long startSerialize = System.nanoTime();
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                new RTFEditorKit().write(out, doc, 0, doc.getLength());
                long serializeDone = System.nanoTime();

                serializeTime[0] = serializeDone - startSerialize;

            } catch (Exception e) {
                fail("Notes operation failed: " + e.getMessage());
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(120, TimeUnit.SECONDS), "Notes operation should complete within 120s");
        System.out.println("[PERF] Image + text insert into StyledDocument: " + (insertTime[0] / 1_000_000) + "ms");
        System.out.println("[PERF] RTF serialization of full document: " + (serializeTime[0] / 1_000_000) + "ms");
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void testFullClipboardPasteFlow() throws Exception {
        System.out.println("[PERF] === FULL CLIPBOARD PASTE FLOW TEST ===");
        System.out.println("[PERF] Image: 2.4MB on disk");

        BufferedImage image = javax.imageio.ImageIO.read(testImagePath.toFile());
        assertNotNull(image);

        final AtomicLong totalTime = new AtomicLong(0);
        final AtomicLong clipboardReadTime = new AtomicLong(0);
        final AtomicLong scaleTime = new AtomicLong(0);
        final AtomicLong insertTime = new AtomicLong(0);
        final AtomicLong serializeTime = new AtomicLong(0);
        final AtomicLong encodeTime = new AtomicLong(0);
        final String[] encodedResult = {null};
        CountDownLatch latch = new CountDownLatch(1);

        SwingUtilities.invokeLater(() -> {
            long overallStart = System.nanoTime();

            try {
                long clipboardStart = System.nanoTime();
                Transferable t = Toolkit.getDefaultToolkit().getSystemClipboard().getContents(null);
                boolean hasImage = t != null && t.isDataFlavorSupported(DataFlavor.imageFlavor);
                clipboardReadTime.set(System.nanoTime() - clipboardStart);
                System.out.println("[PERF] Step 0 - Clipboard check: " + (clipboardReadTime.get() / 1_000_000) + "ms, hasImage=" + hasImage);

                long scaleStart = System.nanoTime();
                int maxWidth = 800;
                int width = image.getWidth(null);
                int height = image.getHeight(null);
                if (width > maxWidth) {
                    height = (int) Math.round(height * (maxWidth / (double) width));
                    width = maxWidth;
                }
                Image scaled = image.getScaledInstance(width, height, Image.SCALE_SMOOTH);
                scaleTime.set(System.nanoTime() - scaleStart);
                System.out.println("[PERF] Step 1 - Image scale to " + width + "x" + height + ": " + (scaleTime.get() / 1_000_000) + "ms");

                long insertStart = System.nanoTime();
                DefaultStyledDocument doc = new DefaultStyledDocument();
                SimpleAttributeSet attrs = new SimpleAttributeSet();
                StyleConstants.setIcon(attrs, new ImageIcon(scaled));

                doc.insertString(0, "Image: ", null);
                doc.insertString(doc.getLength(), " ", attrs);
                doc.insertString(doc.getLength(), " - end of content", null);
                insertTime.set(System.nanoTime() - insertStart);
                System.out.println("[PERF] Step 2 - Insert into StyledDocument: " + (insertTime.get() / 1_000_000) + "ms");

                long serializeStart = System.nanoTime();
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                new RTFEditorKit().write(out, doc, 0, doc.getLength());
                byte[] rtfBytes = out.toByteArray();
                serializeTime.set(System.nanoTime() - serializeStart);
                System.out.println("[PERF] Step 3 - RTF serialization: " + (serializeTime.get() / 1_000_000) + "ms, RTF size=" + rtfBytes.length + " bytes");

                long encodeStart = System.nanoTime();
                encodedResult[0] = Base64.getEncoder().encodeToString(rtfBytes);
                encodeTime.set(System.nanoTime() - encodeStart);
                System.out.println("[PERF] Step 4 - Base64 encode: " + (encodeTime.get() / 1_000_000) + "ms, final size=" + encodedResult[0].length() + " chars");

                totalTime.set(System.nanoTime() - overallStart);
                System.out.println("[PERF] TOTAL ELAPSED: " + (totalTime.get() / 1_000_000) + "ms");

            } catch (Exception e) {
                fail("Full flow failed: " + e.getMessage());
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(180, TimeUnit.SECONDS), "Full flow should complete within 180s");
        assertNotNull(encodedResult[0]);
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void testDocumentWithLargeImageSerialization() throws Exception {
        System.out.println("[PERF] === DOCUMENT WITH LARGE IMAGE SERIALIZATION TEST ===");
        System.out.println("[PERF] Image: 2.4MB, scaling to 800px width (typical paste scenario)");

        BufferedImage image = javax.imageio.ImageIO.read(testImagePath.toFile());
        assertNotNull(image);

        final AtomicLong totalTime = new AtomicLong(0);
        final int[] resultSize = {0};
        CountDownLatch latch = new CountDownLatch(1);

        new Thread(() -> {
            long overallStart = System.nanoTime();

            try {
                long start = System.nanoTime();
                int maxWidth = 800;
                int width = image.getWidth(null);
                int height = image.getHeight(null);
                if (width > maxWidth) {
                    height = (int) Math.round(height * (maxWidth / (double) width));
                    width = maxWidth;
                }
                Image scaled = image.getScaledInstance(width, height, Image.SCALE_SMOOTH);
                long scaleTime = System.nanoTime() - start;
                System.out.println("[PERF] Scale image to " + width + "x" + height + ": " + (scaleTime / 1_000_000) + "ms");

                start = System.nanoTime();
                BufferedImage bi = new BufferedImage(scaled.getWidth(null), scaled.getHeight(null), BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = bi.createGraphics();
                g.drawImage(scaled, 0, 0, null);
                g.dispose();
                long convertTime = System.nanoTime() - start;
                System.out.println("[PERF] Convert to BufferedImage: " + (convertTime / 1_000_000) + "ms");

                start = System.nanoTime();
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                javax.imageio.ImageIO.write(bi, "png", out);
                byte[] pngBytes = out.toByteArray();
                long pngTime = System.nanoTime() - start;
                System.out.println("[PERF] PNG encode: " + (pngTime / 1_000_000) + "ms, PNG size=" + pngBytes.length + " bytes");

                start = System.nanoTime();
                String encoded = Base64.getEncoder().encodeToString(pngBytes);
                long encodeTime = System.nanoTime() - start;
                resultSize[0] = encoded.length();
                System.out.println("[PERF] Base64 encode: " + (encodeTime / 1_000_000) + "ms, encoded size=" + resultSize[0] + " chars");

                totalTime.set(System.nanoTime() - overallStart);
                System.out.println("[PERF] TOTAL TIME: " + (totalTime.get() / 1_000_000) + "ms");
                System.out.println("[PERF] NOTE: This is what serializeNotesState() does for each image - it produces ~4MB for full HD image");

            } catch (Exception e) {
                fail("Document serialization failed: " + e.getMessage());
            } finally {
                latch.countDown();
            }
        }, "notes-serialization-test").start();

        assertTrue(latch.await(120, TimeUnit.SECONDS), "Test should complete within 120s");
        assertTrue(resultSize[0] > 100_000, "Encoded image should be >100KB, was: " + resultSize[0]);
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void testWhiteboardImageAddTime() throws Exception {
        System.out.println("[PERF] === WHITEBOARD IMAGE ADD TEST ===");

        BufferedImage image = javax.imageio.ImageIO.read(testImagePath.toFile());
        assertNotNull(image);

        final AtomicLong totalTime = new AtomicLong(0);
        final AtomicLong scaleTime = new AtomicLong(0);
        final AtomicLong convertTime = new AtomicLong(0);
        final AtomicLong encodeTime = new AtomicLong(0);
        final String[] encodedResult = {null};
        CountDownLatch latch = new CountDownLatch(1);

        new Thread(() -> {
            long overallStart = System.nanoTime();

            try {
                long scaleStart = System.nanoTime();
                int maxW = 220;
                int maxH = 160;
                int width = image.getWidth(null);
                int height = image.getHeight(null);
                if (width > maxW) {
                    height = (int) Math.round(height * (maxW / (double) width));
                    width = maxW;
                }
                if (height > maxH) {
                    width = (int) Math.round(width * (maxH / (double) height));
                    height = maxH;
                }
                Image scaled = image.getScaledInstance(width, height, Image.SCALE_SMOOTH);
                scaleTime.set(System.nanoTime() - scaleStart);
                System.out.println("[PERF] Step 1 - Scale to " + width + "x" + height + ": " + (scaleTime.get() / 1_000_000) + "ms");

                long convertStart = System.nanoTime();
                BufferedImage bi = new BufferedImage(scaled.getWidth(null), scaled.getHeight(null), BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = bi.createGraphics();
                g.drawImage(scaled, 0, 0, null);
                g.dispose();
                convertTime.set(System.nanoTime() - convertStart);
                System.out.println("[PERF] Step 2 - Convert to BufferedImage: " + (convertTime.get() / 1_000_000) + "ms");

                long encodeStart = System.nanoTime();
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                javax.imageio.ImageIO.write(bi, "png", out);
                encodedResult[0] = Base64.getEncoder().encodeToString(out.toByteArray());
                encodeTime.set(System.nanoTime() - encodeStart);
                System.out.println("[PERF] Step 3 - PNG + Base64 encode: " + (encodeTime.get() / 1_000_000) + "ms, size=" + encodedResult[0].length() + " chars");

                totalTime.set(System.nanoTime() - overallStart);
                System.out.println("[PERF] TOTAL: " + (totalTime.get() / 1_000_000) + "ms");

            } catch (Exception e) {
                fail("Whiteboard image add failed: " + e.getMessage());
            } finally {
                latch.countDown();
            }
        }, "whiteboard-image-test").start();

        assertTrue(latch.await(120, TimeUnit.SECONDS), "Whiteboard test should complete within 120s");
        assertNotNull(encodedResult[0]);
    }
}
