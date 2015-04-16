package gis.rytis;

import gis.rytis.actions.AddVectorLayerAction;
import gis.rytis.actions.AttributesWindowAction;
import gis.rytis.actions.SelectAction;
import gis.rytis.actions.ZoomToSelectionAction;
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

        JButton btn;

        window.getToolBar().addSeparator();

        btn = new JButton(new AddVectorLayerAction(window.getMapPane()));
        window.getToolBar().add(btn);

        window.getToolBar().addSeparator();

        btn = new JButton(new SelectAction(window.getMapPane()));
        window.getToolBar().add(btn);

        btn = new JButton(new ZoomToSelectionAction(window.getMapPane()));
        window.getToolBar().add(btn);

        window.getToolBar().addSeparator();

        btn = new JButton(new AttributesWindowAction(window.getMapPane()));
        window.getToolBar().add(btn);

        window.getToolBar().addSeparator();

        window.setVisible(true);
    }

}