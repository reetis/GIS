package gis.rytis;

import org.geotools.data.DataAccess;
import org.geotools.data.DataStore;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.filter.text.cql2.CQLException;
import org.geotools.filter.text.ecql.ECQL;
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

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.util.Optional;
import java.util.stream.Collectors;

public class AttributesWindow extends JFrame {
    private JComboBox<String> featureTypeCBox;
    private JComboBox<FeatureLayer> layerCBox;
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
        text.addActionListener(e -> {
            try {
                showFeatures();
            } catch (Exception e1) {
                System.out.println("Klaida: " + e1);
            }
        });

        table = new JTable();
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.setModel(new DefaultTableModel());
        table.setPreferredScrollableViewportSize(new Dimension(500, 200));

        table.setRowSelectionAllowed(true);
        table.setColumnSelectionAllowed(false);

        JScrollPane scrollPane = new JScrollPane(table);
        getContentPane().add(scrollPane, BorderLayout.CENTER);

        JMenuBar menubar = new JMenuBar();
        setJMenuBar(menubar);

        layerCBox = new JComboBox<>();
        layerCBox.setRenderer(new layerCBoxRenderer());
        menubar.add(layerCBox);

        layerCBox.addItemListener(e -> onLayerChanged());

        featureTypeCBox = new JComboBox<>();
        menubar.add(featureTypeCBox);

        JMenu dataMenu = new JMenu("Selection");
        menubar.add(dataMenu);

        dataMenu.add(new SafeAction("Get selected") {
            public void action(ActionEvent e) throws Throwable {
//                showFeatures();
            }
        });
        dataMenu.add(new SafeAction("Select on map") {
            public void action(ActionEvent e) throws Throwable {
//                showFeatures();
            }
        });
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
        Optional<DataStore> dataStore = getSelectedLayerDataStore();
        if (!dataStore.isPresent()) {
            System.out.println("Klaida: Nėra pasirinkta sluoksnio ( updateUI() )");
            return;
        }
        ComboBoxModel<String> cbm = new DefaultComboBoxModel<>(dataStore.get().getTypeNames());
        featureTypeCBox.setModel(cbm);

        table.setModel(new DefaultTableModel(5, 5));
    }

    public void showFeatures() {
        String typeName = (String) featureTypeCBox.getSelectedItem();

        Optional<DataStore> dataStore = getSelectedLayerDataStore();
        if (!dataStore.isPresent()) {
            System.out.println("Klaida: Nėra pasirinkta sluoksnio ( showFeatures() )");
            table.setModel(new DefaultTableModel());
            return;
        }
        try {
            SimpleFeatureSource source = dataStore.get().getFeatureSource(typeName);
            Filter filter = ECQL.toFilter(text.getText());
            SimpleFeatureCollection features = source.getFeatures(filter);
            FeatureCollectionTableModel model = new FeatureCollectionTableModel(features);
            table.setModel(model);
        } catch (IOException|CQLException e) {
            System.out.println("Klaida: " + e);
        }
    }

    private Optional<DataStore> getSelectedLayerDataStore() {
        final FeatureLayer selectedLayer = getSelectedLayer();
        if (selectedLayer == null) {
            return Optional.empty();
        }
        final DataAccess<SimpleFeatureType, SimpleFeature> dataAccess = selectedLayer.getSimpleFeatureSource().getDataStore();
        if (!(dataAccess instanceof DataStore)) {
            return Optional.empty();
        }

        return Optional.of((DataStore) dataAccess);
    }

    private void onLayerChanged() {
        Optional<DataStore> dataStore = getSelectedLayerDataStore();
        if (!dataStore.isPresent()) {
            System.out.println("Klaida: Nėra pasirinkta sluoksnio ( onLayerChanged() )");
            featureTypeCBox.setModel(new DefaultComboBoxModel<>());
            return;
        }
        try {
            featureTypeCBox.setModel(new DefaultComboBoxModel<>(dataStore.get().getTypeNames()));
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
