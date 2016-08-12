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

import java.util.regex.Matcher

import com.kodebeagle.indexer.{JavaDocIndexHelper, TypeDocsIndices}
import com.kodebeagle.javaparser.{JavaASTParser, SingleClassBindingResolver}
import com.kodebeagle.javaparser.JavaASTParser.ParseType
import org.eclipse.jdt.core.dom.{CompilationUnit, SimpleName}
import org.scalatest.{BeforeAndAfterAll, FunSuite}

import scala.collection.JavaConversions
import scala.io.Source

trait JavaParserSuiteTrait {

  val typePattern = "([\\p{L}_$][\\p{L}\\p{N}_$]*\\.)*[\\p{L}_$][\\p{L}\\p{N}_$]*"

  def isValidType(str: String): Unit = {
    assert(str.matches(typePattern), s"$str is not a valid java type.")
  }

  def isValidIdentifier(str: String): Unit = {
    val strOption = Option(str)
    val valid = if (strOption.isEmpty || strOption.get.isEmpty) {
      false
    } else {
      Character.isJavaIdentifierStart(strOption.get.head) &&
        !(strOption.get.tail.exists(c => !(Character.isJavaIdentifierPart(c))))
    }
    assert(valid, s"${str} is not a valid identifier.")

  }

  def constructResolver(fileName: String): Option[SingleClassBindingResolver] = {
    val stream = this.getClass.getResourceAsStream(fileName)
    val fileContent = Source.fromInputStream(stream).mkString
    val parser: JavaASTParser = new JavaASTParser(true,true)
    val cu: CompilationUnit = parser.
      getAST(fileContent, ParseType.COMPILATION_UNIT).asInstanceOf[CompilationUnit]
    val scbr = new SingleClassBindingResolver(cu)
    scbr.resolve()
    Option(scbr)
  }
}

class MethodInvocationResolverSuite extends FunSuite with BeforeAndAfterAll
  with JavaParserSuiteTrait {
  var enumTestFileResolver: Option[SingleClassBindingResolver] = None

  override def beforeAll: Unit = {
    enumTestFileResolver = constructResolver("/EnumTypeTestFile.java")
  }

  test("method invoke test") {
    import scala.collection.JavaConversions._
    val expectedRoureqMethodInvoks = Set("getRoute", "getRequest", "RoutedRequest")
    val methodDecl = enumTestFileResolver.get.getDeclaredMethods
      .find(_.getMethodName == "handleResponse").get

    val roureqMethodInvoks=enumTestFileResolver.get.getMethodInvoks.get(methodDecl).
      groupBy(_.getTargetType).get("org.apache.http.impl.client.RoutedRequest")

    roureqMethodInvoks.get.foreach {
      methodInvoke =>
        assert(expectedRoureqMethodInvoks.contains(methodInvoke.getMethodName))
    }
  }

  test("return type test") {
    import scala.collection.JavaConversions._
    val methodDeclarations = enumTestFileResolver.get.getDeclaredMethods
    if(methodDeclarations.size() > 0) {
      val methodDecl = methodDeclarations.find(_.getMethodName == "handleResponse").get
      assert("org.apache.http.impl.client.RoutedRequest".equals(methodDecl.getReturnType))
    }
  }

  test("imports test") {
    val imports = enumTestFileResolver.get.getImports
    assert(imports.size() == 65)
  }

  test("method declaration test") {
    val methodDeclaration = enumTestFileResolver.get.getDeclaredMethods
    assert(methodDeclaration.size() == 5)
  }

  test("test node types in file") {
    val nodeTypeAtPosition = enumTestFileResolver.get.getTypesAtPosition
  }

  test("test enum type") {
    assert(enumTestFileResolver.get.getClassesInFile.containsKey("InternalEnum"))
  }

  test("test enum constant declaration") {
    import scala.collection.JavaConverters._
    val enumBindings = enumTestFileResolver.get.getVariableDependencies.asScala.
      filter { case (x, y) => (x.asInstanceOf[SimpleName].getIdentifier == "X") }
    assert(enumBindings.size == 3)
  }

  test("type declaration in file") {
    val typeDeclaration = enumTestFileResolver.get.getTypeDeclarations
    assert(typeDeclaration.size == 5)
    assert(typeDeclaration.get(0).getClassName == "DefaultRequestDirector")
    assert(typeDeclaration.get(3).getClassName == "InternalEnum")

    val fullyQualifiedTypesInFile = enumTestFileResolver.get.getClassesInFile
    assert(fullyQualifiedTypesInFile.size() == 5)
    assert(fullyQualifiedTypesInFile.get(
      "DefaultRequestDirector") == "x.y.z.DefaultRequestDirector")
    assert(fullyQualifiedTypesInFile.get("DEF") == "x.y.z.DefaultRequestDirector.DEF")
    assert(fullyQualifiedTypesInFile.get("GHI") == "x.y.z.DefaultRequestDirector.DEF.GHI")
    assert(fullyQualifiedTypesInFile.get("InternalEnum") ==
      "x.y.z.DefaultRequestDirector.InternalEnum")
    assert(fullyQualifiedTypesInFile.get("ExternalEnum") == "x.y.z.ExternalEnum")
  }


}

