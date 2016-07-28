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

package com.kodebeagle.parser

import com.kodebeagle.javaparser.{JavaASTParser, SingleClassBindingResolver}
import com.kodebeagle.javaparser.JavaASTParser.ParseType
import org.eclipse.jdt.core.dom.{CompilationUnit, SimpleName}
import org.scalatest.{BeforeAndAfterAll, FunSuite}

import scala.io.Source

class MethodInvocationResolverSuite extends FunSuite with BeforeAndAfterAll{
  var singleClassBindingResolver: Option[SingleClassBindingResolver]=None

  override def beforeAll: Unit ={
    val stream=this.getClass.getResourceAsStream("/EnumTypeTest.java")
    val fileContent=Source.fromInputStream(stream).mkString
    val parser: JavaASTParser = new JavaASTParser(true)
    val cu: CompilationUnit = parser.
      getAST(fileContent, ParseType.COMPILATION_UNIT).asInstanceOf[CompilationUnit]
    singleClassBindingResolver = Option(new SingleClassBindingResolver(cu))
    singleClassBindingResolver.get.resolve
  }

  test("method invoke test"){
    import scala.collection.JavaConversions._
    val expectedRoureqMethodInvoks=Set("getRoute","getRequest","RoutedRequest")

    val methodDecl = singleClassBindingResolver.get.getDeclaredMethods
      .find(_.getMethodName == "handleResponse").get

    val roureqMethodInvoks=singleClassBindingResolver.get.getMethodInvoks.get(methodDecl).
      groupBy(_.getTargetType).get("org.apache.http.impl.client.RoutedRequest")
    assert(roureqMethodInvoks.get.size==3)

    roureqMethodInvoks.get.foreach{
      methodInvoke =>
        assert(expectedRoureqMethodInvoks.contains(methodInvoke.getMethodName))
    }
  }

  test("imports test"){
    val imports=singleClassBindingResolver.get.getImports
    assert(imports.size()==65)
  }

  test("method declaration test"){
    val methodDeclaration=singleClassBindingResolver.get.getDeclaredMethods
    assert(methodDeclaration.size()==5)
  }

  test("test node types in file"){
    val nodeTypeAtPosition=singleClassBindingResolver.get.getTypesAtPosition
  }

  test("test enum type"){
    assert(singleClassBindingResolver.get.getClassesInFile.containsKey("InternalEnum"))
  }

  test("test enum constant declaration"){
    import scala.collection.JavaConverters._
    val enumBindings=singleClassBindingResolver.get.getVariableDependencies.asScala.
      filter{case (x,y)=> (x.asInstanceOf[SimpleName].getIdentifier=="X")}
    assert(enumBindings.size==3)
  }

  test("type declaration in file"){
    val typeDeclaration=singleClassBindingResolver.get.getTypeDeclarations
    assert(typeDeclaration.size==6)
    assert(typeDeclaration.get(0).getClassName=="DefaultRequestDirector")
    assert(typeDeclaration.get(3).getClassName=="InternalEnum")

    val fullyQualifiedTypesInFile=singleClassBindingResolver.get.getClassesInFile
    assert(fullyQualifiedTypesInFile.size()==6)
    assert(fullyQualifiedTypesInFile.get("DefaultRequestDirector")=="x.y.z.DefaultRequestDirector")
    assert(fullyQualifiedTypesInFile.get("DEF")=="x.y.z.DefaultRequestDirector.DEF")
    assert(fullyQualifiedTypesInFile.get("GHI")=="x.y.z.DefaultRequestDirector.DEF.GHI")
    assert(fullyQualifiedTypesInFile.get("InternalEnum")==
      "x.y.z.DefaultRequestDirector.InternalEnum")
    assert(fullyQualifiedTypesInFile.get("ExternalEnum")=="x.y.z.ExternalEnum")
  }

}
