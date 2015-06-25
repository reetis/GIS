package gis.rytis.actions;

import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.Polygon;
import gis.rytis.SelectableLayer;
import gis.rytis.Utilities;
import gis.rytis.tools.SearchSelectTool;
import org.geotools.data.DataUtilities;
import org.geotools.data.collection.ListFeatureCollection;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.feature.SchemaException;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.geometry.jts.JTSFactoryFinder;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.map.FeatureLayer;
import org.geotools.swing.MapPane;
import org.geotools.swing.action.MapAction;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.filter.Filter;
import org.opengis.filter.FilterFactory2;
import org.opengis.filter.expression.PropertyName;
import org.opengis.filter.sort.SortBy;
import org.opengis.filter.sort.SortOrder;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

public class SearchAction extends MapAction {

    public static final String TOOL_NAME = "Park Area Search";
    public static final String TOOL_TIP = "Search area for adventure park";
    public static final String ICON_IMAGE = null;

    private FilterFactory2 ff = CommonFactoryFinder.getFilterFactory2();
    private GeometryFactory gf = JTSFactoryFinder.getGeometryFactory();
    private JFrame frame;
    private JPanel rootPanel;
    private JTextField minAreaSizeMTextField;
    private JTextField distanceToRoadMTextField;
    private JTextField minRiverWidthMTextField;
    private JTextField heightDifferenceMTextField;
    private JButton startSearchButton;
    private JButton selectButton;
    private JLabel lowCoordLabel;
    private JLabel upCoordLabel;

    private ReferencedEnvelope bbox;

    public SearchAction(MapPane mapPane) {
        super.init(mapPane, TOOL_NAME, TOOL_TIP, ICON_IMAGE);
        frame = new JFrame("Park search");
        frame.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);

        frame.setContentPane(rootPanel);

        frame.pack();

