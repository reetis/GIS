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
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
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
            JOptionPane.showMessageDialog(null, "Region layer (SAV_P) geometry is not lines");
            return;
        }

        CoordinateReferenceSystem crs = regionLayer.getSimpleFeatureSource().getSchema().getCoordinateReferenceSystem();

        try {
            SimpleFeatureCollection features = regionLayer.getSimpleFeatureSource().getFeatures();
            SimpleFeatureIterator featureIterator = features.features();
            while (featureIterator.hasNext()) {
                SimpleFeature feature = featureIterator.next();
                CompletableFuture completableFuture = CompletableFuture.supplyAsync(() -> calculate(feature, roadLayer, crs));
//                calculate(feature, roadLayer);
            }
        } catch (IOException e1) {
            e1.printStackTrace();
        }
    }

    public boolean calculate(SimpleFeature region, FeatureLayer roadLayer, CoordinateReferenceSystem crs) {
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

        try {
            System.out.println(regionName + ": " + squareGrid.getFeatures().size() + " kvadratai");
        } catch (IOException e) {
            e.printStackTrace();
        }

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

        System.out.println(regionName + ": " + roadLength);
        return true;
    }
}
