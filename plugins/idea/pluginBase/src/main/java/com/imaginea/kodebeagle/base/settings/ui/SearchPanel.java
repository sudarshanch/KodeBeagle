/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.imaginea.kodebeagle.base.settings.ui;

import com.imaginea.kodebeagle.base.action.RefreshActionBase;
import com.imaginea.kodebeagle.base.model.Search;
import com.intellij.openapi.ui.ComboBox;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class SearchPanel {

    private static final String TITLE4 = "KodeBeagle Api Server";
    private static final String KODEBEAGLE_SEARCH_API_URL = "KodeBeagle Api URL:";
    private static final String OVERRIDE = "Override";

    private static final int[] KODEBEAGLE_SEARCH_PANEL_COLUMN_WIDTHS =
            new int[] {29, 84, 63, 285, 0, 95, 0};

    private static final int[] KODEBEAGLE_SEARCH_PANEL_ROW_HEIGHTS = new int[] {0, 0, 0};

    private static final double[] KODEBEAGLE_SEARCH_PANEL_COLUMN_WEIGHTS =
            new double[] {0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.0E-4};

    private static final double[] KODEBEAGLE_SEARCH_PANEL_ROW_WEIGHTS =
            new double[] {0.0, 0.0, 1.0E-4};

    private final GridBagConstraints kodebeagleSearchPanelVerticalSpacer1 =
            new GridBagConstraints(0, 0, 1, 1, 0.0, 0.0, GridBagConstraints.CENTER,
                    GridBagConstraints.BOTH, new Insets(0, 0, 5, 5), 0, 0);

    private final GridBagConstraints kodebeagleSearchPanelFirstLeft =
            new GridBagConstraints(1, 1, 5, 1, 0.0, 0.0, GridBagConstraints.CENTER,
                    GridBagConstraints.BOTH, new Insets(0, 0, 0, 0), 0, 0);

    private final GridBagConstraints kodebeagleSearchPanelFirstCenter =
            new GridBagConstraints(3, 1, 2, 1, 0.0, 0.0, GridBagConstraints.CENTER,
                    GridBagConstraints.BOTH, new Insets(0, 0, 0, 5), 0, 0);

    private final GridBagConstraints kodebeagleSearchPanelFirstRight =
            new GridBagConstraints(5, 1, 1, 1, 0.0, 0.0, GridBagConstraints.CENTER,
                    GridBagConstraints.BOTH, new Insets(0, 0, 0, 0), 0, 0);

    private final JPanel spacer = new JPanel(null);
    private ComboBox kbApiURLComboBox;
    private JCheckBox kbApiURLOverrideCheckBox;

    protected SearchPanel() {
        createFields();
    }

    public final ComboBox getKbApiURLComboBox() {
        return kbApiURLComboBox;
    }

    public final JCheckBox getKbApiURLOverrideCheckBox() {
        return kbApiURLOverrideCheckBox;
    }

    private void createFields() {
        kbApiURLComboBox = new ComboBox();
        kbApiURLComboBox.setEditable(true);
        kbApiURLComboBox.setVisible(true);
        kbApiURLOverrideCheckBox = new JCheckBox();
        kbApiURLOverrideCheckBox.setVisible(true);
        kbApiURLOverrideCheckBox.setText(OVERRIDE);
        kbApiURLOverrideCheckBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(final ActionEvent e) {
                if (kbApiURLComboBox.isEnabled()) {
                    kbApiURLComboBox.setEnabled(false);
                    kbApiURLComboBox.setSelectedItem(RefreshActionBase.KODEBEAGLE_API_URL_DEFAULT);
                } else {
                    kbApiURLComboBox.setEnabled(true);
                    kbApiURLComboBox.requestFocus();
                }
            }
        });
    }

    public final JPanel getPanel() {
        JPanel elasticSearchPanel = new JPanel();
        elasticSearchPanel.setBorder(new TitledBorder(TITLE4));
        elasticSearchPanel.setLayout(new GridBagLayout());
        ((GridBagLayout) elasticSearchPanel.getLayout()).columnWidths =
                KODEBEAGLE_SEARCH_PANEL_COLUMN_WIDTHS;
        ((GridBagLayout) elasticSearchPanel.getLayout()).rowHeights =
                KODEBEAGLE_SEARCH_PANEL_ROW_HEIGHTS;
        ((GridBagLayout) elasticSearchPanel.getLayout()).columnWeights =
                KODEBEAGLE_SEARCH_PANEL_COLUMN_WEIGHTS;
        ((GridBagLayout) elasticSearchPanel.getLayout()).rowWeights =
                KODEBEAGLE_SEARCH_PANEL_ROW_WEIGHTS;
        elasticSearchPanel.add(spacer, kodebeagleSearchPanelVerticalSpacer1);
        elasticSearchPanel.add(new JLabel(KODEBEAGLE_SEARCH_API_URL), kodebeagleSearchPanelFirstLeft);
        elasticSearchPanel.add(kbApiURLComboBox, kodebeagleSearchPanelFirstCenter);
        elasticSearchPanel.add(kbApiURLOverrideCheckBox, kodebeagleSearchPanelFirstRight);
        return elasticSearchPanel;
    }

    public final void reset(final Search search) {
        kbApiURLComboBox.setModel(
                new DefaultComboBoxModel(
                        search.getApiURLS()));
        kbApiURLOverrideCheckBox.setSelected(
                search.getApiUrlOverrideCheckBoxValue());
        if (search.getApiUrlOverrideCheckBoxValue()) {
            kbApiURLComboBox.setEnabled(true);
            kbApiURLComboBox.setSelectedItem(search.getSelectedApiURL());
        } else {
            kbApiURLComboBox.setEnabled(false);
            kbApiURLComboBox.setSelectedItem(RefreshActionBase.KODEBEAGLE_API_URL_DEFAULT);
        }
    }

    public final Search getKodeBeagleSearch() {
        String esURL = ((JTextField) kbApiURLComboBox.getEditor().getEditorComponent()).getText();
        boolean esOverrideCheckBoxValue = kbApiURLOverrideCheckBox.isSelected();
        return new Search(esURL, esOverrideCheckBoxValue);
    }
}

