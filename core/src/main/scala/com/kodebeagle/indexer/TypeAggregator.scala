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

package com.kodebeagle.indexer

import java.util.regex.Pattern

import scala.collection.{Iterable, mutable}

// for aggregation tasks on basis of types
class TypeAggregator() extends Serializable {

  import TypeAggregator._

  // # used in other types (method usage will be higher)
  var score = 0
  val methods: mutable.Set[MethodType] = mutable.Set.empty[MethodType]
  val varNames: mutable.Map[String, Int] = mutable.Map.empty[String, Int]
  val methodCount: mutable.Map[(String, Int), Int] = mutable.Map.empty[(String, Int), Int]

  def merge(other: TypeAggregator): TypeAggregator = {
    score += other.score
    mergeMethods(other.methods)
    other.methodCount.foreach(m => methodCount.update(m._1, methodCount.getOrElse(m._1, 0) + m._2))
    other.varNames.foreach(e => varNames.update(e._1, varNames.getOrElse(e._1, 0) + e._2))
    // A bit of a hack to manage the count map size
    if (varNames.size > 20) {
      val min = varNames.values.min
      varNames.retain((k, v) => v == min)
    }
    this
  }

  def merge(vars: Set[String], ms: Set[MethodType]): TypeAggregator = {
    score += 1
    mergeMethods(ms)
    vars.foreach(e => varNames.update(e, varNames.getOrElse(e, 0) + 1))
    ms.foreach(m => {
      // Only if this method is not a decl, it counts towards usage.
      if (!m.isDeclared) methodCount.update(
        (m.methodName, m.argTypes.size),
        methodCount.getOrElse((m.methodName, m.argTypes.size), 0) + 1)
    })
    // A bit of a hack to manage the count map size
    if (varNames.size > 20) {
      val min = varNames.values.min
      varNames.retain((k, v) => v == min)
    }
    this
  }

  /**
    * Method Aggregation strategy:
    * 1. If method type is declared, check if a method already exists with same name
    * and number of arguments. If yes, remove the earlier method if it is not a
    * declared method type. Finally add the new method type.
    * 2. If method type is used, check if the method with name and type numbers
    * already exists, if yes check if we have more specific types of args (Object-> Specific),
    * if yes, then replace the older one with new method else skip.
    */
  // scalastyle:off
  private def mergeMethods(otherMethods: Iterable[MethodType]): Unit = {
    otherMethods.foreach(om => om.isDeclared match {
      // 1.
      case true => {
        // Find an already encountered method with particular name, and args size
        // that was not marked as a declared method
        val existingMethod = methods.find(m => m.methodName.equals(om.methodName) &&
          m.argTypes.size == om.argTypes.size && !m.isDeclared)
        existingMethod match {
          case Some(m) => {
            methods remove m
            methods add om
          }
          case None => methods add om
        }
      }
      // 2.
      case false => {
        val existingMethod = methods.find(m => m.methodName.equals(om.methodName) &&
          m.argTypes.size == om.argTypes.size)
        existingMethod match {
          case Some(m) => if (!m.isDeclared) {
            // handle the case where existing infered method is present
            // and we have to decide how to reconcile it with the current method
            // In case the existing method is declared, we do nothing (no else block).
            if (isBetterMethodDefinition(m, om)) {
              methods remove m
              methods add om
            }

          }
          // There's no matching method encountered yet, add it
          case None => methods add om
        }
      }
    })
  }

  // scalastyle:on

  // Figure out if second method type argument is a better version of the first one?
  def isBetterMethodDefinition(m: MethodType, om: MethodType): Boolean = {
    var score = 0
    m.argTypes.zip(om.argTypes).foreach(pair => {
      if (pair._1.equalsIgnoreCase(OBJ_TYPE) &&
        !pair._2.equalsIgnoreCase(OBJ_TYPE)) {
        // second method is better for this arg type
        score += 1
      } else if (pair._2.equalsIgnoreCase(OBJ_TYPE) &&
        !pair._1.equalsIgnoreCase(OBJ_TYPE)) {
        // first method is better for this arg type
        score -= 1
      }
    })
    score > 0
  }

  def result(typeName: String): TypeAggregation = {
    val tokens = typeName.split("\\.")

    val srchText = methods.map(m => {
      val typeprefix = s"${camelCasePattern.split(typeName).mkString(" ")}"
      val methodprefix  = s"${camelCasePattern.split(m.methodName).mkString(" ")}"
      val methodText = m.argTypes
        .map(e => camelCasePattern.split(e.split("\\.").last).mkString(" ")).mkString(" ")
      s"$typeprefix $methodprefix $methodText"
    }).toSet

    TypeAggregation(typeName, score,
      context = tokens.slice(0, tokens.length - 1).toSet,
      typeSuggest = CompletionSuggest(camelCasePattern.split(typeName).toSet, typeName, score),
      methodSuggest = PayloadCompletionSuggest(
        camelCasePattern.split(methods.map(_.methodName).mkString(" ")).toSet, score,
        // Coz: https://issues.scala-lang.org/browse/SI-6476
        """{"type": """" + typeName + """"}"""),
      searchText = srchText,
      vars = varNames.map(e => VarCount(e._1, e._2)).toSet,
      methods = methodCount.map(e => MethodCount(e._1._1, e._1._2, e._2)).toSet)
  }
}

object TypeAggregator {
  val OBJ_TYPE = "java.lang.Object"
  val camelCasePattern = Pattern.compile(
    """([^\p{L}\d]+)|(?<=\D)(?=\d)|(?<=\d)(?=\D)|(?<=[\p{L}&&[^\p{Lu}]])"""
      + """(?=\p{Lu})|(?<=\p{Lu})(?=\p{Lu}[\p{L}&&[^\p{Lu}]])""")
}
