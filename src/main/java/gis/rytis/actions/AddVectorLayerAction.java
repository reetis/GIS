package gis.rytis.actions;

import gis.rytis.Utilities;
import org.geotools.swing.MapPane;
import org.geotools.swing.action.MapAction;
import org.geotools.swing.data.JFileDataStoreChooser;

import java.awt.event.ActionEvent;
import java.io.File;

public class AddVectorLayerAction extends MapAction {

    public static final String TOOL_NAME = "Add Layer";
    public static final String TOOL_TIP = "Add Vector Layer";
    public static final String ICON_IMAGE = null;



    public AddVectorLayerAction(MapPane mapPane) {
        super.init(mapPane, TOOL_NAME, TOOL_TIP, ICON_IMAGE);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        // display a data store file chooser dialog for shapefiles
        File file = JFileDataStoreChooser.showOpenFile("shp", null);
        if (file == null) {
            return;
        }

        getMapPane().getMapContent().addLayers(Utilities.createLayers(file));
    }
}
