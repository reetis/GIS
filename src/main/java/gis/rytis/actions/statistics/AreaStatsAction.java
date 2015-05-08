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

public class AreaStatsAction extends MapAction {

    public static final String TOOL_NAME = "Areas";
    public static final String TOOL_TIP = "Areas statistics";
    public static final String ICON_IMAGE = null;
    private static final int GRID_WIDTH = 4;
    private static final int GRID_HEIGHT = 4;

    private FilterFactory2 ff = CommonFactoryFinder.getFilterFactory2();

    public AreaStatsAction(MapPane mapPane) {
        super.init(mapPane, TOOL_NAME, TOOL_TIP, ICON_IMAGE);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        Optional<SelectableLayer> optRegionLayer = Utilities.findLayerByName(getMapPane(), "SAV_P");
        Optional<SelectableLayer> optAreaLayer = Utilities.findLayerByName(getMapPane(), "PLO_P");

        if (!optRegionLayer.isPresent()) {
            JOptionPane.showMessageDialog(null, "Region layer (SAV_P) not found");
            return;
        }
        if (!optAreaLayer.isPresent()) {
            JOptionPane.showMessageDialog(null, "Roads layer (PLO_P) not found");
            return;
        }

        SelectableLayer regionLayer = optRegionLayer.get();
        SelectableLayer areaLayer = optAreaLayer.get();

        if (Utilities.getLayerType(regionLayer) != Utilities.LayerType.POLYGON) {
            JOptionPane.showMessageDialog(null, "Region layer (SAV_P) geometry is not polygons");
            return;
        }
        if (Utilities.getLayerType(areaLayer) != Utilities.LayerType.POLYGON) {
            JOptionPane.showMessageDialog(null, "Region layer (PLO_P) geometry is not polygons");
            return;
        }

        CoordinateReferenceSystem crs = regionLayer.getSimpleFeatureSource().getSchema().getCoordinateReferenceSystem();

        try {
            List<CompletableFuture<CalculationResult>> resultsFutures = new ArrayList<>();
            SimpleFeatureCollection features = regionLayer.getSimpleFeatureSource().getFeatures();
            SimpleFeatureIterator featureIterator = features.features();
            while (featureIterator.hasNext()) {
                SimpleFeature feature = featureIterator.next();
                resultsFutures.add(CompletableFuture.supplyAsync(() -> calculate(feature, areaLayer, crs)));
            }
            List<CalculationResult> results = new ArrayList<>();
            for (CompletableFuture<CalculationResult> future: resultsFutures) {
                results.add(future.get());
            }

            results.sort((x, y) -> x.getRegionName().compareToIgnoreCase(y.getRegionName()));

            for (CalculationResult result: results) {
                System.out.println("-- " + result.getRegionName() + " (" + result.getRegionArea() + " m^2): ");
                System.out.println("Hidrografijos teritorija: " + result.getHydroArea() + " m^2 (" +
                                    result.getHydroPercentage() + " %)");
                System.out.println("Medžiai ir krūmai: " + result.getForestArea() + " m^2 (" +
                                    result.getForestPercentage() + " %)");
                System.out.println("Ūžstatyta teritorija: " + result.getBuildingArea() + " m^2 (" +
                                    result.getBuildingPercentage() + " %)");
                System.out.println("Pramoniniai sodai: " + result.getGardenArea() + " m^2 (" +
                                    result.getGardenPercentage() + " %)");
            }

            JFrame frame = new JFrame("Area statistics");
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

    public CalculationResult calculate(SimpleFeature region, FeatureLayer areaLayer, CoordinateReferenceSystem crs) {
        String regionName = (String) region.getAttribute("SAV");
        System.out.println("Starting " + regionName);

        String areaGeomLocalName = areaLayer.getSimpleFeatureSource().getSchema().getGeometryDescriptor().getLocalName();
        Geometry regionGeometry = (Geometry) region.getDefaultGeometry();

        DoubleAdder hydroArea = new DoubleAdder();
        DoubleAdder forestsArea = new DoubleAdder();
        DoubleAdder buildingsArea = new DoubleAdder();
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

                Filter filter = ff.intersects(ff.property(areaGeomLocalName), ff.literal(squareGeometry));

                try {
                    areaLayer.getSimpleFeatureSource().getFeatures(filter).accepts(area -> {
                        Geometry areaGeometry = (Geometry) ((SimpleFeature) area).getDefaultGeometry();
                        Geometry intersected = squareRegion.intersection(areaGeometry);
                        double intersectedArea = intersected.getArea();
                        switch (Utilities.getAreaType((SimpleFeature) area)) {
                            case GARDEN:
                                gardensArea.add(intersectedArea);
                                break;
                            case HYDRO:
                                hydroArea.add(intersectedArea);
                                break;
                            case BUILDING:
                                buildingsArea.add(intersectedArea);
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

        System.out.println("Finished: " + regionName);
        return new CalculationResult(regionName, regionGeometry.getArea(), hydroArea.doubleValue(), forestsArea.doubleValue(),
                buildingsArea.doubleValue(), gardensArea.doubleValue());
    }

    private class CalculationResult{
        private String regionName;
        private double regionArea;
        private double hydroArea;
        private double forestArea;
        private double buildingArea;
        private double gardenArea;

        public CalculationResult(String regionName, double regionArea, double hydroArea, double forestArea, double buildingArea, double gardenArea) {
            this.regionName = regionName;
            this.regionArea = regionArea;
            this.hydroArea = hydroArea;
            this.forestArea = forestArea;
            this.buildingArea = buildingArea;
            this.gardenArea = gardenArea;
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

        public double getBuildingArea() {
            return buildingArea;
        }

        public void setBuildingArea(double buildingArea) {
            this.buildingArea = buildingArea;
        }

        public double getGardenArea() {
            return gardenArea;
        }

        public void setGardenArea(double gardenArea) {
            this.gardenArea = gardenArea;
        }

        public double getHydroPercentage() {
            return hydroArea/regionArea*100;
        }

        public double getForestPercentage() {
            return forestArea/regionArea*100;
        }

        public double getBuildingPercentage() {
            return buildingArea/regionArea*100;
        }

        public double getGardenPercentage() {
            return gardenArea/regionArea*100;
        }
    }

    private class ResultWindow extends JPanel {
        public ResultWindow(List<CalculationResult> results) {
            super(new GridLayout(1, 0));
            String[] columnNames = {"Region Name", "Region Area (m^2)", "Hydro Area (m^2)", "Hydro Percentage",
                                            "Forest Area (m^2)", "Forest Percentage",
                                            "Built Area (m^2)", "Built Percentage",
                                            "Garden Area (m^2)", "Garden Percentage"};
            String[][] data = new String[results.size()][10];
            for (int i = 0; i < results.size(); ++i) {
                data[i][0] = results.get(i).getRegionName();
                data[i][1] = String.format("%f", results.get(i).getRegionArea());
                data[i][2] = String.format("%f", results.get(i).getHydroArea());
                data[i][3] = String.format("%.3f %%", results.get(i).getHydroPercentage());
                data[i][4] = String.format("%f", results.get(i).getForestArea());
                data[i][5] = String.format("%.3f %%", results.get(i).getForestPercentage());
                data[i][6] = String.format("%f", results.get(i).getBuildingArea());
                data[i][7] = String.format("%.3f %%", results.get(i).getBuildingPercentage());
                data[i][8] = String.format("%f", results.get(i).getGardenArea());
                data[i][9] = String.format("%.3f %%", results.get(i).getGardenPercentage());
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
