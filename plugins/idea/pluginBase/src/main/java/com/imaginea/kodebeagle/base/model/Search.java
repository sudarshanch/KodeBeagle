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

package com.imaginea.kodebeagle.base.model;

import com.imaginea.kodebeagle.base.action.RefreshActionBase;
import com.intellij.ide.util.PropertiesComponent;

import java.util.Arrays;

public class Search {

    private static final int PRIME = 31;
    private String selectedApiURL;
    private String[] apiURLS;
    private boolean apiUrlOverrideCheckBoxValue;
    private final PropertiesComponent propertiesComponent = PropertiesComponent.getInstance();

    public Search(final String pSelectedApiURL, final boolean pEsOverrideCheckBoxValue) {
        this.selectedApiURL = pSelectedApiURL;
        this.apiUrlOverrideCheckBoxValue = pEsOverrideCheckBoxValue;
        if (propertiesComponent.isValueSet(RefreshActionBase.KB_API_URL_VALUES)) {
            apiURLS = propertiesComponent.getValues(RefreshActionBase.KB_API_URL_VALUES);
        }
    }

    public Search() {
        retrieve();
    }

    public final String getSelectedApiURL() {
        return selectedApiURL;
    }

    public final String[] getApiURLS() {

        if (apiURLS == null) {
            apiURLS = new String[]{};
        }
        return apiURLS.clone();
    }

    public final boolean getApiUrlOverrideCheckBoxValue() {
        return apiUrlOverrideCheckBoxValue;
    }

    public final void setApiUrlOverrideCheckBoxValue(final boolean pEsOverrideCheckBoxValue) {
        this.apiUrlOverrideCheckBoxValue = pEsOverrideCheckBoxValue;
    }


    public final void setSelectedApiURL(final String pSelectedEsURL) {
        this.selectedApiURL = pSelectedEsURL;
    }

    public final void setApiURLS(final String[] pEsURLS) {
        if (pEsURLS != null) {
            apiURLS = new String[pEsURLS.length];
            System.arraycopy(pEsURLS, 0, apiURLS, 0, pEsURLS.length);
        }
    }

    private void retrieve() {
        this.setApiUrlOverrideCheckBoxValue(Boolean.valueOf(propertiesComponent.getValue(
                RefreshActionBase.KB_API_URL_CHECKBOX_VALUE,
                RefreshActionBase.KB_API_URL_DEFAULT_CHECKBOX_VALUE)));
        this.setSelectedApiURL(propertiesComponent.getValue(RefreshActionBase.KB_API_URL,
                RefreshActionBase.KODEBEAGLE_API_URL_DEFAULT));
        this.setApiURLS(propertiesComponent.getValues(RefreshActionBase.KB_API_URL_VALUES));
    }

    public final void save() {

        propertiesComponent.setValue(RefreshActionBase.KB_API_URL_CHECKBOX_VALUE,
                String.valueOf(this.getApiUrlOverrideCheckBoxValue()));
        propertiesComponent.setValue(RefreshActionBase.KB_API_URL, this.getSelectedApiURL());
        propertiesComponent.setValues(RefreshActionBase.KB_API_URL_VALUES, this.getApiURLS());
    }

    @Override
    public final boolean equals(final Object obj) {
        if (obj == this) {
            return  true;
        }
        if (obj == null || this.getClass() != obj.getClass()) {
            return false;
        }
        Search mySearch = (Search) obj;
        return this.getSelectedApiURL().equals(mySearch.getSelectedApiURL());
    }

    @Override
    public final int hashCode() {
        int hashCode = 0;
        if (selectedApiURL != null) {
            hashCode = PRIME * selectedApiURL.hashCode() + hashCode;
        }
        if (apiURLS != null) {
            hashCode = PRIME * Arrays.hashCode(apiURLS) + hashCode;
        }
        return hashCode;
    }
}