        selectButton.addActionListener(e -> mapPane.setCursorTool(new SearchSelectTool(this)));
        startSearchButton.addActionListener(e -> {
            try {
                startSearch();
            } catch (SchemaException | IOException e1) {
                JOptionPane.showMessageDialog(null, "Calculation failed: " + e1);
            }
        });
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        frame.setVisible(true);
    }

    public void setBBox(ReferencedEnvelope bbox) {
        this.bbox = bbox;
        lowCoordLabel.setText(String.format("[%f; %f]", bbox.getMinX(), bbox.getMinY()));
        upCoordLabel.setText(String.format("[%f; %f]", bbox.getMaxX(), bbox.getMaxY()));
    }

    public void startSearch() throws SchemaException, IOException{
        // --------------
        // Validate and get inputs
        // --------------

        double minAreaSize;
        double distanceToRoad;
        double minRiverWidth;
        double heightDiff;

        try {
            minAreaSize = Double.valueOf(minAreaSizeMTextField.getText());
            distanceToRoad = Double.valueOf(distanceToRoadMTextField.getText());
            minRiverWidth = Double.valueOf(minRiverWidthMTextField.getText());
            heightDiff = Double.valueOf(heightDifferenceMTextField.getText());
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(null, "Wrong input");
            return;
        }

        Polygon bboxPoly;

        if (bbox != null) {
            bboxPoly = Utilities.createPolygon(bbox);
        } else {
            JOptionPane.showMessageDialog(null, "Area not selected");
            return;
        }


        // --------------
        // Validate and get layers
        // --------------
        Optional<SelectableLayer> optRoadLayer = Utilities.findLayerByName(getMapPane(), "KEL_L");
        Optional<SelectableLayer> optHidroLayer = Utilities.findLayerByName(getMapPane(), "HID_L");
        Optional<SelectableLayer> optAreaLayer = Utilities.findLayerByName(getMapPane(), "PLO_P");
        Optional<SelectableLayer> optHeightLayer = Utilities.findLayerByName(getMapPane(), "R100PS");

        if (!optRoadLayer.isPresent()) {
            JOptionPane.showMessageDialog(null, "Roads layer (KEL_L) not found");
            return;
        }
        if (!optHidroLayer.isPresent()) {
            JOptionPane.showMessageDialog(null, "Hidro layer (HID_L) not found");
            return;
        }
        if (!optAreaLayer.isPresent()) {
            JOptionPane.showMessageDialog(null, "Areas layer (PLO_P) not found");
            return;
        }
        if (!optHeightLayer.isPresent()) {
            JOptionPane.showMessageDialog(null, "Heights layer (R100PS) not found");
            return;
        }

        SelectableLayer roadLayer = optRoadLayer.get();
        SelectableLayer hidroLayer = optHidroLayer.get();
        SelectableLayer areaLayer = optAreaLayer.get();
        SelectableLayer heightLayer = optHeightLayer.get();

        if (Utilities.getLayerType(roadLayer) != Utilities.LayerType.LINE) {
            JOptionPane.showMessageDialog(null, "Road layer (KEL_L) geometry is not lines");
            return;
        }
        if (Utilities.getLayerType(hidroLayer) != Utilities.LayerType.LINE) {
            JOptionPane.showMessageDialog(null, "Hidro layer (HID_L) geometry is not lines");
            return;
        }
        if (Utilities.getLayerType(areaLayer) != Utilities.LayerType.POLYGON) {
            JOptionPane.showMessageDialog(null, "Area layer (PLO_P) geometry is not polygons");
            return;
        }
        if (Utilities.getLayerType(heightLayer) != Utilities.LayerType.POLYGON) {
            JOptionPane.showMessageDialog(null, "Height layer (R100PS) geometry is not polygons");
            return;
        }

        CoordinateReferenceSystem crs = roadLayer.getSimpleFeatureSource().getSchema().getCoordinateReferenceSystem();

        // -------------------------------------------------------------------------------------------------------------
        // Main calculation
        // -------------------------------------------------------------------------------------------------------------

        // --------------
        // Road buffering
        // --------------
        System.out.println("Starting road buffering");
        FeatureLayer roadBuffered = makeRoadBuffer(roadLayer, bboxPoly, distanceToRoad);
        getMapPane().getMapContent().addLayer(roadBuffered);

        // --------------
        // Forest intersection
        // --------------
        System.out.println("Starting forest intersection");
        FeatureLayer forestIntersection = makeForestInters(areaLayer, roadBuffered, bboxPoly, minAreaSize);
        getMapPane().getMapContent().addLayer(forestIntersection);
        System.out.println(forestIntersection.getSimpleFeatureSource().getFeatures().size());

        // --------------
        // Filter by rivers
        // --------------
        System.out.println("Starting river filtering");
        FeatureLayer filteredByRivers = makeFilterRiver(forestIntersection, hidroLayer, bboxPoly, minRiverWidth);
        getMapPane().getMapContent().addLayer(filteredByRivers);
        System.out.println(filteredByRivers.getSimpleFeatureSource().getFeatures().size());

        // --------------
        // Height difference calculation
        // --------------
        System.out.println("Starting height diff calculation");
        FeatureLayer filteredByHeight = makeFilterHeight(filteredByRivers, heightLayer, heightDiff);
        getMapPane().getMapContent().addLayer(filteredByHeight);
        System.out.println(filteredByHeight.getSimpleFeatureSource().getFeatures().size());

        // --------------
        // Peaks searching
        // --------------
        System.out.println("Starting peaks searching");
        FeatureLayer peaks = makePeaks(heightLayer, bboxPoly);
        getMapPane().getMapContent().addLayer(peaks);

        // --------------
        // Peaks calculation
        // --------------
        System.out.println("Starting peaks calculation");
        FeatureLayer forestsPeaks = makeForestPeaks(filteredByHeight, peaks);
        getMapPane().getMapContent().addLayer(forestsPeaks);

        // --------------
        // Print attribute data
        // --------------
        System.out.println("Results:");
        forestsPeaks.getSimpleFeatureSource().getFeatures().accepts(x -> {
            SimpleFeature sf = (SimpleFeature) x;
            System.out.println("Height Diff: " + (double) sf.getAttribute("HeightDiff") + "; Peaks: " + (int) sf.getAttribute("PeaksQty"));
        }, null);
    }

    private FeatureLayer makeForestPeaks(FeatureLayer filteredByHeight, FeatureLayer peaks) throws SchemaException, IOException {
        String geomName = peaks.getSimpleFeatureSource().getSchema().getGeometryDescriptor().getLocalName();

        SimpleFeatureType forestType = DataUtilities.createType("forestHeight", "edge:MultiPolygon,HeightDiff:Double,PeaksQty:Integer");
        ListFeatureCollection forestCollection = new ListFeatureCollection(forestType);

        filteredByHeight.getSimpleFeatureSource().getFeatures().accepts(forest -> {
            SimpleFeature sf = (SimpleFeature) forest;
            Geometry geometry = (Geometry) sf.getDefaultGeometry();

            Filter filter = ff.intersects(ff.property(geomName), ff.literal(geometry));

            try {
                SimpleFeatureBuilder sfb = new SimpleFeatureBuilder(forestType);
                sfb.set("edge", geometry);
                sfb.set("HeightDiff", sf.getAttribute("HeightDiff"));
                sfb.set("PeaksQty", peaks.getSimpleFeatureSource().getFeatures(filter).size());
                forestCollection.add(sfb.buildFeature(sf.getID()));
            } catch (IOException e) {
                e.printStackTrace();
            }

        }, null);

        SimpleFeatureCollection finalFeatures = forestCollection.sort(new SortBy() {
            @Override
            public PropertyName getPropertyName() {
                return ff.property("PeaksQty");
            }

            @Override
            public SortOrder getSortOrder() {
                return SortOrder.DESCENDING;
            }
        });
        FeatureLayer layer = Utilities.createLayer(finalFeatures, "Forests with peaks");
        return layer;
    }

    private FeatureLayer makeFilterHeight(FeatureLayer filteredByRivers, FeatureLayer heightLayer, double heightDiff) throws SchemaException, IOException {
        String geomName = heightLayer.getSimpleFeatureSource().getSchema().getGeometryDescriptor().getLocalName();
        String geomName2 = filteredByRivers.getSimpleFeatureSource().getSchema().getGeometryDescriptor().getLocalName();

        SimpleFeatureType forestType = DataUtilities.createType("forestHeight", "edge:MultiPolygon,HeightDiff:double");
        ListFeatureCollection forestCollection = new ListFeatureCollection(forestType);

        filteredByRivers.getSimpleFeatureSource().getFeatures().accepts(forest -> {
            SimpleFeature sf = (SimpleFeature) forest;
            Geometry geometry = (Geometry) sf.getDefaultGeometry();

            CustomDouble maxHeightL = new CustomDouble(-99999);
            CustomDouble minHeightL = new CustomDouble(99999);

            Filter filter = ff.intersects(ff.property(geomName), ff.literal(geometry));

            try {
                heightLayer.getSimpleFeatureSource().getFeatures(filter).accepts(height -> {
                    SimpleFeature sf2 = (SimpleFeature) height;
                    double h = (Double) sf2.getAttribute("Aukstis");
                    if (h > maxHeightL.getNumber()) {
                        maxHeightL.setNumber(h);
                    }
                    if (h < minHeightL.getNumber()) {
                        minHeightL.setNumber(h);
                    }
                }, null);
            } catch (IOException e) {
                e.printStackTrace();
            }

            double maxHeight = maxHeightL.getNumber();
            double minHeight = minHeightL.getNumber();

            if (maxHeight-minHeight > heightDiff) {
                SimpleFeatureBuilder sfb = new SimpleFeatureBuilder(forestType);
                sfb.set("edge", geometry);
                sfb.set("HeightDiff", maxHeight-minHeight);
                forestCollection.add(sfb.buildFeature(sf.getID()));
            }

        }, null);

        FeatureLayer layer = Utilities.createLayer(forestCollection, "Forests with height diff");
        return layer;
    }

    private FeatureLayer makeFilterRiver(FeatureLayer forestIntersection, FeatureLayer hidroLayer, Polygon bboxPoly, double minRiverWidth) throws SchemaException, IOException {
        String geomName = hidroLayer.getSimpleFeatureSource().getSchema().getGeometryDescriptor().getLocalName();
        String geomName2 = forestIntersection.getSimpleFeatureSource().getSchema().getGeometryDescriptor().getLocalName();

        SimpleFeatureType forestType = DataUtilities.createType("forest", "edge:MultiPolygon");
        ListFeatureCollection forestCollection = new ListFeatureCollection(forestType);

        forestIntersection.getSimpleFeatureSource().getFeatures().accepts(forest -> {
            SimpleFeature sf = (SimpleFeature) forest;
            Geometry geometry = (Geometry) sf.getDefaultGeometry();

            Filter filter = ff.and(
                    ff.intersects(ff.property(geomName), ff.literal(geometry)),
                    ff.greater(ff.property("PLOTIS"), ff.literal(minRiverWidth))
            );

            try {
                if (hidroLayer.getSimpleFeatureSource().getFeatures(filter).size() > 0) {
                    SimpleFeatureBuilder sfb = new SimpleFeatureBuilder(forestType);
                    sfb.init(sf);
                    forestCollection.add(sfb.buildFeature(sf.getID()));
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

        }, null);


        FeatureLayer layer = Utilities.createLayer(forestCollection, "Forests with river");
        return layer;
    }

    private FeatureLayer makePeaks(FeatureLayer heightLayer, Polygon bboxPoly) throws SchemaException, IOException {
        String geomName = heightLayer.getSimpleFeatureSource().getSchema().getGeometryDescriptor().getLocalName();
        Filter filter = ff.intersects(ff.property(geomName), ff.literal(bboxPoly));

        SimpleFeatureType peaksType = DataUtilities.createType("peaks", "edge:MultiPolygon");
        ListFeatureCollection peaksCollection = new ListFeatureCollection(peaksType);

        heightLayer.getSimpleFeatureSource().getFeatures(filter).accepts(height -> {
            SimpleFeature sf = (SimpleFeature) height;
            Geometry geometry = (Geometry) sf.getDefaultGeometry();

            double peakHeight = (Double) sf.getAttribute("Aukstis");
            AtomicBoolean isPeak = new AtomicBoolean(true);

            Filter filter2 = ff.touches(ff.property(geomName), ff.literal(geometry));

            try {
                heightLayer.getSimpleFeatureSource().getFeatures(filter2).accepts(neighbour -> {
                    SimpleFeature sf2 = (SimpleFeature) neighbour;
                    double neighbourHeight = (Double) sf2.getAttribute("Aukstis");
                    if (peakHeight < neighbourHeight) {
                        isPeak.set(false);
                    }
                }, null);
            } catch (IOException e) {
                e.printStackTrace();
            }

            if (isPeak.get()) {
                SimpleFeatureBuilder sfb = new SimpleFeatureBuilder(peaksType);
                sfb.add(geometry);
                peaksCollection.add(sfb.buildFeature(sf.getID()));
            }
        }, null);

        FeatureLayer layer = Utilities.createLayer(peaksCollection, "Peaks");
        return layer;
    }

    private FeatureLayer makeForestInters(FeatureLayer areaLayer, FeatureLayer roadBuffered, Polygon bboxPoly, double minAreaSize) throws SchemaException, IOException{
        String geomName = areaLayer.getSimpleFeatureSource().getSchema().getGeometryDescriptor().getLocalName();
        String geomName2 = roadBuffered.getSimpleFeatureSource().getSchema().getGeometryDescriptor().getLocalName();
        Filter filter = ff.and(
                ff.intersects(ff.property(geomName), ff.literal(bboxPoly)),
                ff.equals(ff.property("GKODAS"), ff.literal("ms0"))
        );

        SimpleFeatureType forestType = DataUtilities.createType("forest", "edge:MultiPolygon");
        ListFeatureCollection forestCollection = new ListFeatureCollection(forestType);

        areaLayer.getSimpleFeatureSource().getFeatures(filter).accepts(area -> {
            SimpleFeature sf = (SimpleFeature) area;
            Geometry geometry = (Geometry) sf.getDefaultGeometry();

            try {
                roadBuffered.getFeatureSource().getFeatures().accepts(buffer -> {
                    SimpleFeature sf2 = (SimpleFeature) buffer;
                    Geometry geometry2 = (Geometry) sf2.getDefaultGeometry();
                    Geometry newForest = geometry.intersection(geometry2);
                    if (newForest.getArea() >= minAreaSize) {
                        SimpleFeatureBuilder sfb = new SimpleFeatureBuilder(forestType);
                        sfb.add(newForest);
                        forestCollection.add(sfb.buildFeature(null));
                    }
                }, null);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }, null);

        FeatureLayer layer = Utilities.createLayer(forestCollection, "Forest intersection");
        return layer;
    }

    private FeatureLayer makeRoadBuffer(FeatureLayer roadLayer, Polygon bboxPoly, double roadBufferLength) throws SchemaException, IOException{
        String geomName = roadLayer.getSimpleFeatureSource().getSchema().getGeometryDescriptor().getLocalName();
        Filter filter = ff.intersects(ff.property(geomName), ff.literal(bboxPoly));

        SimpleFeatureType roadBufferType = DataUtilities.createType("road_buffer", "edge:MultiPolygon");
        ListFeatureCollection roadBufferCollection = new ListFeatureCollection(roadBufferType);
        List<Geometry> matchingGeoms = new ArrayList<>();

        roadLayer.getSimpleFeatureSource().getFeatures(filter).accepts(road -> {
            SimpleFeature sf = (SimpleFeature) road;
            Geometry geometry = (Geometry) sf.getDefaultGeometry();
            matchingGeoms.add(geometry.intersection(bboxPoly).buffer(roadBufferLength));
        }, null);

        SimpleFeatureBuilder sfb = new SimpleFeatureBuilder(roadBufferType);
        sfb.add(gf.buildGeometry(matchingGeoms).union());
        roadBufferCollection.add(sfb.buildFeature("RB"));

        FeatureLayer layer = Utilities.createLayer(roadBufferCollection, "Road buffer");
        return layer;
    }

    class CustomDouble {
        double number;

        public CustomDouble(double number) {
            this.number = number;
        }

        public double getNumber() {
            return number;
        }

        public void setNumber(double number) {
            this.number = number;
        }
    }
}