class JavaASTParserSuite extends FunSuite with BeforeAndAfterAll with JavaParserSuiteTrait {

  import JavaConversions._

  var resolvers: Set[SingleClassBindingResolver] = Set.empty[SingleClassBindingResolver]

  override def beforeAll: Unit = {
    val files = Seq("ConnectFactoryBuilderTestFile.java",
      "ConnectFactoryBuilderTestFile.java", "ProtocolBaseTestFile.java",
      "TransportClient.java")
    resolvers = files.map(f => constructResolver(s"/$f").get).toSet
  }

  test("test method invocation target types") {
    resolvers.foreach(r => r.getMethodInvoks.toMap.foreach { case (k, v) => {
      v.foreach(e => {
        isValidType(e.getTargetType)
        isValidIdentifier(e.getMethodName)
        if(!e.getConstructor) isValidIdentifier(e.getTarget)
        isValidType(Option(e.getReturnType).getOrElse("var"))
        if(!e.getConstructor) isValidIdentifier(e.getTarget)
      })
    }
    })
  }

  test("test method declarations for valid tokens") {
    resolvers.flatMap(r => r.getDeclaredMethods).foreach(md => {
      assert(md.getArgs.size() >= 0)
      assert(md.getLocation >= 0)
      isValidIdentifier(md.getMethodName)
      md.getArgs.values().foreach(x => isValidType(x))
    })
  }

  test("test type declaration for valid types") {
    resolvers.flatMap(r => r.getTypeDeclarations).foreach(td => {
      isValidType(td.getClassName)
    })
  }

  test("test import types for valid types") {
    resolvers.flatMap(r => r.getImports).foreach(im => {
      isValidType(im)
    })
  }

  test("test super types for valid types") {
    resolvers.flatMap(r => r.getSuperType).foreach(st => {
      isValidType(st._2)
    })
  }

  test("test interfaces for valid types"){
    resolvers.flatMap(r => r.getInterfaces).foreach(in => {
      in._2.foreach(str => isValidType(str))
    })
  }

  test("javadoc for Types, methods, enums") {

    val javadocResolver: SingleClassBindingResolver =
      constructResolver("/JavadocParsingTestClass.java").get

    val comments: Set[TypeDocsIndices] = JavaDocIndexHelper.
      generateJavaDocs(1L,"test",javadocResolver)

    assert(comments.size == 4)

    for(comment: TypeDocsIndices <- comments){

      if (comment.typeName.equals("x.y.z.HelloWorld")){

        assert(comment.typeDoc.trim.contains("Test class comments"))
        assert(comment.propertyDocs.size == 2)
        comment.propertyDocs.foreach(mthdCmnt =>
          assert(mthdCmnt.propertyDoc.contains("Test class method")))
      }

      if (comment.typeName.equals("x.y.z.HelloWorldInterface")){

        assert(comment.typeDoc.trim.contains("Test interface comments"))
        assert(comment.propertyDocs.size == 1)
        comment.propertyDocs.foreach(mthdCmnt =>
          assert(mthdCmnt.propertyDoc.contains("Test interface method comments")))
      }

      if (comment.typeName.equals("x.y.z.Direction")){

        assert(comment.typeDoc.trim.contains("Test Enum comments"))
        assert(comment.propertyDocs.size == 1)
        comment.propertyDocs.foreach(mthdCmnt =>
          assert(mthdCmnt.propertyDoc.contains("Test Enum method comments")))
      }

      if (comment.typeName.equals("x.y.z.OrderedPair")){

        assert(comment.typeDoc.trim.contains("Test generic class comments"))
        assert(comment.propertyDocs.size == 1)
        comment.propertyDocs.foreach(mthdCmnt =>
          assert(mthdCmnt.propertyDoc.contains("Test generic class method comments")))
      }

    }

  }

}
