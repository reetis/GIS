package gis.rytis;

import gis.rytis.actions.*;
import gis.rytis.actions.statistics.AreaStatsAction;
import gis.rytis.actions.statistics.BuildingStatsAction;
import gis.rytis.actions.statistics.RoadStatsAction;
import org.geotools.map.MapContent;
import org.geotools.swing.JMapFrame;

import javax.swing.*;
import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {
    public static void main(String[] args) throws Exception {
        MapContent map = new MapContent();
        map.setTitle("GIS");

        JMapFrame window = new JMapFrame();
        window.enableToolBar(true);
        window.enableLayerTable(true);
        window.enableStatusBar(true);
        window.initComponents();
        window.setSize(1200, 800);
        window.setMapContent(map);
        window.setTitle("GIS");

        window.getToolBar().addSeparator();

        window.getToolBar().add(new AddVectorLayerAction(window.getMapPane()));

        window.getToolBar().addSeparator();

        window.getToolBar().add(new SelectAction(window.getMapPane()));
        window.getToolBar().add(new ZoomToSelectionAction(window.getMapPane()));

        window.getToolBar().addSeparator();

        window.getToolBar().add(new AttributesWindowAction(window.getMapPane()));

        window.getToolBar().addSeparator();

        window.getToolBar().add(new SearchAction(window.getMapPane()));

        window.getToolBar().addSeparator();

        window.getToolBar().add((new JLabel("Statistics: ")));
        window.getToolBar().add(new RoadStatsAction(window.getMapPane()));
        window.getToolBar().add(new AreaStatsAction(window.getMapPane()));
        window.getToolBar().add(new BuildingStatsAction(window.getMapPane()));

        if (args.length > 0) {
            findFiles(new File(args[0]), "shp")
                    .filter(File::exists)
                    .map(Utilities::createLayers)
                    .flatMap(Collection::stream)
                    .collect(Collectors.toList())
                    .forEach(x -> map.addLayer(x));
        }

        window.setVisible(true);
    }

    private static Stream<File> findFiles(File path, String extension) {
        if (!path.exists()) {
            return Stream.empty();
        }

        final File[] files = path.listFiles();
        if (files == null) {
            return Stream.empty();
        }

        final String lowerExtension = extension.toLowerCase();
        return Arrays.asList(files).stream()
                .filter(it -> it.getName().toLowerCase().endsWith("." + lowerExtension));
    }

}