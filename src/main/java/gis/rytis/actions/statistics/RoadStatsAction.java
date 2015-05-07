package gis.rytis.actions.statistics;

import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.Geometry;
import gis.rytis.SelectableLayer;
import gis.rytis.Utilities;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.grid.DefaultGridFeatureBuilder;
import org.geotools.grid.oblong.Oblongs;
import org.geotools.map.FeatureLayer;
import org.geotools.swing.MapPane;
import org.geotools.swing.action.MapAction;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.filter.Filter;
import org.opengis.filter.FilterFactory2;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.DoubleAdder;

import static java.lang.Math.abs;

public class RoadStatsAction extends MapAction {

    public static final String TOOL_NAME = "Roads";
    public static final String TOOL_TIP = "Roads statistics";
    public static final String ICON_IMAGE = null;
    private static final int GRID_WIDTH = 4;
    private static final int GRID_HEIGHT = 4;

    private FilterFactory2 ff = CommonFactoryFinder.getFilterFactory2();

    public RoadStatsAction(MapPane mapPane) {
        super.init(mapPane, TOOL_NAME, TOOL_TIP, ICON_IMAGE);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        Optional<SelectableLayer> optRegionLayer = Utilities.findLayerByName(getMapPane(), "SAV_P");
        Optional<SelectableLayer> optRoadLayer = Utilities.findLayerByName(getMapPane(), "KEL_L");

        if (!optRegionLayer.isPresent()) {
            JOptionPane.showMessageDialog(null, "Region layer (SAV_P) not found");
            return;
        }
        if (!optRoadLayer.isPresent()) {
            JOptionPane.showMessageDialog(null, "Roads layer (KEL_L) not found");
            return;
        }

        SelectableLayer regionLayer = optRegionLayer.get();
        SelectableLayer roadLayer = optRoadLayer.get();

        if (Utilities.getLayerType(regionLayer) != Utilities.LayerType.POLYGON) {
            JOptionPane.showMessageDialog(null, "Region layer (SAV_P) geometry is not polygons");
            return;
        }
        if (Utilities.getLayerType(roadLayer) != Utilities.LayerType.LINE) {
            JOptionPane.showMessageDialog(null, "Region layer (KEL_L) geometry is not lines");
            return;
        }

        CoordinateReferenceSystem crs = regionLayer.getSimpleFeatureSource().getSchema().getCoordinateReferenceSystem();

        try {
            List<CompletableFuture<CalculationResult>> resultsFutures = new ArrayList<>();
            SimpleFeatureCollection features = regionLayer.getSimpleFeatureSource().getFeatures();
            SimpleFeatureIterator featureIterator = features.features();
            while (featureIterator.hasNext()) {
                SimpleFeature feature = featureIterator.next();
                resultsFutures.add(CompletableFuture.supplyAsync(() -> calculate(feature, roadLayer, crs)));
            }
            List<CalculationResult> results = new ArrayList<>();
            for (CompletableFuture<CalculationResult> future: resultsFutures) {
                results.add(future.get());
            }

            results.sort((x, y) -> x.getRegionName().compareToIgnoreCase(y.getRegionName()));

            //TODO: Pakeisti į normalų rezultatų atvaizdavimą
            for (CalculationResult result: results) {
                System.out.println(result.getRegionName() + " -> Plotas: " + result.getRegionArea() +
                        " m^2; Kelių ilgis: " + result.getRoadsLength() +
                        " m; Kelių tankis: " + (result.getRoadsDensity() * 1000000) + "m/km^2");
            }
        } catch (IOException|InterruptedException|ExecutionException e1) {
            e1.printStackTrace();
        }


    }

    public CalculationResult calculate(SimpleFeature region, FeatureLayer roadLayer, CoordinateReferenceSystem crs) {
        String regionName = (String) region.getAttribute("SAV");
        System.out.println("Starting " + regionName);

        String roadsGeomLocalName = roadLayer.getSimpleFeatureSource().getSchema().getGeometryDescriptor().getLocalName();
        Geometry regionGeometry = (Geometry) region.getDefaultGeometry();

        DoubleAdder roadLength = new DoubleAdder();
        Envelope envInt = regionGeometry.getEnvelopeInternal();
        double cellWidth = abs(envInt.getMaxX() - envInt.getMinX()) / GRID_WIDTH;
        double cellHeight = abs(envInt.getMaxY() - envInt.getMinY()) / GRID_HEIGHT;
        SimpleFeatureSource squareGrid = Oblongs.createGrid(
                new ReferencedEnvelope(envInt.getMinX(), envInt.getMaxX()+10, envInt.getMinY(), envInt.getMaxY()+10, crs),
                cellWidth, cellHeight, new DefaultGridFeatureBuilder());

//        try {
//            System.out.println(regionName + ": " + squareGrid.getFeatures().size() + " kvadratai");
//        } catch (IOException e) {
//            e.printStackTrace();
//        }

        try {
            squareGrid.getFeatures().accepts(square -> {
                Geometry squareGeometry = (Geometry) (((SimpleFeature) square).getDefaultGeometry());
                Geometry squareRegion = regionGeometry.intersection(squareGeometry);

                Filter filter = ff.and(ff.intersects(ff.property(roadsGeomLocalName), ff.literal(squareRegion)),
                        ff.intersects(ff.property(roadsGeomLocalName), ff.literal(squareGeometry)));

                try {
                    roadLayer.getSimpleFeatureSource().getFeatures(filter).accepts(road -> {
                        Geometry roadGeometry = (Geometry) ((SimpleFeature) road).getDefaultGeometry();
                        Geometry intersected = squareRegion.intersection(roadGeometry);
                        double intersectedLength = intersected.getLength();
                        roadLength.add(intersectedLength);
                    }, null);
                } catch (IOException e) {
                    System.out.println("Error: " + e);
                }
            }, null);
        } catch (IOException e) {
            e.printStackTrace();
        }

        System.out.println("Finished " + regionName);
        return new CalculationResult(regionName, regionGeometry.getArea(), roadLength.doubleValue());
    }

    private class CalculationResult{
        private String regionName;
        private double regionArea;
        private double roadsLength;

        public CalculationResult(String regionName, double regionArea, double roadsLength) {
            this.regionName = regionName;
            this.regionArea = regionArea;
            this.roadsLength = roadsLength;
        }

        public double getRoadsDensity() {
            return roadsLength/regionArea;
        }

        public String getRegionName() {
            return regionName;
        }

        public void setRegionName(String regionName) {
            this.regionName = regionName;
        }

        public double getRegionArea() {
            return regionArea;
        }

        public void setRegionArea(double regionArea) {
            this.regionArea = regionArea;
        }

        public double getRoadsLength() {
            return roadsLength;
        }

        public void setRoadsLength(double roadsLength) {
            this.roadsLength = roadsLength;
        }
    }
}
