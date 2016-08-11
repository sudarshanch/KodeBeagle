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

package com.imaginea.kodebeagle.base

import com.imaginea.kodebeagle.base.util.SearchUtils
import org.scalatest.{BeforeAndAfterAll, FunSuite}

import scala.collection.JavaConversions._
import scala.collection.JavaConverters._

class KodeBeagleQueryJsonSuite extends FunSuite with BeforeAndAfterAll {

  test("Testing EsQueryJson for Java imports with methods") {
    val importVsMethods = Map("java.util.List" -> Set("add").asJava,
      "java.util.HashMap" -> Set("put").asJava)
    val size = 30
    val includeMethods = true
    val actualJSON = new SearchUtils().getQueryJson(importVsMethods,includeMethods,0,size)

    val expectedJSON: String =
      "{\"queries\":[{\"term\":\"java.util.List.add\"," +
        "\"type\":\"method\"},{\"term\":\"java.util.HashMap.put\"," +
        "\"type\":\"method\"}],\"from\":0,\"size\":30}"

    assert(actualJSON == expectedJSON)
  }

  test("Testing EsQueryJson for Java imports only") {
    val emptyMethods: Set[String] = Set()
    val importVsMethods = Map("java.util.List" -> emptyMethods.asJava,
      "java.util.HashMap" -> emptyMethods.asJava)
    val size = 30
    val includeMethods = false
    val actualJSON = new SearchUtils().getQueryJson(importVsMethods,includeMethods,0,size)

    val expectedJSON: String =
      "{\"queries\":[{\"term\":\"java.util.List\",\"type\":\"type\"}," +
        "{\"term\":\"java.util.HashMap\",\"type\":\"type\"}],\"from\":0,\"size\":30}"
    assert(actualJSON == expectedJSON)
  }

}
