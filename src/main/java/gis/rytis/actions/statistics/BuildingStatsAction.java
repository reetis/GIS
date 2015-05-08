package gis.rytis.actions.statistics;

import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import gis.rytis.SelectableLayer;
import gis.rytis.Utilities;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.geometry.jts.JTSFactoryFinder;
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
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.DoubleAdder;

import static java.lang.Math.abs;

public class BuildingStatsAction extends MapAction {

    public static final String TOOL_NAME = "Buildings";
    public static final String TOOL_TIP = "Buildings statistics";
    public static final String ICON_IMAGE = null;
    private static final int GRID_WIDTH = 10;
    private static final int GRID_HEIGHT = 10;

    private FilterFactory2 ff = CommonFactoryFinder.getFilterFactory2();
    private GeometryFactory gf = JTSFactoryFinder.getGeometryFactory();

    public BuildingStatsAction(MapPane mapPane) {
        super.init(mapPane, TOOL_NAME, TOOL_TIP, ICON_IMAGE);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        Optional<SelectableLayer> optRegionLayer = Utilities.findLayerByName(getMapPane(), "SAV_P");
        Optional<SelectableLayer> optBuildingLayer = Utilities.findLayerByName(getMapPane(), "PAS_P");
        Optional<SelectableLayer> optAreaLayer = Utilities.findLayerByName(getMapPane(), "PLO_P");

        if (!optRegionLayer.isPresent()) {
            JOptionPane.showMessageDialog(null, "Region layer (SAV_P) not found");
            return;
        }
        if (!optBuildingLayer.isPresent()) {
            JOptionPane.showMessageDialog(null, "Building layer (PAS_P) not found");
            return;
        }
        if (!optAreaLayer.isPresent()) {
            JOptionPane.showMessageDialog(null, "Area layer (PLO_P) not found");
            return;
        }

        SelectableLayer regionLayer = optRegionLayer.get();
        SelectableLayer buildingLayer = optBuildingLayer.get();
        SelectableLayer areaLayer = optAreaLayer.get();

        if (Utilities.getLayerType(regionLayer) != Utilities.LayerType.POLYGON) {
            JOptionPane.showMessageDialog(null, "Region layer (SAV_P) geometry is not polygons");
            return;
        }
        if (Utilities.getLayerType(buildingLayer) != Utilities.LayerType.POLYGON) {
            JOptionPane.showMessageDialog(null, "Building layer (PAS_P) geometry is not polygons");
            return;
        }
        if (Utilities.getLayerType(areaLayer) != Utilities.LayerType.POLYGON) {
            JOptionPane.showMessageDialog(null, "Area layer (PLO_P) geometry is not polygons");
            return;
        }

        CoordinateReferenceSystem crs = regionLayer.getSimpleFeatureSource().getSchema().getCoordinateReferenceSystem();

        try {
            List<CompletableFuture<CalculationResult>> resultsFutures = new ArrayList<>();
            SimpleFeatureCollection features = regionLayer.getSimpleFeatureSource().getFeatures();
            SimpleFeatureIterator featureIterator = features.features();
            while (featureIterator.hasNext()) {
                SimpleFeature feature = featureIterator.next();
                resultsFutures.add(CompletableFuture.supplyAsync(() -> calculate(feature, buildingLayer, areaLayer, crs)));
            }
            List<CalculationResult> results = new ArrayList<>();
            for (CompletableFuture<CalculationResult> future: resultsFutures) {
                results.add(future.get());
            }

            results.sort((x, y) -> x.getRegionName().compareToIgnoreCase(y.getRegionName()));

            for (CalculationResult result: results) {
                System.out.println("-- " + result.getRegionName() + " (" + result.getRegionArea() + " m^2): ");
                System.out.println("Pastatų plotas: " + result.getBuildingArea() + " m^2");
                System.out.println("Hidrografijos teritorijoje: " + result.getHydroArea() + " m^2 (" +
                        result.getHydroPercentage() + " %)");
                System.out.println("Medžių ir krūmų teritorijoje: " + result.getForestArea() + " m^2 (" +
                        result.getForestPercentage() + " %)");
                System.out.println("Ūžstatytoje teritorijoje: " + result.getBuiltArea() + " m^2 (" +
                        result.getBuiltPercentage() + " %)");
                System.out.println("Pramoninių sodų teritorijoje: " + result.getGardenArea() + " m^2 (" +
                        result.getGardenPercentage() + " %)");
            }

            JFrame frame = new JFrame("Buildings statistics");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

            //Create and set up the content pane.
            ResultWindow newContentPane = new ResultWindow(results);
            newContentPane.setOpaque(true); //content panes must be opaque
            frame.setContentPane(newContentPane);

            //Display the window.
            frame.pack();
            frame.setVisible(true);
        } catch (IOException |InterruptedException|ExecutionException e1) {
            e1.printStackTrace();
        }


    }

