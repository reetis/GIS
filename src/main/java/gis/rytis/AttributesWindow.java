package gis.rytis;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.util.stream.Collectors;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;

import org.geotools.data.DataAccess;
import org.geotools.data.DataStore;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.filter.text.cql2.CQL;
import org.geotools.map.FeatureLayer;
import org.geotools.map.Layer;
import org.geotools.map.event.MapAdapter;
import org.geotools.map.event.MapLayerListEvent;
import org.geotools.swing.MapPane;
import org.geotools.swing.action.SafeAction;
import org.geotools.swing.table.FeatureCollectionTableModel;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.filter.Filter;

public class AttributesWindow extends JFrame {
    private JComboBox featureTypeCBox;
    private JComboBox layerCBox;
    private JTable table;
    private JTextField text;
    private MapPane mapPane;

    public AttributesWindow(MapPane mapPane) {
        this.mapPane = mapPane;

        setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
        getContentPane().setLayout(new BorderLayout());

        text = new JTextField(80);
        text.setText("include"); // include selects everything!
        getContentPane().add(text, BorderLayout.NORTH);
        text.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    filterFeatures();
                } catch (Exception e1) {
                    System.out.println("Klaida: " + e1);
                }
            }
        });

        table = new JTable();
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.setModel(new DefaultTableModel(5, 5));
        table.setPreferredScrollableViewportSize(new Dimension(500, 200));

        table.setRowSelectionAllowed(true);
        table.setColumnSelectionAllowed(false);

        JScrollPane scrollPane = new JScrollPane(table);
        getContentPane().add(scrollPane, BorderLayout.CENTER);

        JMenuBar menubar = new JMenuBar();
        setJMenuBar(menubar);

        layerCBox = new JComboBox();
        layerCBox.setRenderer(new layerCBoxRenderer());
        menubar.add(layerCBox);

        layerCBox.addItemListener(e -> onLayerChanged());

        featureTypeCBox = new JComboBox();
        menubar.add(featureTypeCBox);

//        JMenu dataMenu = new JMenu("Data");
//        menubar.add(dataMenu);
//
//        dataMenu.add(new SafeAction("Get features") {
//            public void action(ActionEvent e) throws Throwable {
//                filterFeatures();
//            }
//        });
        pack();

        mapPane.getMapContent().addMapLayerListListener(
                new MapAdapter() {
                    @Override
                    public void layerAdded(MapLayerListEvent event) {
                        updateLayerCbox();
                    }

                    @Override
                    public void layerRemoved(MapLayerListEvent event) {
                        updateLayerCbox();
                    }

                    @Override
                    public void layerMoved(MapLayerListEvent event) {
                        updateLayerCbox();
                    }
                });

    }

    private void updateLayerCbox() {
        final Layer selectedLayer = getSelectedLayer();

        final java.util.List<FeatureLayer> layers = mapPane.getMapContent().layers().stream()
                .filter(it -> it instanceof FeatureLayer)
                .map(it -> (FeatureLayer)it)
                .collect(Collectors.toList());
        final FeatureLayer[] array = layers.toArray(new FeatureLayer[layers.size()]);

        layerCBox.setModel(new DefaultComboBoxModel<>(array));
        if (selectedLayer != null) {
            layerCBox.setSelectedItem(selectedLayer);
        }
        layerCBox.repaint();

        onLayerChanged();
    }

    private FeatureLayer getSelectedLayer() {
        return (FeatureLayer)layerCBox.getSelectedItem();
    }

    private void updateUI() throws Exception {
        ComboBoxModel cbm = new DefaultComboBoxModel(getSelectedLayerDataStore().getTypeNames());
        featureTypeCBox.setModel(cbm);

        table.setModel(new DefaultTableModel(5, 5));
    }

    private void filterFeatures() throws Exception {
        String typeName = (String) featureTypeCBox.getSelectedItem();
        SimpleFeatureSource source = getSelectedLayerDataStore().getFeatureSource(typeName);

        Filter filter = CQL.toFilter(text.getText());
        SimpleFeatureCollection features = source.getFeatures(filter);
        FeatureCollectionTableModel model = new FeatureCollectionTableModel(features);
        table.setModel(model);
    }

    private DataStore getSelectedLayerDataStore() {
        final FeatureLayer selectedLayer = getSelectedLayer();
        if (selectedLayer == null) {
            return null;
        }
        final DataAccess<SimpleFeatureType, SimpleFeature> dataAccess =
                selectedLayer.getSimpleFeatureSource().getDataStore();
        if (!(dataAccess instanceof DataStore)) {
            return null;
        }

        return (DataStore) dataAccess;
    }

    private void onLayerChanged() {
        final DataStore dataStore = getSelectedLayerDataStore();
        if (dataStore == null) {
            return;
        }
        try {
            featureTypeCBox.setModel(new DefaultComboBoxModel<>(dataStore.getTypeNames()));
        } catch (IOException e) {
            System.out.println("Klaida " + e);
        }

        updateTableFromSelection();
    }

    private void updateTableFromSelection() {
        final SelectableLayer selectableLayer = getSelectableLayer();
        if (selectableLayer == null) {
            return;
        }

        table.clearSelection();
        table.setModel(new FeatureCollectionTableModel(selectableLayer.getSelectedFeatures()));
    }

    private SelectableLayer getSelectableLayer() {
        final FeatureLayer selectedLayer = getSelectedLayer();
        if (selectedLayer == null) {
            return null;
        }
        if (! (selectedLayer instanceof SelectableLayer)) {
            return null;
        }

        return (SelectableLayer) selectedLayer;
    }

    private static class layerCBoxRenderer extends JLabel implements ListCellRenderer<Layer> {

        @Override
        public Component getListCellRendererComponent(JList<? extends Layer> list, Layer value, int index, boolean isSelected, boolean cellHasFocus) {
            if (value == null) {
                setText("");
            } else {
                setText(value.getTitle());
            }
            return this;
        }
    }
}
