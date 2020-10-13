/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.calm.templatematching;

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
import net.calm.iaclasslibrary.IAClasses.Utils;
import net.calm.iaclasslibrary.Math.Correlation.Correlation;

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
        int eHeight = (int) Math.round(height * 1.1);
        int eWidth = (int) Math.round(width * 1.1);
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
                ImageProcessor expandedImage = slice.createProcessor(eWidth, eHeight);
                expandedImage.noise(25);
                expandedImage.insert(slice, (eWidth - width) / 2, (eHeight - height) / 2);
                FloatProcessor output = new FloatProcessor(eWidth, eHeight);
                output.setValue(0.0);
                output.fill();
                for (int y = 0; y < eHeight; y++) {
                    for (int x = 0; x < eWidth; x++) {
                        Rectangle roi = new Rectangle(x - x0, y - y0, tw, th);
                        expandedImage.setRoi(roi);
                        exec.submit(new Correlation(expandedImage.crop(), template, new ImageProcessor[]{output, null}, new int[]{x, y}, Correlation.PEARSONS));
                    }
                }
                exec.shutdown();
                exec.awaitTermination(12, TimeUnit.HOURS);
                findCorrelationMaxima(overlay, output, z);
                IJ.saveAs(new ImagePlus("", expandedImage), "TIF", "D:/debugging/eImage.tiff");
                IJ.saveAs(new ImagePlus("", output), "TIF", "D:/debugging/corr.tiff");
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
