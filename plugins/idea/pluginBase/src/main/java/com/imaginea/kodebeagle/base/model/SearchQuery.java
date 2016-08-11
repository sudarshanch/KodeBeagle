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

import com.google.gson.Gson;

import java.util.HashSet;
import java.util.Set;

/**
 * Created by bipulk on 10/8/16.
 */
public class SearchQuery {


    private Set<QueryParam> queries = new HashSet<>();
    private int from = 0;
    private int size = 10;


    public String build() {

        Gson gson = new Gson();
        String query = gson.toJson(this);
        return query;
    }

    public static enum Type {

        method, type;

    }

    public int getFrom() {
        return from;
    }

    public void setFrom(int from) {
        this.from = from;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public Set<QueryParam> getQueries() {
        return queries;
    }

    public void setQueries(Set<QueryParam> queries) {
        this.queries = queries;
    }

    public void addQueryParams(QueryParam queryParam) {

        this.queries.add(queryParam);

    }

    public void addAllQueryParams(Set<QueryParam> queryParams) {

        for (QueryParam queryParam : queryParams) {

            this.queries.add(queryParam);

        }

    }

    public static class QueryParam {

        private String term;
        private Type type;

        public QueryParam(String term, Type type) {
            this.term = term;
            this.type = type;
        }

        public String getTerm() {
            return term;
        }

        public void setTerm(String term) {
            this.term = term;
        }

        public Type getType() {
            return type;
        }

        public void setType(Type type) {
            this.type = type;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            QueryParam that = (QueryParam) o;

            return ((QueryParam) o).term.equals(that.term);

        }

        @Override
        public int hashCode() {
            int result = term.hashCode();
            return result;
        }
    }


}
