/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package TemplateMatching;

import Math.Correlation.Correlation;
import ij.ImageStack;
import ij.process.ImageProcessor;

/**
 *
 * @author David Barry <david.barry at crick dot ac dot uk>
 */
public class PlateFitter extends TemplateMatcher {

    private Plate plateTemplate;
    private ImageStack image;

    public PlateFitter(ImageStack image, int rows, int cols, double wellRad, double xBuff, double yBuff, double interWellSpacing, double shrinkFactor) {
        super();
        this.image = image;
        this.plateTemplate = new Plate(rows, cols, wellRad, xBuff, yBuff, interWellSpacing, shrinkFactor);
    }

    public void doFit() {
        ImageProcessor template = plateTemplate.drawPlate(0.0);
                TemplateMatcher tm = new TemplateMatcher(image, template);
                tm.run();
    }

    public Plate getPlateTemplate() {
        return plateTemplate;
    }

}
