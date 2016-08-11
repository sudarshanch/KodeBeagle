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

import com.imaginea.kodebeagle.base.model.Identity;
import com.imaginea.kodebeagle.base.model.Imports;
import com.imaginea.kodebeagle.base.model.Search;
import com.imaginea.kodebeagle.base.model.Limits;
import com.imaginea.kodebeagle.base.model.Notifications;
import com.imaginea.kodebeagle.base.model.Settings;
import com.imaginea.kodebeagle.base.model.SettingsBuilder;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.ui.ComboBox;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SettingsConfigurable implements Configurable {
    public static final String KODE_BEAGLE_SETTINGS = "KodeBeagle Settings";
    private SettingsPanel settingsPanel = new SettingsPanel();
    private JLabel beagleIdValue;
    private ComboBox apiURLComboBox;
    private IdentityPanel identityPanel;
    private LimitsPanel limitsPanel;
    private ImportsPanel importsPanel;
    private SearchPanel searchPanel;
    private NotificationPanel notificationPanel;

    private void getFields() {
        beagleIdValue = settingsPanel.getBeagleIdValue();
        apiURLComboBox = settingsPanel.getEsURLComboBox();
        identityPanel = settingsPanel.getIdentityPanel();
        limitsPanel = settingsPanel.getLimitsPanel();
        importsPanel = settingsPanel.getImportsPanel();
        searchPanel = settingsPanel.getSearchPanel();
        notificationPanel = settingsPanel.getNotificationPanel();
    }

    @Nullable
    @Override
    public final JComponent createComponent() {
        JComponent panel = settingsPanel.createPanel();
        getFields();
        return panel;
    }

    @Override
    public final boolean isModified() {
        boolean isModified = false;
        Settings oldSettings = new Settings();
        Settings newSettings = getNewSettings();

        if (!oldSettings.equals(newSettings)) {
            isModified = true;
        }
        return isModified;
    }

    @Override
    public final void apply() {
        settingsPanel.getImportsPatternFilter().stopEditing();
        Settings newSettings = getNewSettings();
        List<String> currentEsURLs =
                new ArrayList<>(
                        Arrays.asList(newSettings.getSearch().getApiURLS()));
        String esURL = ((JTextField) apiURLComboBox.getEditor().getEditorComponent()).getText();
        if (!currentEsURLs.contains(esURL)) {
            currentEsURLs.add(esURL);
            newSettings.getSearch().setApiURLS(
                    currentEsURLs.toArray(new String[currentEsURLs.size()]));
        }
        newSettings.getIdentity().loadBeagleId();
        beagleIdValue.setText(newSettings.getIdentity().getBeagleIdValue());
        newSettings.save();
    }

    private Settings getNewSettings() {
        Identity identity = identityPanel.getIdentity();
        Limits limits = limitsPanel.getLimits();
        Imports imports = importsPanel.getImports();
        Search search = searchPanel.getKodeBeagleSearch();
        Notifications notifications = notificationPanel.getNotifications();
        return SettingsBuilder.settings().withIdentity(identity)
                .withLimits(limits).withImports(imports)
                .withElasticSearch(search)
                .withNotifications(notifications).build();
    }

    @Override
    public final void reset() {
        Settings mySettings = new Settings();
        identityPanel.reset(mySettings.getIdentity());
        limitsPanel.reset(mySettings.getLimits());
        importsPanel.reset(mySettings.getImports());
        searchPanel.reset(mySettings.getSearch());
        notificationPanel.reset(mySettings.getNotifications());
    }

    @Override
    public final void disposeUIResources() {

    }

    @Nls
    @Override
    public final String getDisplayName() {
        return KODE_BEAGLE_SETTINGS;
    }

    @Nullable
    @Override
    public final String getHelpTopic() {
        return null;
    }
}
