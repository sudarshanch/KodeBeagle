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

package com.kodebeagle.model

abstract class BaseFileInfo(val filePath: String) extends Serializable
  with FileInfo with LazyLoadSupport {

  val UNKNOWN_LANG: String = "Unknown"
  private var _fileName: Option[String] = None
  private var _language: Option[String] = None
  private var _fileContent: Option[String] = None
  private var _sloc: Option[Int] = None

  // This extracts file name from path, without verifying if the file actually exists.
  def fileName: String = {
    getOrCompute(_fileName, () => {
      _fileName = Option(extractFileName())
      _fileName.get
    })
  }

  // This extracts file language from path, without verifying if the file actually exists.
  def language: String = {
    getOrCompute(_language, () => {
      _language = Option(extractLang())
      _language.get
    })
  }

  def fileContent: String = {
    getOrCompute(_fileContent, () => {
      _fileContent = Option(readFileContent())
      _fileContent.get
    })
  }

  def sloc: Int = {
    getOrCompute(_sloc, () => {
      _sloc = Option(readSloc())
      _sloc.get
    })
  }

  def extractFileName(): String

  def extractLang(): String

  def readFileContent(): String

  def readSloc(): Int

}

