/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package TemplateMatching;

import IAClasses.Utils;
import Math.Correlation.Correlation;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.gui.OvalRoi;
import ij.gui.Overlay;
import ij.io.LogStream;
import ij.macro.ExtensionDescriptor;
import ij.macro.MacroExtension;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 *
 * @author David Barry <david.barry at crick dot ac dot uk>
 */
public class TemplateMatcher implements MacroExtension {

    private static final String title = "Template Matcher";
    private ImageStack image;
    private ImageProcessor template;
    private double threshold = 0.5;
    private final String[] extensionFunctionNames = new String[]{"runTemplateMatcher"};

    public TemplateMatcher() {
        this(null, null);
    }

    public TemplateMatcher(ImageStack image, ImageProcessor template) {
        this.image = image;
        this.template = template;
    }

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        try {
            (new TemplateMatcher(IJ.openImage().getImageStack(), IJ.openImage().getProcessor())).run();
        } catch (Exception e) {
            System.out.println("Error writing output file.");
        }
        System.exit(0);
    }

    public void run() {
        LogStream.redirectSystemOut("");
        int width = image.getWidth();
        int height = image.getHeight();
        int depth = image.getSize();
        int tw = template.getWidth();
        int th = template.getHeight();
        int x0 = tw / 2;
        int y0 = th / 2;
        System.out.println(String.format("Running %s...", title));
        int nProcessors = Runtime.getRuntime().availableProcessors();
        System.out.println(String.format("%d processsors available", nProcessors));
        try {
            Overlay overlay = new Overlay();
            for (int z = 1; z <= depth; z++) {
                System.out.println(String.format("Processing slice %d of %d", z, depth));
                ExecutorService exec = Executors.newFixedThreadPool(nProcessors);
                ImageProcessor slice = image.getProcessor(z);
                FloatProcessor output = new FloatProcessor(width, height);
                output.setValue(0.0);
                output.fill();
                for (int y = y0; y < height - y0 - 1; y++) {
                    for (int x = x0; x < width - x0 - 1; x++) {
                        Rectangle roi = new Rectangle(x - x0, y - y0, tw, th);
                        slice.setRoi(roi);
                        ImageProcessor croppedImage = slice.crop();
                        exec.submit(new Correlation(croppedImage, template, new ImageProcessor[]{output, null}, new int[]{x, y}, Correlation.PEARSONS));
                    }
                }
                exec.shutdown();
                exec.awaitTermination(12, TimeUnit.HOURS);
                findCorrelationMaxima(overlay, output, z);
                IJ.saveAs(new ImagePlus("", output), "TIF", "D:\\OneDrive - The Francis Crick Institute\\Working Data\\Sahai\\Karin\\corr");
            }
            ImagePlus finalResult = new ImagePlus("", image);
            finalResult.setOverlay(overlay);
            finalResult.show();
        } catch (Exception e) {
            System.out.println("Error writing output file.");
        }
        LogStream.revertSystem();
    }

    boolean showDialog() {
        GenericDialog gd = new GenericDialog(title);
        gd.addNumericField("Specify correlation threshold:", threshold, 3);
        gd.showDialog();
        if (!gd.wasOKed()) {
            return false;
        }
        threshold = gd.getNextNumber();
        return true;
    }

    void findCorrelationMaxima(Overlay overlay, ImageProcessor maps, int z) {
        ArrayList<int[]> maxima = Utils.findLocalMaxima(1, maps, threshold, false, true);
        int r = template.getWidth() / 2;
        for (int[] point : maxima) {
            OvalRoi roi = new OvalRoi(point[0] - r, point[1] - r, template.getWidth(), template.getWidth());
            roi.setPosition(z);
            overlay.addElement(roi);
        }
    }

    public ExtensionDescriptor[] getExtensionFunctions() {
        return new ExtensionDescriptor[]{
            new ExtensionDescriptor(extensionFunctionNames[0], new int[]{
                MacroExtension.ARG_STRING, MacroExtension.ARG_STRING, MacroExtension.ARG_NUMBER
            }, this)
        };
    }

    public String handleExtension(String name, Object[] args) {
        if (name.contentEquals(extensionFunctionNames[0])) {
            if (!(args[0] instanceof String && args[1] instanceof String && args[2] instanceof Double)) {
                System.out.print(String.format("Error: arguments passed to %s are not valid.", extensionFunctionNames[0]));
                return "";
            }
            this.image = WindowManager.getImage((String) args[0]).getImageStack();
            this.template = WindowManager.getImage((String) args[1]).getProcessor();
            this.threshold = (Double) args[2];
            run();
        }
        return null;
    }

}