    public CalculationResult calculate(SimpleFeature region, FeatureLayer buildingLayer, FeatureLayer areaLayer, CoordinateReferenceSystem crs) {
        String regionName = (String) region.getAttribute("SAV");
        System.out.println("Starting " + regionName);

        String buildingGeomLocalName = buildingLayer.getSimpleFeatureSource().getSchema().getGeometryDescriptor().getLocalName();
        String areaGeomLocalName = areaLayer.getSimpleFeatureSource().getSchema().getGeometryDescriptor().getLocalName();
        Geometry regionGeometry = (Geometry) region.getDefaultGeometry();

        DoubleAdder buildingArea = new DoubleAdder();
        DoubleAdder hydroArea = new DoubleAdder();
        DoubleAdder forestsArea = new DoubleAdder();
        DoubleAdder builtArea = new DoubleAdder();
        DoubleAdder gardensArea = new DoubleAdder();

        Envelope envInt = regionGeometry.getEnvelopeInternal();
        double cellWidth = abs(envInt.getMaxX() - envInt.getMinX()) / GRID_WIDTH;
        double cellHeight = abs(envInt.getMaxY() - envInt.getMinY()) / GRID_HEIGHT;
        SimpleFeatureSource squareGrid = Oblongs.createGrid(
                new ReferencedEnvelope(envInt.getMinX(), envInt.getMaxX() + 10, envInt.getMinY(), envInt.getMaxY() + 10, crs),
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


                /// Calculating buildings area
                Filter filter1 = ff.intersects(ff.property(buildingGeomLocalName), ff.literal(squareRegion));
                List<Geometry> buildings = new ArrayList<>();
                try {
                    buildingLayer.getSimpleFeatureSource().getFeatures(filter1).accepts(building -> {
                        Geometry buildGeometry = (Geometry) ((SimpleFeature) building).getDefaultGeometry();
                        Geometry intersected = squareRegion.intersection(buildGeometry);
                        if (!intersected.isEmpty()) {
                            buildings.add(intersected);
                        }
                    }, null);
                } catch (IOException e) {
                    System.out.println("Error: " + e);
                }

                Geometry buildingsUnited = gf.buildGeometry(buildings).union();
                buildingArea.add(buildingsUnited.getArea());

                /// Calculate buildings in specific area
                Filter filter2 = ff.intersects(ff.property(areaGeomLocalName), ff.literal(buildingsUnited));
                try {
                    areaLayer.getSimpleFeatureSource().getFeatures(filter2).accepts(area -> {
                        Geometry areaGeometry = (Geometry) ((SimpleFeature) area).getDefaultGeometry();
                        Geometry intersected = areaGeometry.intersection(buildingsUnited);
                        double intersectedArea = intersected.getArea();
                        switch (Utilities.getAreaType((SimpleFeature) area)) {
                            case GARDEN:
                                gardensArea.add(intersectedArea);
                                break;
                            case HYDRO:
                                hydroArea.add(intersectedArea);
                                break;
                            case BUILDING:
                                builtArea.add(intersectedArea);
                                break;
                            case FOREST:
                                forestsArea.add(intersectedArea);
                                break;
                            default:
                                System.out.println("UNKNOWN type area");
                        }
                    }, null);
                } catch (IOException e) {
                    System.out.println("Error: " + e);
                }

            }, null);
        } catch (IOException e) {
            e.printStackTrace();
        }

        System.out.println("Finished " + regionName);
        return new CalculationResult(regionName, regionGeometry.getArea(), buildingArea.doubleValue(),
                hydroArea.doubleValue(), forestsArea.doubleValue(), builtArea.doubleValue(), gardensArea.doubleValue());
    }

    private class CalculationResult{
        private String regionName;
        private double buildingArea;
        private double regionArea;
        private double hydroArea;
        private double forestArea;
        private double builtArea;
        private double gardenArea;

        public CalculationResult(String regionName, double buildingArea, double regionArea, double hydroArea, double forestArea, double builtArea, double gardenArea) {
            this.regionName = regionName;
            this.buildingArea = buildingArea;
            this.regionArea = regionArea;
            this.hydroArea = hydroArea;
            this.forestArea = forestArea;
            this.builtArea = builtArea;
            this.gardenArea = gardenArea;
        }

        public String getRegionName() {
            return regionName;
        }

        public void setRegionName(String regionName) {
            this.regionName = regionName;
        }

        public double getBuildingArea() {
            return buildingArea;
        }

        public void setBuildingArea(double buildingArea) {
            this.buildingArea = buildingArea;
        }

        public double getRegionArea() {
            return regionArea;
        }

        public void setRegionArea(double regionArea) {
            this.regionArea = regionArea;
        }

        public double getHydroArea() {
            return hydroArea;
        }

        public void setHydroArea(double hydroArea) {
            this.hydroArea = hydroArea;
        }

        public double getForestArea() {
            return forestArea;
        }

        public void setForestArea(double forestArea) {
            this.forestArea = forestArea;
        }

        public double getBuiltArea() {
            return builtArea;
        }

        public void setBuiltArea(double builtArea) {
            this.builtArea = builtArea;
        }

        public double getGardenArea() {
            return gardenArea;
        }

        public void setGardenArea(double gardenArea) {
            this.gardenArea = gardenArea;
        }

        public double getHydroPercentage() {
            return hydroArea/buildingArea*100;
        }

        public double getForestPercentage() {
            return forestArea/buildingArea*100;
        }

        public double getBuiltPercentage() {
            return builtArea/buildingArea*100;
        }

        public double getGardenPercentage() {
            return gardenArea/buildingArea*100;
        }
    }

    private class ResultWindow extends JPanel {
        public ResultWindow(List<CalculationResult> results) {
            super(new GridLayout(1, 0));
            String[] columnNames = {"Region Name", "Building Area (m^2)", "In Hydro (m^2)", "Hydro Percentage",
                    "In Forest (m^2)", "Forest Percentage",
                    "In Built (m^2)", "Built Percentage",
                    "In Garden (m^2)", "Garden Percentage"};
            String[][] data = new String[results.size()][10];
            for (int i = 0; i < results.size(); ++i) {
                data[i][0] = results.get(i).getRegionName();
                data[i][1] = String.format("%f", results.get(i).getBuildingArea());
                data[i][2] = String.format("%f", results.get(i).getHydroArea());
                data[i][3] = String.format("%.8f %%", results.get(i).getHydroPercentage());
                data[i][4] = String.format("%f", results.get(i).getForestArea());
                data[i][5] = String.format("%.8f %%", results.get(i).getForestPercentage());
                data[i][6] = String.format("%f", results.get(i).getBuiltArea());
                data[i][7] = String.format("%.8f %%", results.get(i).getBuiltPercentage());
                data[i][8] = String.format("%f", results.get(i).getGardenArea());
                data[i][9] = String.format("%.8f %%", results.get(i).getGardenPercentage());
            }
            final JTable table = new JTable(data, columnNames);
            table.setPreferredScrollableViewportSize(new Dimension(500, 70));
            table.setFillsViewportHeight(true);
            //Create the scroll pane and add the table to it.
            JScrollPane scrollPane = new JScrollPane(table);

            //Add the scroll pane to this panel.
            add(scrollPane);
        }
    }
}
