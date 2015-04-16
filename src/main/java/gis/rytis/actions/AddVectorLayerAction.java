package gis.rytis.actions;

import gis.rytis.SelectableLayer;
import org.geotools.data.DataStore;
import org.geotools.data.DataStoreFinder;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.map.Layer;
import org.geotools.swing.MapPane;
import org.geotools.swing.action.MapAction;
import org.geotools.swing.data.JFileDataStoreChooser;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

public class AddVectorLayerAction extends MapAction {

    public static final String TOOL_NAME = "Add Vector Layer";
    public static final String TOOL_TIP = "Add Vector Layer";
    public static final String ICON_IMAGE = null;

    public static final Color[] colors = {  Color.BLUE.darker(),
                                            Color.BLACK,
                                            Color.RED.darker(),
                                            Color.GREEN.darker(),
                                            Color.GRAY};
    public static int colorIter = 0;

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

        try {
            Map<String,Object> params = new HashMap<>();
            params.put( "url", file.toURI().toURL());
            params.put( "create spatial index", true);
            params.put( "memory mapped buffer", true);
            params.put( "charset", Charset.forName("Windows-1257") );

            DataStore store = DataStoreFinder.getDataStore(params);

            for (String typeName : store.getTypeNames()) {
                SimpleFeatureSource featureSource = store.getFeatureSource(typeName);
                String layerTitle = file.getName() + "#" + typeName;

                Layer layer = new SelectableLayer(layerTitle, featureSource, colors[colorIter%colors.length], colors[colorIter%colors.length].brighter());
                colorIter++;
                getMapPane().getMapContent().addLayer(layer);
            }


        } catch (IOException exc) {
            System.out.println("Could not read file: " + exc);
        }
    }
}
