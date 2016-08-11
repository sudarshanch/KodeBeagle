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

package com.imaginea.kodebeagle.base.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import com.imaginea.kodebeagle.base.action.RefreshActionBase;
import com.imaginea.kodebeagle.base.model.SearchQuery;
import com.imaginea.kodebeagle.base.model.Settings;
import com.imaginea.kodebeagle.base.object.WindowObjects;
import com.imaginea.kodebeagle.base.ui.KBNotification;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SearchUtils {

    private static final String HITS = "hits";
    private static final String TOTAL_COUNT = "total_hits";
    private static final String TYPES = "types";
    private static final String SOURCEFILE_SEARCH = "/source/";
    private static final String FAILED_HTTP_ERROR = "Connection Error: ";
    private static final String USER_AGENT = "USER-AGENT";
    private static final int HTTP_OK_STATUS = 200;
    private static final String FILE_NAME = "fileName";
    private static final String UID_WITH_AMPERSAND = "&uid=";
    private static final String UID = "?uid=";
    private static final String OPTED_OUT = "opted-out";
    private static final String TYPE_EXACT_NAME = "name";
    public static final String SCORE = "score";
    public static final String PROPS = "props";
    public static final String LINES = "lines";
    public static final String GITHUB_BLOB = "blob";
    public static final String DOT = ".";

    private static WindowObjects windowObjects = WindowObjects.getInstance();
    private int resultCount;
    private long totalHitsCount;

    public final long getTotalHitsCount() {
        return totalHitsCount;
    }

    public final int getResultCount() {
        return resultCount;
    }


    public final void fetchContentsAndUpdateMap(final List<String> fileNames) {

            for (String fileName : fileNames) {
                if (!fileName.isEmpty()) {

                    String  kbFileResultJson = getResponseJson(windowObjects.getKbAPIURL() + SOURCEFILE_SEARCH, fileName, true);



                    windowObjects.getFileNameContentsMap().put(fileName,
                            kbFileResultJson);
                }
            }
    }

    public final String getContentsForFile(final String fileName) {
        Map<String, String> fileNameContentsMap =
                windowObjects.getFileNameContentsMap();

        if (!fileNameContentsMap.containsKey(fileName)) {

            fetchContentsAndUpdateMap(Arrays.asList(fileName));

        }
        String fileContent = fileNameContentsMap.get(fileName);

        return fileContent;
    }

    public final Map<String, String> getFileTokens(final String kbResultJson) {
        Map<String, String> fileTokenMap = new HashMap<String, String>();
        final JsonObject hitsObject = getJsonElements(kbResultJson);
        if (hitsObject != null) {
            JsonArray hitsArray = getJsonHitsArray(hitsObject);
            resultCount = hitsArray.size();
            totalHitsCount = getTotalHits(hitsObject);
            for (JsonElement hits : hitsArray) {
                JsonObject hitObject = hits.getAsJsonObject();
                String fileName = hitObject.getAsJsonPrimitive(FILE_NAME).getAsString();
                String types = hitObject.getAsJsonArray(TYPES).toString();
                fileTokenMap.put(fileName, types);
            }
        }
        return fileTokenMap;
    }

    protected final JsonObject getJsonElements(final String esResultJson) {
        JsonReader reader = new JsonReader(new StringReader(esResultJson));
        reader.setLenient(true);
        JsonElement jsonElement = new JsonParser().parse(reader);
        if (jsonElement.isJsonObject()) {
            return jsonElement.getAsJsonObject();
        } else {
            return null;
        }
    }

    private Long getTotalHits(final JsonObject hitsObject) {
        return hitsObject.get(TOTAL_COUNT).getAsLong();
    }

    private JsonArray getJsonHitsArray(final JsonObject hitsObject) {
        return hitsObject.getAsJsonArray(HITS);
    }

    public final String getResponseJson(final String url, final String query, final boolean appendNewLineInReponse) {
        StringBuilder stringBuilder = new StringBuilder();
        try {
            HttpClient httpClient = new DefaultHttpClient();
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8.name());
            String esGetURL = url + encodedQuery;
            Settings currentSettings = new Settings();
            HttpGet getRequest;
            if (!currentSettings.getIdentity().getOptOutCheckBoxValue()) {

                if (esGetURL.contains("?")) {
                    
                    esGetURL = esGetURL + UID_WITH_AMPERSAND + windowObjects.getBeagleId();

                }
                else {

                    esGetURL = esGetURL + UID + windowObjects.getBeagleId();

                }
                String versionInfo = windowObjects.getOsInfo() + "  "
                        + windowObjects.getApplicationVersion() + "  "
                        + windowObjects.getPluginVersion();

                getRequest = new HttpGet(esGetURL);
                getRequest.setHeader(USER_AGENT, versionInfo);
            } else {
                esGetURL = esGetURL + OPTED_OUT;
                getRequest = new HttpGet(esGetURL);
            }
            HttpResponse response = httpClient.execute(getRequest);
            if (response.getStatusLine().getStatusCode() != HTTP_OK_STATUS) {
                throw new RuntimeException(FAILED_HTTP_ERROR
                        + response.getStatusLine().getStatusCode() + "  "
                        + response.getStatusLine().getReasonPhrase());
            }

            BufferedReader bufferedReader = new BufferedReader(
                    new InputStreamReader((response.getEntity().getContent()),
                            StandardCharsets.UTF_8.name()));
            String output;
            while ((output = bufferedReader.readLine()) != null) {

                if (appendNewLineInReponse) {
                    stringBuilder.append(output + "\n");
                }
                else {

                    stringBuilder.append(output);
                }
            }
            bufferedReader.close();
            httpClient.getConnectionManager().shutdown();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            return handleHttpException(e);
        }
        return stringBuilder.toString();
    }

    private String handleHttpException(final Exception e) {
        KBNotification.getInstance().error(e);
        e.printStackTrace();
        return RefreshActionBase.EMPTY_KB_API_URL;
    }

    public final String getProjectName(final String fileName) {
        //Project name is till 2nd '/'
        int startIndex = fileName.indexOf('/');
        int endIndex = fileName.indexOf('/', startIndex + 1);
        return fileName.substring(0, endIndex);
    }

    public final void updateRepoStarsMap(final String kbResultJson) {

        JsonArray hitsArray = getJsonHitsArray(getJsonElements(kbResultJson));
        hitsArray.toString();
        for (JsonElement hit : hitsArray) {

            JsonObject hitObject = hit.getAsJsonObject();
            hitObject.toString();
            String score = hitObject.getAsJsonPrimitive(SCORE).getAsString();
            String fileName = hitObject.getAsJsonPrimitive(FILE_NAME).getAsString();
            String repoName = fileName.substring(0, fileName.indexOf(GITHUB_BLOB) -1);
            windowObjects.getRepoStarsMap().put(repoName, score);

        }
    }

    public final String getQueryJson(final Map<String, Set<String>> importsInLines,
                                     final boolean includeMethods, final int from, final int size) {

        SearchQuery searchQuery = new SearchQuery();
        searchQuery.setFrom(from);
        searchQuery.setSize(size);
        Set<SearchQuery.QueryParam> queryParams = new HashSet<>();
        for (String type : importsInLines.keySet()) {

            Set<String> methods = importsInLines.get(type);
            if (methods.size() == 0 || !includeMethods) {

                queryParams.add(new SearchQuery.QueryParam(type, SearchQuery.Type.type));

            } else {
                for (String method : methods) {

                    queryParams.add(new SearchQuery.QueryParam(type + DOT + method, SearchQuery.Type.method));

                }

            }
        }
        searchQuery.setQueries(queryParams);
        return searchQuery.build();
    }


    public final List<Integer> getLineNumbers(final Collection<String> imports,
                                              final String tokens) {

        List<Integer> lineNumbers = new ArrayList<Integer>();
        JsonReader reader = new JsonReader(new StringReader(tokens));
        reader.setLenient(true);
        JsonArray tokensArray = new JsonParser().parse(reader).getAsJsonArray();
        for (JsonElement token : tokensArray) {
            JsonObject jObject = token.getAsJsonObject();
            String typeName = jObject.getAsJsonPrimitive(TYPE_EXACT_NAME).getAsString();
            if (imports.contains(typeName)) {

                JsonArray propsArray = jObject.getAsJsonArray(PROPS);
                for (JsonElement propsInfo : propsArray) {

                    JsonArray lineNumbersArray =  propsInfo.getAsJsonObject().getAsJsonArray(LINES);
                    for (JsonElement lineNumber: lineNumbersArray) {

                        lineNumbers.add(lineNumber.getAsJsonArray().get(0).getAsInt());

                    }

                }
            }
        }
        return lineNumbers;
    }

}

