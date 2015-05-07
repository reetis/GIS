package gis.rytis;

import gis.rytis.actions.AddVectorLayerAction;
import gis.rytis.actions.AttributesWindowAction;
import gis.rytis.actions.SelectAction;
import gis.rytis.actions.ZoomToSelectionAction;
import gis.rytis.actions.statistics.AreaStatsAction;
import gis.rytis.actions.statistics.BuildingStatsAction;
import gis.rytis.actions.statistics.RoadStatsAction;
import org.geotools.map.MapContent;
import org.geotools.swing.JMapFrame;

import javax.swing.*;

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

        window.getToolBar().add((new JLabel("Statistics: ")));
        window.getToolBar().add(new RoadStatsAction(window.getMapPane()));
        window.getToolBar().add(new AreaStatsAction(window.getMapPane()));
        window.getToolBar().add(new BuildingStatsAction(window.getMapPane()));

        window.setVisible(true);
    }

}